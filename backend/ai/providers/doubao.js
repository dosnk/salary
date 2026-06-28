/**
 * 豆包(字节跳动)提供商适配器
 * 兼容OpenAI API格式
 */

const BaseProvider = require('./baseProvider');
const { aiConfig } = require('../config');
const logger = require('../../config/logger');

class DoubaoProvider extends BaseProvider {
  constructor() {
    super(aiConfig.providers.doubao);
  }

  async chat(messages, options = {}) {
    const fetch = (await import('node-fetch')).default;

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
      logger.error('豆包API错误:', error);
      throw new Error(`豆包API错误: ${response.status}`);
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

  async chatStream(messages, onChunk, options = {}) {
    const fetch = (await import('node-fetch')).default;

    const body = {
      model: this.model,
      messages,
      max_tokens: options.maxTokens || this.maxTokens,
      temperature: options.temperature || this.temperature,
      stream: true,
    };

    const response = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: this.buildHeaders(),
      body: JSON.stringify(body),
    });

    let fullContent = '';
    const text = await response.text();
    const lines = text.split('\n').filter(l => l.startsWith('data: '));

    for (const line of lines) {
      const data = line.slice(6);
      if (data === '[DONE]') break;
      try {
        const parsed = JSON.parse(data);
        const delta = parsed.choices[0]?.delta?.content || '';
        if (delta) {
          fullContent += delta;
          if (onChunk) onChunk(delta);
        }
      } catch (e) {}
    }

    return { content: fullContent };
  }
}

module.exports = DoubaoProvider;
