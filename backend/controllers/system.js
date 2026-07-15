/**
 * 系统管理控制器 (Controller)
 *
 * 负责系统级管理操作的请求处理和参数校验，包括：
 * - 数据一致性校验（调用 scripts/verify-data-consistency.js 的可复用函数）
 *
 * 业务逻辑不在本层实现，仅做参数解析、调用复用模块、包装响应
 */

const logger = require('../config/logger');
const { verifyDataConsistency } = require('../scripts/verify-data-consistency');

/**
 * 数据一致性校验
 * 仅 admin 角色可调用（路由层已通过 requireAdmin() 限制）
 *
 * 查询参数：
 *   - userId: number     可选，只校验指定用户（不传则校验全部用户）
 *   - tolerance: number  可选，金额容差（元），默认 0.01
 *
 * 响应结构：
 *   {
 *     passed: number,      // 通过项数
 *     failed: number,      // 失败项数
 *     warnings: number,    // 警告项数
 *     elapsed: number,     // 耗时（秒）
 *     details: Array<{ name: string, passed: boolean, detail: string }>
 *   }
 */
const verifyDataConsistencyHandler = async (ctx) => {
  // 1. 参数解析（带容错：非数字或负数则使用默认值）
  const userIdRaw = ctx.query.userId;
  const toleranceRaw = ctx.query.tolerance;

  let userId = null;
  if (userIdRaw !== undefined && userIdRaw !== null && userIdRaw !== '') {
    userId = parseInt(userIdRaw, 10);
    if (Number.isNaN(userId) || userId <= 0) {
      ctx.fail(1001, 'userId 参数必须为正整数');
      return;
    }
  }

  let tolerance = 0.01;
  if (toleranceRaw !== undefined && toleranceRaw !== null && toleranceRaw !== '') {
    tolerance = parseFloat(toleranceRaw);
    if (Number.isNaN(tolerance) || tolerance < 0) {
      ctx.fail(1001, 'tolerance 参数必须为非负数');
      return;
    }
  }

  // 2. 调用可复用校验函数（静默模式，不打印日志）
  try {
    const result = await verifyDataConsistency({
      userId,
      tolerance,
      silent: true
    });

    ctx.success(result);
  } catch (error) {
    logger.error('数据一致性校验执行失败:', error);
    ctx.fail(5001, `数据一致性校验执行失败: ${error.message}`);
  }
};

module.exports = {
  verifyDataConsistencyHandler
};
