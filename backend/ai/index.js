/**
 * AI模块入口
 *
 * 统一管理AI相关的所有子模块:
 * - providers: 大模型提供商适配
 * - engine: 排料引擎
 * - tools: FunctionCall工具
 * - knowledge: RAG知识库
 * - intentRouter: 意图路由
 * - chatManager: 对话管理
 */

const { aiConfig, getProviderConfig } = require('./config');
const logger = require('../config/logger');

/**
 * AI模块初始化
 * 检查配置完整性，验证API Key
 */
const initialize = () => {
  const provider = aiConfig.defaultProvider;
  const config = aiConfig.providers[provider];

  if (!config) {
    logger.warn(`AI模块: 默认提供商 "${provider}" 配置不存在`);
    return false;
  }

  if (!config.apiKey) {
    logger.warn(`AI模块: 提供商 "${provider}" 未配置API Key，AI功能将不可用`);
    return false;
  }

  logger.info(`AI模块: 已启用提供商 "${config.name}" (${provider})`);
  return true;
};

/**
 * 获取当前提供商
 * @returns {object} 提供商配置
 */
const getCurrentProvider = () => {
  return getProviderConfig(aiConfig.defaultProvider);
};

/**
 * 检查AI功能是否可用
 * @returns {boolean}
 */
const isAvailable = () => {
  const config = aiConfig.providers[aiConfig.defaultProvider];
  return !!(config && config.apiKey);
};

module.exports = {
  initialize,
  getCurrentProvider,
  isAvailable,
  aiConfig,
  getProviderConfig,
};
