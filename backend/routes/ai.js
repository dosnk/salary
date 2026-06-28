/**
 * AI模块路由
 *
 * POST /v1/ai/chat          - 发送消息（普通响应）
 * POST /v1/ai/chat/stream   - 发送消息（SSE流式响应）
 * POST /v1/ai/layout        - 排料计算
 * GET  /v1/ai/history       - 获取对话历史
 * GET  /v1/ai/materials     - 获取材料列表
 * GET  /v1/ai/materials/categories - 获取材料分类
 * POST /v1/ai/materials     - 创建材料参数(admin)
 * PUT  /v1/ai/materials/:id - 更新材料参数(admin)
 * DELETE /v1/ai/materials/:id - 删除材料参数(admin)
 * GET  /v1/ai/knowledge     - 获取知识库列表(admin)
 * POST /v1/ai/knowledge     - 添加知识文档(admin)
 * GET  /v1/ai/knowledge/:title - 获取知识文档详情(admin)
 * DELETE /v1/ai/knowledge/:title - 删除知识文档(admin)
 */

const Router = require('koa-router');
const router = new Router();
const pool = require('../config/database');
const auth = require('../middleware/auth');
const { requireAdmin } = require('../middleware/rbac');
const validation = require('../middleware/validation');
const { sendMessage, sendMessageStream } = require('../ai/chatManager');
const { calculateLayout } = require('../ai/engine');
const materialsController = require('../controllers/materials');
const knowledgeController = require('../controllers/knowledge');

// ========== AI对话 ==========

/** 发送消息（普通响应） */
router.post('/chat', auth.authenticate, async (ctx) => {
  const { message, sessionId } = ctx.request.body;
  const user = ctx.state.user;

  if (!message) {
    ctx.fail(1001, '消息不能为空');
    return;
  }

  try {
    const result = await sendMessage({
      userId: user.id,
      sessionId: sessionId || `session_${Date.now()}`,
      message,
      user: { id: user.id, role: user.role },
    });
    ctx.success(result);
  } catch (error) {
    ctx.fail(5001, `AI服务异常: ${error.message}`);
  }
});

/** 发送消息（SSE流式响应） */
router.post('/chat/stream', auth.authenticate, async (ctx) => {
  const { message, sessionId } = ctx.request.body;
  const user = ctx.state.user;

  if (!message) {
    ctx.fail(1001, '消息不能为空');
    return;
  }

  // 设置SSE响应头
  ctx.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });

  try {
    const result = await sendMessageStream(
      {
        userId: user.id,
        sessionId: sessionId || `session_${Date.now()}`,
        message,
        user: { id: user.id, role: user.role },
      },
      (text) => {
        // SSE格式: data: {json}\n\n
        ctx.res.write(`data: ${JSON.stringify({ type: 'content', text })}\n\n`);
      }
    );

    // 发送结束标记
    ctx.res.write(`data: ${JSON.stringify({ type: 'done', intent: result.intent })}\n\n`);
    ctx.res.end();
  } catch (error) {
    ctx.res.write(`data: ${JSON.stringify({ type: 'error', message: error.message })}\n\n`);
    ctx.res.end();
  }
});

/** 排料计算 */
router.post('/layout', auth.authenticate, async (ctx) => {
  const { roomLength, roomWidth, materialOptions } = ctx.request.body;

  if (!roomLength || !roomWidth) {
    ctx.fail(1001, '请提供房间长度和宽度');
    return;
  }

  try {
    const result = await calculateLayout({
      roomLength: parseFloat(roomLength),
      roomWidth: parseFloat(roomWidth),
      materialOptions: materialOptions || {},
    });
    ctx.success(result);
  } catch (error) {
    ctx.fail(5001, `排料计算失败: ${error.message}`);
  }
});

/** 获取对话历史 */
router.get('/history', auth.authenticate, async (ctx) => {
  const { sessionId } = ctx.query;
  const userId = ctx.state.user.id;

  try {
    const result = await pool.query(
      `SELECT role, content, intent, created_at
       FROM ai_chat_history
       WHERE user_id = $1 AND session_id = $2
       ORDER BY created_at ASC`,
      [userId, sessionId]
    );
    ctx.success(result.rows);
  } catch (error) {
    ctx.fail(5001, '获取对话历史失败');
  }
});

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
router.get('/config', auth.authenticate, requireAdmin(), (ctx) => {
  const { aiConfig } = require('../ai/config');
  // 返回配置信息，API Key做脱敏处理（只显示前4位+***）
  const providers = {};
  for (const [key, provider] of Object.entries(aiConfig.providers)) {
    providers[key] = {
      name: provider.name,
      apiKey: provider.apiKey ? provider.apiKey.slice(0, 4) + '***' : '',
      secretKey: provider.secretKey ? provider.secretKey.slice(0, 4) + '***' : '',
      model: provider.model,
      maxTokens: provider.maxTokens,
      temperature: provider.temperature,
      baseUrl: provider.baseUrl,
      hasApiKey: !!provider.apiKey,
      hasSecretKey: !!provider.secretKey,
    };
  }
  ctx.success({
    defaultProvider: aiConfig.defaultProvider,
    providers,
  });
});

