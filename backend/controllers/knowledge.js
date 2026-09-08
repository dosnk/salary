/**
 * 知识库管理API控制器
 *
 * 知识库文档的完整生命周期管理：
 * - 手动录入（文本）/ 文件导入（txt/md/pdf/docx）/ 媒体导入（图片/视频/音频）
 * - 文档分块、向量嵌入由 ai/knowledge/retriever.js 完成
 * - 图片自动识别由 ai/knowledge/vision.js 完成（不可用时降级）
 * - 列表/详情/编辑/删除（ID 化，编辑内容自动重新分块）
 */

const fs = require('fs');
const path = require('path');
const moment = require('moment');
const { v4: uuidv4 } = require('uuid');
const pool = require('../config/database');
const Joi = require('joi');
const logger = require('../config/logger');
const { addKnowledge, rechunkDocument } = require('../ai/knowledge/retriever');
const { extractText, detectDocType, isOverSize } = require('../ai/knowledge/extractor');
const { describeImage } = require('../ai/knowledge/vision');

/** 文档类型对应的媒体大小限制（字节） */
const MEDIA_SIZE_LIMITS = {
  image: 10 * 1024 * 1024,   // 图片: 10MB（受视觉模型 base64 限制）
  audio: 50 * 1024 * 1024,   // 音频: 50MB
  video: 200 * 1024 * 1024,  // 视频: 200MB
};

/** 支持的媒体扩展名 */
const MEDIA_EXTENSIONS = {
  image: ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'],
  audio: ['.mp3', '.wav', '.m4a', '.aac'],
  video: ['.mp4', '.mov', '.avi', '.mkv'],
};

/** 手动录入校验Schema */
const createKnowledgeSchema = Joi.object({
  title: Joi.string().min(1).max(200).required()
    .description('文档标题'),
  content: Joi.string().min(10).max(50000).required()
    .description('文档内容'),
  category: Joi.string().max(50).default('未分类')
    .description('知识分类'),
});

/** 编辑文档校验Schema（至少传入一个字段） */
const updateKnowledgeSchema = Joi.object({
  title: Joi.string().min(1).max(200),
  content: Joi.string().min(10).max(50000),
  category: Joi.string().max(50),
  description: Joi.string().max(2000).allow('', null),
}).min(1);

/**
 * 从 multipart 请求中提取上传文件对象
 * @param {object} ctx - Koa上下文
 * @returns {object|null} formidable 文件对象（含 filepath/originalFilename/size/mimetype）
 */
const getUploadFile = (ctx) => {
  const files = ctx.request.files && ctx.request.files.file;
  if (!files) return null;
  return Array.isArray(files) ? files[0] : files;
};

/**
 * 将临时文件保存到知识库媒体目录
 * @param {string} tempFilePath - formidable 临时文件路径
 * @param {string} originalFileName - 原始文件名（用于保留扩展名）
 * @returns {{fileUrl: string, absPath: string}} 保存后的URL与绝对路径
 */
