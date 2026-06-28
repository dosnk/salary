/**
 * 统计控制器 (Controller)
 *
 * 职责：请求处理（解析参数、调用 Service、返回响应）
 * 所有业务逻辑由 statisticsService 处理
 */

const Joi = require('joi');
const validation = require('../middleware/validation');
const statisticsService = require('../services/statisticsService');
const logger = require('../config/logger');

/**
 * 获取月度统计
 * 从请求中提取 userId/role/months，调用 statisticsService.getMonthlyStatistics
 */
const getMonthlyStatistics = async (ctx) => {
  const { months } = ctx.query;
  const userId = ctx.state.user.id;
  const role = ctx.state.user.role;

  try {
    // 将逗号分隔的月份字符串转为数组
    const monthList = months ? months.split(',').map(m => m.trim()) : [];

    const result = await statisticsService.getMonthlyStatistics({
      userId,
      role,
      months: monthList
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取月度统计失败:', error);
    ctx.fail(5001, '获取月度统计失败');
  }
};

/**
 * 获取工程统计
 * 从请求中提取 userId/role，调用 statisticsService.getProjectStatistics
 */
const getProjectStatistics = async (ctx) => {
  const userId = ctx.state.user.id;
  const role = ctx.state.user.role;

  try {
    const result = await statisticsService.getProjectStatistics({ userId, role });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取工程统计失败:', error);
    ctx.fail(5001, '获取工程统计失败');
  }
};

/**
 * 获取收入统计（结算统计）
 * 从请求中提取 userId/role/startDate/endDate，调用 statisticsService.getIncomeStatistics
 */
const getIncomeStatistics = async (ctx) => {
  const { startDate, endDate } = ctx.query;
  const userId = ctx.state.user.id;
  const role = ctx.state.user.role;

  try {
    const result = await statisticsService.getIncomeStatistics({
      userId,
      role,
      startDate,
      endDate
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取收入统计失败:', error);
    ctx.fail(5001, '获取收入统计失败');
  }
};

/**
 * 获取施工方案统计
 * 从请求中提取 userId/role，调用 statisticsService.getConstructionPlanStatistics
 */
const getConstructionPlanStatistics = async (ctx) => {
  const userId = ctx.state.user.id;
  const role = ctx.state.user.role;

  try {
    const result = await statisticsService.getConstructionPlanStatistics({ userId, role });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取施工方案统计失败:', error);
    ctx.fail(5001, '获取施工方案统计失败');
  }
};

/**
 * 获取人员统计
 * 从请求中提取 userId/role，调用 statisticsService.getWorkerStatistics
 */
const getWorkerStatistics = async (ctx) => {
  const userId = ctx.state.user.id;
  const role = ctx.state.user.role;

  try {
    const result = await statisticsService.getWorkerStatistics({ userId, role });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取人员统计失败:', error);
    ctx.fail(5001, '获取人员统计失败');
  }
};

// ========== 校验规则 ==========

/** 月度统计参数校验 */
const getMonthlyStatisticsSchema = Joi.object({
  months: Joi.string().required()
    .description('月份列表，多个月份用逗号分隔，格式为 YYYY-MM')
});

/** 收入统计参数校验 */
const getIncomeStatisticsSchema = Joi.object({
  startDate: Joi.date(),
  endDate: Joi.date()
});

module.exports = {
  getMonthlyStatistics,
  getProjectStatistics,
  getIncomeStatistics,
  getConstructionPlanStatistics,
  getWorkerStatistics,
  getMonthlyStatisticsSchema,
  getIncomeStatisticsSchema
};
