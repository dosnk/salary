const pool = require('../config/database');
const logger = require('../config/logger');
const Joi = require('joi');
const cache = require('../services/cacheService');

/**
 * 获取空间类型列表（永久缓存，变更时失效）
 */
const getSpaceTypes = async (ctx) => {
  try {
    const data = await cache.getOrSet(
      cache.cacheKey('dictionary', 'spaceTypes'),
      async () => {
        const result = await pool.query('SELECT id, name FROM space_types ORDER BY id');
        return result.rows;
      },
      cache.TTL.PERMANENT
    );
    ctx.success(data);
  } catch (error) {
    logger.error('获取空间类型失败:', error);
    ctx.fail(5001, '获取空间类型失败');
  }
};

/**
 * 创建空间类型
 */
const createSpaceType = async (ctx) => {
  const { name } = ctx.request.body;

  try {
    const existing = await pool.query(
      'SELECT id FROM space_types WHERE name = $1',
      [name]
    );
    
    if (existing.rows.length > 0) {
      ctx.fail(8001);
      return;
    }

    const result = await pool.query(
      'INSERT INTO space_types (name) VALUES ($1) RETURNING id, name',
      [name]
    );
    
    // 清除字典缓存
    await cache.invalidateDictionaryCache();
    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('创建空间类型失败:', error);
    ctx.fail(5001, '创建空间类型失败');
  }
};

/**
 * 更新空间类型
 */
const updateSpaceType = async (ctx) => {
  const { id } = ctx.params;
  const { name } = ctx.request.body;

  try {
    const existing = await pool.query(
      'SELECT id FROM space_types WHERE id = $1',
      [id]
    );
    
    if (existing.rows.length === 0) {
      ctx.fail(8002);
      return;
    }

    const duplicate = await pool.query(
      'SELECT id FROM space_types WHERE name = $1 AND id != $2',
      [name, id]
    );
    
    if (duplicate.rows.length > 0) {
      ctx.fail(8001);
      return;
    }

    const result = await pool.query(
      'UPDATE space_types SET name = $1 WHERE id = $2 RETURNING id, name',
      [name, id]
    );
    
    // 清除字典缓存
    await cache.invalidateDictionaryCache();
    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('更新空间类型失败:', error);
    ctx.fail(5001, '更新空间类型失败');
  }
};

/**
 * 删除空间类型
 */
const deleteSpaceType = async (ctx) => {
  const { id } = ctx.params;

  try {
    const existing = await pool.query(
      'SELECT id FROM space_types WHERE id = $1',
      [id]
    );
    
    if (existing.rows.length === 0) {
      ctx.fail(8002);
      return;
    }

    // 检查是否有子项目使用该空间类型
    const inUse = await pool.query(
      'SELECT COUNT(*) as count FROM subprojects WHERE space_type_id = $1',
      [id]
    );
    
    if (parseInt(inUse.rows[0].count) > 0) {
      ctx.fail(8003);
      return;
    }

    await pool.query('DELETE FROM space_types WHERE id = $1', [id]);
    // 清除字典缓存
    await cache.invalidateDictionaryCache();
    ctx.success(null);
  } catch (error) {
    logger.error('删除空间类型失败:', error);
    ctx.fail(5001, '删除空间类型失败');
  }
};

/**
 * 获取施工方案列表（永久缓存，变更时失效）
 */
const getConstructionPlans = async (ctx) => {
  try {
    const data = await cache.getOrSet(
      cache.cacheKey('dictionary', 'constructionPlans'),
      async () => {
        const result = await pool.query('SELECT id, name, unit, price FROM construction_plans ORDER BY id');
        return result.rows;
      },
      cache.TTL.PERMANENT
    );
    ctx.success(data);
  } catch (error) {
    logger.error('获取施工方案失败:', error);
    ctx.fail(5001, '获取施工方案失败');
  }
};

/**
 * 创建施工方案
 */
const createConstructionPlan = async (ctx) => {
  const { name, unit, price } = ctx.request.body;

  try {
    const existing = await pool.query(
      'SELECT id FROM construction_plans WHERE name = $1',
      [name]
    );
    
    if (existing.rows.length > 0) {
      ctx.fail(8004);
      return;
    }

    const result = await pool.query(
      'INSERT INTO construction_plans (name, unit, price) VALUES ($1, $2, $3) RETURNING id, name, unit, price',
      [name, unit, price]
    );
    
    // 清除字典缓存
    await cache.invalidateDictionaryCache();
    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('创建施工方案失败:', error);
    ctx.fail(5001, '创建施工方案失败');
  }
};

/**
 * 更新施工方案
 */
