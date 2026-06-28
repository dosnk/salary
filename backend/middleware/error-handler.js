const logger = require('../config/logger');
const errorCodes = require('../config/error-codes');

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
      
      // 确保CORS响应头被设置
      ctx.set('Access-Control-Allow-Origin', '*');
      ctx.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
      ctx.set('Access-Control-Allow-Headers', 'Content-Type, Authorization, Accept');
      ctx.set('Access-Control-Allow-Credentials', 'true');
      
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