// 文件上传路由
const Router = require('koa-router');
const uploadController = require('../controllers/upload');
const authMiddleware = require('../middleware/auth');
const validationMiddleware = require('../middleware/validationMiddleware');
const Joi = require('joi');
const { deduplicate } = require('../middleware/deduplicate');

const router = new Router({
  prefix: '/v1'
});

// 文件上传验证规则
const uploadValidation = {
  body: Joi.object({
    // 可以添加非文件类型的参数验证
  })
};

// 文件上传接口
/**
 * @swagger
 * /v1/upload:
 *   post:
 *     summary: 文件上传
 *     description: 上传图片、视频、PDF等文件，支持批量上传。文件存储路径为 upload/年月/工程名称/
 *     tags:
 *       - 文件管理
 *     security:
 *       - bearerAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         multipart/form-data:
 *           schema:
 *             type: object
 *             required:
 *               - file
 *             properties:
 *               file:
 *                 type: string
 *                 format: binary
 *                 description: 要上传的文件
 *           encoding:
 *             file:
 *               contentType: image/jpeg, image/png, image/gif, application/pdf, video/mp4
 *     responses:
 *       200:
 *         description: 上传成功
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/FileUploadResponse'
 *             examples:
 *               imageUpload:
 *                 summary: 图片上传成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     url: 'https://your-domain.com/upload/202512/salary/abc123-def456-ghi789.jpg'
 *                   msg: 'ok'
 *               videoUpload:
 *                 summary: 视频上传成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     url: 'https://your-domain.com/upload/202512/salary/xyz789-uvw123-rst456.mp4'
 *                   msg: 'ok'
 *               pdfUpload:
 *                 summary: PDF上传成功
 *                 value:
 *                   code: 200
 *                   data:
 *                     url: 'https://your-domain.com/upload/202512/salary/def456-ghi789-jkl012.pdf'
 *                   msg: 'ok'
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               missingFile:
 *                 summary: 缺少上传文件
 *                 value:
 *                   code: 6004
 *                   data: null
 *                   msg: '缺少上传文件'
 *               unsupportedFileType:
 *                 summary: 不支持的文件类型
 *                 value:
 *                   code: 6002
 *                   data: null
 *                   msg: '不支持的文件类型'
 *               fileTooLarge:
 *                 summary: 文件大小超过限制
 *                 value:
 *                   code: 6003
 *                   data: null
 *                   msg: '文件大小超过限制'
 *       401:
 *         description: 未授权或token无效
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               tokenExpired:
 *                 summary: Token过期或无效
 *                 value:
 *                   code: 4001
 *                   data: null
 *                   msg: 'Token过期或无效'
 *               tokenFormatError:
 *                 summary: Token格式错误
 *                 value:
 *                   code: 4003
 *                   data: null
 *                   msg: 'Token格式错误'
 *       500:
 *         description: 服务器内部错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               uploadFailed:
 *                 summary: 文件上传失败
 *                 value:
 *                   code: 6001
 *                   data: null
 *                   msg: '文件上传失败'
 *               storageFailed:
 *                 summary: 文件存储失败
 *                 value:
 *                   code: 6005
 *                   data: null
 *                   msg: '文件存储失败'
 *               serverError:
 *                 summary: 服务器内部错误
 *                 value:
 *                   code: 5003
 *                   data: null
 *                   msg: '服务器内部错误'
 */
router.post('/upload',
  authMiddleware.authenticate,
  uploadController.uploadFile
);

module.exports = router;