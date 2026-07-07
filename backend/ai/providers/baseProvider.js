/**
 * AI提供商基类
 * 所有提供商适配器必须继承此类并实现 chat 方法
 *
 * 最佳实践：
 * - chatStream 默认实现采用真流式读取（response.body.getReader()）
 *   避免使用 response.text() 一次性缓冲，确保首字延迟≈首个token延迟
 * - 支持OpenAI兼容格式的SSE解析（data: {json}\n\n）
 * - 统一处理 FunctionCall 的增量 tool_calls 合并
 */

const logger = require('../../config/logger');

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
    // 保留config引用，子类可访问secretKey等额外字段
    this.config = config;
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
   * 流式对话（子类可选覆盖）
   * 默认实现：调用 chat 后一次性回调（作为兜底，非真流式）
   * 子类应覆盖此方法以实现真流式
   * @param {Array} messages - 消息列表
   * @param {function} onChunk - 流式回调 (text: string) => void
   * @param {object} [options] - 额外选项
   * @returns {Promise<{content: string, toolCalls?: Array, usage?: object}>}
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

  /**
   * 真流式读取SSE响应（OpenAI兼容格式）
   * 使用 response.body.getReader() 逐块读取，避免 response.text() 缓冲
   *
   * @param {Response} response - fetch响应对象
   * @param {function} onChunk - 文本增量回调 (text: string) => void
   * @param {object} [hooks] - 解析钩子，用于处理不同提供商的SSE字段差异
   * @param {function} [hooks.extractDelta] - 从parsed JSON提取文本delta，默认读取 choices[0].delta.content
   * @param {function} [hooks.isEnd] - 判断是否流式结束，默认检测 [DONE] 或 finish_reason='stop'
   * @returns {Promise<{content: string, toolCalls: Array}>} 完整内容与工具调用
   */
  async readStream(response, onChunk, hooks = {}) {
    const extractDelta = hooks.extractDelta || ((parsed) => parsed.choices?.[0]?.delta?.content || '');
    const isEnd = hooks.isEnd || ((parsed, rawData) => rawData === '[DONE]' || parsed.choices?.[0]?.finish_reason === 'stop');

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let fullContent = '';
    const toolCallMap = new Map();  // 按index合并增量tool_calls

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // 增量解码，stream:true确保多字节字符跨块正确处理
        buffer += decoder.decode(value, { stream: true });

        // 按换行切分，保留最后不完整的行到buffer
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed || !trimmed.startsWith('data: ')) continue;

          const data = trimmed.slice(6).trim();
          if (data === '[DONE]') {
            continue;  // 流结束标记，跳出循环由外层done处理
          }

          try {
            const parsed = JSON.parse(data);

            // 提取并回调文本增量
            const delta = extractDelta(parsed);
            if (delta) {
              fullContent += delta;
              if (onChunk) onChunk(delta);
            }

            // 合并增量tool_calls（OpenAI流式格式：每个delta包含部分tool_call字段）
            const deltaToolCalls = parsed.choices?.[0]?.delta?.tool_calls;
            if (Array.isArray(deltaToolCalls)) {
              for (const tc of deltaToolCalls) {
                const idx = tc.index ?? 0;
                if (!toolCallMap.has(idx)) {
                  toolCallMap.set(idx, { id: tc.id || '', name: '', arguments: '' });
                }
                const existing = toolCallMap.get(idx);
                if (tc.id) existing.id = tc.id;
                if (tc.function?.name) existing.name += tc.function.name;
                if (tc.function?.arguments) existing.arguments += tc.function.arguments;
              }
            }

            // 检测流式结束
            if (isEnd(parsed, data)) {
              // 流式结束，但继续消费reader直到done，确保资源释放
              continue;
            }
          } catch (e) {
            // 忽略单行JSON解析错误（可能是keepalive行或部分数据）
            logger.debug(`[${this.name}] SSE行解析跳过: ${data.slice(0, 100)}`);
          }
        }
      }
    } finally {
      // 确保reader释放
      try { reader.releaseLock(); } catch (e) { /* 已释放 */ }
    }

    // 整理toolCalls结果
    const toolCalls = [];
    for (const tc of toolCallMap.values()) {
      if (tc.name) {
        let args = {};
        try {
          args = tc.arguments ? JSON.parse(tc.arguments) : {};
        } catch (e) {
          logger.warn(`[${this.name}] tool_call参数解析失败: ${tc.arguments}`);
        }
        toolCalls.push({ id: tc.id, name: tc.name, arguments: args });
      }
    }

    return { content: fullContent, toolCalls };
  }
}

module.exports = BaseProvider;
