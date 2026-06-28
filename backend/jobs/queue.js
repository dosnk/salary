/**
 * BullMQ 队列初始化
 *
 * 队列用途：
 * - excelExport: 异步Excel导出（大结算单生成）
 * - statsPrecompute: 统计预计算（每日凌晨定时任务）
 *
 * 使用方式：
 *   const { getQueue } = require('./queue');
 *   const queue = getQueue('excelExport');
 *   await queue.add('export', { settlementId: 1, userId: 2 });
 */

const { isRedisAvailable } = require('../config/redis');
const logger = require('../config/logger');

// 队列实例缓存
const queues = {};

// Redis连接配置（复用现有配置）
const getRedisConfig = () => ({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT, 10) || 6379,
  password: process.env.REDIS_PASSWORD || undefined,
  db: process.env.REDIS_DB || 0,
  maxRetriesPerRequest: null, // BullMQ要求设为null
});

/**
 * 获取或创建队列实例
 * @param {string} name - 队列名称
 * @returns {import('bullmq').Queue|null} 队列实例，Redis不可用时返回null
 */
const getQueue = (name) => {
  if (!isRedisAvailable()) {
    logger.warn('Redis不可用，无法创建BullMQ队列', { queue: name });
    return null;
  }

  if (!queues[name]) {
    try {
      // 延迟加载bullmq，避免未安装时启动报错
      const { Queue } = require('bullmq');
      queues[name] = new Queue(name, {
        connection: getRedisConfig(),
        defaultJobOptions: {
          removeOnComplete: 100,  // 保留最近100个完成的任务
          removeOnFail: 50,      // 保留最近50个失败的任务
          attempts: 3,           // 失败重试3次
          backoff: {
            type: 'exponential',
            delay: 5000,         // 首次重试延迟5秒
          },
        },
      });
      logger.info('BullMQ队列已创建', { queue: name });
    } catch (error) {
      logger.warn('BullMQ队列创建失败（可能未安装bullmq依赖）', { queue: name, error: error.message });
      return null;
    }
  }

  return queues[name];
};

/**
 * 关闭所有队列连接
 */
const closeAllQueues = async () => {
  const closePromises = Object.entries(queues).map(async ([name, queue]) => {
    try {
      await queue.close();
      logger.info('BullMQ队列已关闭', { queue: name });
    } catch (error) {
      logger.warn('BullMQ队列关闭失败', { queue: name, error: error.message });
    }
  });

  await Promise.all(closePromises);
  Object.keys(queues).forEach(key => delete queues[key]);
};

module.exports = {
  getQueue,
  closeAllQueues,
};
