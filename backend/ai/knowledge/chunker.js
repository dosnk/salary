/**
 * 文档分块器
 *
 * 将长文档按固定大小分块，支持重叠
 * 用于RAG知识库的预处理
 */

const { aiConfig } = require('../config');

/**
 * 将文本分块
 * @param {string} text - 原始文本
 * @param {object} [options] - 分块选项
 * @param {number} [options.chunkSize] - 分块大小（字符）
 * @param {number} [options.chunkOverlap] - 重叠大小
 * @returns {Array<{index: number, content: string, charCount: number}>}
 */
const chunkText = (text, options = {}) => {
  const chunkSize = options.chunkSize || aiConfig.knowledge.chunkSize;
  const overlap = options.chunkOverlap || aiConfig.knowledge.chunkOverlap;

  if (!text || text.length <= chunkSize) {
    return [{ index: 0, content: text || '', charCount: (text || '').length }];
  }

  const chunks = [];
  let start = 0;
  let index = 0;

  while (start < text.length) {
    const end = Math.min(start + chunkSize, text.length);
    const content = text.slice(start, end);

    chunks.push({
      index,
      content,
      charCount: content.length,
    });

    start += chunkSize - overlap;
    index++;

    // 防止无限循环
    if (start <= 0) break;
  }

  return chunks;
};

/**
 * 按段落分块（优先按段落边界切分）
 * @param {string} text - 原始文本
 * @param {number} [maxChunkSize] - 最大分块大小
 * @returns {Array<{index: number, content: string, charCount: number}>}
 */
const chunkByParagraph = (text, maxChunkSize) => {
  const maxSize = maxChunkSize || aiConfig.knowledge.chunkSize;

  if (!text || text.length <= maxSize) {
    return [{ index: 0, content: text || '', charCount: (text || '').length }];
  }

  const paragraphs = text.split(/\n\n+/);
  const chunks = [];
  let currentChunk = '';
  let index = 0;

  for (const para of paragraphs) {
    if (currentChunk.length + para.length + 2 > maxSize && currentChunk.length > 0) {
      chunks.push({ index, content: currentChunk.trim(), charCount: currentChunk.trim().length });
      index++;
      currentChunk = para;
    } else {
      currentChunk += (currentChunk ? '\n\n' : '') + para;
    }
  }

  if (currentChunk.trim()) {
    chunks.push({ index, content: currentChunk.trim(), charCount: currentChunk.trim().length });
  }

  return chunks;
};

module.exports = { chunkText, chunkByParagraph };
