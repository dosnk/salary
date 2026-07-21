/**
 * 清理预置材料数据脚本（一次性执行）
 *
 * 用途：清理旧的预置材料数据（软删除 is_active=FALSE）
 * 执行方式：docker compose exec app node scripts/clear-preset-materials.js
 *
 * 注意：
 * - 此脚本会软删除所有 is_active=TRUE 的材料参数
 * - 用户可通过 Android 端材料库页面重新录入自己常用的材料
 * - 此脚本只需执行一次，执行后预置材料将被标记为不活跃
 */

const pool = require('../config/database');
const logger = require('../config/logger');

const clearPresetMaterials = async () => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 查询当前活跃材料数量
    const beforeResult = await client.query(
      'SELECT COUNT(*) as count FROM material_params WHERE is_active = TRUE'
    );
    const beforeCount = parseInt(beforeResult.rows[0].count);

    // 软删除所有活跃材料
    const result = await client.query(
      `UPDATE material_params SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
       WHERE is_active = TRUE RETURNING id`
    );

    await client.query('COMMIT');
    logger.info('预置材料清理完成', {
      beforeCount,
      clearedCount: result.rowCount,
    });
    console.log(`预置材料清理完成：共清理 ${result.rowCount} 条材料数据`);
    console.log('用户可通过 Android 端材料库页面重新录入常用材料');
  } catch (error) {
    await client.query('ROLLBACK');
    logger.error('预置材料清理失败', { error: error.message });
    console.error('预置材料清理失败:', error.message);
    throw error;
  } finally {
    client.release();
  }
};

// 直接运行时执行
if (require.main === module) {
  clearPresetMaterials()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}

module.exports = { clearPresetMaterials };
