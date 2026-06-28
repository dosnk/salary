/**
 * Provider工厂 - 根据配置创建对应的提供商实例
 */

const TongyiProvider = require('./tongyi');
const WenxinProvider = require('./wenxin');
const DeepSeekProvider = require('./deepseek');
const GlmProvider = require('./glm');
const DoubaoProvider = require('./doubao');
const { aiConfig } = require('../config');

const providerMap = {
  tongyi: TongyiProvider,
  wenxin: WenxinProvider,
  deepseek: DeepSeekProvider,
  glm: GlmProvider,
  doubao: DoubaoProvider,
};

/**
 * 创建AI提供商实例
 * @param {string} [name] - 提供商名称，默认使用配置中的默认提供商
 * @returns {BaseProvider} 提供商实例
 */
const createProvider = (name) => {
  const providerName = name || aiConfig.defaultProvider;
  const ProviderClass = providerMap[providerName];

  if (!ProviderClass) {
    throw new Error(`不支持的AI提供商: ${providerName}`);
  }

  return new ProviderClass();
};

module.exports = { createProvider, providerMap };
