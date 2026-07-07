require('dotenv').config();

const Koa = require('koa');
const path = require('path'); // 引入path模块处理文件路径
const koaBody = require('koa-body').default; // 修复koa-body v6.x的导入问题
const cors = require('koa2-cors');
const { koaSwagger } = require('koa2-swagger-ui');
const fs = require('fs');
const serve = require('koa-static'); // 添加静态文件服务
const authMiddleware = require('./middleware/auth');
const responseMiddleware = require('./middleware/response');
const loggerMiddleware = require('./middleware/logger-middleware');
const logger = require('./config/logger'); // 导入日志模块
const errorHandlerMiddleware = require('./middleware/error-handler'); // 导入错误处理中间件
const authRouter = require('./routes/auth');
const userRouter = require('./routes/users');
const projectRouter = require('./routes/projects');
const uploadRouter = require('./routes/upload');
const settlementRouter = require('./routes/settlements');
const statisticsRouter = require('./routes/statistics');
const dictionaryRouter = require('./routes/dictionary');
const advancesRouter = require('./routes/advances');
const messagesRouter = require('./routes/messages');
const salarySheetRouter = require('./routes/salarySheet');
const aiRouter = require('./routes/ai');
// 限流中间件：基于Redis的滑动窗口限流，按用户/IP控制请求频率
const { globalLimiter, loginLimiter, sensitiveLimiter } = require('./middleware/rateLimiter');

const app = new Koa();

// 安全响应头中间件（等价于helmet，手动实现以避免引入额外依赖）
// 添加安全相关的HTTP响应头，防止常见Web攻击
app.use(async (ctx, next) => {
  await next();
  // 防止MIME类型嗅探
  ctx.set('X-Content-Type-Options', 'nosniff');
  // 防止点击劫持（禁止被嵌入iframe）
  ctx.set('X-Frame-Options', 'DENY');
  // 启用浏览器XSS过滤器
  ctx.set('X-XSS-Protection', '1; mode=block');
  // HSTS：强制HTTPS（仅生产环境启用，且需HTTPS才生效）
  if (process.env.NODE_ENV === 'production') {
    ctx.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  }
  // 内容安全策略：仅允许同源资源加载
  ctx.set('Content-Security-Policy', "default-src 'self'");
  // 禁用referrer泄露完整URL
  ctx.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  // 禁用缓存（API响应不应被缓存）
  ctx.set('Cache-Control', 'no-store, no-cache, must-revalidate');
  // 隐藏X-Powered-By
  ctx.remove('X-Powered-By');
});

// CORS中间件 - 严格限制允许的域名
app.use(cors({
  origin: function(ctx) {
    // 白名单：仅允许配置的域名访问
    // Android客户端无Origin头，不经过CORS检查
    // Web端必须在白名单内
    const allowedOrigins = [
      'http://1.12.234.248:9080',
      'http://1.12.234.248/api-docs',  // API文档
      'http://localhost:5173',  // 本地开发
      'http://127.0.0.1:5173'   // 本地开发
    ];
    const requestOrigin = ctx.request.header.origin;
    if (requestOrigin && allowedOrigins.includes(requestOrigin)) {
      return requestOrigin;
    }
    // 无Origin头（移动端/服务端请求）放行，不设置CORS头
    // 不在白名单的Origin不返回Access-Control-Allow-Origin，浏览器会拦截
    return false;
  },
  credentials: true,
  allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization', 'Accept'],
  exposeHeaders: ['X-RateLimit-Limit', 'X-RateLimit-Remaining', 'X-RateLimit-Reset']
}));

// 中间件
app.use(responseMiddleware());
app.use(loggerMiddleware());
app.use(koaBody({
  multipart: true,
  formidable: {
    maxFileSize: 500 * 1024 * 1024, // 500MB（业务层各类型有独立限制，见 controllers/upload.js）
    maxFieldsSize: 20 * 1024 * 1024, // 20MB（text field 总大小）
    uploadDir: path.join(__dirname, 'temp'),
    keepExtensions: true,
    multiples: true, // 支持多文件上传
    onFileBegin: (name, file) => {
      // 确保临时目录存在
      const tempDir = path.join(__dirname, 'temp');
      if (!fs.existsSync(tempDir)) {
        fs.mkdirSync(tempDir, { recursive: true });
      }
    }
  }
}));

// 动态生成swagger文档（延迟加载，不阻塞启动）
const swaggerJSDoc = require('swagger-jsdoc');
const getSwaggerSpec = require('./swagger');

// 不在启动时生成swagger文档，改为按需生成
// const swaggerSpec = getSwaggerSpec();
// logger.info('Swagger文档已生成');

// Swagger文档 - 自定义页面，包含导出功能
app.use(async (ctx, next) => {
  if (ctx.path === '/api-docs') {
    ctx.type = 'text/html';
    ctx.body = fs.createReadStream(path.join(__dirname, 'public', 'swagger-custom.html'));
    return;
  }
  await next();
});

