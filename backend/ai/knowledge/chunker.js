/**
 * 文档分块器
 *
 * 知识库RAG的预处理：将长文档切分为适合检索的分块
 * 策略：段落边界优先（保持语义完整），超长段落滑窗切分（带重叠）
 */

const { aiConfig } = require('../config');

/** 中文常用停用片段（分块时跳过纯符号/空白段落） */
const EMPTY_PARA_PATTERN = /^[\s\u3000]*$/;

/**
 * 将文本按固定大小滑窗切分（带重叠）
 * @param {string} text - 原始文本
 * @param {object} [options] - 分块选项
 * @param {number} [options.chunkSize] - 分块大小（字符）
 * @param {number} [options.chunkOverlap] - 重叠大小
 * @returns {Array<{index: number, content: string, charCount: number}>}
 */
const chunkText = (text, options = {}) => {
  const chunkSize = options.chunkSize || aiConfig.knowledge.chunkSize;
  const overlap = options.chunkOverlap || aiConfig.knowledge.chunkOverlap;
  // 重叠必须小于分块大小，防止死循环
  const safeOverlap = Math.min(Math.max(overlap, 0), Math.floor(chunkSize / 2));

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

    start += chunkSize - safeOverlap;
    index++;

    // 防止无限循环（safeOverlap 已保证 start 递增，此处双重保险）
    if (start <= 0) break;
  }

  return chunks;
};

/**
 * 按段落边界聚合分块（段落优先，保持语义完整）
 * 单个段落超过 chunkSize 时降级为滑窗切分该段落
 * @param {string} text - 原始文本
 * @param {object} [options] - 分块选项
 * @param {number} [options.chunkSize] - 分块大小（字符）
 * @param {number} [options.chunkOverlap] - 超长段落滑窗切分时的重叠大小
 * @returns {Array<{index: number, content: string, charCount: number}>}
 */
const chunkDocument = (text, options = {}) => {
  const chunkSize = options.chunkSize || aiConfig.knowledge.chunkSize;

  if (!text || text.length <= chunkSize) {
    return [{ index: 0, content: text || '', charCount: (text || '').length }];
  }

  // 按空行（连续换行）拆分段落
  const paragraphs = text
    .split(/\n{2,}/)
    .map(p => p.trim())
    .filter(p => !EMPTY_PARA_PATTERN.test(p));

  // 段落拆分后整体不超限（或无段落结构），直接整段聚合
  const totalLen = paragraphs.reduce((sum, p) => sum + p.length, 0);
  if (paragraphs.length === 0 || totalLen <= chunkSize) {
    const merged = paragraphs.join('\n\n') || text;
    if (merged.length <= chunkSize) {
      return [{ index: 0, content: merged, charCount: merged.length }];
    }
    // 无段落结构的长文本降级滑窗
    return chunkText(text, options);
  }

  const chunks = [];
  let currentChunk = '';
  let index = 0;

  /**
   * 将聚合好的 currentChunk 推入结果
   */
  const pushChunk = () => {
    const trimmed = currentChunk.trim();
    if (trimmed) {
      chunks.push({ index, content: trimmed, charCount: trimmed.length });
      index++;
    }
    currentChunk = '';
  };

  for (const para of paragraphs) {
    // 单个段落超限：先推出已聚合内容，再对超长段落滑窗切分
    if (para.length > chunkSize) {
      pushChunk();
      const subChunks = chunkText(para, options);
      for (const sub of subChunks) {
        chunks.push({ index, content: sub.content.trim(), charCount: sub.content.trim().length });
        index++;
      }
      continue;
    }

    // 聚合后超限：推出当前聚合，开启新块
    if (currentChunk.length + para.length + 2 > chunkSize && currentChunk.length > 0) {
      pushChunk();
    }
    currentChunk += (currentChunk ? '\n\n' : '') + para;
  }

  // 收尾：推送最后一段聚合内容
  pushChunk();

  return chunks;
};

/**
 * 按段落分块（旧接口，保留兼容）
 * @param {string} text - 原始文本
 * @param {number} [maxChunkSize] - 最大分块大小
 * @returns {Array<{index: number, content: string, charCount: number}>}
 */
const chunkByParagraph = (text, maxChunkSize) => {
  return chunkDocument(text, { chunkSize: maxChunkSize });
};

module.exports = { chunkText, chunkByParagraph, chunkDocument };
