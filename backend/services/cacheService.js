/**
 * Redis 缓存服务
 *
 * 提供统一的缓存管理接口，支持：
 * - 通用 get/set/del 操作
 * - JSON 自动序列化/反序列化
 * - 按键前缀批量失效
 * - 缓存穿透保护（空值缓存短TTL）
 * - 优雅降级（Redis不可用时跳过缓存）
 */

const { getRedisClient, isRedisAvailable } = require('../config/redis');
const logger = require('../config/logger');

// 缓存TTL常量（秒）
const TTL = {
  SHORT: 60 * 5,          // 5分钟 - 临时数据
  MEDIUM: 60 * 10,        // 10分钟 - 工程列表
  LONG: 60 * 30,          // 30分钟 - 统计数据
  HOUR: 60 * 60,          // 1小时
  DAY: 60 * 60 * 24,      // 1天
  PERMANENT: 0,            // 永久（需手动失效）- 字典数据
  NULL_VALUE: 30,          // 空值缓存30秒，防穿透
};

/**
 * 获取缓存
 * @param {string} key - 缓存键
 * @returns {Promise<any|null>} 缓存值，未命中返回null
 */
const get = async (key) => {
  if (!isRedisAvailable()) return null;

  try {
    const redis = getRedisClient();
    const data = await redis.get(key);

    if (data === null) return null;

    // 空值标记
    if (data === '__NULL__') return undefined;

    try {
      return JSON.parse(data);
    } catch {
      return data;
    }
  } catch (error) {
    logger.warn('缓存读取失败', { key, error: error.message });
    return null;
  }
};

/**
 * 设置缓存
 * @param {string} key - 缓存键
 * @param {*} value - 缓存值
 * @param {number} ttl - 过期时间（秒），0表示永久
 */
const set = async (key, value, ttl = TTL.MEDIUM) => {
  if (!isRedisAvailable()) return false;

  try {
    const redis = getRedisClient();
    const data = value === undefined ? '__NULL__' : JSON.stringify(value);

    if (ttl > 0) {
      await redis.set(key, data, 'EX', ttl);
    } else {
      await redis.set(key, data);
    }
    return true;
  } catch (error) {
    logger.warn('缓存写入失败', { key, error: error.message });
    return false;
  }
};

/**
 * 删除缓存
 * @param {string} key - 缓存键
 */
const del = async (key) => {
  if (!isRedisAvailable()) return false;

  try {
    const redis = getRedisClient();
    await redis.del(key);
    return true;
  } catch (error) {
    logger.warn('缓存删除失败', { key, error: error.message });
    return false;
  }
};

/**
 * 按前缀批量删除缓存
 * @param {string} prefix - 键前缀
 */
const delByPrefix = async (prefix) => {
  if (!isRedisAvailable()) return false;

  try {
    const redis = getRedisClient();
    // 使用 SCAN 避免阻塞 Redis
    let cursor = '0';
    let totalDeleted = 0;

    do {
      const [nextCursor, keys] = await redis.scan(
        cursor, 'MATCH', `${prefix}*`, 'COUNT', 100
      );
      cursor = nextCursor;

      if (keys.length > 0) {
        await redis.del(...keys);
        totalDeleted += keys.length;
      }
    } while (cursor !== '0');

    logger.info('按前缀清除缓存', { prefix, totalDeleted });
    return true;
  } catch (error) {
    logger.warn('按前缀清除缓存失败', { prefix, error: error.message });
    return false;
  }
};

/**
 * 缓存穿透保护：获取或设置缓存
 * 如果缓存未命中，执行 fetchFn 获取数据并写入缓存
 *
 * @param {string} key - 缓存键
 * @param {Function} fetchFn - 数据获取函数
 * @param {number} ttl - 过期时间（秒）
 * @returns {Promise<any>}
 */
const getOrSet = async (key, fetchFn, ttl = TTL.MEDIUM) => {
  // 先尝试从缓存获取
  const cached = await get(key);
  if (cached !== null) {
    // 空值标记返回undefined，但不算缓存未命中
    if (cached === undefined) return null;
    return cached;
  }

  // 缓存未命中，执行数据获取
  try {
    const data = await fetchFn();

    // 空结果也缓存（防穿透），但TTL较短
    if (data === null || data === undefined) {
      await set(key, undefined, TTL.NULL_VALUE);
      return null;
    }

    await set(key, data, ttl);
    return data;
  } catch (error) {
    logger.error('缓存回源失败', { key, error: error.message });
    throw error;
  }
};

/**
 * 生成缓存键
 * @param {string} namespace - 命名空间
 * @param  {...any} parts - 键组成部分
 * @returns {string}
 *
 * 示例: cacheKey('projects', userId, 'list', page) => 'projects:5:list:1'
 */
const cacheKey = (namespace, ...parts) => {
  return [namespace, ...parts].join(':');
};

/**
 * 使工程相关缓存失效
 * @param {number} userId - 用户ID
 */
const invalidateProjectCache = async (userId) => {
  await delByPrefix(`projects:${userId || '*'}`);
  await delByPrefix('statistics:');
};

/**
 * 使统计相关缓存失效
 * @param {number} userId - 用户ID
 */
const invalidateStatisticsCache = async (userId) => {
  await delByPrefix(`statistics:${userId || '*'}`);
};

/**
 * 使字典缓存失效
 */
const invalidateDictionaryCache = async () => {
  await delByPrefix('dictionary:');
};

module.exports = {
  TTL,
  get,
  set,
  del,
  delByPrefix,
  getOrSet,
  cacheKey,
  invalidateProjectCache,
  invalidateStatisticsCache,
  invalidateDictionaryCache,
};