// 暴露swagger.json（按需生成）
app.use(async (ctx, next) => {
  if (ctx.path === '/swagger.json') {
    const swaggerSpec = getSwaggerSpec();
    ctx.body = swaggerSpec;
    ctx.type = 'application/json';
  } else if (ctx.path === '/swagger/history') {
    // 文档版本历史记录
    ctx.success({
      history: [
        {
          version: '1.3.0',
          date: '2025-12-25',
          changes: ['添加环境变量自动切换服务器地址', '实现文档版本管理']
        },
        {
          version: '1.2.0',
          date: '2025-12-24',
          changes: ['优化文档加载性能', '实现文档缓存重启清理']
        },
        {
          version: '1.1.0',
          date: '2025-12-23',
          changes: ['添加Swagger文档生成', '修复API文档显示问题']
        },
        {
          version: '1.0.0',
          date: '2025-12-22',
          changes: ['初始版本', '基础API接口']
        }
      ]
    });
  } else {
    await next();
  }
});

// JWT认证中间件 - 已移除全局应用，在路由中单独配置
// app.use(authMiddleware.authenticate);

// 错误处理中间件
app.use(errorHandlerMiddleware());

// 主动初始化Redis连接（触发连接建立）
// 不阻塞启动，连接失败不影响业务可用性（限流等功能自动降级）
const { getRedisClient, isRedisAvailable } = require('./config/redis');
getRedisClient(); // 触发ioredis自动连接（非lazyConnect模式会立即连接）
logger.info('Redis初始化', { available: isRedisAvailable() });

// 全局API限流：100次/分钟/IP，防止恶意请求刷接口
// Redis不可用时自动放行，不影响业务可用性
app.use(globalLimiter);

// 健康检查接口 - 无需鉴权，供前端探测后端在线状态
// 返回服务器时间和状态，前端通过测量请求耗时计算延迟
app.use(async (ctx, next) => {
  if (ctx.path === '/v1/health' && ctx.method === 'GET') {
    ctx.success({
      status: 'ok',
      timestamp: Date.now(),
      uptime: process.uptime()
    });
    return;
  }
  await next();
});

// 注册路由
app.use(authRouter.routes()).use(authRouter.allowedMethods());
app.use(userRouter.routes()).use(userRouter.allowedMethods());
app.use(projectRouter.routes()).use(projectRouter.allowedMethods());
app.use(uploadRouter.routes()).use(uploadRouter.allowedMethods());
app.use(settlementRouter.routes()).use(settlementRouter.allowedMethods());
app.use(statisticsRouter.routes()).use(statisticsRouter.allowedMethods());
app.use(dictionaryRouter.routes()).use(dictionaryRouter.allowedMethods());
app.use(advancesRouter.routes()).use(advancesRouter.allowedMethods());
app.use(messagesRouter.routes()).use(messagesRouter.allowedMethods());
app.use(salarySheetRouter.routes()).use(salarySheetRouter.allowedMethods());
app.use(aiRouter.routes()).use(aiRouter.allowedMethods());

// 静态文件服务 - 用于访问上传的文件（放在路由之后，404处理之前）
app.use(async (ctx, next) => {
  if (ctx.path.startsWith('/upload')) {
    const filePath = ctx.path.substring(7); // 移除 /upload 前缀
    const decodedPath = decodeURIComponent(filePath); // 解码URL编码
    const fullPath = path.join(__dirname, 'upload', decodedPath);
    
    try {
      const stats = await fs.promises.stat(fullPath);
      if (stats.isFile()) {
        const ext = path.extname(fullPath).toLowerCase();
        const contentType = getContentType(ext);
        
        ctx.set('Content-Type', contentType);
        ctx.body = fs.createReadStream(fullPath);
        return;
      }
    } catch (err) {
      // 文件不存在，继续处理
    }
  }
  await next();
});

// 静态文件服务 - 用于访问 public 目录（自定义 Swagger UI 页面）
app.use(async (ctx, next) => {
  if (ctx.path.startsWith('/public')) {
    const filePath = ctx.path.substring(8); // 移除 /public 前缀
    const decodedPath = decodeURIComponent(filePath); // 解码URL编码
    const fullPath = path.join(__dirname, 'public', decodedPath);
    
    try {
      const stats = await fs.promises.stat(fullPath);
      if (stats.isFile()) {
        const ext = path.extname(fullPath).toLowerCase();
        const contentType = getContentType(ext);
        
        ctx.set('Content-Type', contentType);
        ctx.body = fs.createReadStream(fullPath);
        return;
      }
    } catch (err) {
      // 文件不存在，继续处理
    }
  }
  await next();
});

// 获取文件Content-Type
function getContentType(ext) {
  const contentTypes = {
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.png': 'image/png',
    '.gif': 'image/gif',
    '.webp': 'image/webp',
    '.bmp': 'image/bmp',
    '.svg': 'image/svg+xml',
    '.pdf': 'application/pdf',
    '.mp4': 'video/mp4',
    '.avi': 'video/avi',
    '.mov': 'video/quicktime',
    '.wmv': 'video/x-ms-wmv',
    '.flv': 'video/x-flv',
    '.mp3': 'audio/mpeg',
    '.wav': 'audio/wav',
    '.aac': 'audio/aac',
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json'
  };
  return contentTypes[ext] || 'application/octet-stream';
}

// 404处理 - 必须在所有路由和静态文件服务之后
app.use(async (ctx) => {
  ctx.fail(1002, '接口不存在');
});

const port = process.env.PORT || 80;

// 只在非测试环境下启动服务器
let server;
if (process.env.NODE_ENV !== 'test') {
  server = app.listen(port, '0.0.0.0', () => {
    console.log(`服务器运行在 http://0.0.0.0:${port}`);
    console.log(`Swagger文档（含导出功能）在 http://0.0.0.0:${port}/api-docs`);
  });
}

// 导出 app 供 supertest 使用
module.exports = app.callback();