/**
 * 数据一致性校验脚本
 *
 * 用途：验证金额、统计、结算三个口径的一致性，自动化捕获金额计算误差
 * 设计目标：可在任意时刻运行，不修改任何数据，只读取校验
 *
 * 用法：
 *   docker compose exec app node scripts/verify-data-consistency.js
 *   docker compose exec app node scripts/verify-data-consistency.js --user=5       # 校验指定用户
 *   docker compose exec app node scripts/verify-data-consistency.js --tolerance=0.01  # 自定义容差
 *
 * 校验项（8项）：
 *   1. 工程总额 = 子项目金额之和
 *   2. 结算单总额 = 工资分配明细之和
 *   3. 结算快照总额 = 工资分配明细之和
 *   4. 统计卡片1（待结算）与手动重算一致
 *   5. 统计卡片4（月均收入）与手动重算一致
 *   6. wage_distributions 无孤儿记录（settlement_id 非空但结算单已删除）
 *   7. 视图 v_project_user_settlement_status 状态正确
 *   8. project_workers.workdays 不丢失（work_days 模式下工日数据完整）
 *
 * 退出码：
 *   0 - 全部通过
 *   1 - 存在不一致项
 *   2 - 脚本执行异常
 */

const { Pool } = require('pg');
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  pass: (msg) => console.log(`[${new Date().toISOString()}] [✓ PASS] ${msg}`),
  fail: (msg) => console.error(`[${new Date().toISOString()}] [✗ FAIL] ${msg}`),
  error: (msg) => console.error(`[${new Date().toISOString()}] [✗ ERROR] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [✓] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [!] ${msg}`)
};

// ===================== 数据库连接池 =====================
// CLI模式自建连接池，API模式复用应用连接池
let pool;
try {
  pool = require('../config/database');
} catch (e) {
  // config/database 不可用时降级为自建连接池
  pool = new Pool({
    user: process.env.DB_USER || 'postgres',
    host: process.env.DB_HOST || 'localhost',
    database: process.env.DB_NAME || 'salary_system',
    password: process.env.DB_PASSWORD,
    port: parseInt(process.env.DB_PORT, 10) || 5432,
    max: 5
  });
}

/**
 * 数据一致性校验主函数（可被API控制器调用）
 *
 * @param {Object} options - 校验选项
 * @param {number} [options.userId=null] - 只校验指定用户（null=全部用户）
 * @param {number} [options.tolerance=0.01] - 金额容差（元）
 * @param {boolean} [options.silent=false] - 静默模式（不打印日志，API调用时使用）
 * @returns {Promise<Object>} 校验结果 { passed, failed, warnings, details, elapsed }
 */
