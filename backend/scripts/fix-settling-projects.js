/**
 * settling 状态工程诊断与修复脚本
 *
 * 背景：
 *   数据一致性校验7发现22条"settling状态工程缺少已完工子项目"的记录。
 *   新系统业务规则：settlement_status='settling'（结算中）的前提是工程有已完工子项目。
 *
 * settling 状态的两个来源（视图 CASE 分支）：
 *   A. 工程 p.status='completed'（工程已完工）→ 子项目状态应同步为completed，
 *      旧库存在工程完工但子项目状态未同步的遗留数据
 *   B. project_user_status 表 settlement_status='settling'（旧结算流程写入）→
 *      旧库残留状态，工程本身可能未完工
 *
 * 处理策略：
 *   A类：自动修复——按项目规则"工程完工时必须同步所有子项目状态"，
 *        将该工程下所有非completed子项目同步为completed（--fix时执行）
 *   B类：仅诊断输出明细（工程未完工却处于结算中，需人工判断业务真实性），
 *        不自动修改
 *
 * 用法：
 *   docker compose exec app node scripts/fix-settling-projects.js            # 仅诊断
 *   docker compose exec app node scripts/fix-settling-projects.js --fix      # 诊断 + 修复A类
 */

const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
const { Pool } = require('pg');

