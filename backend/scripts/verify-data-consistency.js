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

// ===================== 配置 =====================
const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary_system',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 5
});

// 默认容差：0.01元（1分钱）
let TOLERANCE = 0.01;
let TARGET_USER_ID = null; // null 表示校验所有用户

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
    log.pass(`${name}${detail ? ' | ' + detail : ''}`);
  } else {
    results.failed++;
    log.fail(`${name}${detail ? ' | ' + detail : ''}`);
  }
};

/**
 * 解析命令行参数
 */
const parseArgs = () => {
  const args = process.argv.slice(2);
  for (const arg of args) {
    if (arg.startsWith('--user=')) {
      TARGET_USER_ID = parseInt(arg.slice(7), 10);
      if (isNaN(TARGET_USER_ID) || TARGET_USER_ID < 1) {
        log.error(`无效的 --user 参数：${arg}`);
        process.exit(2);
      }
    } else if (arg.startsWith('--tolerance=')) {
      TOLERANCE = parseFloat(arg.slice(12));
      if (isNaN(TOLERANCE) || TOLERANCE < 0) {
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
};

// ===================== 校验项 =====================

/**
 * 校验1：工程总额 = 子项目金额之和
 * 检测 projects.total_amount 与 SUM(subprojects.amount) 是否一致
 */
const checkProjectTotalConsistency = async () => {
  log.info('\n--- 校验1：工程总额 = 子项目金额之和 ---');

  // 查找差异超过容差的工程
  const result = await pool.query(`
    SELECT
      p.id AS project_id,
      p.name AS project_name,
      p.total_amount,
      COALESCE(SUM(sp.amount), 0) AS subproject_total,
      p.total_amount - COALESCE(SUM(sp.amount), 0) AS diff
    FROM projects p
    LEFT JOIN subprojects sp ON sp.project_id = p.id
    WHERE p.status != 'canceled'
    GROUP BY p.id, p.name, p.total_amount
    HAVING ABS(p.total_amount - COALESCE(SUM(sp.amount), 0)) > $1
    ORDER BY ABS(p.total_amount - COALESCE(SUM(sp.amount), 0)) DESC
    LIMIT 50
  `, [TOLERANCE]);

  if (result.rows.length === 0) {
    recordResult('工程总额一致性', true, '所有工程总额与子项目金额之和一致');
  } else {
    const detail = `发现 ${result.rows.length} 个工程不一致（前5个）: ` +
      result.rows.slice(0, 5).map(r =>
        `工程${r.project_id}(${r.project_name}) total=${r.total_amount} 子项目之和=${r.subproject_total} 差异=${r.diff}`
      ).join('; ');
    recordResult('工程总额一致性', false, detail);

    // 打印所有不一致工程
    log.info('  不一致工程明细：');
    result.rows.forEach(r => {
      log.info(`    工程#${r.project_id} ${r.project_name}: total_amount=${r.total_amount}, 子项目之和=${r.subproject_total}, 差异=${r.diff}`);
    });
  }
};

/**
 * 校验2：结算单总额 = 工资分配明细之和
 * 检测 wage_settlements.total_amount 与 SUM(wage_distributions.amount) 是否一致
 */
const checkSettlementTotalConsistency = async () => {
  log.info('\n--- 校验2：结算单总额 = 工资分配明细之和 ---');

  // 注意：wage_settlements 是单用户的，wage_distributions 同一 settlement_id 下
  // 可能关联了多个用户的记录，必须加 user_id 过滤
  const result = await pool.query(`
    SELECT
      ws.id AS settlement_id,
      ws.settlement_no,
      ws.user_id,
      ws.total_amount AS settlement_total,
      COALESCE(SUM(wd.amount), 0) AS distribution_total,
      ws.total_amount - COALESCE(SUM(wd.amount), 0) AS diff
    FROM wage_settlements ws
    LEFT JOIN wage_distributions wd ON wd.settlement_id = ws.id AND wd.user_id = ws.user_id
    GROUP BY ws.id, ws.settlement_no, ws.user_id, ws.total_amount
    HAVING ABS(ws.total_amount - COALESCE(SUM(wd.amount), 0)) > $1
    ORDER BY ABS(ws.total_amount - COALESCE(SUM(wd.amount), 0)) DESC
    LIMIT 50
  `, [TOLERANCE]);

  if (result.rows.length === 0) {
    recordResult('结算单总额一致性', true, '所有结算单总额与工资分配明细之和一致');
  } else {
    const detail = `发现 ${result.rows.length} 个结算单不一致（前5个）: ` +
      result.rows.slice(0, 5).map(r =>
        `结算单${r.settlement_id}(${r.settlement_no}) total=${r.settlement_total} 分配之和=${r.distribution_total} 差异=${r.diff}`
      ).join('; ');
    recordResult('结算单总额一致性', false, detail);

    log.info('  不一致结算单明细：');
    result.rows.forEach(r => {
      log.info(`    结算单#${r.settlement_id} ${r.settlement_no} 用户${r.user_id}: total=${r.settlement_total}, 分配之和=${r.distribution_total}, 差异=${r.diff}`);
    });
  }
};

/**
 * 校验3：结算快照总额 = 工资分配明细之和
 * 检测 wage_settlement_snapshots.total_amount 与 SUM(wage_distributions.amount) 是否一致
 * 这是历史上27.52元差异Bug的根因（quantity*price 反算 vs amount 直接累加）
 */
const checkSnapshotTotalConsistency = async () => {
  log.info('\n--- 校验3：结算快照总额 = 工资分配明细之和 ---');

  // 注意：wage_settlements 是单用户的，wage_distributions 同一 settlement_id 下
  // 可能关联了多个用户的记录，必须加 user_id 过滤，否则会把其他用户的分配也累加
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
    recordResult('结算快照总额一致性', true, '所有结算快照总额与工资分配明细之和一致');
  } else {
    const detail = `发现 ${result.rows.length} 个快照不一致（前5个）: ` +
      result.rows.slice(0, 5).map(r =>
        `快照${r.snapshot_id}(结算单${r.settlement_id}) 快照total=${r.snapshot_total} 分配之和=${r.distribution_total} 差异=${r.diff}`
      ).join('; ');
    recordResult('结算快照总额一致性', false, detail);

    log.info('  不一致快照明细：');
    result.rows.forEach(r => {
      log.info(`    快照#${r.snapshot_id} 结算单#${r.settlement_id} ${r.settlement_no}: 快照total=${r.snapshot_total}, 分配之和=${r.distribution_total}, 差异=${r.diff}`);
    });
  }
};

/**
 * 校验4：统计卡片1（待结算工程）与手动重算一致
 * 复现 getDashboard 卡片1的 LATERAL JOIN 逻辑，对每个施工员重算并比对
 */
const checkDashboardCard1Consistency = async () => {
  log.info('\n--- 校验4：统计卡片1（待结算工程）与手动重算一致 ---');

  // 获取所有有 settling 状态工程的用户
  const usersResult = await pool.query(`
    SELECT DISTINCT pus.user_id, u.username, u.nickname
    FROM v_project_user_settlement_status pus
    JOIN users u ON pus.user_id = u.id
    WHERE pus.settlement_status = 'settling'
      AND u.role = 'constructor'
  `);

  if (usersResult.rows.length === 0) {
    recordResult('卡片1待结算工程一致性', true, '无 settling 状态工程，跳过校验');
    return;
  }

  // 注：这里只校验 SQL 重算结果的一致性（与 getDashboard 使用相同 SQL）
  // 真正的端到端校验需通过 HTTP 接口调用，此处保证 SQL 逻辑可重放
  let allConsistent = true;
  const inconsistentUsers = [];

  for (const user of usersResult.rows) {
    if (TARGET_USER_ID && user.user_id !== TARGET_USER_ID) continue;

    // 复现 getDashboard 卡片1的 SQL
    const result = await pool.query(`
      SELECT
        COUNT(DISTINCT p.id) AS unsettled_project_count,
        COALESCE(SUM(
          CASE
            WHEN p.salary_distribution = 'average' THEN
              sp_agg.sp_total / GREATEST(COALESCE(wc.worker_count, 0), 1)
            WHEN p.salary_distribution = 'work_days' THEN
              CASE
                WHEN COALESCE(tw.total_workdays, 0) > 0 THEN
                  sp_agg.sp_total * (COALESCE(uw.user_workdays, 0) / tw.total_workdays)
                ELSE
                  sp_agg.sp_total / GREATEST(COALESCE(wc.worker_count, 0), 1)
              END
            ELSE
              sp_agg.sp_total / GREATEST(COALESCE(wc.worker_count, 0), 1)
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
        SELECT COUNT(DISTINCT pw2.user_id) AS worker_count
        FROM project_workers pw2
        WHERE pw2.project_id = p.id
      ) wc ON true
      LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(pw3.workdays), 0) AS total_workdays
        FROM project_workers pw3
        WHERE pw3.project_id = p.id
      ) tw ON true
      LEFT JOIN LATERAL (
        SELECT COALESCE(pw4.workdays, 0) AS user_workdays
        FROM project_workers pw4
        WHERE pw4.project_id = p.id AND pw4.user_id = $1
      ) uw ON true
      WHERE EXISTS (
        SELECT 1 FROM project_workers pw
        WHERE pw.project_id = p.id AND pw.user_id = $1
      )
    `, [user.user_id]);

    const unsettledProjectCount = parseInt(result.rows[0].unsettled_project_count) || 0;
    const unsettledAmount = parseFloat(result.rows[0].unsettled_amount) || 0;

    // 二次校验：用 calculateUserWage 等价逻辑手动重算
    // 取所有 settling 工程的子项目，按分配方式逐个计算
    const projectsResult = await pool.query(`
      SELECT DISTINCT p.id, p.salary_distribution
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus
        ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settling'
      WHERE EXISTS (
        SELECT 1 FROM project_workers pw
        WHERE pw.project_id = p.id AND pw.user_id = $1
      )
    `, [user.user_id]);

    let manualAmount = 0;
    for (const proj of projectsResult.rows) {
      // 子项目总额
      const spResult = await pool.query('SELECT COALESCE(SUM(amount), 0) AS total FROM subprojects WHERE project_id = $1', [proj.id]);
      const spTotal = parseFloat(spResult.rows[0].total) || 0;

      // 施工人数
      const wcResult = await pool.query('SELECT COUNT(DISTINCT user_id) AS cnt FROM project_workers WHERE project_id = $1', [proj.id]);
      const workerCount = parseInt(wcResult.rows[0].cnt) || 1;

      // 总工日
      const twResult = await pool.query('SELECT COALESCE(SUM(workdays), 0) AS total FROM project_workers WHERE project_id = $1', [proj.id]);
      const totalWorkdays = parseFloat(twResult.rows[0].total) || 0;

      // 用户工日
      const uwResult = await pool.query('SELECT COALESCE(workdays, 0) AS wd FROM project_workers WHERE project_id = $1 AND user_id = $2', [proj.id, user.user_id]);
      const userWorkdays = parseFloat(uwResult.rows[0].wd) || 0;

      // 复现 calculateUserWage 逻辑
      let userAmount;
      if (proj.salary_distribution === 'average') {
        userAmount = spTotal / Math.max(workerCount, 1);
      } else if (proj.salary_distribution === 'work_days') {
        if (totalWorkdays > 0) {
          userAmount = spTotal * (userWorkdays / totalWorkdays);
        } else {
          userAmount = spTotal / Math.max(workerCount, 1);
        }
      } else {
        userAmount = spTotal / Math.max(workerCount, 1);
      }
      manualAmount += userAmount;
    }

    const diff = Math.abs(unsettledAmount - manualAmount);
    if (diff > TOLERANCE) {
      allConsistent = false;
      inconsistentUsers.push({
        userId: user.user_id,
        username: user.username,
        sqlAmount: unsettledAmount,
        manualAmount,
        diff
      });
    }
  }

  if (allConsistent) {
    recordResult('卡片1待结算工程一致性', true, `所有 ${usersResult.rows.length} 个用户的 SQL 重算与手动重算一致`);
  } else {
    const detail = `${inconsistentUsers.length} 个用户不一致: ` +
      inconsistentUsers.map(u => `用户${u.userId}(${u.username}) SQL=${u.sqlAmount} 手动=${u.manualAmount} 差异=${u.diff}`).join('; ');
    recordResult('卡片1待结算工程一致性', false, detail);
  }
};

/**
 * 校验5：统计卡片4（月均收入）与手动重算一致
 * 复现 getDashboard 卡片4的逻辑，按结算时间过滤
 */
const checkDashboardCard4Consistency = async () => {
  log.info('\n--- 校验5：统计卡片4（月均收入）与手动重算一致 ---');

  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const monthDivisor = Math.max(currentMonth, 1);

  // 获取所有有今年结算记录的用户
  const usersResult = await pool.query(`
    SELECT DISTINCT wd.user_id, u.username, u.nickname
    FROM wage_distributions wd
    JOIN users u ON wd.user_id = u.id
    WHERE wd.settlement_id IS NOT NULL
      AND EXTRACT(YEAR FROM wd.created_at) = $1
      AND u.role = 'constructor'
  `, [currentYear]);

  if (usersResult.rows.length === 0) {
    recordResult('卡片4月均收入一致性', true, '无今年结算记录，跳过校验');
    return;
  }

  let allConsistent = true;
  const inconsistentUsers = [];

  for (const user of usersResult.rows) {
    if (TARGET_USER_ID && user.user_id !== TARGET_USER_ID) continue;

    // SQL 重算（与 getDashboard 一致）
    const sqlResult = await pool.query(`
      SELECT
        COUNT(DISTINCT p.id) AS year_settled_count,
        COALESCE(SUM(wd.amount), 0) AS year_settled_user_amount
      FROM wage_distributions wd
      INNER JOIN subprojects sp ON wd.subproject_id = sp.id
      INNER JOIN projects p ON sp.project_id = p.id
      WHERE wd.user_id = $1
        AND wd.settlement_id IS NOT NULL
        AND EXTRACT(YEAR FROM wd.created_at) = $2
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
    `, [user.user_id, currentYear]);

    const sqlCount = parseInt(sqlResult.rows[0].year_settled_count) || 0;
    const sqlAmount = parseFloat(sqlResult.rows[0].year_settled_user_amount) || 0;
    const sqlMonthlyAvgAmount = sqlAmount / monthDivisor;

    // 手动重算：直接查 wage_distributions 逐条累加
    const manualResult = await pool.query(`
      SELECT
        wd.id,
        wd.amount,
        wd.subproject_id,
        sp.project_id,
        wd.created_at
      FROM wage_distributions wd
      INNER JOIN subprojects sp ON wd.subproject_id = sp.id
      INNER JOIN projects p ON sp.project_id = p.id
      WHERE wd.user_id = $1
        AND wd.settlement_id IS NOT NULL
        AND EXTRACT(YEAR FROM wd.created_at) = $2
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
    `, [user.user_id, currentYear]);

    const manualCount = new Set(manualResult.rows.map(r => r.project_id)).size;
    const manualAmount = manualResult.rows.reduce((sum, r) => sum + (parseFloat(r.amount) || 0), 0);
    const manualMonthlyAvgAmount = manualAmount / monthDivisor;

    const diffCount = Math.abs(sqlCount - manualCount);
    const diffAmount = Math.abs(sqlMonthlyAvgAmount - manualMonthlyAvgAmount);

    if (diffCount > 0 || diffAmount > TOLERANCE) {
      allConsistent = false;
      inconsistentUsers.push({
        userId: user.user_id,
        username: user.username,
        sqlCount, manualCount, diffCount,
        sqlMonthlyAvgAmount, manualMonthlyAvgAmount, diffAmount
      });
    }
  }

  if (allConsistent) {
    recordResult('卡片4月均收入一致性', true, `所有 ${usersResult.rows.length} 个用户的 SQL 重算与手动重算一致`);
  } else {
    const detail = `${inconsistentUsers.length} 个用户不一致: ` +
      inconsistentUsers.map(u =>
        `用户${u.userId}(${u.username}) 份数SQL=${u.sqlCount}/手动=${u.manualCount} 金额SQL=${u.sqlMonthlyAvgAmount}/手动=${u.manualMonthlyAvgAmount}`
      ).join('; ');
    recordResult('卡片4月均收入一致性', false, detail);
  }
};

/**
 * 校验6：wage_distributions 无孤儿记录
 * settlement_id 非空但对应结算单已删除（ON DELETE SET NULL 后 settlement_id 应为 NULL）
 */
const checkOrphanWageDistributions = async () => {
  log.info('\n--- 校验6：wage_distributions 无孤儿记录 ---');

  // 检查 settlement_id 非空但结算单不存在的情况（理论上外键约束已保证，但防御性校验）
  const result = await pool.query(`
    SELECT COUNT(*) AS orphan_count
    FROM wage_distributions wd
    WHERE wd.settlement_id IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM wage_settlements ws WHERE ws.id = wd.settlement_id
      )
  `);

  const orphanCount = parseInt(result.rows[0].orphan_count) || 0;
  if (orphanCount === 0) {
    recordResult('工资分配无孤儿记录', true, '所有 settlement_id 非空的记录都有对应结算单');
  } else {
    recordResult('工资分配无孤儿记录', false, `发现 ${orphanCount} 条孤儿记录（settlement_id 非空但结算单不存在）`);
  }

  // 额外检查：subproject_id 指向的子项目是否存在
  const subprojectOrphanResult = await pool.query(`
    SELECT COUNT(*) AS orphan_count
    FROM wage_distributions wd
    WHERE NOT EXISTS (
      SELECT 1 FROM subprojects sp WHERE sp.id = wd.subproject_id
    )
  `);

  const subprojectOrphanCount = parseInt(subprojectOrphanResult.rows[0].orphan_count) || 0;
  if (subprojectOrphanCount > 0) {
    recordResult('工资分配子项目引用', false, `发现 ${subprojectOrphanCount} 条记录引用了不存在的子项目`);
  } else {
    recordResult('工资分配子项目引用', true, '所有 subproject_id 引用有效');
  }
};

/**
 * 校验7：视图 v_project_user_settlement_status 状态正确
 * 抽样校验：settled 状态确实有结算单，settling 状态确实有完工子项目
 */
const checkSettlementStatusView = async () => {
  log.info('\n--- 校验7：视图 v_project_user_settlement_status 状态正确 ---');

  // 校验 settled 状态：应有对应结算单
  const settledCheckResult = await pool.query(`
    SELECT COUNT(*) AS invalid_count
    FROM v_project_user_settlement_status pus
    WHERE pus.settlement_status = 'settled'
      AND pus.settlement_id IS NULL
  `);
  const invalidSettledCount = parseInt(settledCheckResult.rows[0].invalid_count) || 0;
  if (invalidSettledCount === 0) {
    recordResult('settled 状态有结算单', true, '所有 settled 状态记录都有 settlement_id');
  } else {
    recordResult('settled 状态有结算单', false, `发现 ${invalidSettledCount} 条 settled 状态但无 settlement_id`);
  }

  // 校验 settling 状态：应有已完工子项目
  const settlingCheckResult = await pool.query(`
    SELECT COUNT(*) AS invalid_count
    FROM v_project_user_settlement_status pus
    WHERE pus.settlement_status = 'settling'
      AND NOT EXISTS (
        SELECT 1 FROM subprojects sp
        WHERE sp.project_id = pus.project_id
          AND sp.status = 'completed'
      )
  `);
  const invalidSettlingCount = parseInt(settlingCheckResult.rows[0].invalid_count) || 0;
  if (invalidSettlingCount === 0) {
    recordResult('settling 状态有完工子项目', true, '所有 settling 状态工程都有已完工子项目');
  } else {
    recordResult('settling 状态有完工子项目', false, `发现 ${invalidSettlingCount} 条 settling 状态但无完工子项目`);
  }
};

/**
 * 校验8：work_days 模式下工日数据完整
 * 工程使用 work_days 分配方式时，所有施工人员应有工日数（>0）
 */
const checkWorkdaysIntegrity = async () => {
  log.info('\n--- 校验8：work_days 模式下工日数据完整 ---');

  const result = await pool.query(`
    SELECT
      p.id AS project_id,
      p.name AS project_name,
      pw.user_id,
      u.username,
      pw.workdays
    FROM projects p
    JOIN project_workers pw ON pw.project_id = p.id
    JOIN users u ON pw.user_id = u.id
    WHERE p.salary_distribution = 'work_days'
      AND (pw.workdays IS NULL OR pw.workdays <= 0)
    LIMIT 50
  `);

  if (result.rows.length === 0) {
    recordResult('工日数据完整性', true, '所有 work_days 模式工程的施工人员都有有效工日');
  } else {
    const detail = `发现 ${result.rows.length} 条无效工日记录（前5个）: ` +
      result.rows.slice(0, 5).map(r =>
        `工程${r.project_id}(${r.project_name}) 用户${r.user_id}(${r.username}) workdays=${r.workdays}`
      ).join('; ');
    recordResult('工日数据完整性', false, detail);

    log.info('  无效工日明细：');
    result.rows.forEach(r => {
      log.info(`    工程#${r.project_id} ${r.project_name} | 用户${r.user_id}(${r.username}) | workdays=${r.workdays}`);
    });
  }
};

// ===================== 主流程 =====================

/**
 * 打印汇总报告
 */
const printSummary = () => {
  console.log('\n========================================');
  console.log('  数据一致性校验报告');
  console.log('========================================');
  console.log(`  容差: ${TOLERANCE} 元`);
  console.log(`  目标用户: ${TARGET_USER_ID || '全部用户'}`);
  console.log(`  通过: ${results.passed} 项`);
  console.log(`  失败: ${results.failed} 项`);
  console.log(`  警告: ${results.warnings} 项`);
  console.log('========================================\n');

  if (results.failed > 0) {
    console.log('失败项明细：');
    results.details.filter(d => !d.passed).forEach(d => {
      console.log(`  ✗ ${d.name}`);
      console.log(`    ${d.detail}`);
    });
    console.log('');
  }
};

/**
 * 主函数
 */
const main = async () => {
  parseArgs();

  log.info('===== 数据一致性校验 =====');
  log.info(`数据库: ${process.env.DB_NAME || 'salary_system'}@${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}`);
  log.info(`容差: ${TOLERANCE} 元 | 目标用户: ${TARGET_USER_ID || '全部'}`);
  log.info('=========================\n');

  const startTime = Date.now();

  // 每个校验独立 try/catch，单项失败不影响其他校验执行
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
      log.error(`${check.name} 执行异常: ${error.message}`);
      console.error(error.stack);
      recordResult(check.name, false, `执行异常: ${error.message}`);
    }
  }

  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  printSummary();
  log.info(`校验总耗时: ${elapsed}s`);

  // 退出码：有失败项返回1，全部通过返回0
  try {
    await pool.end();
  } catch (e) { /* 忽略连接池关闭错误 */ }

  if (results.failed > 0) {
    log.fail(`存在 ${results.failed} 项不一致，请检查`);
    process.exit(1);
  } else {
    log.success('全部校验通过');
    process.exit(0);
  }
};

main();
