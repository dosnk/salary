/**
 * FunctionCall工具 - 知识库检索（只读工具）
 * 供 AI 主动检索知识库文档（施工规范、材料知识、常见问题等专业资料）
 *
 * 【硬性规定·只读】本工具只执行 SELECT 检索，禁止任何写操作。
 *
 * 返回字段说明：
 * - total: 检索结果数
 * - results: 分块内容列表（title/category/content/similarity）
 */

const { retrieve } = require('../knowledge/retriever');

const execute = async (args) => {
  const { query, category } = args;

  if (!query || typeof query !== 'string') {
    return { error: '请提供搜索关键词' };
  }

  // 混合检索（向量语义 + 中文关键词），topK 固定5
  const results = await retrieve(query, { topK: 5, category: category || undefined });

  return {
    total: results.length,
    results: results.map(r => ({
      docId: r.docId,
      title: r.title || '未命名文档',
      category: r.category || '未分类',
      content: r.content,
      similarity: Math.round(r.similarity * 100) / 100,
    })),
  };
};

module.exports = { execute };
