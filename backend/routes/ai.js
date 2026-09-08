/**
 * AI模块路由
 *
 * 职责：仅声明路由 + 鉴权中间件 + 参数校验中间件
 * 业务逻辑位于 controllers/ai.js、controllers/materials.js、controllers/knowledge.js
 *
 * 路由清单：
 * POST /v1/ai/chat          - 发送消息（普通响应）
 * POST /v1/ai/chat/stream   - 发送消息（SSE流式响应）
 * POST /v1/ai/layout        - 排料计算
 * GET  /v1/ai/history       - 获取对话历史
 * GET  /v1/ai/materials     - 获取材料列表
 * GET  /v1/ai/materials/categories - 获取材料分类
 * POST /v1/ai/materials     - 创建材料参数(admin)
 * PUT  /v1/ai/materials/:id - 更新材料参数(admin)
 * DELETE /v1/ai/materials/:id - 删除材料参数(admin)
 * GET  /v1/ai/knowledge                  - 获取知识库列表(admin)
 * GET  /v1/ai/knowledge/categories       - 获取知识分类列表(admin)
 * POST /v1/ai/knowledge                  - 手动录入知识文档(admin)
 * POST /v1/ai/knowledge/import-file      - 文件导入知识文档(admin)
 * POST /v1/ai/knowledge/import-media     - 媒体导入知识文档(admin)
 * GET  /v1/ai/knowledge/:id              - 获取知识文档详情(admin)
 * PUT  /v1/ai/knowledge/:id              - 编辑知识文档(admin)
 * DELETE /v1/ai/knowledge/:id            - 删除知识文档(admin)
 * POST /v1/ai/knowledge/:id/redescribe   - 图片重新识别(admin)
 * GET  /v1/ai/config        - 获取AI配置(admin)
 * PUT  /v1/ai/config        - 更新AI配置(admin)
 * POST /v1/ai/test          - API连接测试(admin)
 */

const Router = require('koa-router');
const router = new Router({
  prefix: '/v1/ai'
});
const auth = require('../middleware/auth');
const { requireAdmin } = require('../middleware/rbac');
const validation = require('../middleware/validation');
const aiController = require('../controllers/ai');
const materialsController = require('../controllers/materials');
const knowledgeController = require('../controllers/knowledge');

// ========== AI对话 ==========

/** 发送消息（普通响应） */
router.post('/chat', auth.authenticate, aiController.chat);

/** 发送消息（SSE流式响应，含心跳保活） */
router.post('/chat/stream', auth.authenticate, aiController.chatStream);

/** 排料计算 */
router.post('/layout', auth.authenticate, aiController.layout);

/** 获取对话历史 */
router.get('/history', auth.authenticate, aiController.getHistory);

// ========== 材料参数管理 ==========

/** 获取材料分类 */
router.get('/materials/categories', auth.authenticate, materialsController.getCategories);

/** 获取所有材料 */
router.get('/materials', auth.authenticate, materialsController.getAllMaterials);

/** 获取指定分类的材料 */
router.get('/materials/category/:categoryId', auth.authenticate, materialsController.getMaterialsByCategory);

/** 创建材料参数（仅admin） */
router.post('/materials', auth.authenticate, requireAdmin(), validation(materialsController.createMaterialSchema), materialsController.createMaterial);

/** 更新材料参数（仅admin） */
router.put('/materials/:id', auth.authenticate, requireAdmin(), validation(materialsController.updateMaterialSchema, { includeParams: true }), materialsController.updateMaterial);

/** 删除材料参数（仅admin） */
router.delete('/materials/:id', auth.authenticate, requireAdmin(), materialsController.deleteMaterial);

// ========== AI配置管理（仅admin） ==========

/** 获取AI配置 */
router.get('/config', auth.authenticate, requireAdmin(), aiController.getConfig);

/** 更新AI配置 */
router.put('/config', auth.authenticate, requireAdmin(), aiController.updateConfig);

/** API连接测试 */
router.post('/test', auth.authenticate, requireAdmin(), aiController.testConnection);

// ========== 知识库管理（仅admin） ==========

/** 获取知识分类列表（注意：必须在 /knowledge/:id 之前注册） */
router.get('/knowledge/categories', auth.authenticate, requireAdmin(), knowledgeController.getCategories);

/** 获取知识库文档列表（分页 + 分类筛选 + 关键词搜索） */
router.get('/knowledge', auth.authenticate, requireAdmin(), knowledgeController.listKnowledge);

/** 手动录入知识文档（纯文本） */
router.post('/knowledge', auth.authenticate, requireAdmin(), validation(knowledgeController.createKnowledgeSchema), knowledgeController.createKnowledge);

/** 文件导入知识文档（txt/md/pdf/docx，multipart） */
router.post('/knowledge/import-file', auth.authenticate, requireAdmin(), knowledgeController.importFile);

/** 媒体导入知识文档（图片/视频/音频，multipart） */
router.post('/knowledge/import-media', auth.authenticate, requireAdmin(), knowledgeController.importMedia);

/** 图片重新识别（视觉模型配置好后补识别） */
router.post('/knowledge/:id/redescribe', auth.authenticate, requireAdmin(), knowledgeController.redescribeImage);

/** 获取知识文档详情 */
router.get('/knowledge/:id(\\d+)', auth.authenticate, requireAdmin(), knowledgeController.getKnowledgeDetail);

/** 编辑知识文档（内容变更自动重新分块） */
router.put('/knowledge/:id(\\d+)', auth.authenticate, requireAdmin(), validation(knowledgeController.updateKnowledgeSchema), knowledgeController.updateKnowledge);

/** 删除知识文档（级联删分块+删媒体文件） */
router.delete('/knowledge/:id(\\d+)', auth.authenticate, requireAdmin(), knowledgeController.deleteKnowledge);

module.exports = router;
