const jwt = require('koa-jwt');

// JWT认证中间件
module.exports = {
  // 验证Token
  authenticate: jwt({
    secret: process.env.JWT_SECRET,
    getToken: (ctx) => {
      if (ctx.headers.authorization && ctx.headers.authorization.split(' ')[0] === 'Bearer') {
        return ctx.headers.authorization.split(' ')[1];
      }
      return null;
    }
  }).unless({
    path: [
      /^\/v1\/auth\/login/,
      /^\/v1\/auth\/register/,
      /^\/api-docs/  // 修复Swagger文档路径
    ]
  }),

  // 权限验证
  authorize: (roles) => {
    return async (ctx, next) => {
      const user = ctx.state.user;
      if (!roles.includes(user.role)) {
        ctx.fail(4002, '无操作权限');
        return;
      }
      await next();
    };
  }
};