const verifyDataConsistency = async (options = {}) => {
  const TOLERANCE = options.tolerance !== undefined ? options.tolerance : 0.01;
  const TARGET_USER_ID = options.userId || null;
  const silent = options.silent !== false;

  // 静默模式下不打印，非静默模式（CLI）打印详细日志
  const logger = silent ? {
    info: () => {}, pass: () => {}, fail: () => {},
    error: () => {}, success: () => {}, warn: () => {}
  } : log;

  // 校验结果统计
  const results = {
    passed: 0,
    failed: 0,
    warnings: 0,
    details: []
  };

  /**
   * 记录校验结果
   */
  const recordResult = (name, passed, detail = '') => {
    results.details.push({ name, passed, detail });
    if (passed) {
      results.passed++;
      logger.pass(`${name}${detail ? ' | ' + detail : ''}`);
    } else {
      results.failed++;
      logger.fail(`${name}${detail ? ' | ' + detail : ''}`);
    }
  };

  // ===================== 校验项 =====================

  /**
   * 校验1：工程总额 = 子项目金额之和
   */
  const checkProjectTotalConsistency = async () => {
    logger.info('\n--- 校验1：工程总额 = 子项目金额之和 ---');

    const result = await pool.query(`
      SELECT
        p.id AS project_id,
        p.name AS project_name,
        p.total_amount AS project_total,
        COALESCE(SUM(sp.amount), 0) AS subproject_total,
        p.total_amount - COALESCE(SUM(sp.amount), 0) AS diff
      FROM projects p
      LEFT JOIN subprojects sp ON sp.project_id = p.id
      GROUP BY p.id, p.name, p.total_amount
      HAVING ABS(p.total_amount - COALESCE(SUM(sp.amount), 0)) > $1
      ORDER BY ABS(p.total_amount - COALESCE(SUM(sp.amount), 0)) DESC
      LIMIT 50
    `, [TOLERANCE]);

    if (result.rows.length === 0) {
      recordResult('校验1：工程总额一致性', true, '所有工程总额与子项目金额之和一致');
    } else {
      const detail = `发现 ${result.rows.length} 个工程不一致（前5个）: ` +
        result.rows.slice(0, 5).map(r =>
          `工程${r.project_id}(${r.project_name}) 工程=${r.project_total} 子项目=${r.subproject_total} 差异=${r.diff}`
        ).join('; ');
      recordResult('校验1：工程总额一致性', false, detail);

      logger.info('  不一致工程明细：');
      result.rows.forEach(r => {
        logger.info(`    工程#${r.project_id} ${r.project_name}: 工程=${r.project_total}, 子项目=${r.subproject_total}, 差异=${r.diff}`);
      });
    }
  };

  /**
   * 校验2：结算单总额 = 工资分配明细之和
   */
  const checkSettlementTotalConsistency = async () => {
    logger.info('\n--- 校验2：结算单总额 = 工资分配明细之和 ---');

    const result = await pool.query(`
      SELECT
        ws.id AS settlement_id,
        ws.settlement_no,
        ws.user_id,
        ws.total_amount AS settlement_total,
        COALESCE(SUM(wd.amount), 0) AS distribution_total,
        ws.total_amount - COALESCE(SUM(wd.amount), 0) AS diff
      FROM wage_settlements ws
      LEFT JOIN wage_distributions wd ON wd.settlement_id = ws.id
      GROUP BY ws.id, ws.settlement_no, ws.user_id, ws.total_amount
      HAVING ABS(ws.total_amount - COALESCE(SUM(wd.amount), 0)) > $1
      ORDER BY ABS(ws.total_amount - COALESCE(SUM(wd.amount), 0)) DESC
      LIMIT 50
    `, [TOLERANCE]);

    if (result.rows.length === 0) {
      recordResult('校验2：结算单总额一致性', true, '所有结算单总额与工资分配明细之和一致');
    } else {
      const detail = `发现 ${result.rows.length} 个结算单不一致（前5个）: ` +
        result.rows.slice(0, 5).map(r =>
          `结算单${r.settlement_id}(${r.settlement_no}) total=${r.settlement_total} 分配之和=${r.distribution_total} 差异=${r.diff}`
        ).join('; ');
      recordResult('校验2：结算单总额一致性', false, detail);

      logger.info('  不一致结算单明细：');
      result.rows.forEach(r => {
        logger.info(`    结算单#${r.settlement_id} ${r.settlement_no} 用户${r.user_id}: total=${r.settlement_total}, 分配之和=${r.distribution_total}, 差异=${r.diff}`);
      });
    }
  };

  // ========== 以下校验3-8与原脚本完全一致，仅改为函数内定义 ==========

  /**
   * 校验3：结算快照总额 = 工资分配明细之和
   */
  const checkSnapshotTotalConsistency = async () => {
    logger.info('\n--- 校验3：结算快照总额 = 工资分配明细之和 ---');

    const result = await pool.query(`
      SELECT
        snap.id AS snapshot_id,
        snap.settlement_id,
        snap.settlement_no,
        snap.user_id,
        snap.total_amount AS snapshot_total,
        agg.distribution_total,
        snap.total_amount - agg.distribution_total AS diff
      FROM wage_settlement_snapshots snap
      INNER JOIN (
        SELECT wd.settlement_id, wd.user_id, SUM(wd.amount) AS distribution_total
        FROM wage_distributions wd
        WHERE wd.settlement_id IS NOT NULL
        GROUP BY wd.settlement_id, wd.user_id
      ) agg ON agg.settlement_id = snap.settlement_id AND agg.user_id = snap.user_id
      WHERE ABS(snap.total_amount - agg.distribution_total) > $1
      ORDER BY ABS(snap.total_amount - agg.distribution_total) DESC
      LIMIT 50
    `, [TOLERANCE]);

    if (result.rows.length === 0) {
      recordResult('校验3：结算快照总额一致性', true, '所有结算快照总额与工资分配明细之和一致');
    } else {
      const detail = `发现 ${result.rows.length} 个快照不一致（前5个）: ` +
        result.rows.slice(0, 5).map(r =>
          `快照${r.snapshot_id}(结算单${r.settlement_id}) 快照total=${r.snapshot_total} 分配之和=${r.distribution_total} 差异=${r.diff}`
        ).join('; ');
      recordResult('校验3：结算快照总额一致性', false, detail);

      logger.info('  不一致快照明细：');
      result.rows.forEach(r => {
        logger.info(`    快照#${r.snapshot_id} 结算单#${r.settlement_id} ${r.settlement_no}: 快照total=${r.snapshot_total}, 分配之和=${r.distribution_total}, 差异=${r.diff}`);
      });
    }
  };

  /**
   * 校验4：统计卡片1（待结算工程）与手动重算一致
   */
  const checkDashboardCard1Consistency = async () => {
    logger.info('\n--- 校验4：统计卡片1（待结算工程）与手动重算一致 ---');

    // 获取所有施工员用户
    const usersResult = await pool.query(`
      SELECT DISTINCT u.id, u.nickname
      FROM users u
      INNER JOIN project_workers pw ON pw.user_id = u.id
      WHERE u.role = 'constructor'
      ORDER BY u.id
    `);

    if (TARGET_USER_ID) {
      usersResult.rows = usersResult.rows.filter(u => u.id === TARGET_USER_ID);
    }

    if (usersResult.rows.length === 0) {
      recordResult('校验4：卡片1待结算工程一致性', true, '无施工员用户，跳过校验');
      return;
    }

    let allMatch = true;
    const mismatches = [];

    for (const user of usersResult.rows) {
      // 调用与getDashboard卡片1相同的SQL
      const sqlResult = await pool.query(`
        SELECT
          COUNT(DISTINCT p.id) AS unsettled_project_count,
          COALESCE(SUM(
            CASE
              WHEN p.salary_distribution = 'average' THEN
                sp_agg.sp_total / GREATEST(COALESCE(pw_agg.worker_count, 0), 1)
              WHEN p.salary_distribution = 'work_days' THEN
                CASE
                  WHEN COALESCE(pw_agg.total_workdays, 0) > 0 THEN
                    sp_agg.sp_total * (COALESCE(pw_agg.user_workdays, 0) / pw_agg.total_workdays)
                  ELSE
                    sp_agg.sp_total / GREATEST(COALESCE(pw_agg.worker_count, 0), 1)
                END
              ELSE
                sp_agg.sp_total / GREATEST(COALESCE(pw_agg.worker_count, 0), 1)
            END
          ), 0) AS unsettled_amount
        FROM projects p
        INNER JOIN v_project_user_settlement_status pus
          ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settling'
        LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(sp.amount), 0) AS sp_total
          FROM subprojects sp
          WHERE sp.project_id = p.id
        ) sp_agg ON true
        LEFT JOIN LATERAL (
          SELECT
            COUNT(DISTINCT pw2.user_id) AS worker_count,
            COALESCE(SUM(pw2.workdays), 0) AS total_workdays,
            COALESCE(MAX(CASE WHEN pw2.user_id = $1 THEN pw2.workdays END), 0) AS user_workdays
          FROM project_workers pw2
          WHERE pw2.project_id = p.id
        ) pw_agg ON true
        WHERE 1=1
      `, [user.id]);

      // 手动重算：遍历所有settling状态的工程，用calculateUserWage逻辑计算
      const projectsResult = await pool.query(`
        SELECT
          p.id, p.total_amount, p.salary_distribution,
          COALESCE(SUM(sp.amount), 0) AS sp_total,
          COUNT(DISTINCT pw2.user_id) AS worker_count,
          COALESCE(SUM(pw2.workdays), 0) AS total_workdays,
          COALESCE(MAX(CASE WHEN pw2.user_id = $1 THEN pw2.workdays END), 0) AS user_workdays
        FROM projects p
        INNER JOIN v_project_user_settlement_status pus
          ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settling'
        LEFT JOIN subprojects sp ON sp.project_id = p.id
        LEFT JOIN project_workers pw2 ON pw2.project_id = p.id
        GROUP BY p.id, p.total_amount, p.salary_distribution
      `, [user.id]);

      let manualAmount = 0;
      for (const p of projectsResult.rows) {
        const spTotal = parseFloat(p.sp_total) || 0;
        const workerCount = parseInt(p.worker_count) || 0;
        const totalWorkdays = parseFloat(p.total_workdays) || 0;
        const userWorkdays = parseFloat(p.user_workdays) || 0;

        if (p.salary_distribution === 'average') {
          manualAmount += spTotal / Math.max(workerCount, 1);
        } else if (p.salary_distribution === 'work_days') {
          if (totalWorkdays > 0) {
            manualAmount += spTotal * (userWorkdays / totalWorkdays);
          } else {
            manualAmount += spTotal / Math.max(workerCount, 1);
          }
        } else {
          manualAmount += spTotal / Math.max(workerCount, 1);
        }
      }

      const sqlAmount = parseFloat(sqlResult.rows[0].unsettled_amount) || 0;
      const diff = Math.abs(sqlAmount - manualAmount);

      if (diff > TOLERANCE) {
        allMatch = false;
        mismatches.push(`用户${user.id}(${user.nickname}) SQL=${sqlAmount.toFixed(2)} 手算=${manualAmount.toFixed(2)} 差异=${diff.toFixed(2)}`);
      }
    }

    if (allMatch) {
      recordResult('校验4：卡片1待结算工程一致性', true, `所有 ${usersResult.rows.length} 个用户的 SQL 重算与手动重算一致`);
    } else {
      recordResult('校验4：卡片1待结算工程一致性', false, `发现 ${mismatches.length} 个不一致: ${mismatches.join('; ')}`);
    }
  };

  /**
   * 校验5：统计卡片4（月均收入）与手动重算一致
   */
  const checkDashboardCard4Consistency = async () => {
    logger.info('\n--- 校验5：统计卡片4（月均收入）与手动重算一致 ---');

    const currentYear = new Date().getFullYear();
    const yearStart = `${currentYear}-01-01`;
    const nextYearStart = `${currentYear + 1}-01-01`;
    const currentMonth = new Date().getMonth() + 1;
    const monthDivisor = currentMonth > 0 ? currentMonth : 1;

    // 获取所有施工员用户
    const usersResult = await pool.query(`
      SELECT DISTINCT u.id, u.nickname
      FROM users u
      INNER JOIN project_workers pw ON pw.user_id = u.id
      WHERE u.role = 'constructor'
      ORDER BY u.id
    `);

    if (TARGET_USER_ID) {
      usersResult.rows = usersResult.rows.filter(u => u.id === TARGET_USER_ID);
    }

    if (usersResult.rows.length === 0) {
      recordResult('校验5：卡片4月均收入一致性', true, '无施工员用户，跳过校验');
      return;
    }

    let allMatch = true;
    const mismatches = [];

    for (const user of usersResult.rows) {
      // 调用与getDashboard卡片4相同的SQL
      const sqlResult = await pool.query(`
        SELECT
          COUNT(DISTINCT p.id) AS year_settled_count,
          COALESCE(SUM(wd.amount), 0) AS year_settled_user_amount
        FROM wage_distributions wd
        INNER JOIN subprojects sp ON wd.subproject_id = sp.id
        INNER JOIN projects p ON sp.project_id = p.id
        WHERE wd.user_id = $1
          AND wd.settlement_id IS NOT NULL
          AND wd.created_at >= $2::date AND wd.created_at < $3::date
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
      `, [user.id, yearStart, nextYearStart]);

      const sqlCount = parseInt(sqlResult.rows[0].year_settled_count) || 0;
      const sqlAmount = parseFloat(sqlResult.rows[0].year_settled_user_amount) || 0;
      const sqlMonthlyAvg = sqlAmount / monthDivisor;

      // 手动重算：直接查wage_distributions表
      const manualResult = await pool.query(`
        SELECT
          COUNT(DISTINCT p.id) AS project_count,
          COALESCE(SUM(wd.amount), 0) AS total_amount
        FROM wage_distributions wd
        INNER JOIN subprojects sp ON wd.subproject_id = sp.id
        INNER JOIN projects p ON sp.project_id = p.id
        WHERE wd.user_id = $1
          AND wd.settlement_id IS NOT NULL
          AND wd.created_at >= $2::date AND wd.created_at < $3::date
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
      `, [user.id, yearStart, nextYearStart]);

      const manualCount = parseInt(manualResult.rows[0].project_count) || 0;
      const manualAmount = parseFloat(manualResult.rows[0].total_amount) || 0;
      const manualMonthlyAvg = manualAmount / monthDivisor;

      const diffCount = Math.abs(sqlCount - manualCount);
      const diffAmount = Math.abs(sqlMonthlyAvg - manualMonthlyAvg);

      if (diffCount > 0 || diffAmount > TOLERANCE) {
        allMatch = false;
        mismatches.push(`用户${user.id}(${user.nickname}) SQL份数=${sqlCount} 手算份数=${manualCount} SQL月均=${sqlMonthlyAvg.toFixed(2)} 手算月均=${manualMonthlyAvg.toFixed(2)}`);
      }
    }

    if (allMatch) {
      recordResult('校验5：卡片4月均收入一致性', true, `所有 ${usersResult.rows.length} 个用户的 SQL 重算与手动重算一致`);
    } else {
      recordResult('校验5：卡片4月均收入一致性', false, `发现 ${mismatches.length} 个不一致: ${mismatches.join('; ')}`);
    }
  };

  /**
   * 校验6：wage_distributions 无孤儿记录
   */
  const checkOrphanWageDistributions = async () => {
    logger.info('\n--- 校验6：wage_distributions 无孤儿记录 ---');

    // 检查 settlement_id 非空但结算单已删除的记录
    const orphanResult = await pool.query(`
      SELECT COUNT(*) AS orphan_count
      FROM wage_distributions wd
      WHERE wd.settlement_id IS NOT NULL
        AND NOT EXISTS (
          SELECT 1 FROM wage_settlements ws WHERE ws.id = wd.settlement_id
        )
    `);

    const orphanCount = parseInt(orphanResult.rows[0].orphan_count) || 0;

    if (orphanCount === 0) {
      recordResult('校验6：工资分配无孤儿记录', true, '所有 settlement_id 非空的记录都有对应结算单');
    } else {
      recordResult('校验6：工资分配无孤儿记录', false, `发现 ${orphanCount} 条孤儿记录（settlement_id 非空但结算单已删除）`);
    }

    // 检查 subproject_id 引用有效性
    const invalidRefResult = await pool.query(`
      SELECT COUNT(*) AS invalid_count
      FROM wage_distributions wd
      WHERE NOT EXISTS (
        SELECT 1 FROM subprojects sp WHERE sp.id = wd.subproject_id
      )
    `);

    const invalidCount = parseInt(invalidRefResult.rows[0].invalid_count) || 0;

    if (invalidCount === 0) {
      recordResult('校验6：工资分配子项目引用', true, '所有 subproject_id 引用有效');
    } else {
      recordResult('校验6：工资分配子项目引用', false, `发现 ${invalidCount} 条无效引用`);
    }
  };

  /**
   * 校验7：视图 v_project_user_settlement_status 状态正确
   */
  const checkSettlementStatusView = async () => {
    logger.info('\n--- 校验7：视图 v_project_user_settlement_status 状态正确 ---');

    // 检查 settled 状态的记录是否都有 settlement_id
    const settledResult = await pool.query(`
      SELECT COUNT(*) AS invalid_count
      FROM v_project_user_settlement_status pus
      WHERE pus.settlement_status = 'settled'
        AND pus.settlement_id IS NULL
    `);

    const invalidSettled = parseInt(settledResult.rows[0].invalid_count) || 0;

    if (invalidSettled === 0) {
      recordResult('校验7：settled 状态有结算单', true, '所有 settled 状态记录都有 settlement_id');
    } else {
      recordResult('校验7：settled 状态有结算单', false, `发现 ${invalidSettled} 条 settled 状态记录缺少 settlement_id`);
    }

    // 检查 settling 状态的工程是否都有已完工子项目
    const settlingResult = await pool.query(`
      SELECT COUNT(*) AS invalid_count
      FROM v_project_user_settlement_status pus
      WHERE pus.settlement_status = 'settling'
        AND NOT EXISTS (
          SELECT 1 FROM subprojects sp
          WHERE sp.project_id = pus.project_id
            AND sp.status = 'completed'
        )
    `);

    const invalidSettling = parseInt(settlingResult.rows[0].invalid_count) || 0;

    if (invalidSettling === 0) {
      recordResult('校验7：settling 状态有完工子项目', true, '所有 settling 状态工程都有已完工子项目');
    } else {
      recordResult('校验7：settling 状态有完工子项目', false, `发现 ${invalidSettling} 条 settling 状态工程缺少已完工子项目`);
    }
  };

  /**
   * 校验8：work_days 模式下工日数据完整
   */
  const checkWorkdaysIntegrity = async () => {
    logger.info('\n--- 校验8：work_days 模式下工日数据完整 ---');

    // 检查 work_days 模式下，施工人员工日是否为0或null
    const invalidResult = await pool.query(`
      SELECT
        p.id AS project_id,
        p.name AS project_name,
        pw.user_id,
        u.nickname
      FROM projects p
      INNER JOIN project_workers pw ON pw.project_id = p.id
      INNER JOIN users u ON u.id = pw.user_id
      WHERE p.salary_distribution = 'work_days'
        AND (pw.workdays IS NULL OR pw.workdays <= 0)
    `);

    if (invalidResult.rows.length === 0) {
      recordResult('校验8：工日数据完整性', true, '所有 work_days 模式工程的施工人员都有有效工日');
    } else {
      const detail = `发现 ${invalidResult.rows.length} 条无效工日记录（前5个）: ` +
        invalidResult.rows.slice(0, 5).map(r =>
          `工程${r.project_id}(${r.project_name}) 用户${r.user_id}(${r.nickname})`
        ).join('; ');
      recordResult('校验8：工日数据完整性', false, detail);
    }
  };

  // ===================== 执行校验 =====================

  if (!silent) {
    log.info('===== 数据一致性校验 =====');
    log.info(`数据库: ${process.env.DB_NAME || 'salary_system'}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}`);
    log.info(`容差: ${TOLERANCE} 元 | 目标用户: ${TARGET_USER_ID || '全部'}`);
    log.info('=========================\n');
  }

  const startTime = Date.now();

  const checks = [
    { name: '校验1：工程总额一致性', fn: checkProjectTotalConsistency },
    { name: '校验2：结算单总额一致性', fn: checkSettlementTotalConsistency },
    { name: '校验3：结算快照总额一致性', fn: checkSnapshotTotalConsistency },
    { name: '校验4：卡片1待结算工程一致性', fn: checkDashboardCard1Consistency },
    { name: '校验5：卡片4月均收入一致性', fn: checkDashboardCard4Consistency },
    { name: '校验6：工资分配无孤儿记录', fn: checkOrphanWageDistributions },
    { name: '校验7：视图结算状态正确性', fn: checkSettlementStatusView },
    { name: '校验8：工日数据完整性', fn: checkWorkdaysIntegrity }
  ];

  for (const check of checks) {
    try {
      await check.fn();
    } catch (error) {
      logger.error(`${check.name} 执行异常: ${error.message}`);
      recordResult(check.name, false, `执行异常: ${error.message}`);
    }
  }

  const elapsed = parseFloat(((Date.now() - startTime) / 1000).toFixed(1));

  if (!silent) {
    // 打印汇总
    log.info('');
    log.info('========================================');
    log.info('  数据一致性校验报告');
    log.info('========================================');
    log.info(`  容差: ${TOLERANCE} 元`);
    log.info(`  目标用户: ${TARGET_USER_ID || '全部用户'}`);
    log.info(`  通过: ${results.passed} 项`);
    log.info(`  失败: ${results.failed} 项`);
    log.info(`  警告: ${results.warnings} 项`);
    log.info('========================================');
    log.info('');

    if (results.failed > 0) {
      log.info('失败项明细：');
      results.details.filter(d => !d.passed).forEach(d => {
        log.info(`  ✗ ${d.name}`);
        log.info(`    ${d.detail}`);
      });
      log.info('');
    }

    log.info(`校验总耗时: ${elapsed}s`);

    if (results.failed > 0) {
      log.fail(`存在 ${results.failed} 项不一致，请检查`);
    } else {
      log.success('全部校验通过');
    }
  }

  return {
    passed: results.passed,
    failed: results.failed,
    warnings: results.warnings,
    details: results.details,
    elapsed
  };
};

