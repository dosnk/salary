/**
 * 材料参数初始化脚本
 *
 * 设计变更说明：
 * 原脚本预置了21条标准材料数据（面材/主龙骨/副龙骨/收边条/配件）。
 * 根据新需求，材料库改为"用户常用材料"模式：
 * - 仅保留材料分类（面材/主龙骨/副龙骨/收边条/配件），便于用户添加材料时选择
 * - 不再预置任何材料参数，由用户根据实际使用情况自行录入
 *
 * 用户可通过 Android 端"知识库 > 材料库"页面录入、编辑、删除自己的常用材料。
 *
 * 注意：此脚本不会清理已有的材料数据，避免误删用户手动添加的材料。
 *       如需清理旧的预置材料，请手动执行 scripts/clear-preset-materials.js
 */

const pool = require('../config/database');
const logger = require('../config/logger');

const seedMaterials = async () => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 仅插入/更新材料分类（保留分类便于用户添加材料时选择）
    const categories = [
      { name: '面材', description: '吊顶面板材料', sort_order: 1 },
      { name: '主龙骨', description: '承载龙骨', sort_order: 2 },
      { name: '副龙骨', description: '覆面龙骨', sort_order: 3 },
      { name: '收边条', description: '收边/角线材料', sort_order: 4 },
      { name: '配件', description: '吊杆/挂件/螺丝等', sort_order: 5 },
    ];

    for (const cat of categories) {
      await client.query(
        `INSERT INTO material_categories (name, description, sort_order)
         VALUES ($1, $2, $3)
         ON CONFLICT (name) DO UPDATE SET description = $2, sort_order = $3`,
        [cat.name, cat.description, cat.sort_order]
      );
    }

    await client.query('COMMIT');
    logger.info('材料分类初始化完成', {
      categoriesCount: categories.length,
      message: '材料库已改为用户自管理模式，不再预置材料数据',
    });
    console.log(`材料分类初始化完成：保留${categories.length}个分类，不再预置材料数据`);
  } catch (error) {
    await client.query('ROLLBACK');
    logger.error('材料分类初始化失败', { error: error.message });
    console.error('材料分类初始化失败:', error.message);
    throw error;
  } finally {
    client.release();
  }
};

// 直接运行时执行
if (require.main === module) {
  seedMaterials()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}

module.exports = { seedMaterials };
