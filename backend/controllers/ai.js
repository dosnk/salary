/**
 * AI模块控制器
 *
 * 职责：请求参数校验、SSE响应头设置、调用chatManager业务层、流式回调透传
 * 业务逻辑位于 ai/chatManager.js，数据访问位于 repositories/aiRepo.js
 *
 * 注意：材料管理使用 controllers/materials.js，知识库使用 controllers/knowledge.js
 *      本文件仅负责AI对话、排料、历史、配置管理
 */

const { sendMessage, sendMessageStream } = require('../ai/chatManager');
const { calculateLayout } = require('../ai/engine');
const pool = require('../config/database');
const logger = require('../config/logger');

/**
 * SSE心跳间隔（毫秒）
 * LLM生成期间定时发送注释行，防止Nginx/反向代理超时断连（默认60秒）
 */
const SSE_HEARTBEAT_INTERVAL_MS = 15000;

/**
 * 发送消息（普通响应，非流式）
 */
async function chat(ctx) {
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
    logger.error('AI对话失败:', error);
    ctx.fail(5001, `AI服务异常: ${error.message}`);
  }
}

/**
 * 发送消息（SSE流式响应）
 * 含心跳保活机制，防止长任务超时断连
 */
async function chatStream(ctx) {
  const { message, sessionId } = ctx.request.body;
  const user = ctx.state.user;

  if (!message) {
    ctx.fail(1001, '消息不能为空');
    return;
  }

  // 设置SSE响应头
  // 注意: 必须显式设置 ctx.status = 200，否则 Koa 默认 status 为 404，
  // 直接操作 ctx.res.write() 时会以 404 状态码发送响应头，导致前端误判为接口不存在
  ctx.status = 200;
  ctx.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',  // 禁用Nginx缓冲，保证流式实时性
  });

  // 心跳定时器：LLM生成期间发送SSE注释行（以冒号开头），客户端会忽略
  // 防止Nginx/反向代理因长时间无数据而断连
  const heartbeatTimer = setInterval(() => {
    try {
      ctx.res.write(': heartbeat\n\n');
    } catch (e) {
      // 连接已断开，忽略
    }
  }, SSE_HEARTBEAT_INTERVAL_MS);

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
  } catch (error) {
    logger.error('AI流式对话失败:', error);
    ctx.res.write(`data: ${JSON.stringify({ type: 'error', message: error.message })}\n\n`);
  } finally {
    // 清理心跳定时器并结束响应
    clearInterval(heartbeatTimer);
    ctx.res.end();
  }
}

/**
 * 排料计算
 */
async function layout(ctx) {
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
    logger.error('排料计算失败:', error);
    ctx.fail(5001, `排料计算失败: ${error.message}`);
  }
}

/**
 * 获取对话历史
 */
async function getHistory(ctx) {
  const { sessionId } = ctx.query;
  const userId = ctx.state.user.id;

  if (!sessionId) {
    ctx.fail(1001, '请提供sessionId');
    return;
  }

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
    logger.error('获取对话历史失败:', error);
    ctx.fail(5001, '获取对话历史失败');
  }
}

/**
 * 获取AI配置（API Key脱敏）
 */
async function getConfig(ctx) {
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
}

/**
 * 更新AI配置（写入.env文件并刷新内存）
 */
async function updateConfig(ctx) {
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

    // aiConfig 已改为 getter 动态读取 process.env，更新 process.env 后立即生效，无需清除 require 缓存
    ctx.success({ message: 'AI配置已更新，已立即生效' });
  } catch (error) {
    logger.error('更新AI配置失败:', error);
    ctx.fail(5001, `更新AI配置失败: ${error.message}`);
  }
}

/**
 * API连接测试（仅admin）
 */
async function testConnection(ctx) {
  const { provider } = ctx.request.body;

  if (!provider) {
    ctx.fail(1001, '请指定要测试的提供商');
    return;
  }

  try {
    // aiConfig 使用 getter 动态读取 process.env，直接 require 即可获取最新配置
    const { aiConfig } = require('../ai/config');
    const { createProvider } = require('../ai/providers');

    const providerConfig = aiConfig.providers[provider];
    if (!providerConfig) {
      ctx.fail(1001, `不支持的AI提供商: ${provider}`);
      return;
    }

    if (!providerConfig.apiKey) {
      ctx.fail(5001, `${providerConfig.name} 未配置API Key`);
      return;
    }

    // 创建提供商实例，发送测试消息
    const testProvider = createProvider(provider);
    const testMessages = [
      { role: 'user', content: '你好' }
    ];

    const result = await testProvider.chat(testMessages, { maxTokens: 50 });

    ctx.success({
      provider,
      providerName: providerConfig.name,
      model: providerConfig.model,
      response: result.content.substring(0, 100),
      message: '连接测试成功',
    });
  } catch (error) {
    logger.error('AI连接测试失败:', error);
    ctx.fail(5001, `连接测试失败: ${error.message}`);
  }
}

module.exports = {
  chat,
  chatStream,
  layout,
  getHistory,
  getConfig,
  updateConfig,
  testConnection,
};
