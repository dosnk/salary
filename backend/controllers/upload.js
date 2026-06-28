const fs = require('fs');
const path = require('path');
const moment = require('moment');
const { v4: uuidv4 } = require('uuid');
const crypto = require('crypto');
const logger = require('../config/logger');
const { getRedisClient, isRedisAvailable } = require('../config/redis');
const pool = require('../config/database');

const ensureUploadDir = (dir) => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
};

const sanitizeFileName = (fileName) => {
  if (!fileName) return 'unknown';
  return fileName
    .replace(/[<>:"/\\|?*]/g, '_')
    .replace(/\.\./g, '')
    .replace(/[\x00-\x1f\x80-\x9f]/g, '')
    .substring(0, 200);
};

const FILE_SIGNATURES = {
  'image/jpeg': [[0xFF, 0xD8, 0xFF]],
  'image/png': [[0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]],
  'image/gif': [[0x47, 0x49, 0x46, 0x38]],
  'image/webp': [[0x52, 0x49, 0x46, 0x46]],
  'image/bmp': [[0x42, 0x4D]],
  'application/pdf': [[0x25, 0x50, 0x44, 0x46]],
  'application/zip': [[0x50, 0x4B, 0x03, 0x04], [0x50, 0x4B, 0x05, 0x06]],
  'video/mp4': [[0x00, 0x00, 0x00], [0x66, 0x74, 0x79, 0x70]],
  'audio/mp3': [[0x49, 0x44, 0x33], [0xFF, 0xFB], [0xFF, 0xFA]],
};

const verifyFileSignature = (filePath, expectedMimeType) => {
  const signatures = FILE_SIGNATURES[expectedMimeType];
  if (!signatures) return true;
  
  try {
    const buffer = Buffer.alloc(8);
    const fd = fs.openSync(filePath, 'r');
    fs.readSync(fd, buffer, 0, 8, 0);
    fs.closeSync(fd);
    
    for (const signature of signatures) {
      let match = true;
      for (let i = 0; i < signature.length; i++) {
        if (buffer[i] !== signature[i]) {
          match = false;
          break;
        }
      }
      if (match) return true;
    }
    return false;
  } catch (error) {
    logger.warn('文件签名验证失败', { filePath, error: error.message });
    return false;
  }
};

const calculateFileHash = (filePath) => {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (data) => hash.update(data));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
};

const cleanTempFile = (filePath) => {
  try {
    if (filePath && fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
      logger.info('清理临时文件', { filePath });
    }
  } catch (error) {
    logger.warn('清理临时文件失败', { filePath, error: error.message });
  }
};

const uploadController = {
  allowedFileTypes: {
    image: ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'],
    document: ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
    video: ['video/mp4', 'video/avi', 'video/mov', 'video/wmv', 'video/flv'],
    audio: ['audio/mp3', 'audio/wav', 'audio/aac']
  },

  maxFileSize: {
    image: 10 * 1024 * 1024,
    document: 20 * 1024 * 1024,
    video: 200 * 1024 * 1024,
    audio: 50 * 1024 * 1024
  },

  allowedExtensions: ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.pdf', '.doc', '.docx', '.mp4', '.avi', '.mov', '.wmv', '.flv', '.mp3', '.wav', '.aac'],

  dangerousExtensions: ['.exe', '.bat', '.cmd', '.sh', '.ps1', '.vbs', '.js', '.jar', '.msi', '.dll', '.scr', '.pif', '.com'],

  uploadFile: async (ctx) => {
    const tempFiles = [];
    
    try {
      const userId = ctx.state.user?.id;
      
      if (!userId) {
        ctx.fail(4001, '请先登录');
        return;
      }

      logger.info('文件上传请求开始', {
        hasFiles: !!ctx.request.files,
        filesKeys: ctx.request.files ? Object.keys(ctx.request.files) : [],
        body: ctx.request.body,
        userId
      });

      if (isRedisAvailable()) {
        const redis = getRedisClient();
        const rateLimitKey = `upload:rate:${userId}`;
        const uploadCount = await redis.incr(rateLimitKey);

        if (uploadCount === 1) {
          await redis.expire(rateLimitKey, 60);
        }

        const maxUploadsPerMinute = 20;
        if (uploadCount > maxUploadsPerMinute) {
          logger.warn('文件上传频率过高', { userId, uploadCount });
          ctx.fail(1004, '上传频率过高，请稍后再试');
          return;
        }

        const sizeLimitKey = `upload:size:${userId}`;
        const currentSize = parseInt(await redis.get(sizeLimitKey) || '0');
        const maxSizePerHour = 500 * 1024 * 1024;
        
        if (currentSize > maxSizePerHour) {
          logger.warn('上传总量超限', { userId, currentSize });
          ctx.fail(1004, '上传总量超限，请稍后再试');
          return;
        }
      } else {
        logger.warn('Redis不可用，跳过上传频率限制检查', { userId });
      }

      if (!ctx.request.files || Object.keys(ctx.request.files).length === 0) {
        logger.warn('缺少上传文件', { userId });
        ctx.fail(6004, '请选择要上传的文件');
        return;
      }

      let files = ctx.request.files.file;
      if (!files) {
        logger.warn('缺少file参数', { userId });
        ctx.fail(6004, '文件参数错误');
        return;
      }

      if (!Array.isArray(files)) {
        files = [files];
      }

      if (files.length > 10) {
        ctx.fail(1004, '单次最多上传10个文件');
        return;
      }

      logger.info('上传文件数量', { count: files.length, userId });

      const projectName = sanitizeFileName(ctx.request.body.projectName || 'salary');
      logger.info('工程名称', { projectName, userId });

      const datePath = moment().format('YYYYMM');
      const uploadDir = path.join(__dirname, '../upload', datePath, projectName);
      ensureUploadDir(uploadDir);
      logger.info('上传目录', { uploadDir, userId });

      const uploadedFiles = [];
      const errors = [];

      for (const file of files) {
        const tempFilePath = file.filepath || file.path;
        if (tempFilePath) tempFiles.push(tempFilePath);
        
        try {
          const originalFileName = file.originalFilename || file.name || 'unknown';
          const fileName = sanitizeFileName(originalFileName);
          const ext = path.extname(fileName).toLowerCase();

          logger.info('文件信息', {
            fileName,
            ext,
            type: file.mimetype || file.type,
            size: file.size,
            userId
          });

          if (uploadController.dangerousExtensions.includes(ext)) {
            logger.warn('危险文件类型', { ext, fileName, userId });
            errors.push({ fileName, error: '不允许上传此类型文件' });
            continue;
          }

          if (!uploadController.allowedExtensions.includes(ext)) {
            logger.warn('不支持的文件扩展名', { ext, fileName, userId });
            errors.push({ fileName, error: `不支持的文件类型: ${ext}` });
            continue;
          }

          const fileType = file.mimetype || file.type;
          let fileCategory = null;

          for (const [category, types] of Object.entries(uploadController.allowedFileTypes)) {
            if (types.includes(fileType)) {
              fileCategory = category;
              break;
            }
          }

          if (!fileCategory) {
            logger.warn('不支持的文件MIME类型', { fileType, fileName, userId });
            errors.push({ fileName, error: `不支持的文件格式: ${fileType}` });
            continue;
          }

          const maxSize = uploadController.maxFileSize[fileCategory];
          if (file.size > maxSize) {
            const maxSizeMB = Math.round(maxSize / 1024 / 1024);
            logger.warn('文件大小超过限制', { fileSize: file.size, maxSize, fileName, userId });
            errors.push({ fileName, error: `文件超过${maxSizeMB}MB限制` });
            continue;
          }

          if (file.size === 0) {
            logger.warn('空文件', { fileName, userId });
            errors.push({ fileName, error: '文件内容为空' });
            continue;
          }

          if (!verifyFileSignature(tempFilePath, fileType)) {
            logger.warn('文件签名验证失败', { fileName, fileType, userId });
            errors.push({ fileName, error: '文件内容与类型不匹配' });
            continue;
          }

          const uniqueFileName = `${uuidv4()}${ext}`;
          const filePath = path.join(uploadDir, uniqueFileName);

          const fileHash = await calculateFileHash(tempFilePath);
          logger.info('文件哈希', { fileName, hash: fileHash.substring(0, 16), userId });

          await new Promise((resolve, reject) => {
            const reader = fs.createReadStream(tempFilePath);
            const writer = fs.createWriteStream(filePath, { mode: 0o644 });
            
            reader.on('error', reject);
            writer.on('error', reject);
            writer.on('finish', resolve);
            reader.pipe(writer);
          });

          logger.info('文件保存完成', { filePath, userId });

          const fileUrl = `/upload/${datePath}/${projectName}/${uniqueFileName}`;

          uploadedFiles.push({
            url: fileUrl,
            fileName: fileName,
            fileSize: file.size,
            fileType: fileType,
            fileHash: fileHash,
            uploadedAt: moment().toISOString()
          });

          if (isRedisAvailable()) {
            const redis = getRedisClient();
            const sizeLimitKey = `upload:size:${userId}`;
            await redis.incrby(sizeLimitKey, file.size);
            await redis.expire(sizeLimitKey, 3600);
          }

          logger.info('文件上传成功', { fileUrl, fileName, userId });
        } catch (error) {
          logger.error('文件处理失败', { error: error.message, stack: error.stack, userId });
          errors.push({ 
            fileName: file.originalFilename || file.name || 'unknown', 
            error: '文件处理失败，请重试' 
          });
        }
      }

      for (const tempFile of tempFiles) {
        cleanTempFile(tempFile);
      }

      if (uploadedFiles.length === 0) {
        const errorMessages = errors.map(e => `${e.fileName}: ${e.error}`).join('; ');
        ctx.fail(6001, errorMessages || '所有文件上传失败');
        return;
      }

      if (errors.length > 0) {
        logger.warn('部分文件上传失败', { errors, userId });
      }

      if (uploadedFiles.length === 1) {
        ctx.success({
          ...uploadedFiles[0],
          warnings: errors.length > 0 ? errors : undefined
        });
      } else {
        ctx.success({
          files: uploadedFiles,
          errors: errors.length > 0 ? errors : undefined,
          total: uploadedFiles.length
        });
      }
    } catch (error) {
      logger.error('文件上传失败', { error: error.message, stack: error.stack });
      
      for (const tempFile of tempFiles) {
        cleanTempFile(tempFile);
      }
      
      ctx.fail(6001, '文件上传失败，请稍后重试');
    }
  }
};

module.exports = uploadController;
