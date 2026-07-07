/**
 * 向量嵌入器
 *
 * 将文本转换为向量表示，用于语义搜索
 * 支持多种嵌入模型
 */

const { aiConfig, getProviderConfig } = require('../config');
const logger = require('../../config/logger');

/**
 * 生成文本嵌入向量
 * 使用当前配置的提供商的嵌入接口
 *
 * @param {string} text - 输入文本
 * @returns {Promise<Array<number>>} 嵌入向量
 */
const generateEmbedding = async (text) => {
  const config = getProviderConfig();

  try {
    // 使用Node.js 18+内置的全局fetch

    // 大多数提供商兼容OpenAI的嵌入接口
    const response = await fetch(`${config.baseUrl}/embeddings`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify({
        model: 'text-embedding-v1', // 通用嵌入模型名
        input: text,
      }),
    });

    if (!response.ok) {
      // 嵌入接口不可用时，返回空向量（降级处理）
      logger.warn('嵌入接口不可用，使用关键词搜索降级');
      return null;
    }

    const data = await response.json();
    return data.data?.[0]?.embedding || null;
  } catch (error) {
    logger.warn('生成嵌入向量失败:', error.message);
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

module.exports = { generateEmbedding, generateEmbeddings };
