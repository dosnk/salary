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
    },
    // 仅接受 access token 作为访问凭据
    // 签发时 access token 带 type:'access'，refresh token 带 type:'refresh'（30天有效）
    // 若不区分，refresh token 在 2 小时内等价于 access token，可调用全部业务接口（2026-09-10 加固）
    isRevoked: async (ctx, decodedToken) => {
      return decodedToken.type !== 'access';
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