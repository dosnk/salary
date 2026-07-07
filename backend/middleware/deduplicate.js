const { getRedisClient, isRedisAvailable } = require('../config/redis');
const logger = require('../config/logger');

/**
 * 防重复提交中间件
 *
 * 关键设计：Redis操作与next()调用分离，避免catch中重复调用next()
 * 原先的实现在catch中调用await next()，当next()本身抛出异常时
 * 会导致中间件链被重复执行，引发各种难以排查的问题。
 *
 * @param {object} options 配置选项
 * @param {string} options.prefix Redis键前缀
 * @param {number} options.duration 防重复窗口(秒)
 * @param {function} options.keyGenerator 键生成函数
 * @returns {function} Koa中间件
 */
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
    // Redis不可用时跳过防重复检查，直接放行
    if (!isRedisAvailable()) {
      logger.warn('Redis不可用，跳过防重复提交检查');
      return next();
    }

    const redis = getRedisClient();
    const key = keyGenerator(ctx);

    // 步骤1：检查是否重复请求（独立try-catch）
    let isDuplicate = false;
    try {
      const exists = await redis.exists(key);
      if (exists) {
        logger.warn('检测到重复请求', { key, userId: ctx.state.user?.id });
        isDuplicate = true;
      } else {
        // 标记请求，防止重复提交
        await redis.setex(key, duration, '1');
      }
    } catch (error) {
      // Redis操作异常时跳过防重复检查，不阻塞业务
      logger.warn('防重复提交检查Redis操作异常，跳过检查', {
        key,
        error: error.message
      });
      return next();
    }

    // 步骤2：如果重复，返回错误（不调用next()）
    if (isDuplicate) {
      ctx.fail(1004, '请求过于频繁，请稍后再试');
      return;
    }

    // 步骤3：正常执行后续中间件
    // 注意：next()不在try-catch中，异常会正常冒泡到error-handler
    await next();

    // 步骤4：如果响应状态码>=400，清除防重复标记（允许重试）
    if (ctx.status >= 400) {
      try {
        await redis.del(key);
      } catch (error) {
        // 清除失败不影响已完成的响应
        logger.warn('清除防重复标记失败', { key, error: error.message });
      }
    }
  };
};

/**
 * 幂等性中间件（基于X-Idempotent-Key请求头）
 *
 * @param {object} options 配置选项
 * @param {string} options.prefix Redis键前缀
 * @param {number} options.duration 缓存时间(秒)
 * @param {string} options.headerName 幂等键请求头名称
 * @returns {function} Koa中间件
 */
const deduplicateByIdempotentKey = (options = {}) => {
  const {
    prefix = 'idempotent',
    duration = 600,
    headerName = 'X-Idempotent-Key'
  } = options;

  return async (ctx, next) => {
    const idempotentKey = ctx.get(headerName);

    // 没有幂等键，跳过检查
    if (!idempotentKey) {
      return next();
    }

    // Redis不可用时跳过幂等性检查
    if (!isRedisAvailable()) {
      logger.warn('Redis不可用，跳过幂等性检查');
      return next();
    }

    const redis = getRedisClient();
    const key = `${prefix}:${idempotentKey}`;

    // 步骤1：检查是否有缓存的响应
    let cachedResponse = null;
    try {
      cachedResponse = await redis.get(key);
    } catch (error) {
      // Redis操作异常时跳过幂等性检查
      logger.warn('幂等性检查Redis操作异常，跳过检查', {
        key,
        idempotentKey,
        error: error.message
      });
      return next();
    }

    // 步骤2：如果有缓存响应，直接返回缓存结果
    if (cachedResponse) {
      logger.info('返回幂等请求缓存结果', { key, idempotentKey });
      ctx.body = JSON.parse(cachedResponse);
      ctx.status = 200;
      return;
    }

    // 步骤3：正常执行后续中间件
    await next();

    // 步骤4：如果响应成功，缓存响应结果
    if (ctx.status === 200 && ctx.body) {
      try {
        await redis.setex(key, duration, JSON.stringify(ctx.body));
      } catch (error) {
        // 缓存失败不影响已完成的响应
        logger.warn('缓存幂等响应失败', { key, idempotentKey, error: error.message });
      }
    }
  };
};

module.exports = {
  deduplicate,
  deduplicateByIdempotentKey
};