const updateConstructionPlan = async (ctx) => {
  const { id } = ctx.params;
  const { name, unit, price } = ctx.request.body;

  try {
    const existing = await pool.query(
      'SELECT id FROM construction_plans WHERE id = $1',
      [id]
    );
    
    if (existing.rows.length === 0) {
      ctx.fail(8005);
      return;
    }

    const duplicate = await pool.query(
      'SELECT id FROM construction_plans WHERE name = $1 AND id != $2',
      [name, id]
    );
    
    if (duplicate.rows.length > 0) {
      ctx.fail(8004);
      return;
    }

    const result = await pool.query(
      'UPDATE construction_plans SET name = $1, unit = $2, price = $3 WHERE id = $4 RETURNING id, name, unit, price',
      [name, unit, price, id]
    );
    
    // 清除字典缓存
    await cache.invalidateDictionaryCache();
    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('更新施工方案失败:', error);
    ctx.fail(5001, '更新施工方案失败');
  }
};

/**
 * 删除施工方案
 */
const deleteConstructionPlan = async (ctx) => {
  const { id } = ctx.params;

  try {
    const existing = await pool.query(
      'SELECT id FROM construction_plans WHERE id = $1',
      [id]
    );
    
    if (existing.rows.length === 0) {
      ctx.fail(8005);
      return;
    }

    // 检查是否有子项目使用该施工方案
    const inUse = await pool.query(
      'SELECT COUNT(*) as count FROM subprojects WHERE construction_plan_id = $1',
      [id]
    );
    
    if (parseInt(inUse.rows[0].count) > 0) {
      ctx.fail(8006);
      return;
    }

    await pool.query('DELETE FROM construction_plans WHERE id = $1', [id]);
    // 清除字典缓存
    await cache.invalidateDictionaryCache();
    ctx.success(null);
  } catch (error) {
    logger.error('删除施工方案失败:', error);
    ctx.fail(5001, '删除施工方案失败');
  }
};

/**
 * 获取工资分配方式列表
 */
const getWageDistributionTypes = async (ctx) => {
  try {
    const result = await pool.query(
      'SELECT id, name, code FROM wage_distribution_types ORDER BY id'
    );
    ctx.success(result.rows);
  } catch (error) {
    logger.error('获取工资分配方式失败:', error);
    ctx.fail(5001, '获取工资分配方式失败');
  }
};

/**
 * 获取工程状态列表
 * 工程状态存储在 projects 表的 status 字段
 */
const getProjectStatuses = async (ctx) => {
  try {
    const statuses = [
      { code: 'preparing', name: '备料中' },
      { code: 'constructing', name: '施工中' },
      { code: 'completed', name: '已完工' },
      { code: 'canceled', name: '已取消' }
    ];
    ctx.success(statuses);
  } catch (error) {
    logger.error('获取工程状态失败:', error);
    ctx.fail(5001, '获取工程状态失败');
  }
};

/**
 * 获取结算状态列表
 * 结算状态存储在 project_user_status 表的 settlement_status 字段
 */
const getSettlementStatuses = async (ctx) => {
  try {
    const statuses = [
      { code: 'settling', name: '统计中' },
      { code: 'unsettled', name: '未结算' },
      { code: 'settled', name: '已结算' }
    ];
    ctx.success(statuses);
  } catch (error) {
    logger.error('获取结算状态失败:', error);
    ctx.fail(5001, '获取结算状态失败');
  }
};

/**
 * 获取施工方案单位列表
 * 单位类型：area-面积, perimeter-周长, length-长度
 */
const getConstructionUnits = async (ctx) => {
  try {
    const units = [
      { code: 'area', name: '面积' },
      { code: 'perimeter', name: '周长' },
      { code: 'length', name: '长度' }
    ];
    ctx.success(units);
  } catch (error) {
    logger.error('获取施工方案单位失败:', error);
    ctx.fail(5001, '获取施工方案单位失败');
  }
};

const createSpaceTypeSchema = Joi.object({
  name: Joi.string().min(1).max(50).required()
});

const updateSpaceTypeSchema = Joi.object({
  id: Joi.number().integer().positive().required(),
  name: Joi.string().min(1).max(50).required()
});

const deleteSpaceTypeSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

const createConstructionPlanSchema = Joi.object({
  name: Joi.string().min(1).max(100).required(),
  unit: Joi.string().min(1).max(20).required(),
  price: Joi.number().positive().required()
});

const updateConstructionPlanSchema = Joi.object({
  id: Joi.number().integer().positive().required(),
  name: Joi.string().min(1).max(100).required(),
  unit: Joi.string().min(1).max(20).required(),
  price: Joi.number().positive().required()
});

const deleteConstructionPlanSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

module.exports = {
  getSpaceTypes,
  createSpaceType,
  updateSpaceType,
  deleteSpaceType,
  getConstructionPlans,
  createConstructionPlan,
  updateConstructionPlan,
  deleteConstructionPlan,
  getWageDistributionTypes,
  getProjectStatuses,
  getSettlementStatuses,
  getConstructionUnits,
  createSpaceTypeSchema,
  updateSpaceTypeSchema,
  deleteSpaceTypeSchema,
  createConstructionPlanSchema,
  updateConstructionPlanSchema,
  deleteConstructionPlanSchema
};
