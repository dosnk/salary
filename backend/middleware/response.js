const errorCodes = require('../config/error-codes');

// 统一响应中间件
module.exports = () => {
  return async (ctx, next) => {
    // 成功响应
    ctx.success = (data = null, msg = 'ok') => {
      ctx.body = {
        code: 200,
        data,
        msg
      };
    };

    // 失败响应
    ctx.fail = (code, msg = null, data = null) => {
      const errorMsg = msg || errorCodes[code] || '未知错误';
      ctx.body = {
        code,
        data,
        msg: errorMsg
      };
    };

    // 分页响应
    ctx.paginate = (list, total, page, size) => {
      // 修复：确保 hasNext 计算正确，避免无限循环
      // 当已加载数量 >= 总数量时，hasNext 应该为 false
      const loadedCount = page * size;
      const hasNext = loadedCount < total && list.length > 0;
      
      ctx.body = {
        code: 200,
        data: {
          list,
          total,
          page,
          size,
          hasNext
        },
        msg: 'ok'
      };
    };

    await next();
  };
};