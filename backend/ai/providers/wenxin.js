/**
 * 文心一言提供商适配器
 * 使用百度API格式（需先获取access_token）
 * 支持普通对话和流式对话（SSE）
 */

const BaseProvider = require('./baseProvider');
const { aiConfig } = require('../config');
const logger = require('../../config/logger');

class WenxinProvider extends BaseProvider {
  constructor() {
    super(aiConfig.providers.wenxin);
    this.accessToken = null;
    this.tokenExpiry = 0;
  }

  /**
   * 获取百度access_token
   */
  async getAccessToken() {
    // 缓存token，有效期内的不重复获取
    if (this.accessToken && Date.now() < this.tokenExpiry) {
      return this.accessToken;
    }

    const fetch = (await import('node-fetch')).default;
    const url = `https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=${this.apiKey}&client_secret=${this.config.secretKey}`;

    const response = await fetch(url, { method: 'POST' });
    const data = await response.json();

    this.accessToken = data.access_token;
    this.tokenExpiry = Date.now() + (data.expires_in - 60) * 1000; // 提前60秒过期

    return this.accessToken;
  }

  /**
   * 获取模型对应的端点路径
   */
  getModelEndpoint() {
    const modelMap = {
      'ernie-4.0-8k': '/completions_pro',
      'ernie-3.5-8k': '/completions',
      'ernie-speed-8k': '/ernie_speed',
    };
    return modelMap[this.model] || '/completions_pro';
  }

  async chat(messages, options = {}) {
    const fetch = (await import('node-fetch')).default;
    const token = await this.getAccessToken();
    const endpoint = this.getModelEndpoint();
    const url = `${this.baseUrl}${endpoint}?access_token=${token}`;

    // 文心格式转换
    const body = {
      messages: messages.map(m => ({ role: m.role, content: m.content })),
      temperature: options.temperature || this.temperature,
    };

    if (options.maxTokens) {
      body.max_output_tokens = options.maxTokens;
    }

    // FunctionCall支持
    if (options.tools && options.tools.length > 0) {
      body.functions = options.tools.map(t => ({
        name: t.name,
        description: t.description,
        parameters: t.parameters,
      }));
    }

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response.text();
      logger.error('文心一言API错误:', error);
      throw new Error(`文心一言API错误: ${response.status}`);
    }

    const data = await response.json();
    const result = {
      content: data.result || '',
      usage: data.usage ? { prompt_tokens: data.usage.prompt_tokens, completion_tokens: data.usage.completion_tokens } : undefined,
    };

    // 解析FunctionCall
    if (data.function_call) {
      result.toolCalls = [{
        name: data.function_call.name,
        arguments: JSON.parse(data.function_call.arguments),
      }];
    }

    return result;
  }

  /**
   * 流式对话 - 文心一言SSE格式
   * 请求体添加 stream: true，返回SSE事件流
   */
  async chatStream(messages, onChunk, options = {}) {
    const fetch = (await import('node-fetch')).default;
    const token = await this.getAccessToken();
    const endpoint = this.getModelEndpoint();
    const url = `${this.baseUrl}${endpoint}?access_token=${token}`;

    const body = {
      messages: messages.map(m => ({ role: m.role, content: m.content })),
      temperature: options.temperature || this.temperature,
      stream: true,
    };

    if (options.maxTokens) {
      body.max_output_tokens = options.maxTokens;
    }

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response.text();
      logger.error('文心一言流式API错误:', error);
      throw new Error(`文心一言流式API错误: ${response.status}`);
    }

    let fullContent = '';

    // 文心一言SSE格式: data: {json}\n\n
    const text = await response.text();
    const lines = text.split('\n').filter(l => l.startsWith('data: '));

    for (const line of lines) {
      const data = line.slice(6).trim();
      if (data === '[DONE]') break;
      try {
        const parsed = JSON.parse(data);
        // 文心流式返回字段为 result
        const delta = parsed.result || '';
        if (delta) {
          fullContent += delta;
          if (onChunk) onChunk(delta);
        }
        // 流式结束标记
        if (parsed.is_end) break;
      } catch (e) {
        // 忽略解析错误
      }
    }

    return { content: fullContent };
  }
}

module.exports = WenxinProvider;