/** 更新AI配置 */
router.put('/config', auth.authenticate, requireAdmin(), async (ctx) => {
  const { defaultProvider, providerConfigs } = ctx.request.body;

  if (defaultProvider && !['tongyi', 'wenxin', 'deepseek', 'glm', 'doubao'].includes(defaultProvider)) {
    ctx.fail(1001, '不支持的AI提供商');
    return;
  }

  try {
    const fs = require('fs');
    const path = require('path');
    const envPath = path.join(__dirname, '..', '.env');

    // 读取现有.env文件
    let envContent = '';
    if (fs.existsSync(envPath)) {
      envContent = fs.readFileSync(envPath, 'utf-8');
    }

    // 更新默认提供商
    if (defaultProvider) {
      const regex = /^AI_PROVIDER=.*$/m;
      if (regex.test(envContent)) {
        envContent = envContent.replace(regex, `AI_PROVIDER=${defaultProvider}`);
      } else {
        envContent += `\nAI_PROVIDER=${defaultProvider}`;
      }
      process.env.AI_PROVIDER = defaultProvider;
    }

    // 更新各提供商配置
    if (providerConfigs) {
      const keyMapping = {
        tongyi: { apiKey: 'TONGYI_API_KEY', model: 'TONGYI_MODEL' },
        wenxin: { apiKey: 'WENXIN_API_KEY', secretKey: 'WENXIN_SECRET_KEY', model: 'WENXIN_MODEL' },
        deepseek: { apiKey: 'DEEPSEEK_API_KEY', model: 'DEEPSEEK_MODEL' },
        glm: { apiKey: 'GLM_API_KEY', model: 'GLM_MODEL' },
        doubao: { apiKey: 'DOUBAO_API_KEY', model: 'DOUBAO_MODEL' },
      };

      for (const [provider, config] of Object.entries(providerConfigs)) {
        const mapping = keyMapping[provider];
        if (!mapping) continue;

        // 更新API Key（非脱敏值才更新）
        if (config.apiKey && !config.apiKey.endsWith('***')) {
          const envKey = mapping.apiKey;
          const regex = new RegExp(`^${envKey}=.*$`, 'm');
          if (regex.test(envContent)) {
            envContent = envContent.replace(regex, `${envKey}=${config.apiKey}`);
          } else {
            envContent += `\n${envKey}=${config.apiKey}`;
          }
          process.env[envKey] = config.apiKey;
        }

        // 更新Secret Key（文心一言）
        if (config.secretKey && !config.secretKey.endsWith('***')) {
          const envKey = mapping.secretKey;
          const regex = new RegExp(`^${envKey}=.*$`, 'm');
          if (regex.test(envContent)) {
            envContent = envContent.replace(regex, `${envKey}=${config.secretKey}`);
          } else {
            envContent += `\n${envKey}=${config.secretKey}`;
          }
          process.env[envKey] = config.secretKey;
        }

        // 更新模型
        if (config.model) {
          const envKey = mapping.model;
          const regex = new RegExp(`^${envKey}=.*$`, 'm');
          if (regex.test(envContent)) {
            envContent = envContent.replace(regex, `${envKey}=${config.model}`);
          } else {
            envContent += `\n${envKey}=${config.model}`;
          }
          process.env[envKey] = config.model;
        }
      }
    }

    // 写回.env文件
    fs.writeFileSync(envPath, envContent, 'utf-8');

    // 重新加载配置到内存
    // 清除require缓存，下次请求时重新加载
    delete require.cache[require.resolve('../ai/config')];

    ctx.success({ message: 'AI配置已更新，将在下次请求时生效' });
  } catch (error) {
    ctx.fail(5001, `更新AI配置失败: ${error.message}`);
  }
});

// ========== 知识库管理（仅admin） ==========

/** 获取知识库列表 */
router.get('/knowledge', auth.authenticate, requireAdmin(), knowledgeController.listKnowledge);

/** 添加知识文档 */
router.post('/knowledge', auth.authenticate, requireAdmin(), validation(knowledgeController.createKnowledgeSchema), knowledgeController.createKnowledge);

/** 获取知识文档详情 */
router.get('/knowledge/:title', auth.authenticate, requireAdmin(), knowledgeController.getKnowledgeDetail);

/** 删除知识文档 */
router.delete('/knowledge/:title', auth.authenticate, requireAdmin(), knowledgeController.deleteKnowledge);

module.exports = router;