const saveKnowledgeFile = (tempFilePath, originalFileName) => {
  const ext = path.extname(originalFileName || '').toLowerCase();
  const datePath = moment().format('YYYYMM');
  const dir = path.join(__dirname, '..', 'upload', 'knowledge', datePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  const uniqueName = `${uuidv4()}${ext}`;
  const absPath = path.join(dir, uniqueName);
  fs.copyFileSync(tempFilePath, absPath);
  return { fileUrl: `/upload/knowledge/${datePath}/${uniqueName}`, absPath };
};

/**
 * 删除知识库媒体文件（失败仅记录日志，不影响主流程）
 * @param {string} fileUrl - 媒体文件URL（/upload/knowledge/...）
 */
const removeKnowledgeFile = (fileUrl) => {
  if (!fileUrl || !fileUrl.startsWith('/upload/')) return;
  try {
    const uploadDir = path.resolve(__dirname, '..', 'upload');
    const relPath = fileUrl.substring('/upload/'.length);
    const absPath = path.resolve(path.join(uploadDir, relPath));
    // 路径穿越防护：必须仍在 upload 目录内
    if (absPath.startsWith(uploadDir + path.sep) && fs.existsSync(absPath)) {
      fs.unlinkSync(absPath);
    }
  } catch (error) {
    logger.warn(`删除知识库媒体文件失败: ${error.message}`);
  }
};

/**
 * 清理 formidable 临时文件（失败仅记录日志）
 * @param {object} file - formidable 文件对象
 */
const cleanTempFile = (file) => {
  try {
    if (file && file.filepath && fs.existsSync(file.filepath)) {
      fs.unlinkSync(file.filepath);
    }
  } catch (error) {
    logger.warn(`清理临时文件失败: ${error.message}`);
  }
};

/**
 * 更新文档的分块统计信息
 * @param {number} docId - 文档ID
 * @param {{chunks: Array, embeddingStatus: string}} chunkResult - 分块结果
 * @param {string} content - 文档全文
 */
const updateDocChunkStats = async (docId, chunkResult, content) => {
  await pool.query(
    `UPDATE ai_knowledge_docs
     SET chunk_count = $2, char_count = $3, embedding_status = $4, updated_at = CURRENT_TIMESTAMP
     WHERE id = $1`,
    [docId, chunkResult.chunks.length, (content || '').length, chunkResult.embeddingStatus]
  );
};

/**
 * 获取知识库文档列表（分页 + 分类筛选 + 关键词搜索）
 */
const listKnowledge = async (ctx) => {
  const { page = 1, pageSize = 20, category, keyword } = ctx.query;
  const pageNum = Math.max(1, parseInt(page) || 1);
  const sizeNum = Math.min(50, Math.max(1, parseInt(pageSize) || 20));
  const offset = (pageNum - 1) * sizeNum;

  try {
    const conditions = [];
    const params = [];

    if (category) {
      params.push(category);
      conditions.push(`category = $${params.length}`);
    }
    if (keyword) {
      params.push(`%${keyword}%`);
      conditions.push(`(title ILIKE $${params.length} OR content ILIKE $${params.length})`);
    }
    const whereClause = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

    const countResult = await pool.query(
      `SELECT COUNT(*) as total FROM ai_knowledge_docs ${whereClause}`,
      params
    );

    const result = await pool.query(
      `SELECT id, title, category, source_type, doc_type, file_name, file_size,
              file_path, char_count, chunk_count, embedding_status, vision_status,
              to_char(created_at, 'YYYY-MM-DD HH24:MI') as created_at,
              to_char(updated_at, 'YYYY-MM-DD HH24:MI') as updated_at
       FROM ai_knowledge_docs
       ${whereClause}
       ORDER BY created_at DESC, id DESC
       LIMIT $${params.length + 1} OFFSET $${params.length + 2}`,
      [...params, sizeNum, offset]
    );

    ctx.success({
      total: parseInt(countResult.rows[0]?.total || 0),
      page: pageNum,
      pageSize: sizeNum,
      items: result.rows.map(r => ({
        id: r.id,
        title: r.title,
        category: r.category,
        sourceType: r.source_type,
        docType: r.doc_type,
        fileName: r.file_name,
        fileSize: Number(r.file_size || 0),
        mediaUrl: r.file_path || null,
        charCount: r.char_count,
        chunkCount: r.chunk_count,
        embeddingStatus: r.embedding_status,
        visionStatus: r.vision_status,
        createdAt: r.created_at,
        updatedAt: r.updated_at,
      })),
    });
  } catch (error) {
    ctx.fail(5001, `获取知识库列表失败: ${error.message}`);
  }
};

/**
 * 获取知识分类列表（含各分类文档数）
 */
const getCategories = async (ctx) => {
  try {
    const result = await pool.query(
      `SELECT category as name, COUNT(*) as count
       FROM ai_knowledge_docs
       GROUP BY category
       ORDER BY count DESC, name ASC`
    );
    ctx.success({
      categories: result.rows.map(r => ({ name: r.name, count: parseInt(r.count) })),
    });
  } catch (error) {
    ctx.fail(5001, `获取知识分类失败: ${error.message}`);
  }
};

/**
 * 手动录入知识文档（纯文本）
 * 内容自动分块、生成向量嵌入
 */
const createKnowledge = async (ctx) => {
  const { title, content, category } = ctx.request.body;
  const userId = ctx.state.user.id;

  try {
    // 创建文档主记录
    const docResult = await pool.query(
      `INSERT INTO ai_knowledge_docs (title, category, source_type, doc_type, content, char_count, created_by)
       VALUES ($1, $2, 'manual', 'text', $3, $4, $5)
       RETURNING id`,
      [title, category || '未分类', content, content.length, userId]
    );
    const docId = docResult.rows[0].id;

    // 分块入库（含向量生成）
    const chunkResult = await addKnowledge({
      docId,
      sourceType: 'manual',
      sourceId: userId,
      title,
      content,
    });
    await updateDocChunkStats(docId, chunkResult, content);

    ctx.success({
      message: '知识文档添加成功',
      id: docId,
      chunkCount: chunkResult.chunks.length,
      embeddingStatus: chunkResult.embeddingStatus,
    });
  } catch (error) {
    ctx.fail(5001, `添加知识文档失败: ${error.message}`);
  }
};

/**
 * 文件导入知识文档（txt/md/pdf/docx）
 * 自动提取文本、分块、向量化，并保存原文件供下载
 */
const importFile = async (ctx) => {
  const file = getUploadFile(ctx);
  const userId = ctx.state.user.id;

  if (!file) {
    ctx.fail(1001, '请选择要导入的文件');
    return;
  }

  try {
    const originalName = file.originalFilename || 'unknown';
    const docType = detectDocType(originalName);

    if (!docType) {
      cleanTempFile(file);
      ctx.fail(1001, '仅支持导入 txt / md / pdf / docx 文档（.doc 老格式请先另存为 .docx）');
      return;
    }

    if (isOverSize(file.size || 0, docType === 'markdown' ? 'text' : docType)) {
      cleanTempFile(file);
      ctx.fail(1001, '文件超过大小限制（txt/md ≤ 2MB，pdf/docx ≤ 20MB）');
      return;
    }

    // 提取文本
    const { content, truncated } = await extractText(file.filepath, docType);
    if (!content || content.length < 10) {
      cleanTempFile(file);
      ctx.fail(1001, '未能从文件中提取到有效文本内容');
      return;
    }

    // 标题：优先用请求体传入，缺省用文件名去扩展名
    const bodyTitle = (ctx.request.body && ctx.request.body.title || '').trim();
    const title = bodyTitle || originalName.replace(/\.[^.]+$/, '').substring(0, 200);
    const category = (ctx.request.body && ctx.request.body.category) || '未分类';

    // 保存原文件（供详情页下载查看）
    const { fileUrl } = saveKnowledgeFile(file.filepath, originalName);
    cleanTempFile(file);

    // 创建文档主记录
    const docResult = await pool.query(
      `INSERT INTO ai_knowledge_docs
         (title, category, source_type, doc_type, file_path, file_name, file_size, mime_type,
          content, char_count, created_by, metadata)
       VALUES ($1, $2, 'file', $3, $4, $5, $6, $7, $8, $9, $10, $11)
       RETURNING id`,
      [
        title, category, docType, fileUrl, originalName, file.size || 0,
        file.mimetype || '', content, content.length, userId,
        JSON.stringify({ truncated }),
      ]
    );
    const docId = docResult.rows[0].id;

    // 分块入库
    const chunkResult = await addKnowledge({
      docId,
      sourceType: 'file',
      sourceId: userId,
      title,
      content,
    });
    await updateDocChunkStats(docId, chunkResult, content);

    ctx.success({
      message: truncated ? '文档导入成功（文本超长已截断至50000字符）' : '文档导入成功',
      id: docId,
      title,
      chunkCount: chunkResult.chunks.length,
      charCount: content.length,
      truncated,
      embeddingStatus: chunkResult.embeddingStatus,
    });
  } catch (error) {
    cleanTempFile(file);
    logger.error(`文件导入知识库失败: ${error.message}`);
    ctx.fail(5001, `文件导入失败: ${error.message}`);
  }
};

/**
 * 媒体导入（图片/视频/音频）
 * 图片自动调用视觉模型生成描述参与检索；视频/音频靠标题+手动描述检索
 */
const importMedia = async (ctx) => {
  const file = getUploadFile(ctx);
  const userId = ctx.state.user.id;

  if (!file) {
    ctx.fail(1001, '请选择要导入的媒体文件');
    return;
  }

  try {
    const originalName = file.originalFilename || 'unknown';
    const ext = path.extname(originalName).toLowerCase();

    // 判定媒体类型
    let mediaType = null;
    for (const [type, exts] of Object.entries(MEDIA_EXTENSIONS)) {
      if (exts.includes(ext)) {
        mediaType = type;
        break;
      }
    }
    if (!mediaType) {
      cleanTempFile(file);
      ctx.fail(1001, '仅支持导入图片(jpg/png/gif/webp/bmp)、音频(mp3/wav/m4a/aac)、视频(mp4/mov/avi/mkv)');
      return;
    }

    // 大小限制
    const sizeLimit = MEDIA_SIZE_LIMITS[mediaType];
    if ((file.size || 0) > sizeLimit) {
      cleanTempFile(file);
      const limitMB = Math.round(sizeLimit / 1024 / 1024);
      ctx.fail(1001, `文件超过${limitMB}MB大小限制`);
      return;
    }

    // 请求体参数
    const body = ctx.request.body || {};
    const title = (body.title || '').trim();
    if (!title) {
      cleanTempFile(file);
      ctx.fail(1001, '请填写媒体文件的标题');
      return;
    }
    const category = body.category || '未分类';
    const description = (body.description || '').trim();

    // 保存媒体文件
    const { fileUrl } = saveKnowledgeFile(file.filepath, originalName);

    // 图片：调用视觉模型生成描述（不可用时降级）
    let visionStatus = null;
    let visionMessage = null;
    let visionDescription = '';
    if (mediaType === 'image') {
      const imageBase64 = fs.readFileSync(file.filepath).toString('base64');
      const visionResult = await describeImage(imageBase64, file.mimetype);
      visionStatus = visionResult.status;
      visionMessage = visionResult.message;
      visionDescription = visionResult.description;
      logger.info(`图片识别结果: status=${visionStatus}`);
    }
    cleanTempFile(file);

    // 组合检索文本：视觉描述 + 手动描述 + 标题
    const contentParts = [];
    if (visionDescription) contentParts.push(visionDescription);
    if (description) contentParts.push(`补充说明：${description}`);
    contentParts.push(`标题：${title}`);
    const content = contentParts.join('\n');

    // 创建文档主记录
    const docResult = await pool.query(
      `INSERT INTO ai_knowledge_docs
         (title, category, source_type, doc_type, file_path, file_name, file_size, mime_type,
          content, description, char_count, vision_status, created_by)
       VALUES ($1, $2, 'media', $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       RETURNING id`,
      [
        title, category, mediaType, fileUrl, originalName, file.size || 0,
        file.mimetype || '', content, description, content.length, visionStatus, userId,
      ]
    );
    const docId = docResult.rows[0].id;

    // 分块入库（媒体文本较短，通常单块）
    const chunkResult = await addKnowledge({
      docId,
      sourceType: 'media',
      sourceId: userId,
      title,
      content,
    });
    await updateDocChunkStats(docId, chunkResult, content);

    ctx.success({
      message: visionMessage ? `媒体导入成功。${visionMessage}` : '媒体导入成功',
      id: docId,
      visionStatus,
      chunkCount: chunkResult.chunks.length,
      embeddingStatus: chunkResult.embeddingStatus,
    });
  } catch (error) {
    cleanTempFile(file);
    logger.error(`媒体导入知识库失败: ${error.message}`);
    ctx.fail(5001, `媒体导入失败: ${error.message}`);
  }
};

/**
 * 获取知识文档详情（元信息 + 全文内容 + 媒体URL）
 */
const getKnowledgeDetail = async (ctx) => {
  const docId = parseInt(ctx.params.id);

  if (!docId || docId <= 0) {
    ctx.fail(1001, '文档ID无效');
    return;
  }

  try {
    const result = await pool.query(
      `SELECT id, title, category, source_type, doc_type, file_path, file_name, file_size,
              mime_type, content, description, char_count, chunk_count, embedding_status,
              vision_status,
              to_char(created_at, 'YYYY-MM-DD HH24:MI') as created_at,
              to_char(updated_at, 'YYYY-MM-DD HH24:MI') as updated_at
       FROM ai_knowledge_docs
       WHERE id = $1`,
      [docId]
    );

    if (result.rows.length === 0) {
      ctx.fail(4004, '知识文档不存在');
      return;
    }

    const r = result.rows[0];
    ctx.success({
      id: r.id,
      title: r.title,
      category: r.category,
      sourceType: r.source_type,
      docType: r.doc_type,
      mediaUrl: r.file_path || null,
      fileName: r.file_name,
      fileSize: Number(r.file_size || 0),
      mimeType: r.mime_type,
      content: r.content,
      description: r.description,
      charCount: r.char_count,
      chunkCount: r.chunk_count,
      embeddingStatus: r.embedding_status,
      visionStatus: r.vision_status,
      createdAt: r.created_at,
      updatedAt: r.updated_at,
    });
  } catch (error) {
    ctx.fail(5001, `获取知识文档详情失败: ${error.message}`);
  }
};

/**
 * 编辑知识文档（标题/分类/内容/描述）
 * 内容变更时自动删除旧分块并重新分块入库
 */
const updateKnowledge = async (ctx) => {
  const docId = parseInt(ctx.params.id);
  const { title, content, category, description } = ctx.request.body;

  if (!docId || docId <= 0) {
    ctx.fail(1001, '文档ID无效');
    return;
  }

  try {
    // 查询文档是否存在
    const existing = await pool.query(
      'SELECT id, title, content FROM ai_knowledge_docs WHERE id = $1',
      [docId]
    );
    if (existing.rows.length === 0) {
      ctx.fail(4004, '知识文档不存在');
      return;
    }

    const doc = existing.rows[0];

    // 基础字段更新
    const setClauses = [];
    const params = [docId];
    if (title !== undefined) {
      params.push(title);
      setClauses.push(`title = $${params.length}`);
    }
    if (category !== undefined) {
      params.push(category);
      setClauses.push(`category = $${params.length}`);
    }
    if (description !== undefined) {
      params.push(description);
      setClauses.push(`description = $${params.length}`);
    }

    // 内容变更：删旧分块 → 重新分块入库 → 更新统计
    let chunkResult = null;
    if (content !== undefined && content !== doc.content) {
      chunkResult = await rechunkDocument(docId, {
        title: title !== undefined ? title : doc.title,
        content,
        sourceType: 'manual',
        sourceId: ctx.state.user.id,
      });
      params.push(content);
      setClauses.push(`content = $${params.length}`);
    }

    if (setClauses.length > 0) {
      setClauses.push('updated_at = CURRENT_TIMESTAMP');
      await pool.query(
        `UPDATE ai_knowledge_docs SET ${setClauses.join(', ')} WHERE id = $1`,
        params
      );
    }

    // 内容变更后同步分块统计
    if (chunkResult) {
      await updateDocChunkStats(docId, chunkResult, content);
    }

    ctx.success({
      message: '知识文档更新成功',
      id: docId,
      chunkCount: chunkResult ? chunkResult.chunks.length : undefined,
      embeddingStatus: chunkResult ? chunkResult.embeddingStatus : undefined,
    });
  } catch (error) {
    ctx.fail(5001, `更新知识文档失败: ${error.message}`);
  }
};

/**
 * 删除知识文档（级联删除分块 + 删除媒体文件）
 */
const deleteKnowledge = async (ctx) => {
  const docId = parseInt(ctx.params.id);

  if (!docId || docId <= 0) {
    ctx.fail(1001, '文档ID无效');
    return;
  }

  try {
    const existing = await pool.query(
      'SELECT id, file_path FROM ai_knowledge_docs WHERE id = $1',
      [docId]
    );
    if (existing.rows.length === 0) {
      ctx.fail(4004, '知识文档不存在');
      return;
    }

    // 删除文档主记录（分块通过 ON DELETE CASCADE 级联删除）
    await pool.query('DELETE FROM ai_knowledge_docs WHERE id = $1', [docId]);

    // 删除媒体/原文件（失败不影响接口结果）
    removeKnowledgeFile(existing.rows[0].file_path);

    ctx.success({
      message: '知识文档删除成功',
      id: docId,
    });
  } catch (error) {
    ctx.fail(5001, `删除知识文档失败: ${error.message}`);
  }
};

/**
 * 图片重新识别（视觉模型配置好后补识别）
 */
const redescribeImage = async (ctx) => {
  const docId = parseInt(ctx.params.id);

  if (!docId || docId <= 0) {
    ctx.fail(1001, '文档ID无效');
    return;
  }

  try {
    const existing = await pool.query(
      `SELECT id, title, description, file_path, doc_type FROM ai_knowledge_docs WHERE id = $1`,
      [docId]
    );
    if (existing.rows.length === 0) {
      ctx.fail(4004, '知识文档不存在');
      return;
    }

    const doc = existing.rows[0];
    if (doc.doc_type !== 'image') {
      ctx.fail(1001, '仅图片类型文档支持重新识别');
      return;
    }
    if (!doc.file_path) {
      ctx.fail(5001, '媒体文件缺失，无法识别');
      return;
    }

    // 读取媒体文件并识别
    const uploadDir = path.resolve(__dirname, '..', 'upload');
    const relPath = doc.file_path.substring('/upload/'.length);
    const absPath = path.resolve(path.join(uploadDir, relPath));
    if (!absPath.startsWith(uploadDir + path.sep) || !fs.existsSync(absPath)) {
      ctx.fail(5001, '媒体文件不存在');
      return;
    }

    const imageBase64 = fs.readFileSync(absPath).toString('base64');
    const visionResult = await describeImage(imageBase64, path.extname(absPath));

    // 识别成功：更新描述并重新分块；失败：仅更新状态
    if (visionResult.status === 'ok') {
      const contentParts = [];
      contentParts.push(visionResult.description);
      if (doc.description) contentParts.push(`补充说明：${doc.description}`);
      contentParts.push(`标题：${doc.title}`);
      const content = contentParts.join('\n');

      await pool.query(
        `UPDATE ai_knowledge_docs
         SET content = $2, vision_status = 'ok', updated_at = CURRENT_TIMESTAMP
         WHERE id = $1`,
        [docId, content]
      );
      const chunkResult = await rechunkDocument(docId, {
        title: doc.title,
        content,
        sourceType: 'media',
      });
      await updateDocChunkStats(docId, chunkResult, content);

      ctx.success({
        message: '图片识别成功',
        id: docId,
        visionStatus: 'ok',
        description: visionResult.description,
        chunkCount: chunkResult.chunks.length,
      });
    } else {
      await pool.query(
        `UPDATE ai_knowledge_docs SET vision_status = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $1`,
        [docId, visionResult.status]
      );
      ctx.success({
        message: visionResult.message,
        id: docId,
        visionStatus: visionResult.status,
      });
    }
  } catch (error) {
    ctx.fail(5001, `图片重新识别失败: ${error.message}`);
  }
};

module.exports = {
  listKnowledge,
  getCategories,
  createKnowledge,
  createKnowledgeSchema,
  importFile,
  importMedia,
  getKnowledgeDetail,
  updateKnowledge,
  updateKnowledgeSchema,
  deleteKnowledge,
  redescribeImage,
};
