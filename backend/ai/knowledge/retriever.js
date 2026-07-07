/**
 * 知识检索器
 *
 * 从知识库中检索与查询最相关的文档分块
 * 优先使用pgvector向量搜索，降级为关键词搜索
 */

const pool = require('../../config/database');
const { aiConfig } = require('../config');
const { generateEmbedding } = require('./embedder');
const logger = require('../../config/logger');

/**
 * 检索相关知识
 * @param {string} query - 查询文本
 * @param {object} [options] - 检索选项
 * @param {number} [options.topK] - 返回结果数
 * @param {number} [options.similarityThreshold] - 相似度阈值
 * @returns {Promise<Array<{content: string, title: string, similarity: number}>>}
 */
const retrieve = async (query, options = {}) => {
  const topK = options.topK || aiConfig.knowledge.topK;
  const threshold = options.similarityThreshold || aiConfig.knowledge.similarityThreshold;

  // 尝试向量搜索
  const embedding = await generateEmbedding(query);

  if (embedding) {
    try {
      const vectorStr = `[${embedding.join(',')}]`;
      const result = await pool.query(
        `SELECT content, title, metadata,
                1 - (embedding <=> $1::vector) as similarity
         FROM ai_knowledge_chunks
         WHERE embedding IS NOT NULL
         ORDER BY embedding <=> $1::vector
         LIMIT $2`,
        [vectorStr, topK]
      );

      const filtered = result.rows.filter(r => r.similarity >= threshold);
      if (filtered.length > 0) {
        return filtered;
      }
    } catch (error) {
      logger.warn('向量搜索失败，降级为关键词搜索:', error.message);
    }
  }

  // 降级：关键词搜索
  return keywordSearch(query, topK);
};

/**
 * 关键词搜索（降级方案）
 * @param {string} query - 查询文本
 * @param {number} limit - 返回数量
 * @returns {Promise<Array>}
 */
const keywordSearch = async (query, limit) => {
  try {
    // 提取关键词
    const keywords = query
      .replace(/[^\u4e00-\u9fa5a-zA-Z0-9]/g, ' ')
      .split(/\s+/)
      .filter(k => k.length > 1)
      .slice(0, 5);

    if (keywords.length === 0) return [];

    // 构建搜索条件
    const conditions = keywords.map((_, i) => `content ILIKE $${i + 1}`).join(' OR ');
    const params = keywords.map(k => `%${k}%`);
    params.push(limit);

    const result = await pool.query(
      `SELECT content, title, metadata, 0.5 as similarity
       FROM ai_knowledge_chunks
       WHERE ${conditions}
       ORDER BY chunk_index
       LIMIT $${params.length}`,
      params
    );

    return result.rows;
  } catch (error) {
    logger.warn('关键词搜索失败:', error.message);
    return [];
  }
};

/**
 * 添加知识到知识库
 * @param {object} params
 * @param {string} params.sourceType - 来源类型
 * @param {number} [params.sourceId] - 来源ID
 * @param {string} [params.title] - 标题
 * @param {string} params.content - 内容
 * @param {object} [params.metadata] - 元数据
 * @returns {Promise<Array>} 创建的分块列表
 */
const addKnowledge = async ({ sourceType, sourceId, title, content, metadata }) => {
  const { chunkText } = require('./chunker');
  const chunks = chunkText(content);

  const results = [];
  for (const chunk of chunks) {
    const embedding = await generateEmbedding(chunk.content);

    // 根据embedding是否为null选择SQL：非null时尝试::vector转换（pgvector可用时生效），
    // 为null时直接插入null，避免在TEXT类型列上执行::vector转换报错
    const useVectorCast = embedding !== null;
    const sql = useVectorCast
      ? `INSERT INTO ai_knowledge_chunks (source_type, source_id, title, content, chunk_index, embedding, metadata)
         VALUES ($1, $2, $3, $4, $5, $6::vector, $7)
         RETURNING id, title, content, chunk_index`
      : `INSERT INTO ai_knowledge_chunks (source_type, source_id, title, content, chunk_index, embedding, metadata)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         RETURNING id, title, content, chunk_index`;

    try {
      const result = await pool.query(
        sql,
        [
          sourceType,
          sourceId,
          title,
          chunk.content,
          chunk.index,
          useVectorCast ? `[${embedding.join(',')}]` : null,
          JSON.stringify(metadata || {}),
        ]
      );
      results.push(result.rows[0]);
    } catch (err) {
      // 如果::vector转换失败（embedding列为TEXT类型），降级为直接插入null
      if (useVectorCast) {
        logger.warn('向量插入失败，降级为关键词模式:', err.message);
        const fallbackResult = await pool.query(
          `INSERT INTO ai_knowledge_chunks (source_type, source_id, title, content, chunk_index, embedding, metadata)
           VALUES ($1, $2, $3, $4, $5, NULL, $6)
           RETURNING id, title, content, chunk_index`,
          [
            sourceType,
            sourceId,
            title,
            chunk.content,
            chunk.index,
            JSON.stringify(metadata || {}),
          ]
        );
        results.push(fallbackResult.rows[0]);
      } else {
        throw err;
      }
    }
  }

  return results;
};

module.exports = { retrieve, keywordSearch, addKnowledge };
