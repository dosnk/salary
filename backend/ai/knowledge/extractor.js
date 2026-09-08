/**
 * 文档文本提取器
 *
 * 从上传的文档文件中提取纯文本，用于知识库检索
 * 支持: txt/md（UTF-8与GBK自适应）、pdf（pdf-parse）、docx（mammoth）
 */

const fs = require('fs');
const path = require('path');
const iconv = require('iconv-lite');
const logger = require('../../config/logger');

/** 提取文本的最大字符数（超出截断） */
const MAX_EXTRACT_CHARS = 50000;

/** 各文档类型的大小限制（字节） */
const FILE_SIZE_LIMITS = {
  text: 2 * 1024 * 1024,    // txt/md: 2MB
  pdf: 20 * 1024 * 1024,    // pdf: 20MB
  docx: 20 * 1024 * 1024,   // docx: 20MB
};

/**
 * 根据扩展名判断文档类型
 * @param {string} fileName - 文件名
 * @returns {string|null} doc_type（text/md/pdf/docx），不支持的类型返回 null
 */
const detectDocType = (fileName) => {
  const ext = path.extname(fileName || '').toLowerCase();
  switch (ext) {
    case '.txt': return 'text';
    case '.md': return 'markdown';
    case '.pdf': return 'pdf';
    case '.docx': return 'docx';
    default: return null;
  }
};

/**
 * 检测文件大小是否超限
 * @param {number} size - 文件大小（字节）
 * @param {string} docType - 文档类型
 * @returns {boolean} true=超限
 */
const isOverSize = (size, docType) => {
  const limit = FILE_SIZE_LIMITS[docType];
  return limit ? size > limit : true;
};

/**
 * 从文件提取纯文本
 *
 * @param {string} filePath - 文件绝对路径（formidable 临时文件）
 * @param {string} docType - 文档类型（text/markdown/pdf/docx）
 * @returns {Promise<{content: string, truncated: boolean}>}
 * @throws {Error} 提取失败时抛出（含中文错误信息）
 */
const extractText = async (filePath, docType) => {
  let content = '';

  if (docType === 'text' || docType === 'markdown') {
    // 纯文本：UTF-8 直读；检测到乱码替换符时按 GBK 解码（兼容中文 txt）
    const buffer = fs.readFileSync(filePath);
    content = buffer.toString('utf8');
    if (content.includes('\uFFFD')) {
      logger.info('检测到非UTF-8编码文本，按GBK解码');
      content = iconv.decode(buffer, 'gbk');
    }
  } else if (docType === 'pdf') {
    // PDF：pdf-parse 提取文本
    const pdfParse = require('pdf-parse');
    const buffer = fs.readFileSync(filePath);
    const pdfData = await pdfParse(buffer);
    content = pdfData.text || '';
  } else if (docType === 'docx') {
    // Word：mammoth 提取纯文本
    const mammoth = require('mammoth');
    const result = await mammoth.extractRawText({ path: filePath });
    content = result.value || '';
  } else {
    throw new Error(`不支持的文档类型: ${docType}`);
  }

  // 规范化空白并截断超长文本
  const normalized = content.replace(/\r\n/g, '\n').trim();
  const truncated = normalized.length > MAX_EXTRACT_CHARS;
  if (truncated) {
    logger.warn(`提取文本超长（${normalized.length}字符），截断至${MAX_EXTRACT_CHARS}字符`);
  }

  return {
    content: truncated ? normalized.slice(0, MAX_EXTRACT_CHARS) : normalized,
    truncated,
  };
};

module.exports = { extractText, detectDocType, isOverSize, MAX_EXTRACT_CHARS };
