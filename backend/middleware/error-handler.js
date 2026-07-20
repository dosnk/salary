const logger = require('../config/logger');
const errorCodes = require('../config/error-codes');

// CORS 白名单（与 index.js 中的 CORS 配置保持一致，避免错误响应绕过白名单）
const CORS_WHITELIST = (process.env.CORS_ORIGINS || '')
  .split(',')
  .map(s => s.trim())
  .filter(Boolean);

/**
 * 获取请求的 CORS Origin 是否在白名单中
 * 与 index.js 的 CORS 中间件逻辑保持一致：无 Origin 头时返回 false 不放行
 * @param {string} origin - 请求头中的 Origin 字段
 * @returns {string|null} 匹配的 Origin 返回该值，不匹配返回 null
 */
function matchCorsOrigin(origin) {
  if (!origin) return null;
  return CORS_WHITELIST.includes(origin) ? origin : null;
}

// 错误处理中间件
module.exports = () => {
  return async (ctx, next) => {
    try {
      await next();
      if (ctx.status === 404) {
        ctx.fail(1002); // 接口不存在
      }
    } catch (error) {
      logger.error('捕获到错误:', {
        name: error.name,
        message: error.message,
        code: error.code,
        isUnauthorized: error.name === 'UnauthorizedError'
      });

      // 错误响应也要严格遵守 CORS 白名单，禁止使用通配符 '*' 避免绕过白名单
      const allowedOrigin = matchCorsOrigin(ctx.headers.origin);
      if (allowedOrigin) {
        ctx.set('Access-Control-Allow-Origin', allowedOrigin);
        ctx.set('Vary', 'Origin');
        ctx.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        ctx.set('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept');
        ctx.set('Access-Control-Allow-Credentials', 'true');
      }

      if (error.name === 'UnauthorizedError') {
        ctx.status = 401;
        ctx.fail(4001); // Token过期或无效
      } else if (error.code && errorCodes.pgErrorMap[error.code]) {
        // PostgreSQL 数据库错误
        const pgError = errorCodes.pgErrorMap[error.code];
        ctx.fail(pgError.code, pgError.msg);
      } else {
        logger.error('服务器错误:', error);
        ctx.fail(5001); // 服务器异常
      }
    }
  };
};