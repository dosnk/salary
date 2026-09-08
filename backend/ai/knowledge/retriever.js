/**
 * 知识检索器
 *
 * 混合检索：向量语义搜索（pgvector，模型可用时）+ 中文关键词搜索（始终可用）
 * 融合策略：向量结果优先，关键词结果补充去重，合计 topK
 */

const pool = require('../../config/database');
const { aiConfig } = require('../config');
const { generateEmbedding, getEmbeddingModel } = require('./embedder');
const logger = require('../../config/logger');

/** 中文常见停用片段（bigram噪声过滤） */
const STOP_WORDS = new Set([
  '怎么', '如何', '什么', '哪些', '为什么', '是不是', '有没有', '可以', '应该',
  '请问', '一下', '告诉', '帮我', '看看', '还是', '这个', '那个', '然后', '但是',
]);

/**
 * 从查询文本提取搜索关键词
 * 中文按2字符滑窗（bigram）切分（修复整句中文被当成单个超长关键词导致ILIKE无法命中的缺陷），
 * 英文/数字按空白分词，过滤停用词，最多取8个词
 *
 * @param {string} query - 查询文本
 * @returns {string[]} 关键词数组
 */
const extractKeywords = (query) => {
  if (!query) return [];

  // 只保留中英文数字和空格，其余符号作为分隔符
  const cleaned = query.replace(/[^\u4e00-\u9fa5a-zA-Z0-9\s]/g, ' ');
  const words = new Set();

  for (const token of cleaned.split(/\s+/).filter(Boolean)) {
    if (/[\u4e00-\u9fa5]/.test(token)) {
      // 中文token：短词整体保留，长词按2字符滑窗切分
      if (token.length <= 4 && !STOP_WORDS.has(token)) {
        words.add(token);
      }
      for (let i = 0; i + 2 <= token.length; i++) {
        const gram = token.slice(i, i + 2);
        if (!STOP_WORDS.has(gram)) {
          words.add(gram);
        }
      }
    } else if (token.length > 1) {
      words.add(token.toLowerCase());
    }
  }

  return [...words].slice(0, 8);
};

/**
 * 关键词搜索（基础检索，始终可用）
 * 多关键词 OR 匹配 + CASE 计分（命中词数越多得分越高）
 *
 * @param {string} query - 查询文本
 * @param {number} limit - 返回数量
 * @param {string} [category] - 分类过滤（可选）
 * @returns {Promise<Array<{docId, title, category, content, similarity, source}>>}
 */
const keywordSearch = async (query, limit, category) => {
  try {
    const keywords = extractKeywords(query);
    if (keywords.length === 0) return [];

    // 动态构建参数化SQL：每个关键词一个 ILIKE 条件 + 计分项
    const likeParams = keywords.map(k => `%${k}%`);
    const conditions = keywords.map((_, i) => `c.content ILIKE $${i + 1}`).join(' OR ');
    const scoreExpr = keywords
      .map((_, i) => `(CASE WHEN c.content ILIKE $${i + 1} THEN 1 ELSE 0 END)`)
      .join(' + ');

    // category 过滤时参数整体后移
    const categoryCondition = category ? ` AND d.category = $${keywords.length + 1}` : '';
    const limitParamIndex = keywords.length + (category ? 2 : 1);
    const params = [...likeParams];
    if (category) params.push(category);
    params.push(limit);

    const result = await pool.query(
      `SELECT c.content, c.title, c.doc_id AS "docId", d.category,
              ((${scoreExpr})::float / ${keywords.length}) as similarity
       FROM ai_knowledge_chunks c
       LEFT JOIN ai_knowledge_docs d ON d.id = c.doc_id
       WHERE (${conditions})${categoryCondition}
       ORDER BY similarity DESC, c.chunk_index ASC
       LIMIT $${limitParamIndex}`,
      params
    );

    return result.rows.map(r => ({
      docId: r.docId,
      title: r.title,
      category: r.category || '未分类',
      content: r.content,
      similarity: Number(r.similarity) || 0.5,
      source: 'keyword',
    }));
  } catch (error) {
    logger.warn('关键词搜索失败:', error.message);
    return [];
  }
};

/**
 * 向量语义搜索（pgvector可用且向量模型配置时执行）
 *
 * @param {string} query - 查询文本
 * @param {number} limit - 返回数量
 * @param {string} [category] - 分类过滤（可选）
 * @param {number} [threshold] - 相似度阈值
 * @returns {Promise<Array<{docId, title, category, content, similarity, source}>>}
 */
const vectorSearch = async (query, limit, category, threshold) => {
  // 向量模型未配置（当前提供商不支持）时直接返回空，走关键词检索
  if (!getEmbeddingModel()) {
    return [];
  }

  const embedding = await generateEmbedding(query);
  if (!embedding) {
    return [];
  }

  try {
    const vectorStr = `[${embedding.join(',')}]`;
    const params = [vectorStr];
    let categoryCondition = '';
    if (category) {
      params.push(category);
      categoryCondition = ` AND d.category = $${params.length}`;
    }
    params.push(limit);

    const result = await pool.query(
      `SELECT c.content, c.title, c.doc_id AS "docId", d.category,
              1 - (c.embedding <=> $1::vector) as similarity
       FROM ai_knowledge_chunks c
       LEFT JOIN ai_knowledge_docs d ON d.id = c.doc_id
       WHERE c.embedding IS NOT NULL${categoryCondition}
       ORDER BY c.embedding <=> $1::vector
       LIMIT $${params.length}`,
      params
    );

    const minThreshold = threshold || aiConfig.knowledge.similarityThreshold;
    return result.rows
      .filter(r => Number(r.similarity) >= minThreshold)
      .map(r => ({
        docId: r.docId,
        title: r.title,
        category: r.category || '未分类',
        content: r.content,
        similarity: Number(r.similarity),
        source: 'vector',
      }));
  } catch (error) {
    // pgvector不可用（embedding列为TEXT降级环境）等情况，降级关键词搜索
    logger.warn('向量搜索失败，降级为关键词搜索:', error.message);
    return [];
  }
};

