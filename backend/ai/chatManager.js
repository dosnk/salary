/**
 * 对话管理器
 *
 * 管理多轮对话:
 * - 维护对话历史
 * - 调用意图路由
 * - 执行FunctionCall
 * - 流式响应SSE
 */

const pool = require('../config/database');
const { createProvider } = require('./providers');
const { detectIntent, getToolsForIntent } = require('./intentRouter');
const { aiConfig } = require('./config');
const { isAvailable } = require('./index');
const { retrieve } = require('./knowledge/retriever');
const logger = require('../config/logger');

// FunctionCall工具执行器映射
const toolExecutors = {};

// 工具角色白名单（V2.0 重新界定：constructor和documenter可用AI除设置外）
// 三角色均可使用所有查询工具（documenter 可查看统计）
const toolRoleWhitelist = {
  calculate_layout: ['admin', 'constructor', 'documenter'],
  query_projects: ['admin', 'constructor', 'documenter'],
  query_statistics: ['admin', 'constructor', 'documenter'],
  query_settlements: ['admin', 'constructor', 'documenter'],
  query_advances: ['admin', 'constructor', 'documenter'],
};

// 注册所有工具执行器
const queryProjectsTool = require('./tools/queryProjects');
const queryStatisticsTool = require('./tools/queryStatistics');
const querySettlementsTool = require('./tools/querySettlements');
const queryAdvancesTool = require('./tools/queryAdvances');
const { calculateLayout } = require('./engine');

/**
 * 包装工具执行器，注入角色白名单校验
 * @param {string} toolName - 工具名称
 * @param {function} executor - 原始执行函数 async (args, user) => result
 * @returns {function} 包装后的执行函数，执行前校验角色权限
 */
const wrapWithRoleCheck = (toolName, executor) => {
  return async (args, user) => {
    const allowedRoles = toolRoleWhitelist[toolName];
    if (allowedRoles && user && !allowedRoles.includes(user.role)) {
      const roleNames = { admin: '管理员', constructor: '施工员', documenter: '资料员' };
      const roleName = roleNames[user.role] || user.role;
      return { error: `${roleName}无权使用此功能` };
    }
    return await executor(args, user);
  };
};

toolExecutors['query_projects'] = wrapWithRoleCheck('query_projects', queryProjectsTool.execute);
toolExecutors['query_statistics'] = wrapWithRoleCheck('query_statistics', queryStatisticsTool.execute);
toolExecutors['query_settlements'] = wrapWithRoleCheck('query_settlements', querySettlementsTool.execute);
toolExecutors['query_advances'] = wrapWithRoleCheck('query_advances', queryAdvancesTool.execute);
toolExecutors['calculate_layout'] = wrapWithRoleCheck('calculate_layout', async (args, user) => {
  return await calculateLayout({
    roomLength: args.length,
    roomWidth: args.width,
    materialOptions: args.materialId ? { panelId: args.materialId } : {},
  });
});

/**
 * 注册工具执行器
 * @param {string} name - 工具名称
 * @param {function} executor - 执行函数 async (args, user) => result
 */
const registerTool = (name, executor) => {
  toolExecutors[name] = executor;
};

/**
 * 发送消息并获取AI回复
 * @param {object} params
 * @param {number} params.userId - 用户ID
 * @param {string} params.sessionId - 会话ID
 * @param {string} params.message - 用户消息
 * @param {object} params.user - 用户信息 {id, role}
 * @returns {Promise<{content: string, intent: string}>}
 */
