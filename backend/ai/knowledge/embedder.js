/**
 * 向量嵌入器
 *
 * 将文本转换为向量表示，用于语义搜索
 * 模型名从 ai/config.js 动态读取（AI_EMBEDDING_MODEL 环境变量 > 提供商默认映射）
 *
 * 提供商支持情况：
 * - 通义千问: text-embedding-v3（1024维）
 * - 智谱: embedding-3（2048维）
 * - 豆包: doubao-embedding
 * - 文心（非OpenAI兼容接口）/ DeepSeek（无embedding接口）: 不支持，返回null降级关键词检索
 */

const { aiConfig, getProviderConfig } = require('../config');
const logger = require('../../config/logger');

/** 嵌入接口请求超时（毫秒） */
const EMBEDDING_TIMEOUT_MS = 15000;

/**
 * 获取当前生效的向量模型名
 * @returns {string|null} 模型名，null表示当前提供商不支持向量能力
 */
const getEmbeddingModel = () => aiConfig.embeddingModel;

/**
 * 生成文本嵌入向量
 * 使用当前配置的提供商的 OpenAI 兼容嵌入接口
 *
 * @param {string} text - 输入文本
 * @returns {Promise<Array<number>|null>} 嵌入向量；模型不支持或调用失败时返回 null（降级关键词检索）
 */
const generateEmbedding = async (text) => {
  const model = getEmbeddingModel();

  // 当前提供商无向量模型（文心/DeepSeek），直接降级
  if (!model) {
    return null;
  }

  const config = getProviderConfig();

  try {
    // 使用Node.js 18+内置的全局fetch，带超时控制
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), EMBEDDING_TIMEOUT_MS);

    const response = await fetch(`${config.baseUrl}/embeddings`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify({
        model,
        input: text,
      }),
      signal: controller.signal,
    });

    clearTimeout(timeout);

    if (!response.ok) {
      // 嵌入接口不可用时，返回空向量（降级处理）
      const errBody = await response.text().catch(() => '');
      logger.warn(`嵌入接口不可用(HTTP ${response.status}): ${errBody.substring(0, 200)}，降级为关键词搜索`);
      return null;
    }

    const data = await response.json();
    const embedding = data.data?.[0]?.embedding;
    if (!Array.isArray(embedding) || embedding.length === 0) {
      logger.warn('嵌入接口返回格式异常，降级为关键词搜索');
      return null;
    }
    return embedding;
  } catch (error) {
    logger.warn(`生成嵌入向量失败(模型:${model}): ${error.message}，降级为关键词搜索`);
    return null;
  }
};

/**
 * 批量生成嵌入向量
 * @param {Array<string>} texts - 文本数组
 * @returns {Promise<Array<Array<number>|null>>}
 */
const generateEmbeddings = async (texts) => {
  const results = [];
  for (const text of texts) {
    const embedding = await generateEmbedding(text);
    results.push(embedding);
  }
  return results;
};

module.exports = { generateEmbedding, generateEmbeddings, getEmbeddingModel };
