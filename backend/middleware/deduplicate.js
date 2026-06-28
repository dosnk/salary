const { getRedisClient, isRedisAvailable } = require('../config/redis');
const logger = require('../config/logger');

const deduplicate = (options = {}) => {
  const {
    prefix = 'deduplicate',
    duration = 5,
    keyGenerator = (ctx) => {
      const userId = ctx.state.user?.id || 'anonymous';
      const path = ctx.path;
      const method = ctx.method;
      const body = JSON.stringify(ctx.request.body || {});
      return `${prefix}:${userId}:${method}:${path}:${Buffer.from(body).toString('base64')}`;
    }
  } = options;

  return async (ctx, next) => {
    try {
      if (!isRedisAvailable()) {
        logger.warn('Redis不可用，跳过防重复提交检查');
        await next();
        return;
      }

      const redis = getRedisClient();
      const key = keyGenerator(ctx);

      const exists = await redis.exists(key);
      if (exists) {
        logger.warn('检测到重复请求', { key, userId: ctx.state.user?.id });
        ctx.fail(1004, '请求过于频繁，请稍后再试');
        return;
      }

      await redis.setex(key, duration, '1');

      await next();

      const statusCode = ctx.status;
      if (statusCode >= 400) {
        await redis.del(key);
      }
    } catch (error) {
      logger.error('防重复提交中间件错误:', error);
      await next();
    }
  };
};

const deduplicateByIdempotentKey = (options = {}) => {
  const {
    prefix = 'idempotent',
    duration = 600,
    headerName = 'X-Idempotent-Key'
  } = options;

  return async (ctx, next) => {
    try {
      const idempotentKey = ctx.get(headerName);
      
      if (!idempotentKey) {
        await next();
        return;
      }

      if (!isRedisAvailable()) {
        logger.warn('Redis不可用，跳过幂等性检查');
        await next();
        return;
      }

      const redis = getRedisClient();
      const key = `${prefix}:${idempotentKey}`;

      const cachedResponse = await redis.get(key);
      if (cachedResponse) {
        logger.info('返回幂等请求缓存结果', { key, idempotentKey });
        ctx.body = JSON.parse(cachedResponse);
        ctx.status = 200;
        return;
      }

      await next();

      if (ctx.status === 200 && ctx.body) {
        await redis.setex(key, duration, JSON.stringify(ctx.body));
      }
    } catch (error) {
      logger.error('幂等性中间件错误:', error);
      await next();
    }
  };
};

module.exports = {
  deduplicate,
  deduplicateByIdempotentKey
};
