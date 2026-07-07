/**
 * 材料加载器
 *
 * 从知识库(material_params表)加载材料参数，供排料引擎使用
 * 引擎不硬编码任何材料数据
 */

const pool = require('../../config/database');
const logger = require('../../config/logger');

/**
 * 加载指定分类的活跃材料列表
 * @param {string} categoryName - 分类名称（面材/主龙骨/副龙骨/收边条/配件）
 * @returns {Promise<Array>} 材料参数列表
 */
const loadMaterialsByCategory = async (categoryName) => {
  try {
    const result = await pool.query(
      `SELECT mp.*, mc.name as category_name
       FROM material_params mp
       JOIN material_categories mc ON mp.category_id = mc.id
       WHERE mc.name = $1 AND mp.is_active = TRUE
       ORDER BY mp.id`,
      [categoryName]
    );
    return result.rows;
  } catch (error) {
    logger.error('加载材料参数失败:', error.message);
    return [];
  }
};

/**
 * 加载指定ID的材料
 * @param {number} materialId - 材料ID
 * @returns {Promise<object|null>} 材料参数
 */
const loadMaterialById = async (materialId) => {
  try {
    const result = await pool.query(
      `SELECT mp.*, mc.name as category_name
       FROM material_params mp
       JOIN material_categories mc ON mp.category_id = mc.id
       WHERE mp.id = $1 AND mp.is_active = TRUE`,
      [materialId]
    );
    return result.rows[0] || null;
  } catch (error) {
    logger.error('加载材料参数失败:', error.message);
    return null;
  }
};

/**
 * 加载排料所需的全部材料参数
 * @param {object} [options] - 选项
 * @param {number} [options.panelId] - 指定面材ID
 * @param {number} [options.mainKeelId] - 指定主龙骨ID
 * @param {number} [options.subKeelId] - 指定副龙骨ID
 * @param {number} [options.trimId] - 指定收边条ID
 * @returns {Promise<{panel: object, mainKeel: object, subKeel: object, trim: object, accessories: Array}>}
 */
const loadLayoutMaterials = async (options = {}) => {
  // 加载面材
  let panel;
  if (options.panelId) {
    panel = await loadMaterialById(options.panelId);
  }
  if (!panel) {
    const panels = await loadMaterialsByCategory('面材');
    panel = panels[0]; // 默认取第一个
  }

  // 加载主龙骨
  let mainKeel;
  if (options.mainKeelId) {
    mainKeel = await loadMaterialById(options.mainKeelId);
  }
  if (!mainKeel) {
    const keels = await loadMaterialsByCategory('主龙骨');
    mainKeel = keels[0];
  }

  // 加载副龙骨
  let subKeel;
  if (options.subKeelId) {
    subKeel = await loadMaterialById(options.subKeelId);
  }
  if (!subKeel) {
    const subKeels = await loadMaterialsByCategory('副龙骨');
    subKeel = subKeels[0];
  }

  // 加载收边条
  let trim;
  if (options.trimId) {
    trim = await loadMaterialById(options.trimId);
  }
  if (!trim) {
    const trims = await loadMaterialsByCategory('收边条');
    trim = trims[0];
  }

  // 加载配件
  const accessories = await loadMaterialsByCategory('配件');

  return { panel, mainKeel, subKeel, trim, accessories };
};

module.exports = { loadMaterialsByCategory, loadMaterialById, loadLayoutMaterials };
