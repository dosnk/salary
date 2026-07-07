/**
 * 智谱ChatGLM提供商适配器
 * 兼容OpenAI API格式
 */

const BaseProvider = require('./baseProvider');
const { aiConfig } = require('../config');
const logger = require('../../config/logger');

class GlmProvider extends BaseProvider {
  constructor() {
    super(aiConfig.providers.glm);
  }

  async chat(messages, options = {}) {
    // 使用Node.js 18+内置的全局fetch

    const body = {
      model: this.model,
      messages,
      max_tokens: options.maxTokens || this.maxTokens,
      temperature: options.temperature || this.temperature,
    };

    if (options.tools && options.tools.length > 0) {
      body.tools = options.tools.map(t => ({
        type: 'function',
        function: t
      }));
      body.tool_choice = options.toolChoice || 'auto';
    }

    const response = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: this.buildHeaders(),
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response.text();
      logger.error('ChatGLM API错误:', error);
      throw new Error(`ChatGLM API错误: ${response.status}`);
    }

    const data = await response.json();
    const choice = data.choices[0];

    const result = {
      content: choice.message.content || '',
      usage: data.usage,
    };

    if (choice.message.tool_calls) {
      result.toolCalls = choice.message.tool_calls.map(tc => ({
        id: tc.id,
        name: tc.function.name,
        arguments: JSON.parse(tc.function.arguments),
      }));
    }

    return result;
  }

  /**
   * 真流式对话 - 使用 response.body.getReader() 逐块读取
   * 支持 FunctionCall（通过 options.tools 传入）
   */
  async chatStream(messages, onChunk, options = {}) {
    const body = {
      model: this.model,
      messages,
      max_tokens: options.maxTokens || this.maxTokens,
      temperature: options.temperature || this.temperature,
      stream: true,
    };

    // 流式模式支持FunctionCall
    if (options.tools && options.tools.length > 0) {
      body.tools = options.tools.map(t => ({ type: 'function', function: t }));
      body.tool_choice = options.toolChoice || 'auto';
    }

    const response = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: this.buildHeaders(),
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response.text();
      logger.error('ChatGLM API错误:', error);
      throw new Error(`ChatGLM API错误: ${response.status}`);
    }

    // 调用基类真流式读取（OpenAI兼容格式）
    return await this.readStream(response, onChunk);
  }
}

module.exports = GlmProvider;