/**
 * 混合检索相关知识
 * 向量语义结果优先，关键词结果补充去重，合计 topK
 *
 * @param {string} query - 查询文本
 * @param {object} [options] - 检索选项
 * @param {number} [options.topK] - 返回结果数
 * @param {string} [options.category] - 分类过滤（可选）
 * @param {number} [options.similarityThreshold] - 向量相似度阈值
 * @returns {Promise<Array<{docId, title, category, content, similarity, source}>>}
 */
const retrieve = async (query, options = {}) => {
  const topK = options.topK || aiConfig.knowledge.topK;

  // 并行执行向量与关键词检索（各自内部失败均返回空数组，互不影响）
  const [vectorResults, keywordResults] = await Promise.all([
    vectorSearch(query, topK, options.category, options.similarityThreshold),
    keywordSearch(query, topK, options.category),
  ]);

  // 融合：向量优先，关键词补充去重
  const seen = new Set();
  const merged = [];
  for (const r of [...vectorResults, ...keywordResults]) {
    const key = `${r.docId}:${r.content}`;
    if (seen.has(key)) continue;
    seen.add(key);
    merged.push(r);
    if (merged.length >= topK) break;
  }

  return merged;
};

/**
 * 添加知识文档分块（分块→生成向量→事务内批量插入）
 *
 * @param {object} params
 * @param {number} params.docId - 文档ID（ai_knowledge_docs.id）
 * @param {string} [params.sourceType] - 来源类型
 * @param {number} [params.sourceId] - 来源用户ID
 * @param {string} [params.title] - 标题（分块冗余列，检索返回用）
 * @param {string} params.content - 文档全文
 * @param {object} [params.metadata] - 元数据
 * @returns {Promise<{chunks: Array, embeddingStatus: string}>}
 *   embeddingStatus: full(全部向量化)/partial(部分)/none(全部降级)
 */
const addKnowledge = async ({ docId, sourceType, sourceId, title, content, metadata }) => {
  const { chunkDocument } = require('./chunker');
  const chunks = chunkDocument(content);

  if (chunks.length === 0) {
    return { chunks: [], embeddingStatus: 'none' };
  }

  // 事务外逐块生成向量（网络调用不持锁），失败的分块向量为null（关键词仍可检索）
  const embeddings = [];
  for (const chunk of chunks) {
    embeddings.push(await generateEmbedding(chunk.content));
  }
  const embeddedCount = embeddings.filter(e => e !== null).length;

  // 事务内批量插入分块，确保文档分块的原子性
  const client = await pool.connect();
  const results = [];
  try {
    await client.query('BEGIN');

    for (let i = 0; i < chunks.length; i++) {
      const chunk = chunks[i];
      const embedding = embeddings[i];

      // 向量非空时尝试::vector转换（pgvector可用时生效）；
      // 为null时直接插入null，避免在TEXT类型列上执行::vector转换报错
      const useVectorCast = embedding !== null;
      const sql = useVectorCast
        ? `INSERT INTO ai_knowledge_chunks (doc_id, source_type, source_id, title, content, chunk_index, embedding, metadata)
           VALUES ($1, $2, $3, $4, $5, $6, $7::vector, $8)
           RETURNING id, title, content, chunk_index`
        : `INSERT INTO ai_knowledge_chunks (doc_id, source_type, source_id, title, content, chunk_index, embedding, metadata)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
           RETURNING id, title, content, chunk_index`;

      const result = await client.query(sql, [
        docId,
        sourceType || 'manual',
        sourceId || null,
        title,
        chunk.content,
        chunk.index,
        useVectorCast ? `[${embedding.join(',')}]` : null,
        JSON.stringify(metadata || {}),
      ]);
      results.push(result.rows[0]);
    }

    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }

  const embeddingStatus = embeddedCount === chunks.length ? 'full'
    : embeddedCount > 0 ? 'partial' : 'none';

  return { chunks: results, embeddingStatus };
};

/**
 * 重新分块文档（编辑文档内容后调用：删旧分块→重新入库）
 *
 * @param {number} docId - 文档ID
 * @param {object} params - 同 addKnowledge 的 title/content/metadata
 * @returns {Promise<{chunks: Array, embeddingStatus: string}>}
 */
const rechunkDocument = async (docId, { title, content, sourceType, sourceId, metadata }) => {
  // 删除旧分块（doc_id 维度）
  await pool.query('DELETE FROM ai_knowledge_chunks WHERE doc_id = $1', [docId]);
  // 重新分块入库
  return addKnowledge({ docId, title, content, sourceType, sourceId, metadata });
};

module.exports = {
  retrieve,
  keywordSearch,
  vectorSearch,
  extractKeywords,
  addKnowledge,
  rechunkDocument,
};