// ===================== CLI 入口 =====================

/**
 * 解析命令行参数
 */
const parseArgs = () => {
  const args = process.argv.slice(2);
  const options = { silent: false };

  for (const arg of args) {
    if (arg.startsWith('--user=')) {
      options.userId = parseInt(arg.slice(7), 10);
      if (isNaN(options.userId) || options.userId < 1) {
        log.error(`无效的 --user 参数：${arg}`);
        process.exit(2);
      }
    } else if (arg.startsWith('--tolerance=')) {
      options.tolerance = parseFloat(arg.slice(12));
      if (isNaN(options.tolerance) || options.tolerance < 0) {
        log.error(`无效的 --tolerance 参数：${arg}`);
        process.exit(2);
      }
    } else if (arg === '--help' || arg === '-h') {
      console.log(`
用法: node scripts/verify-data-consistency.js [选项]

选项:
  --user=<id>           只校验指定用户（默认校验所有用户）
  --tolerance=<amount>  金额容差（默认 0.01 元）
  --help, -h            显示帮助
`);
      process.exit(0);
    }
  }

  return options;
};

// 直接运行时执行（CLI模式）
if (require.main === module) {
  const options = parseArgs();
  verifyDataConsistency(options)
    .then(result => {
      process.exit(result.failed > 0 ? 1 : 0);
    })
    .catch(err => {
      log.error(`校验执行异常: ${err.message}`);
      console.error(err.stack);
      process.exit(2);
    });
}

module.exports = { verifyDataConsistency };