// ===================== 配置 =====================
const DO_FIX = process.argv.includes('--fix');

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
    log.info('  settling 状态工程诊断与修复');
    log.info(`  模式: ${DO_FIX ? '诊断 + 修复A类（工程完工但子项目未同步）' : '仅诊断（加 --fix 执行修复）'}`);
    log.info('==========================================');

    // ========== 步骤1：查询视图settling且无完工子项目的记录 ==========
    log.info('');
    log.info('[步骤 1] 查询 settling 状态且缺少完工子项目的记录...');
    const result = await client.query(`
      SELECT DISTINCT
        pus.project_id,
        p.name AS project_name,
        p.status AS project_status,
        pus.user_id,
        u.nickname AS user_nickname,
        pus.settlement_status AS pus_status,
        (SELECT COUNT(*) FROM subprojects sp WHERE sp.project_id = pus.project_id) AS sub_count,
        (SELECT COUNT(*) FROM subprojects sp WHERE sp.project_id = pus.project_id AND sp.status = 'completed') AS completed_sub_count,
        (SELECT string_agg(DISTINCT sp.status, ',') FROM subprojects sp WHERE sp.project_id = pus.project_id) AS sub_statuses
      FROM v_project_user_settlement_status pus
      INNER JOIN projects p ON p.id = pus.project_id
      INNER JOIN users u ON u.id = pus.user_id
      WHERE pus.settlement_status = 'settling'
        AND NOT EXISTS (
          SELECT 1 FROM subprojects sp
          WHERE sp.project_id = pus.project_id
            AND sp.status = 'completed'
        )
      ORDER BY pus.project_id, pus.user_id
    `);

    if (result.rows.length === 0) {
      log.success('✅ 无 settling 状态但缺少完工子项目的记录，无需处理');
      return;
    }

    log.warn(`发现 ${result.rows.length} 条记录（涉及工程去重后 ${new Set(result.rows.map(r => r.project_id)).size} 个工程）：`);
    console.log('');
    console.log(`  ${'工程ID'.padEnd(8)} ${'工程名'.padEnd(24)} ${'工程状态'.padEnd(14)} ${'用户'.padEnd(12)} ${'pus状态'.padEnd(10)} ${'子项目'.padEnd(8)} ${'子项目状态'}`);
    console.log(`  ${'-'.repeat(100)}`);
    result.rows.forEach(r => {
      console.log(`  ${String(r.project_id).padEnd(8)} ${String(r.project_name).substring(0, 22).padEnd(24)} ${String(r.project_status).padEnd(14)} ${String(`${r.user_id}(${r.user_nickname})`).substring(0, 10).padEnd(12)} ${String(r.pus_status || '-').padEnd(10)} ${String(r.sub_count).padEnd(8)} ${r.sub_statuses || '(无子项目)'}`);
    });
    console.log('');

    // ========== 步骤2：分类 ==========
    // A类：工程状态=completed（工程已完工，子项目状态未同步）
    const typeAProjects = [...new Set(
      result.rows.filter(r => r.project_status === 'completed').map(r => r.project_id)
    )];
    // B类：工程状态≠completed（旧结算流程残留的pus状态）
    const typeBProjects = [...new Set(
      result.rows.filter(r => r.project_status !== 'completed').map(r => r.project_id)
    )];

    log.info('==========================================');
    log.info('  分类结果');
    log.info('==========================================');

    if (typeAProjects.length > 0) {
      log.warn(`A类（工程已完工但子项目状态未同步，可自动修复）: ${typeAProjects.length} 个工程 [${typeAProjects.join(', ')}]`);
      log.warn('  → 修复方式：将该工程下所有非completed子项目同步为completed');
    }
    if (typeBProjects.length > 0) {
      log.warn(`B类（工程未完工却处于结算中，旧库残留，仅诊断）: ${typeBProjects.length} 个工程 [${typeBProjects.join(', ')}]`);
      typeBProjects.forEach(pid => {
        const rows = result.rows.filter(r => r.project_id === pid);
        rows.forEach(r => {
          log.warn(`  工程${pid}(${r.project_name}) 状态=${r.project_status} 用户${r.user_id}(${r.user_nickname}) pus=${r.pus_status} 子项目:${r.sub_count}个(${r.sub_statuses})`);
        });
      });
      log.warn('  → 这些工程的project_user_status为旧库结算流程残留，工程本身未完工。');
      log.warn('     若业务上这些工程确实已结算完毕，需人工确认后处理；脚本不自动修改。');
    }

    // ========== 步骤3：修复A类 ==========
    if (!DO_FIX) {
      log.info('');
      log.info('[未执行修复] 当前为诊断模式。加 --fix 参数执行A类修复（同步子项目状态）。');
      return;
    }

    if (typeAProjects.length === 0) {
      log.info('');
      log.info('无A类记录需要修复。');
      return;
    }

    log.info('');
    log.info(`[步骤 3] 修复A类：同步 ${typeAProjects.length} 个完工工程的子项目状态...`);

    // 先统计受影响的子项目数
    const beforeCount = await client.query(`
      SELECT COUNT(*) AS cnt
      FROM subprojects
      WHERE project_id = ANY($1::int[])
        AND status != 'completed'
    `, [typeAProjects]);
    const affected = parseInt(beforeCount.rows[0].cnt);
    log.info(`  待同步子项目数: ${affected} 条`);

    if (affected === 0) {
      log.warn('  无需同步的子项目（可能工程本身无子项目），跳过');
      return;
    }

    // 事务内同步子项目状态
    await client.query('BEGIN');
    try {
      const updateResult = await client.query(`
        UPDATE subprojects
        SET status = 'completed', updated_at = CURRENT_TIMESTAMP
        WHERE project_id = ANY($1::int[])
          AND status != 'completed'
      `, [typeAProjects]);
      await client.query('COMMIT');
      log.success(`已同步 ${updateResult.rowCount} 条子项目状态为 completed`);
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    }

    // 刷新物化视图
    log.info('刷新物化视图...');
    try {
      await client.query('REFRESH MATERIALIZED VIEW CONCURRENTLY mv_project_user_settlement_status');
    } catch (e) {
      log.warn(`并发刷新失败，降级为非并发刷新: ${e.message}`);
      await client.query('REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
    }
    log.success('物化视图已刷新');

    // 复核
    const recheck = await client.query(`
      SELECT COUNT(*) AS invalid_count
      FROM v_project_user_settlement_status pus
      WHERE pus.settlement_status = 'settling'
        AND NOT EXISTS (
          SELECT 1 FROM subprojects sp
          WHERE sp.project_id = pus.project_id
            AND sp.status = 'completed'
        )
    `);
    const remaining = parseInt(recheck.rows[0].invalid_count);
    log.info('');
    if (remaining === 0) {
      log.success('✅ 复核通过：所有 settling 状态工程均有完工子项目');
    } else {
      log.warn(`⚠️  仍有 ${remaining} 条 settling 记录缺少完工子项目（应为B类旧库残留，需人工处理）`);
    }

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
