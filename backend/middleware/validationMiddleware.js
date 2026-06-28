// 参数验证中间件
const Joi = require('joi');
const logger = require('../config/logger');
const errorCodes = require('../config/error-codes');

/**
 * 创建参数验证中间件
 * @param {Object} schema - Joi验证规则
 * @param {string} source - 验证数据来源 (body, query, params)
 * @returns {Function} Koa中间件
 */
const validate = (schema, source = 'body') => {
  return async (ctx, next) => {
    try {
      // 验证请求数据
      const validated = await Joi.object(schema).validateAsync(ctx[source], {
        abortEarly: false, // 收集所有错误而不是第一个错误
        stripUnknown: true // 移除未定义的字段
      });

      // 将验证后的数据放回ctx
      ctx[source] = validated;
      await next();
    } catch (error) {
      if (error.isJoi) {
        // Joi验证错误
        const errorMessage = error.details.map(detail => detail.message).join('; ');
        logger.warn(`参数验证失败: ${errorMessage}`, { url: ctx.url, method: ctx.method });

        ctx.fail(1001, errorMessage);
      } else {
        // 其他错误
        logger.error('验证中间件异常', { error: error.message, stack: error.stack });
        ctx.fail(errorCodes.SERVER_ERROR, '服务器验证中间件异常');
      }
    }
  };
};

module.exports = {
  validate
};
