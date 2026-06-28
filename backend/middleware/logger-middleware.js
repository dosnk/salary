// 请求日志中间件
const logger = require('../config/logger');

module.exports = () => {
  return async (ctx, next) => {
    // 记录请求开始时间
    const startTime = Date.now();

    try {
      // 执行后续中间件
      await next();
      // 请求完成后记录日志
      logger.logRequest(ctx, startTime);
    } catch (error) {
      // 捕获错误并记录
      logger.logError(error, ctx);
      throw error; // 继续抛出错误，让错误处理中间件处理
    }
  };
};
