const Redis = require('ioredis');
const logger = require('./logger');

let redisClient = null;
// Redis可用性标志位
// 仅在 'ready' 事件（认证成功且Redis准备好接受命令）时设为 true
// 注意: 不能在 'connect' 事件设置，因为 connect 仅代表 TCP 连接建立，
//       认证可能失败（密码错误等），此时命令会抛出异常
let redisAvailable = false;

const getRedisClient = () => {
  if (!redisClient) {
    try {
      redisClient = new Redis({
        host: process.env.REDIS_HOST || 'localhost',
        port: process.env.REDIS_PORT || 6379,
        password: process.env.REDIS_PASSWORD || undefined,
        db: process.env.REDIS_DB || 0,
        retryStrategy: (times) => {
          // 重连退避策略：每次增加50ms，最大2秒
          const delay = Math.min(times * 50, 2000);
          return delay;
        },
        // 单个请求最多重试1次，避免Redis断开时请求长时间挂起
        // （原先3次可能导致请求挂起6秒，触发前端超时）
        maxRetriesPerRequest: 1,
        enableReadyCheck: true,
        connectTimeout: 2000, // 连接超时2秒
        keepAlive: 30000 // 保持连接30秒
      });

      // 'connect' 事件：TCP连接建立，但尚未认证，不设置 redisAvailable = true
      // 避免密码错误时在 connect→error 窗口内命令误执行
      redisClient.on('connect', () => {
        logger.info('Redis TCP连接已建立，等待认证');
      });

      // 'ready' 事件：Redis已认证并准备好接受命令，此时才标记为可用
      redisClient.on('ready', () => {
        redisAvailable = true;
        logger.info('Redis已就绪，认证成功');
      });

      // 'error' 事件：连接失败、认证失败、命令错误等
      redisClient.on('error', (err) => {
        if (redisAvailable) {
          logger.warn('Redis连接异常，已切换为不可用状态', { error: err.message });
        }
        redisAvailable = false;
      });

      // 'close' 事件：连接关闭
      redisClient.on('close', () => {
        redisAvailable = false;
      });

      // 'reconnecting' 事件：正在重连
      redisClient.on('reconnecting', (delay) => {
        logger.info('Redis正在重连', { delay });
      });
    } catch (error) {
      redisAvailable = false;
      logger.error('Redis客户端初始化失败', { error: error.message });
    }
  }
  return redisClient;
};

const isRedisAvailable = () => {
  return redisAvailable;
};

const closeRedisClient = async () => {
  if (redisClient) {
    await redisClient.quit();
    redisClient = null;
    redisAvailable = false;
  }
};

module.exports = {
  getRedisClient,
  isRedisAvailable,
  closeRedisClient
};
