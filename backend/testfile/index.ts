/**
 * 后端服务入口 (TypeScript 版本)
 * 
 * 渐进式迁移: 混合使用 .ts 和 .js 文件
 * 新建功能用 .ts，旧功能保持 .js 逐步迁移
 */
import dotenv from 'dotenv';
dotenv.config();

import Koa from 'koa';
import path from 'path';
import fs from 'fs';
import koaBody from 'koa-body';
import cors from 'koa2-cors';
import logger from './config/logger';

// 中间件（JS 兼容导入）
const authMiddleware = require('./middleware/auth');
const responseMiddleware = require('./middleware/response');
const loggerMiddleware = require('./middleware/logger-middleware');
const errorHandlerMiddleware = require('./middleware/error-handler');
const { globalLimiter, loginLimiter } = require('./middleware/rateLimiter');

// 路由（JS 兼容导入）
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

const app = new Koa();

// ========== CORS 中间件 ==========
app.use(cors({
  origin: function (ctx: Koa.Context) {
    const allowedOrigins = [
      process.env.SERVER_URL || 'http://1.12.234.248',
      'http://localhost:5173',
      'http://127.0.0.1:5173',
    ];
    const requestOrigin = ctx.request.header.origin;
    if (requestOrigin && allowedOrigins.includes(requestOrigin)) {
      return requestOrigin;
    }
    return process.env.SERVER_URL || 'http://1.12.234.248';
  },
  credentials: true,
  allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization', 'Accept'],
}));

// ========== 全局中间件 ==========
app.use(responseMiddleware());
app.use(loggerMiddleware());
app.use(globalLimiter);  // 全局API限流

app.use(koaBody({
  multipart: true,
  formidable: {
    maxFileSize: 50 * 1024 * 1024, // 50MB
    uploadDir: path.join(__dirname, 'temp'),
    keepExtensions: true,
    multiples: true,
    onFileBegin: (name: string, file: any) => {
      const tempDir = path.join(__dirname, 'temp');
      if (!fs.existsSync(tempDir)) {
        fs.mkdirSync(tempDir, { recursive: true });
      }
    },
  },
}));

// ========== 登录接口专用限流（更严格） ==========
app.use(async (ctx: Koa.Context, next: Koa.Next) => {
  if (ctx.path === '/v1/auth/login') {
    return loginLimiter(ctx, next);
  }
  await next();
});

// ========== Swagger文档 ==========
const getSwaggerSpec = require('./swagger');

app.use(async (ctx: Koa.Context, next: Koa.Next) => {
  if (ctx.path === '/api-docs') {
    ctx.type = 'text/html';
    ctx.body = fs.createReadStream(path.join(__dirname, 'public', 'swagger-custom.html'));
    return;
  }
  await next();
});

app.use(async (ctx: Koa.Context, next: Koa.Next) => {
  if (ctx.path === '/swagger.json') {
    const swaggerSpec = getSwaggerSpec();
    ctx.body = swaggerSpec;
    ctx.type = 'application/json';
  } else if (ctx.path === '/swagger/history') {
    (ctx as any).success({
      history: [
        { version: '1.4.0', date: '2026-06-09', changes: ['后端TS迁移', '安全加固(JWT/密码/限流)', 'Knex集成'] },
        { version: '1.3.0', date: '2025-12-25', changes: ['添加环境变量自动切换服务器地址', '实现文档版本管理'] },
        { version: '1.2.0', date: '2025-12-24', changes: ['优化文档加载性能', '实现文档缓存重启清理'] },
        { version: '1.1.0', date: '2025-12-23', changes: ['添加Swagger文档生成', '修复API文档显示问题'] },
        { version: '1.0.0', date: '2025-12-22', changes: ['初始版本', '基础API接口'] },
      ],
    });
  } else {
    await next();
  }
});

// ========== 错误处理 ==========
app.use(errorHandlerMiddleware());

// ========== 路由注册 ==========
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

// ========== 静态文件服务 ==========
const getContentType = (ext: string): string => {
  const contentTypes: Record<string, string> = {
    '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png',
    '.gif': 'image/gif', '.webp': 'image/webp', '.bmp': 'image/bmp',
    '.svg': 'image/svg+xml', '.pdf': 'application/pdf',
    '.mp4': 'video/mp4', '.mov': 'video/quicktime',
    '.mp3': 'audio/mpeg', '.wav': 'audio/wav',
    '.html': 'text/html', '.css': 'text/css',
    '.js': 'application/javascript', '.json': 'application/json',
  };
  return contentTypes[ext] || 'application/octet-stream';
};

app.use(async (ctx: Koa.Context, next: Koa.Next) => {
  if (ctx.path.startsWith('/upload')) {
    const filePath = ctx.path.substring(7);
    const fullPath = path.join(__dirname, 'upload', decodeURIComponent(filePath));
    try {
      const stats = await fs.promises.stat(fullPath);
      if (stats.isFile()) {
        ctx.set('Content-Type', getContentType(path.extname(fullPath).toLowerCase()));
        ctx.body = fs.createReadStream(fullPath);
        return;
      }
    } catch (_) { /* 文件不存在 */ }
  }
  await next();
});

// 404处理
app.use(async (ctx: Koa.Context) => {
  (ctx as any).fail(1002, '接口不存在');
});

// ========== 启动服务 ==========
const port = parseInt(String(process.env.PORT || '3000'), 10);

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`🚀 服务器运行在 http://0.0.0.0:${port}`);
    console.log(`📖 Swagger文档: http://0.0.0.0:${port}/api-docs`);
    console.log(`🔧 环境: ${process.env.NODE_ENV || 'development'}`);
  });
}

export default app.callback();