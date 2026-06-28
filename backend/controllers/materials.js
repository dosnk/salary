/**
 * 材料参数API控制器
 *
 * 提供材料分类和参数的CRUD接口
 * 材料参数是排料引擎和知识库的核心数据源
 */

const pool = require('../config/database');
const validation = require('../middleware/validation');
const Joi = require('joi');

/**
 * 获取所有材料分类
 */
const getCategories = async (ctx) => {
  try {
    const result = await pool.query(
      'SELECT * FROM material_categories ORDER BY sort_order, id'
    );
    ctx.success(result.rows);
  } catch (error) {
    ctx.fail(5001, '获取材料分类失败');
  }
};

/**
 * 获取指定分类的材料列表
 */
const getMaterialsByCategory = async (ctx) => {
  const { categoryId } = ctx.params;
  try {
    const result = await pool.query(
      'SELECT * FROM material_params WHERE category_id = $1 AND is_active = TRUE ORDER BY id',
      [categoryId]
    );
    ctx.success(result.rows);
  } catch (error) {
    ctx.fail(5001, '获取材料列表失败');
  }
};

/**
 * 获取所有活跃材料（扁平列表）
 */
const getAllMaterials = async (ctx) => {
  try {
    const result = await pool.query(
      `SELECT mp.*, mc.name as category_name
       FROM material_params mp
       JOIN material_categories mc ON mp.category_id = mc.id
       WHERE mp.is_active = TRUE
       ORDER BY mc.sort_order, mp.id`
    );
    ctx.success(result.rows);
  } catch (error) {
    ctx.fail(5001, '获取材料列表失败');
  }
};

/**
 * 创建材料参数
 */
const createMaterial = async (ctx) => {
  const { category_id, name, brand, specification, unit, unit_price, width_cm, length_cm, thickness_cm, coverage_area, keel_spacing_cm, remark } = ctx.request.body;

  try {
    const result = await pool.query(
      `INSERT INTO material_params (category_id, name, brand, specification, unit, unit_price, width_cm, length_cm, thickness_cm, coverage_area, keel_spacing_cm, remark)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       RETURNING *`,
      [category_id, name, brand, specification, unit, unit_price, width_cm, length_cm, thickness_cm, coverage_area, keel_spacing_cm, remark]
    );
    ctx.success(result.rows[0]);
  } catch (error) {
    ctx.fail(5001, '创建材料参数失败');
  }
};

/**
 * 更新材料参数
 */
const updateMaterial = async (ctx) => {
  const { id } = ctx.params;
  const fields = ctx.request.body;

  try {
    const setClauses = [];
    const values = [];
    let paramIndex = 1;

    for (const [key, value] of Object.entries(fields)) {
      if (['name', 'brand', 'specification', 'unit', 'unit_price', 'width_cm', 'length_cm', 'thickness_cm', 'coverage_area', 'keel_spacing_cm', 'is_active', 'remark'].includes(key)) {
        setClauses.push(`${key} = $${paramIndex}`);
        values.push(value);
        paramIndex++;
      }
    }

    if (setClauses.length === 0) {
      ctx.fail(1001, '没有可更新的字段');
      return;
    }

    setClauses.push(`updated_at = CURRENT_TIMESTAMP`);
    values.push(id);

    const result = await pool.query(
      `UPDATE material_params SET ${setClauses.join(', ')} WHERE id = $${paramIndex} RETURNING *`,
      values
    );

    if (result.rows.length === 0) {
      ctx.fail(3001, '材料参数不存在');
      return;
    }

    ctx.success(result.rows[0]);
  } catch (error) {
    ctx.fail(5001, '更新材料参数失败');
  }
};

/**
 * 删除材料参数（软删除：设为不活跃）
 */
const deleteMaterial = async (ctx) => {
  const { id } = ctx.params;
  try {
    const result = await pool.query(
      'UPDATE material_params SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = $1 RETURNING id',
      [id]
    );
    if (result.rows.length === 0) {
      ctx.fail(3001, '材料参数不存在');
      return;
    }
    ctx.success(null);
  } catch (error) {
    ctx.fail(5001, '删除材料参数失败');
  }
};

// 校验Schema
const createMaterialSchema = Joi.object({
  category_id: Joi.number().integer().positive().required(),
  name: Joi.string().min(1).max(100).required(),
  brand: Joi.string().max(100).allow('', null),
  specification: Joi.string().max(200).allow('', null),
  unit: Joi.string().max(20).default('张'),
  unit_price: Joi.number().positive().required(),
  width_cm: Joi.number().positive().allow(null),
  length_cm: Joi.number().positive().allow(null),
  thickness_cm: Joi.number().positive().allow(null),
  coverage_area: Joi.number().positive().allow(null),
  keel_spacing_cm: Joi.number().positive().allow(null),
  remark: Joi.string().allow('', null),
});

const updateMaterialSchema = Joi.object({
  name: Joi.string().min(1).max(100),
  brand: Joi.string().max(100).allow('', null),
  specification: Joi.string().max(200).allow('', null),
  unit: Joi.string().max(20),
  unit_price: Joi.number().positive(),
  width_cm: Joi.number().positive().allow(null),
  length_cm: Joi.number().positive().allow(null),
  thickness_cm: Joi.number().positive().allow(null),
  coverage_area: Joi.number().positive().allow(null),
  keel_spacing_cm: Joi.number().positive().allow(null),
  is_active: Joi.boolean(),
  remark: Joi.string().allow('', null),
}).min(1);

module.exports = {
  getCategories,
  getMaterialsByCategory,
  getAllMaterials,
  createMaterial,
  updateMaterial,
  deleteMaterial,
  createMaterialSchema,
  updateMaterialSchema,
};
