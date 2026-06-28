/**
 * AI提供商基类
 * 所有提供商适配器必须继承此类并实现 chat 方法
 */

class BaseProvider {
  /**
   * @param {object} config - 提供商配置
   */
  constructor(config) {
    this.name = config.name;
    this.apiKey = config.apiKey;
    this.baseUrl = config.baseUrl;
    this.model = config.model;
    this.maxTokens = config.maxTokens || 4096;
    this.temperature = config.temperature || 0.7;
  }

  /**
   * 发送对话请求（子类必须实现）
   * @param {Array<{role: string, content: string}>} messages - 消息列表
   * @param {object} [options] - 额外选项（tools, stream等）
   * @returns {Promise<{content: string, toolCalls?: Array, usage?: object}>}
   */
  async chat(messages, options = {}) {
    throw new Error('子类必须实现 chat 方法');
  }

  /**
   * 流式对话（子类可选实现）
   * @param {Array} messages - 消息列表
   * @param {function} onChunk - 流式回调 (text: string) => void
   * @param {object} [options] - 额外选项
   * @returns {Promise<{content: string, usage?: object}>}
   */
  async chatStream(messages, onChunk, options = {}) {
    // 默认实现：非流式调用
    const result = await this.chat(messages, options);
    if (onChunk) onChunk(result.content);
    return result;
  }

  /**
   * 构建请求头
   * @returns {object}
   */
  buildHeaders() {
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.apiKey}`,
    };
  }
}

module.exports = BaseProvider;