const sendMessage = async ({ userId, sessionId, message, user }) => {
  if (!isAvailable()) {
    return { content: 'AI功能暂未启用，请配置API Key后使用。', intent: 'chat' };
  }

  // 1. 识别意图
  const { intent, confidence } = detectIntent(message);

  // 2. 加载对话历史
  const history = await loadChatHistory(userId, sessionId);

  // 3. 构建系统提示词（含RAG知识注入）
  let systemPrompt = aiConfig.chat.systemPrompt;

  // 知识问答意图时，检索相关知识并注入系统提示词
  if (intent === 'knowledge' || intent === 'chat') {
    try {
      const knowledgeResults = await retrieve(message, { topK: 3 });
      if (knowledgeResults.length > 0) {
        const knowledgeContext = knowledgeResults
          .map((r, i) => `[${i + 1}] ${r.title ? r.title + ': ' : ''}${r.content}`)
          .join('\n\n');
        systemPrompt += `\n\n以下是从知识库检索到的相关资料，请参考这些内容回答用户问题：\n${knowledgeContext}`;
        logger.info(`RAG检索到${knowledgeResults.length}条相关知识`);
      }
    } catch (error) {
      logger.warn('RAG知识检索失败，跳过知识注入:', error.message);
    }
  }

  // 4. 构建消息列表
  const messages = [
    { role: 'system', content: systemPrompt },
    ...history.slice(-aiConfig.chat.maxHistoryMessages),
    { role: 'user', content: message },
  ];

  // 5. 获取当前提供商
  const provider = createProvider();

  // 6. 构建FunctionCall工具定义
  const toolNames = getToolsForIntent(intent);
  const tools = toolNames
    .map(name => toolDefinitions[name])
    .filter(Boolean);

  // 7. 调用大模型
  const options = {};
  if (tools.length > 0) {
    options.tools = tools;
  }

  const result = await provider.chat(messages, options);

  // 8. 处理FunctionCall
  let finalContent = result.content;
  if (result.toolCalls && result.toolCalls.length > 0) {
    // 注意: assistant消息的tool_calls必须使用API规范格式 {id, type, function:{name, arguments}}
    //       且后续tool消息必须包含 tool_call_id 关联对应tool_call，否则API返回400
    const toolCallIdMap = {};
    messages.push({
      role: 'assistant',
      content: result.content || null,
      tool_calls: result.toolCalls.map(tc => {
        const id = tc.id || `call_${tc.name}`;
        toolCallIdMap[tc.name] = id;
        return {
          id,
          type: 'function',
          function: { name: tc.name, arguments: JSON.stringify(tc.arguments) },
        };
      }),
    });

    for (const toolCall of result.toolCalls) {
      const executor = toolExecutors[toolCall.name];
      if (executor) {
        try {
          const toolResult = await executor(toolCall.arguments, user);
          messages.push({
            role: 'tool',
            tool_call_id: toolCallIdMap[toolCall.name] || toolCall.id,
            name: toolCall.name,
            content: JSON.stringify(toolResult),
          });
        } catch (error) {
          logger.error(`工具执行失败: ${toolCall.name}`, error);
          messages.push({
            role: 'tool',
            tool_call_id: toolCallIdMap[toolCall.name] || toolCall.id,
            name: toolCall.name,
            content: JSON.stringify({ error: error.message }),
          });
          finalContent += `\n\n[工具调用失败: ${error.message}]`;
        }
      }
    }

    // 工具结果追加完成后，再次调用大模型生成最终回复
    try {
      const followUp = await provider.chat(messages);
      finalContent = followUp.content;
    } catch (error) {
      logger.error('工具调用后大模型请求失败:', error);
      finalContent += `\n\n[生成回复失败: ${error.message}]`;
    }
  }

  // 9. 保存对话历史
  await saveChatHistory(userId, sessionId, 'user', message, intent);
  await saveChatHistory(userId, sessionId, 'assistant', finalContent, intent);

  return { content: finalContent, intent };
};

/**
 * 流式发送消息（SSE）
 * 支持FunctionCall工具调用循环（最多3轮防死循环）
 *
 * @param {object} params - 同sendMessage参数
 * @param {function} onChunk - 流式回调 (text: string) => void
 * @returns {Promise<{content: string, intent: string}>}
 */
const sendMessageStream = async ({ userId, sessionId, message, user }, onChunk) => {
  if (!isAvailable()) {
    onChunk('AI功能暂未启用，请配置API Key后使用。');
    return { content: 'AI功能暂未启用', intent: 'chat' };
  }

  const { intent } = detectIntent(message);
  const history = await loadChatHistory(userId, sessionId);

  // 构建系统提示词（含RAG知识注入）
  let systemPrompt = aiConfig.chat.systemPrompt;

  if (intent === 'knowledge' || intent === 'chat') {
    try {
      const knowledgeResults = await retrieve(message, { topK: 3 });
      if (knowledgeResults.length > 0) {
        const knowledgeContext = knowledgeResults
          .map((r, i) => `[${i + 1}] ${r.title ? r.title + ': ' : ''}${r.content}`)
          .join('\n\n');
        systemPrompt += `\n\n以下是从知识库检索到的相关资料，请参考这些内容回答用户问题：\n${knowledgeContext}`;
      }
    } catch (error) {
      logger.warn('RAG知识检索失败，跳过知识注入:', error.message);
    }
  }

  // 构建FunctionCall工具定义
  const toolNames = getToolsForIntent(intent);
  const tools = toolNames
    .map(name => toolDefinitions[name])
    .filter(Boolean);

  const messages = [
    { role: 'system', content: systemPrompt },
    ...history.slice(-aiConfig.chat.maxHistoryMessages),
    { role: 'user', content: message },
  ];

  const provider = createProvider();
  const streamOptions = tools.length > 0 ? { tools } : {};

  // 工具调用循环（最多3轮，防止死循环）
  // 每轮：调用流式接口 → 若返回tool_calls → 执行工具 → 将结果追加到messages → 继续下一轮流式
  let round = 0;
  let result = await provider.chatStream(messages, onChunk, streamOptions);
  let finalContent = result.content;

  while (result.toolCalls && result.toolCalls.length > 0 && round < 3) {
    // 将assistant的工具调用消息追加到上下文
    messages.push({
      role: 'assistant',
      content: result.content || null,
      tool_calls: result.toolCalls.map(tc => ({
        id: tc.id || `call_${round}_${tc.name}`,
        type: 'function',
        function: { name: tc.name, arguments: JSON.stringify(tc.arguments) },
      })),
    });

    // 逐个执行工具，将结果以tool角色追加到上下文
    // 注意: OpenAI/DeepSeek API规范要求tool消息必须包含 tool_call_id 字段，
    //       用于关联assistant消息中对应的tool_call，否则API返回400错误
    for (const toolCall of result.toolCalls) {
      const executor = toolExecutors[toolCall.name];
      // 为每个工具调用生成稳定的id（流式模式下可能为空，需兜底）
      const toolCallId = toolCall.id || `call_${round}_${toolCall.name}`;
      // 回填到assistant消息的tool_calls中，确保id一致
      const assistantMsg = messages[messages.length - 1];
      if (assistantMsg && assistantMsg.role === 'assistant' && assistantMsg.tool_calls) {
        const tcEntry = assistantMsg.tool_calls.find(
          tc => tc.function && tc.function.name === toolCall.name
        );
        if (tcEntry && !tcEntry.id) tcEntry.id = toolCallId;
      }

      if (executor) {
        try {
          onChunk(`\n\n[正在查询：${toolCall.name}...]\n`);
          const toolResult = await executor(toolCall.arguments, user);
          messages.push({
            role: 'tool',
            tool_call_id: toolCallId,
            name: toolCall.name,
            content: JSON.stringify(toolResult),
          });
        } catch (error) {
          logger.error(`工具执行失败: ${toolCall.name}`, error);
          messages.push({
            role: 'tool',
            tool_call_id: toolCallId,
            name: toolCall.name,
            content: JSON.stringify({ error: error.message }),
          });
        }
      } else {
        logger.warn(`未找到工具执行器: ${toolCall.name}`);
      }
    }

    round++;
    // 继续流式调用（仍带tools，让模型可以继续调用或基于工具结果生成最终回复）
    result = await provider.chatStream(messages, onChunk, streamOptions);
    finalContent = result.content;
  }

  await saveChatHistory(userId, sessionId, 'user', message, intent);
  await saveChatHistory(userId, sessionId, 'assistant', finalContent, intent);

  return { content: finalContent, intent };
};

