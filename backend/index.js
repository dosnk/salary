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
const migrationRouter = require('./routes/migration');

const app = new Koa();

// CORS中间件 - 生产环境限制允许的域名
app.use(cors({
  origin: function(ctx) {
    // 生产环境：只允许指定域名访问
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
    // 如果没有origin头或不在允许列表，返回服务器地址
    return process.env.SERVER_URL || 'http://1.12.234.248';
  },
  credentials: true,
  allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization', 'Accept']
}));

// 中间件
app.use(responseMiddleware());
app.use(loggerMiddleware());
app.use(koaBody({
  multipart: true,
  formidable: {
    maxFileSize: 50 * 1024 * 1024, // 50MB
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
app.use(migrationRouter.routes()).use(migrationRouter.allowedMethods());

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