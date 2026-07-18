/**
 * 修复子项目尺寸单位（米 → 厘米）
 *
 * 问题背景：
 *   旧库 subprojects.length/width 存储单位为"米"
 *   新库 subprojects.length/width 存储单位为"厘米"
 *   迁移时未做单位换算，导致显示尺寸小了 100 倍
 *
 * 修复策略：
 *   - subprojects.length 乘以 100（米 → 厘米）
 *   - subprojects.width  乘以 100（米 → 厘米）
 *   - subprojects.quantity 保持不变（已经是基于"米"计算的正确值）
 *   - subprojects.amount   保持不变（quantity × unit_price，quantity 不变则 amount 不变）
 *
 * 使用方法：
 *   docker exec -it <容器名> node scripts/fix-length-unit.js
 *
 * 安全特性：
 *   - 执行前自动备份待修改数据到 backup_subprojects_length 表
 *   - 事务执行，失败自动回滚
 *   - 执行后输出修改前后对比，便于核对
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

// ===================== 主流程 =====================
async function fixLengthUnit() {
  let client;

  try {
    log.info('==========================================');
    log.info('  修复子项目尺寸单位（米 → 厘米）');
    log.info('==========================================');

    // 测试连接
    log.info('测试数据库连接...');
    client = await pool.connect();
    const versionResult = await client.query('SELECT version()');
    log.success(`数据库连接成功: ${versionResult.rows[0].version.split(' ').slice(0, 2).join(' ')}`);

    // 1. 查看修改前的样本数据（前10条）
    log.info('');
    log.info('[步骤 1] 查看修改前的样本数据...');
    const beforeSample = await client.query(`
      SELECT id, length, width, quantity, amount
      FROM subprojects
      WHERE length IS NOT NULL OR width IS NOT NULL
      ORDER BY id
      LIMIT 10
    `);

    if (beforeSample.rows.length === 0) {
      log.warn('没有需要修复的子项目数据（length 和 width 都为 NULL）');
      return;
    }

    log.info('修改前样本（前10条）:');
    console.log('  ID    | length    | width     | quantity  | amount');
    console.log('  ------+-----------+-----------+-----------+-----------');
    beforeSample.rows.forEach(row => {
      console.log(`  ${String(row.id).padEnd(5)} | ${String(row.length).padEnd(9)} | ${String(row.width).padEnd(9)} | ${String(row.quantity).padEnd(9)} | ${row.amount}`);
    });

    // 2. 统计待修改数量
    const countResult = await client.query(`
      SELECT COUNT(*) AS total,
             COUNT(*) FILTER (WHERE length IS NOT NULL) AS length_count,
             COUNT(*) FILTER (WHERE width IS NOT NULL) AS width_count
      FROM subprojects
    `);
    const totalCount = parseInt(countResult.rows[0].total, 10);
    const lengthCount = parseInt(countResult.rows[0].length_count, 10);
    const widthCount = parseInt(countResult.rows[0].width_count, 10);
    log.info('');
    log.info(`待修改: 共 ${totalCount} 条子项目，其中 length 非空 ${lengthCount} 条，width 非空 ${widthCount} 条`);

    // 3. 事务执行修复
    log.info('');
    log.info('[步骤 2] 执行修复（事务中）...');

    await client.query('BEGIN');

    try {
      // 3.1 备份待修改数据（安全网，便于回滚）
      log.info('  创建备份数据表 backup_subprojects_length...');
      await client.query('DROP TABLE IF EXISTS backup_subprojects_length');
      await client.query(`
        CREATE TABLE backup_subprojects_length AS
        SELECT id, length, width, quantity, amount, NOW() AS backup_time
        FROM subprojects
      `);
      const backupCount = await client.query('SELECT COUNT(*) AS count FROM backup_subprojects_length');
      log.info(`  已备份 ${backupCount.rows[0].count} 条数据到 backup_subprojects_length`);

      // 3.2 执行修复：length 和 width 乘以 100（米 → 厘米）
      log.info('  执行 UPDATE: length = length * 100, width = width * 100');
      const updateResult = await client.query(`
        UPDATE subprojects
        SET length = CASE WHEN length IS NOT NULL THEN length * 100 ELSE NULL END,
            width = CASE WHEN width IS NOT NULL THEN width * 100 ELSE NULL END,
            updated_at = CURRENT_TIMESTAMP
        WHERE length IS NOT NULL OR width IS NOT NULL
      `);
      log.info(`  已修改 ${updateResult.rowCount} 条记录`);

      // 3.3 验证修改后的数据
      log.info('');
      log.info('[步骤 3] 验证修改后的数据...');
      const afterSample = await client.query(`
        SELECT id, length, width, quantity, amount
        FROM subprojects
        WHERE length IS NOT NULL OR width IS NOT NULL
        ORDER BY id
        LIMIT 10
      `);

      log.info('修改后样本（前10条）:');
      console.log('  ID    | length    | width     | quantity  | amount');
      console.log('  ------+-----------+-----------+-----------+-----------');
      afterSample.rows.forEach(row => {
        console.log(`  ${String(row.id).padEnd(5)} | ${String(row.length).padEnd(9)} | ${String(row.width).padEnd(9)} | ${String(row.quantity).padEnd(9)} | ${row.amount}`);
      });

      // 3.4 对比验证：quantity 和 amount 应保持不变
      log.info('');
      log.info('[步骤 4] 对比验证（quantity 和 amount 应保持不变）...');
      const compareResult = await client.query(`
        SELECT
          COUNT(*) FILTER (WHERE ABS(s.quantity - b.quantity) > 0.001) AS quantity_diff_count,
          COUNT(*) FILTER (WHERE ABS(s.amount - b.amount) > 0.01) AS amount_diff_count
        FROM subprojects s
        JOIN backup_subprojects_length b ON s.id = b.id
      `);
      const quantityDiff = parseInt(compareResult.rows[0].quantity_diff_count, 10);
      const amountDiff = parseInt(compareResult.rows[0].amount_diff_count, 10);

      if (quantityDiff > 0 || amountDiff > 0) {
        log.error(`验证失败: quantity 差异 ${quantityDiff} 条, amount 差异 ${amountDiff} 条`);
        log.error('不应发生此情况，正在回滚事务...');
        await client.query('ROLLBACK');
        throw new Error('quantity/amount 验证失败，事务已回滚');
      }

      log.success(`验证通过: quantity 和 amount 全部保持不变（0 条差异）`);

      await client.query('COMMIT');
      log.success('事务已提交');

      // 4. 汇总
      log.info('');
      log.info('==========================================');
      log.success('  ✅ 修复完成！');
      log.info('==========================================');
      log.info('');
      log.info('修改内容:');
      log.info('  - subprojects.length 乘以 100（米 → 厘米）');
      log.info('  - subprojects.width  乘以 100（米 → 厘米）');
      log.info('  - subprojects.quantity 保持不变');
      log.info('  - subprojects.amount   保持不变');
      log.info('');
      log.info(`备份数据已保存到 backup_subprojects_length 表（如需回滚可用）`);
      log.info('');
      log.info('后续操作:');
      log.info('  1. 重启后端服务（清除缓存）');
      log.info('  2. 验证主页工程卡片尺寸显示是否正确');
      log.info('  3. 验证工程详情页子项目尺寸显示是否正确');
      log.info('');
      log.info('如需回滚（不应需要）:');
      log.info('  psql -c "UPDATE subprojects s SET length = b.length, width = b.width FROM backup_subprojects_length b WHERE s.id = b.id"');

    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    }

  } catch (err) {
    log.error(`修复失败: ${err.message}`);
    if (err.stack) {
      log.error(err.stack);
    }
    process.exit(1);
  } finally {
    if (client) {
      client.release();
      log.info('数据库连接已释放');
    }
    await pool.end();
    log.info('数据库连接池已关闭');
  }
}

// 执行修复
fixLengthUnit();
