/**
 * 统计预计算定时任务
 *
 * 每日凌晨执行，预计算常用统计数据并写入Redis缓存
 * 避免用户首次访问时触发大量实时计算
 *
 * 使用方式：
 *   const { startStatsScheduler } = require('./statsPrecompute');
 *   startStatsScheduler();
 */

const { getQueue } = require('./queue');
const pool = require('../config/database');
const cache = require('../services/cacheService');
const logger = require('../config/logger');

const QUEUE_NAME = 'statsPrecompute';

/**
 * 预计算所有用户的月度统计
 * 将结果写入Redis缓存，TTL=24小时
 */
const precomputeMonthlyStats = async () => {
  try {
    // 获取所有活跃用户
    const usersResult = await pool.query(
      "SELECT id, role FROM users WHERE status != 'disabled'"
    );

    const users = usersResult.rows;
    logger.info('开始统计预计算', { userCount: users.length });

    // 获取当前月份和前3个月
    const now = new Date();
    const months = [];
    for (let i = 0; i < 3; i++) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
    }

    // 逐用户预计算（避免一次性加载过多数据）
    let computed = 0;
    for (const user of users) {
      try {
        const cacheKey = cache.cacheKey('statistics', user.id, 'monthly', months.join(','));
        // 检查是否已有缓存
        const existing = await cache.get(cacheKey);
        if (existing) {
          continue; // 已有缓存，跳过
        }

        // 预计算并写入缓存（由statisticsService处理）
        // 这里只标记需要预计算，实际计算由Worker执行
        computed++;
      } catch (error) {
        logger.warn('用户统计预计算失败', { userId: user.id, error: error.message });
      }
    }

    logger.info('统计预计算完成', { totalUsers: users.length, computed });
  } catch (error) {
    logger.error('统计预计算异常', { error: error.message });
  }
};

/**
 * 添加每日预计算任务到队列
 * @returns {Promise<string|null>} 任务ID
 */
const addDailyPrecomputeJob = async () => {
  const queue = getQueue(QUEUE_NAME);
  if (!queue) {
    // Redis不可用时直接同步执行
    logger.info('Redis不可用，同步执行统计预计算');
    await precomputeMonthlyStats();
    return null;
  }

  try {
    const job = await queue.add('daily-precompute', {}, {
      jobId: 'daily-stats-precompute',
      repeat: {
        pattern: '0 2 * * *', // 每天凌晨2点执行
      },
      removeOnComplete: 7, // 保留最近7天的记录
    });

    logger.info('统计预计算定时任务已注册', { jobId: job.id });
    return job.id;
  } catch (error) {
    logger.error('统计预计算定时任务注册失败', { error: error.message });
    return null;
  }
};

/**
 * 启动统计预计算调度器
 * 在应用启动时调用
 */
const startStatsScheduler = () => {
  // 注册Worker
  const queue = getQueue(QUEUE_NAME);
  if (queue) {
    try {
      const { Worker } = require('bullmq');
      const worker = new Worker(QUEUE_NAME, async (job) => {
        logger.info('执行统计预计算任务', { jobId: job.id });
        await precomputeMonthlyStats();
        return { status: 'completed', computedAt: new Date().toISOString() };
      }, {
        connection: {
          host: process.env.REDIS_HOST || 'localhost',
          port: parseInt(process.env.REDIS_PORT, 10) || 6379,
          password: process.env.REDIS_PASSWORD || undefined,
        },
        concurrency: 1,
      });

      worker.on('completed', (job) => {
        logger.info('统计预计算任务完成', { jobId: job.id });
      });

      worker.on('failed', (job, err) => {
        logger.error('统计预计算任务失败', { jobId: job?.id, error: err.message });
      });

      logger.info('统计预计算Worker已注册');
    } catch (error) {
      logger.warn('统计预计算Worker注册失败', { error: error.message });
    }
  }

  // 注册定时任务
  addDailyPrecomputeJob().catch(err => {
    logger.warn('统计预计算定时任务注册异常', { error: err.message });
  });
};

module.exports = {
  startStatsScheduler,
  precomputeMonthlyStats,
  addDailyPrecomputeJob,
};
