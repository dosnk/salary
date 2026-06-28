/**
 * API限流中间件
 * 基于Redis实现的滑动窗口限流，按用户/IP控制请求频率
 * 
 * 默认限制:
 * - 全局: 100次/分钟/IP
 * - 登录: 10次/分钟/IP (防止暴力破解)
 * - 敏感操作(结算/删除): 10次/分钟/用户
 */
const { getRedisClient, isRedisAvailable } = require('../config/redis');

// 限流窗口(秒)
const DEFAULT_WINDOW = 60;
const LOGIN_WINDOW = 60;
const SENSITIVE_WINDOW = 60;

/**
 * 创建限流中间件
 * @param {object} options 配置选项
 * @param {number} options.maxRequests 时间窗口内最大请求数
 * @param {number} options.windowSeconds 时间窗口(秒)
 * @param {string} options.keyPrefix Redis键前缀
 * @returns {function} Koa中间件
 */
const createRateLimiter = (options = {}) => {
  const {
    maxRequests = 100,
    windowSeconds = DEFAULT_WINDOW,
    keyPrefix = 'rate_limit'
  } = options;

  return async (ctx, next) => {
    // Redis不可用时跳过限流
    if (!isRedisAvailable()) {
      return next();
    }

    const redis = getRedisClient();
    
    // 按用户ID或IP限流
    const identifier = ctx.state.user?.id || ctx.ip || ctx.request.ip;
    const key = `${keyPrefix}:${identifier}`;

    try {
      const current = await redis.incr(key);
      
      // 首次请求设置过期时间
      if (current === 1) {
        await redis.expire(key, windowSeconds);
      }

      // 设置响应头
      const ttl = await redis.ttl(key);
      ctx.set('X-RateLimit-Limit', String(maxRequests));
      ctx.set('X-RateLimit-Remaining', String(Math.max(0, maxRequests - current)));
      ctx.set('X-RateLimit-Reset', String(Math.floor(Date.now() / 1000) + ttl));

      if (current > maxRequests) {
        ctx.set('Retry-After', String(ttl));
        ctx.status = 429;
        ctx.body = {
          code: 429,
          data: null,
          msg: `请求过于频繁，请${ttl}秒后重试`
        };
        return;
      }

      await next();
    } catch (error) {
      // Redis异常时放行，不阻塞业务
      console.warn('限流中间件异常:', error.message);
      await next();
    }
  };
};

// 预设限流器

/** 全局API限流: 100次/分钟 */
const globalLimiter = createRateLimiter({
  maxRequests: 100,
  windowSeconds: 60,
  keyPrefix: 'rl:global'
});

/** 登录接口限流: 10次/分钟 */
const loginLimiter = createRateLimiter({
  maxRequests: 10,
  windowSeconds: LOGIN_WINDOW,
  keyPrefix: 'rl:login'
});

/** 敏感操作限流: 10次/分钟 */
const sensitiveLimiter = createRateLimiter({
  maxRequests: 10,
  windowSeconds: SENSITIVE_WINDOW,
  keyPrefix: 'rl:sensitive'
});

module.exports = {
  createRateLimiter,
  globalLimiter,
  loginLimiter,
  sensitiveLimiter
};