/**
 * 加载对话历史
 */
const loadChatHistory = async (userId, sessionId) => {
  try {
    const result = await pool.query(
      `SELECT role, content FROM ai_chat_history
       WHERE user_id = $1 AND session_id = $2
       ORDER BY created_at ASC LIMIT $3`,
      [userId, sessionId, aiConfig.chat.maxHistoryMessages]
    );
    return result.rows.map(r => ({ role: r.role, content: r.content }));
  } catch (error) {
    logger.warn('加载对话历史失败:', error.message);
    return [];
  }
};

/**
 * 保存对话历史
 */
const saveChatHistory = async (userId, sessionId, role, content, intent) => {
  try {
    await pool.query(
      `INSERT INTO ai_chat_history (user_id, session_id, role, content, intent)
       VALUES ($1, $2, $3, $4, $5)`,
      [userId, sessionId, role, content, intent || null]
    );
  } catch (error) {
    logger.warn('保存对话历史失败:', error.message);
  }
};

/**
 * FunctionCall工具定义
 */
const toolDefinitions = {
  calculate_layout: {
    name: 'calculate_layout',
    description: '根据空间尺寸和材料参数计算排料方案，返回所需材料清单',
    parameters: {
      type: 'object',
      properties: {
        spaceType: { type: 'string', description: '空间类型' },
        length: { type: 'number', description: '空间长度(cm)' },
        width: { type: 'number', description: '空间宽度(cm)' },
        materialId: { type: 'number', description: '材料ID（可选）' },
      },
      required: ['spaceType', 'length', 'width'],
    },
  },
  query_projects: {
    name: 'query_projects',
    description: '查询用户有权限查看的工程列表',
    parameters: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '搜索关键词' },
        status: { type: 'string', description: '工程状态筛选' },
      },
    },
  },
  query_statistics: {
    name: 'query_statistics',
    description: '查询用户的统计数据（收入、工程数等）',
    parameters: {
      type: 'object',
      properties: {
        month: { type: 'string', description: '月份(YYYY-MM)' },
      },
    },
  },
  query_settlements: {
    name: 'query_settlements',
    description: '查询用户的工资结算记录，包含结算金额、预支金额、实付金额、结算月份、关联工程等信息。用户询问工资、结算、收入时使用此工具。',
    parameters: {
      type: 'object',
      properties: {
        status: { type: 'string', description: '结算状态筛选，可选值：已支付、已确认、待确认。不传则查询所有状态。' },
        month: { type: 'string', description: '月份筛选，格式YYYY-MM（如2026-07）。用户问"这个月工资"时传入当前月份。' },
      },
    },
  },
  query_advances: {
    name: 'query_advances',
    description: '查询用户的预支记录，包含预支金额、预支日期、是否已结算等信息。用户询问预支、借款时使用此工具。',
    parameters: {
      type: 'object',
      properties: {},
    },
  },
};

module.exports = { sendMessage, sendMessageStream, registerTool, toolDefinitions };
