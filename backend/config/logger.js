// 日志配置文件
const winston = require('winston');
const DailyRotateFile = require('winston-daily-rotate-file');
const path = require('path');
const moment = require('moment');
const fs = require('fs'); // 引入文件系统模块
require('dotenv').config();

// 定义日志格式
const logFormat = winston.format.printf(({ level, message, timestamp, ...meta }) => {
  return `${timestamp} [${level.toUpperCase()}]: ${message} ${Object.keys(meta).length ? JSON.stringify(meta) : ''}`;
});

// 日志存储路径 - 支持环境变量配置
let logDir = process.env.LOG_DIR || path.join(__dirname, '../../logs');

// 如果是生产环境且没有指定LOG_DIR，使用系统临时目录
if (process.env.NODE_ENV === 'production' && !process.env.LOG_DIR) {
  const os = require('os');
  const platform = os.platform();
  
  if (platform === 'linux') {
    // Linux系统：使用/var/log或/tmp目录
    logDir = process.env.USER === 'root' ? '/var/log/salary' : '/tmp/salary-logs';
  } else if (platform === 'win32') {
    // Windows系统：使用C盘根目录或临时目录
    logDir = 'C:\\salary-logs';
  }
}

// 确保日志目录存在
try {
  if (!fs.existsSync(logDir)) {
    fs.mkdirSync(logDir, { recursive: true, mode: 0o755 });
  }
} catch (error) {
  // 如果创建失败，使用系统临时目录
  const os = require('os');
  logDir = path.join(os.tmpdir(), 'salary-logs');
  
  try {
    if (!fs.existsSync(logDir)) {
      fs.mkdirSync(logDir, { recursive: true });
    }
  } catch (fallbackError) {
    logDir = null; // 禁用文件日志
  }
}

// 轮转文件配置
const rotateFileOptions = (level) => ({
  filename: path.join(logDir, `${level}/%DATE%.log`),
  datePattern: 'YYYY-MM-DD',
  maxSize: '20m', // 单个文件最大20MB
  maxFiles: '14d', // 保留14天日志
  level: level,
  format: winston.format.combine(
    winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
    logFormat
  )
});

// 创建日志器
const logger = winston.createLogger({
  levels: winston.config.npm.levels,
  defaultMeta: { service: 'salary-api' },
  transports: [
    // 控制台输出
    new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
        logFormat
      )
    }),
    // 错误日志文件
    new DailyRotateFile(rotateFileOptions('error')),
    // 信息日志文件
    new DailyRotateFile(rotateFileOptions('info'))
  ]
});

// 根据环境调整日志级别
const env = process.env.NODE_ENV || 'development';
if (env === 'production') {
  logger.level = 'info';
} else if (env === 'development') {
  logger.level = 'debug';
}

// 导出日志器
module.exports = logger;

// 日志工具函数
module.exports.logRequest = (ctx, startTime) => {
  const duration = Date.now() - startTime;
  const logData = {
    method: ctx.method,
    url: ctx.url,
    status: ctx.status,
    duration: `${duration}ms`,
    ip: ctx.ip,
    userAgent: ctx.headers['user-agent'],
    userId: ctx.state.user?.id || 'unauthenticated'
  };

  // 根据状态码记录不同级别日志
  if (ctx.status >= 500) {
    logger.error('Request error', logData);
  } else if (ctx.status >= 400) {
    logger.warn('Request warning', logData);
  } else {
    logger.info('Request completed', logData);
  }
};

// 错误日志记录
module.exports.logError = (error, ctx = null) => {
  const logData = {
    message: error.message,
    stack: error.stack,
    timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
  };

  if (ctx) {
    logData.request = {
      method: ctx.method,
      url: ctx.url,
      body: ctx.request.body,
      params: ctx.params,
      query: ctx.query,
      userId: ctx.state.user?.id || 'unauthenticated'
    };
  }

  logger.error('Server error', logData);
};
