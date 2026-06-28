/**
 * 异步Excel导出任务
 *
 * 处理流程：
 * 1. 接收导出请求（settlementId + userId）
 * 2. 从数据库查询结算快照数据
 * 3. 生成Excel文件
 * 4. 保存到临时目录
 * 5. 通过消息通知用户下载
 *
 * 使用方式：
 *   const { addExportJob } = require('./excelExport');
 *   await addExportJob(settlementId, userId);
 */

const { getQueue } = require('./queue');
const logger = require('../config/logger');

const QUEUE_NAME = 'excelExport';

/**
 * 添加Excel导出任务到队列
 * @param {number} settlementId - 结算单ID
 * @param {number} userId - 用户ID
 * @returns {Promise<string|null>} 任务ID，Redis不可用时返回null
 */
const addExportJob = async (settlementId, userId) => {
  const queue = getQueue(QUEUE_NAME);
  if (!queue) {
    logger.warn('Redis不可用，Excel导出任务无法入队，将回退为同步处理');
    return null;
  }

  try {
    const job = await queue.add('export', {
      settlementId,
      userId,
      requestedAt: new Date().toISOString(),
    }, {
      jobId: `export-${settlementId}-${userId}`,
      // 去重：同一结算单+同一用户的导出任务，5分钟内不重复
      deduplication: {
        id: `export-${settlementId}-${userId}`,
        ttl: 300000,
      },
    });

    logger.info('Excel导出任务已入队', {
      jobId: job.id,
      settlementId,
      userId,
    });

    return job.id;
  } catch (error) {
    logger.error('Excel导出任务入队失败', { settlementId, userId, error: error.message });
    return null;
  }
};

/**
 * 注册Excel导出任务处理器
 * 需要在应用启动时调用：registerExcelExportWorker()
 */
const registerExcelExportWorker = () => {
  const queue = getQueue(QUEUE_NAME);
  if (!queue) return;

  try {
    const { Worker } = require('bullmq');
    const worker = new Worker(QUEUE_NAME, async (job) => {
      const { settlementId, userId } = job.data;

      logger.info('开始处理Excel导出任务', { settlementId, userId });

      // TODO: 实现异步Excel导出逻辑
      // 1. 从 settlementRepo 获取快照数据
      // 2. 生成Excel文件
      // 3. 保存到临时目录
      // 4. 通过消息通知用户下载

      // 更新进度
      await job.updateProgress(50);

      logger.info('Excel导出任务完成', { settlementId, userId });

      return { settlementId, userId, status: 'completed' };
    }, {
      connection: {
        host: process.env.REDIS_HOST || 'localhost',
        port: parseInt(process.env.REDIS_PORT, 10) || 6379,
        password: process.env.REDIS_PASSWORD || undefined,
      },
      concurrency: 2, // 同时处理2个导出任务
    });

    worker.on('completed', (job) => {
      logger.info('Excel导出任务已完成', { jobId: job.id });
    });

    worker.on('failed', (job, err) => {
      logger.error('Excel导出任务失败', { jobId: job?.id, error: err.message });
    });

    logger.info('Excel导出Worker已注册');
  } catch (error) {
    logger.warn('Excel导出Worker注册失败', { error: error.message });
  }
};

module.exports = {
  addExportJob,
  registerExcelExportWorker,
};
