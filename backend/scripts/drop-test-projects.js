/**
 * 旧库测试工程清理脚本（一次性修复工具）
 *
 * 背景：
 *   数据一致性校验7发现工程81(诗颂新建工程测试)和工程86(开发测试)
 *   为旧库遗留测试数据：状态completed但无任何子项目，关联大量用户，
 *   视图将全员标记为settling（结算中），业务上不成立。
 *   经人工决策：删除这两个测试工程。
 *
 * 处理逻辑：
 *   1. 前置校验：仅允许删除"无子项目"的工程（防止误删真实业务数据）
 *   2. 删除前输出工程完整信息+关联数据统计（供日志审计）
 *   3. 事务内 DELETE（CASCADE 级联删除 project_workers/project_user_status/
 *      project_history/files 等关联数据）
 *   4. 刷新物化视图 mv_project_user_settlement_status
 *   5. 复核：确认settling异常记录清零
 *
 * 用法：
 *   docker compose exec app node scripts/drop-test-projects.js            # 执行删除
 *   docker compose exec app node scripts/drop-test-projects.js --dry-run   # 仅预览
 */

const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
const { Pool } = require('pg');

// ===================== 配置 =====================
const DRY_RUN = process.argv.includes('--dry-run');
// 待删除的测试工程ID（人工确认为旧库遗留测试数据）
const TEST_PROJECT_IDS = [81, 86];

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [SUCCESS] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [WARN] ${msg}`),
  error: (msg) => console.error(`[${new Date().toISOString()}] [ERROR] ${msg}`)
};

// ===================== 数据库连接 =====================
let pool;
try {
  pool = require('../config/database');
} catch (e) {
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
 * 主函数
 */
async function main() {
  let client;
  try {
    client = await pool.connect();

    log.info('==========================================');
    log.info('  旧库测试工程清理');
    log.info(`  目标工程: ${TEST_PROJECT_IDS.join(', ')}`);
    log.info(`  模式: ${DRY_RUN ? 'Dry Run（仅预览，不执行）' : '执行删除'}`);
    log.info('==========================================');

    // ========== 步骤1：前置校验与信息输出 ==========
    log.info('');
    log.info('[步骤 1] 前置校验...');

    // settling 异常记录基线（删除前后对比用）
    const beforeResult = await client.query(`
      SELECT COUNT(*) AS invalid_count
      FROM v_project_user_settlement_status pus
      WHERE pus.settlement_status = 'settling'
        AND NOT EXISTS (
          SELECT 1 FROM subprojects sp
          WHERE sp.project_id = pus.project_id
            AND sp.status = 'completed'
        )
    `);
    const beforeInvalid = parseInt(beforeResult.rows[0].invalid_count);
    log.info(`  当前settling异常记录: ${beforeInvalid} 条`);

    const projectsResult = await client.query(`
      SELECT p.id, p.name, p.status, p.total_amount, p.created_at,
             (SELECT COUNT(*) FROM subprojects sp WHERE sp.project_id = p.id) AS sub_count,
             (SELECT COUNT(*) FROM project_workers pw WHERE pw.project_id = p.id) AS worker_count,
             (SELECT COUNT(*) FROM files f WHERE f.project_id = p.id) AS file_count,
             (SELECT COUNT(*) FROM wage_distributions wd
              INNER JOIN subprojects sp ON sp.id = wd.subproject_id
              WHERE sp.project_id = p.id) AS dist_count,
             (SELECT COUNT(*) FROM wage_settlements ws
              WHERE ws.project_id = p.id OR ws.project_ids @> to_jsonb(p.id)) AS settlement_count
      FROM projects p
      WHERE p.id = ANY($1::int[])
      ORDER BY p.id
    `, [TEST_PROJECT_IDS]);

    if (projectsResult.rows.length !== TEST_PROJECT_IDS.length) {
      const found = projectsResult.rows.map(r => r.id);
      const missing = TEST_PROJECT_IDS.filter(id => !found.includes(id));
      log.error(`工程不存在: ${missing.join(', ')}，退出`);
      process.exit(1);
    }

    // 逐工程校验：必须是"无子项目"才允许删除（安全护栏，防止误删真实业务数据）
    let allSafe = true;
    projectsResult.rows.forEach(p => {
      const distCount = parseInt(p.dist_count);
      const settleCount = parseInt(p.settlement_count);
      log.info(`  工程#${p.id} ${p.name} 状态=${p.status} 总额=${p.total_amount}`);
      log.info(`    子项目=${p.sub_count} 施工人员=${p.worker_count} 附件=${p.file_count} 工资分配=${distCount} 关联结算单=${settleCount}`);

      if (parseInt(p.sub_count) > 0) {
        log.error(`    ✗ 工程#${p.id} 有子项目，不属于无子项目的测试工程，拒绝删除`);
        allSafe = false;
      }
      if (distCount > 0) {
        log.error(`    ✗ 工程#${p.id} 存在工资分配记录，涉及财务数据，拒绝删除`);
        allSafe = false;
      }
      if (settleCount > 0) {
        log.error(`    ✗ 工程#${p.id} 关联结算单，涉及财务数据，拒绝删除`);
        allSafe = false;
      }
    });

    if (!allSafe) {
      log.error('安全护栏触发：存在不满足删除条件的工程，退出（请人工核对）');
      process.exit(1);
    }
    log.success('安全校验通过：目标工程均无子项目、无工资分配、无关联结算单');

    // ========== 步骤2：执行删除 ==========
    log.info('');
    if (DRY_RUN) {
      log.info('[Dry Run] 预览待执行的操作：');
      log.info(`  1. DELETE FROM projects WHERE id IN (${TEST_PROJECT_IDS.join(',')}) （CASCADE级联删除关联数据）`);
      log.info('  2. REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
      log.info('[Dry Run] 未执行。去掉 --dry-run 参数执行删除。');
      return;
    }

    log.info('[步骤 2] 事务内删除测试工程...');
    await client.query('BEGIN');
    try {
      const deleteResult = await client.query(
        'DELETE FROM projects WHERE id = ANY($1::int[]) RETURNING id, name',
        [TEST_PROJECT_IDS]
      );
      await client.query('COMMIT');
      deleteResult.rows.forEach(r => {
        log.success(`已删除工程#${r.id} ${r.name}（含级联关联数据）`);
      });
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    }

    // ========== 步骤3：刷新物化视图 ==========
    log.info('');
    log.info('[步骤 3] 刷新物化视图...');
    try {
      await client.query('REFRESH MATERIALIZED VIEW CONCURRENTLY mv_project_user_settlement_status');
      log.success('物化视图已刷新');
    } catch (e) {
      log.warn(`并发刷新失败，降级为非并发刷新（短暂锁表）: ${e.message}`);
      await client.query('REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
      log.success('物化视图已刷新（非并发模式）');
    }

    // ========== 步骤4：复核 ==========
    log.info('');
    log.info('[步骤 4] 复核删除结果...');
    const afterResult = await client.query(`
      SELECT COUNT(*) AS invalid_count
      FROM v_project_user_settlement_status pus
      WHERE pus.settlement_status = 'settling'
        AND NOT EXISTS (
          SELECT 1 FROM subprojects sp
          WHERE sp.project_id = pus.project_id
            AND sp.status = 'completed'
        )
    `);
    const afterInvalid = parseInt(afterResult.rows[0].invalid_count);
    log.info(`  settling异常记录: ${beforeInvalid} → ${afterInvalid} 条`);

    // 确认工程已删除
    const remainCheck = await client.query(
      'SELECT id FROM projects WHERE id = ANY($1::int[])',
      [TEST_PROJECT_IDS]
    );
    if (remainCheck.rows.length === 0 && afterInvalid === 0) {
      log.success('✅ 复核通过：测试工程已删除，settling异常记录已清零');
    } else if (remainCheck.rows.length === 0) {
      log.warn(`⚠️ 测试工程已删除，但仍有 ${afterInvalid} 条settling异常记录（其他原因，需人工检查）`);
    } else {
      log.error('⚠️ 复核失败：工程未完全删除，请人工检查');
      process.exit(1);
    }

    log.info('');
    log.info('==========================================');
    log.success('  测试工程清理完成！');
    log.info('==========================================');
    log.info('后续影响：');
    log.info('  1. 数据一致性校验的"校验7：settling状态有完工子项目"将转为通过');
    log.info('  2. 相关用户的工程列表不再显示这两个测试工程');

  } catch (err) {
    log.error(`执行失败: ${err.message}`);
    if (err.stack) {
      log.error(err.stack);
    }
    process.exit(1);
  } finally {
    if (client) {
      client.release();
    }
    await pool.end();
  }
}

// 执行
main();
