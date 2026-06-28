/**
 * 知识库管理API控制器
 *
 * 提供知识库文档的增删查接口
 * 文档内容自动分块、生成向量嵌入，用于RAG检索
 */

const pool = require('../config/database');
const { addKnowledge } = require('../ai/knowledge/retriever');
const Joi = require('joi');

/** 创建知识文档的校验Schema */
const createKnowledgeSchema = Joi.object({
  title: Joi.string().min(1).max(200).required()
    .description('文档标题'),
  content: Joi.string().min(10).max(50000).required()
    .description('文档内容'),
  sourceType: Joi.string().valid('manual', 'upload', 'api').default('manual')
    .description('来源类型'),
  metadata: Joi.object().default({})
    .description('元数据'),
});

/**
 * 添加知识文档
 * 文档内容自动分块、生成向量嵌入
 */
const createKnowledge = async (ctx) => {
  const { title, content, sourceType, metadata } = ctx.request.body;
  const userId = ctx.state.user.id;

  try {
    const results = await addKnowledge({
      sourceType: sourceType || 'manual',
      sourceId: userId,
      title,
      content,
      metadata: { ...metadata, createdBy: userId },
    });

    ctx.success({
      message: '知识文档添加成功',
      chunks: results.length,
      items: results.map(r => ({
        id: r.id,
        title: r.title,
        chunkIndex: r.chunk_index,
        charCount: r.content.length,
      })),
    });
  } catch (error) {
    ctx.fail(5001, `添加知识文档失败: ${error.message}`);
  }
};

/**
 * 获取知识库文档列表
 * 按来源分组，返回文档摘要
 */
const listKnowledge = async (ctx) => {
  const { page = 1, pageSize = 20, sourceType } = ctx.query;
  const offset = (parseInt(page) - 1) * parseInt(pageSize);

  try {
    let whereClause = '';
    const params = [];

    if (sourceType) {
      whereClause = 'WHERE source_type = $1';
      params.push(sourceType);
    }

    // 按标题分组查询文档列表
    const countResult = await pool.query(
      `SELECT COUNT(DISTINCT COALESCE(title, '无标题')) as total
       FROM ai_knowledge_chunks ${whereClause}`,
      params
    );

    const result = await pool.query(
      `SELECT
         title,
         source_type,
         source_id,
         COUNT(*) as chunk_count,
         SUM(LENGTH(content)) as total_chars,
         MIN(created_at) as created_at,
         MAX(updated_at) as updated_at,
         (SELECT metadata FROM ai_knowledge_chunks mc2
          WHERE mc2.title = mc.title AND mc2.metadata IS NOT NULL
          LIMIT 1) as metadata
       FROM ai_knowledge_chunks mc
       ${whereClause}
       GROUP BY title, source_type, source_id
       ORDER BY MIN(created_at) DESC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      [...params, parseInt(pageSize), offset]
    );

    ctx.success({
      total: parseInt(countResult.rows[0]?.total || 0),
      page: parseInt(page),
      pageSize: parseInt(pageSize),
      items: result.rows,
    });
  } catch (error) {
    ctx.fail(5001, `获取知识库列表失败: ${error.message}`);
  }
};

/**
 * 获取知识文档详情（含所有分块）
 */
const getKnowledgeDetail = async (ctx) => {
  const { title } = ctx.params;

  try {
    const result = await pool.query(
      `SELECT id, title, content, chunk_index, source_type, source_id, metadata, created_at
       FROM ai_knowledge_chunks
       WHERE title = $1
       ORDER BY chunk_index ASC`,
      [decodeURIComponent(title)]
    );

    if (result.rows.length === 0) {
      ctx.fail(4004, '知识文档不存在');
      return;
    }

    ctx.success({
      title: result.rows[0].title,
      sourceType: result.rows[0].source_type,
      sourceId: result.rows[0].source_id,
      metadata: result.rows[0].metadata,
      chunkCount: result.rows.length,
      chunks: result.rows.map(r => ({
        id: r.id,
        chunkIndex: r.chunk_index,
        content: r.content,
        charCount: r.content.length,
      })),
    });
  } catch (error) {
    ctx.fail(5001, `获取知识文档详情失败: ${error.message}`);
  }
};

/**
 * 删除知识文档（按标题删除所有分块）
 */
const deleteKnowledge = async (ctx) => {
  const { title } = ctx.params;

  try {
    const decodedTitle = decodeURIComponent(title);

    // 先查询是否存在
    const checkResult = await pool.query(
      'SELECT COUNT(*) as count FROM ai_knowledge_chunks WHERE title = $1',
      [decodedTitle]
    );

    if (parseInt(checkResult.rows[0].count) === 0) {
      ctx.fail(4004, '知识文档不存在');
      return;
    }

    const result = await pool.query(
      'DELETE FROM ai_knowledge_chunks WHERE title = $1 RETURNING id',
      [decodedTitle]
    );

    ctx.success({
      message: '知识文档删除成功',
      deletedChunks: result.rows.length,
    });
  } catch (error) {
    ctx.fail(5001, `删除知识文档失败: ${error.message}`);
  }
};

module.exports = {
  createKnowledge,
  createKnowledgeSchema,
  listKnowledge,
  getKnowledgeDetail,
  deleteKnowledge,
};
