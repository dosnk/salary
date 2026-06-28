const Redis = require('ioredis');

let redisClient = null;
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
          const delay = Math.min(times * 50, 2000);
          return delay;
        },
        maxRetriesPerRequest: 3,
        enableReadyCheck: true,
        lazyConnect: true, // 延迟连接，不阻塞启动
        connectTimeout: 2000, // 连接超时2秒
        keepAlive: 30000 // 保持连接30秒
      });

      redisClient.on('connect', () => {
        redisAvailable = true;
      });

      redisClient.on('error', (err) => {
        redisAvailable = false;
      });

      redisClient.on('close', () => {
        redisAvailable = false;
      });

      // 移除启动时的连接尝试，改为按需连接
    } catch (error) {
      redisAvailable = false;
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
