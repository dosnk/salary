/**
 * 刷新工程总额脚本（独立一次性工具）
 *
 * 用途：迁移数据后，用子项目金额之和修正冗余字段 projects.total_amount
 *
 * 背景：
 *   旧库的 projects.total_amount 是冗余字段，由旧系统 JavaScript 浮点运算更新，
 *   累积了 IEEE754 精度误差（0.01-0.05 元级别）。
 *   新系统统计金额源已统一从 subprojects.amount 聚合，不依赖该冗余字段，
 *   但为保持数据整洁，可在迁移后执行本脚本刷新一次。
 *
 * 安全性：
 *   - 仅更新 total_amount 与子项目金额之和不一致的工程，一致的跳过
 *   - 执行前输出差异明细供核查，执行后输出更新结果
 *   - 不影响 subprojects、wage_distributions 等任何其他表
 *
 * 用法：
 *   docker compose exec app node scripts/refresh-project-totals.js           # 执行刷新
 *   docker compose exec app node scripts/refresh-project-totals.js --dry-run # 仅查看差异，不执行更新
 *
 * 退出码：
 *   0 - 成功（含无需刷新的情况）
 *   1 - 执行失败
 */

const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
const { Pool } = require('pg');

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [SUCCESS] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [WARN] ${msg}`),
  error: (msg) => console.error(`[${new Date().toISOString()}] [ERROR] ${msg}`)
};

// ===================== 配置 =====================
const cliArgs = process.argv.slice(2);
const DRY_RUN = cliArgs.includes('--dry-run') || cliArgs.includes('-n');

const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary_system',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 5,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000
});

/**
 * 主函数：刷新工程总额
 */
async function refresh() {
  let client;
  try {
    client = await pool.connect();

    log.info('==========================================');
    log.info('  刷新工程总额（用子项目金额之和修正冗余字段）');
    log.info(`  模式: ${DRY_RUN ? 'Dry Run（仅查看，不更新）' : '执行更新'}`);
    log.info('==========================================');

    // 步骤1：查询所有工程总额与子项目金额之和不一致的工程
    log.info('');
    log.info('[步骤 1] 查询不一致的工程...');
    const diffResult = await client.query(`
      SELECT p.id, p.name,
             p.total_amount AS old_amount,
             COALESCE(sub.agg_amount, 0) AS new_amount,
             p.total_amount - COALESCE(sub.agg_amount, 0) AS diff
      FROM projects p
      LEFT JOIN (
        SELECT project_id, SUM(amount) AS agg_amount
        FROM subprojects
        GROUP BY project_id
      ) sub ON sub.project_id = p.id
      WHERE p.total_amount IS NULL
         OR p.total_amount != COALESCE(sub.agg_amount, 0)
      ORDER BY ABS(p.total_amount - COALESCE(sub.agg_amount, 0)) DESC
    `);

    if (diffResult.rows.length === 0) {
      log.success('✅ 所有工程总额已与子项目金额一致，无需刷新');
      return;
    }

    log.warn(`发现 ${diffResult.rows.length} 个工程总额不一致：`);
    console.log('');
    console.log(`  ${'工程ID'.padEnd(8)} ${'工程名'.padEnd(30)} ${'当前总额'.padEnd(15)} ${'应有总额'.padEnd(15)} ${'差额'.padEnd(15)}`);
    console.log(`  ${'-'.repeat(85)}`);
    diffResult.rows.forEach(row => {
      const oldVal = parseFloat(row.old_amount || 0).toFixed(4);
      const newVal = parseFloat(row.new_amount).toFixed(4);
      const diffVal = parseFloat(row.diff || 0).toFixed(4);
      console.log(`  ${String(row.id).padEnd(8)} ${String(row.name).substring(0, 28).padEnd(30)} ${oldVal.padEnd(15)} ${newVal.padEnd(15)} ${diffVal}`);
    });
    console.log('');

    // 步骤2：执行更新（非 Dry Run 模式）
    if (DRY_RUN) {
      log.info('[Dry Run] 未执行更新，如需刷新请去掉 --dry-run 参数重新运行');
      return;
    }

    log.info('[步骤 2] 执行更新...');
    const updateResult = await client.query(`
      UPDATE projects p
      SET total_amount = sub.agg_amount, updated_at = CURRENT_TIMESTAMP
      FROM (
        SELECT project_id, COALESCE(SUM(amount), 0) AS agg_amount
        FROM subprojects
        GROUP BY project_id
      ) sub
      WHERE p.id = sub.project_id
        AND (p.total_amount IS NULL OR p.total_amount != sub.agg_amount)
    `);

    const updatedCount = updateResult.rowCount || 0;
    log.success(`✅ 已刷新 ${updatedCount} 个工程的总额`);

    // 步骤3：验证更新结果
    log.info('');
    log.info('[步骤 3] 验证更新结果...');
    const recheckResult = await client.query(`
      SELECT COUNT(*) AS remaining_diff
      FROM projects p
      LEFT JOIN (
        SELECT project_id, SUM(amount) AS agg_amount
        FROM subprojects
        GROUP BY project_id
      ) sub ON sub.project_id = p.id
      WHERE p.total_amount IS NULL
         OR p.total_amount != COALESCE(sub.agg_amount, 0)
    `);

    const remaining = parseInt(recheckResult.rows[0].remaining_diff, 10);
    if (remaining === 0) {
      log.success('✅ 验证通过：所有工程总额已与子项目金额一致');
    } else {
      log.warn(`⚠️  仍有 ${remaining} 个工程不一致，请检查是否有子项目金额变更并发写入`);
    }

    log.info('');
    log.info('==========================================');
    log.success('  刷新完成！');
    log.info('==========================================');

  } catch (err) {
    log.error(`刷新失败: ${err.message}`);
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
refresh();
