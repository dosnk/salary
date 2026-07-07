/**
 * 意图路由器
 *
 * 根据用户输入判断意图，路由到对应的处理模块:
 * - layout: 排料计算（包含空间尺寸、板材、龙骨等关键词）
 * - query: 数据查询（工程/统计/结算/预支）
 * - knowledge: 知识问答（施工规范、材料知识）
 * - chat: 通用对话
 */

const logger = require('../config/logger');

// 意图关键词映射
const INTENT_PATTERNS = {
  layout: {
    keywords: ['排料', '排版', '下料', '板材', '面板', '龙骨', '收边', '吊顶材料', '需要多少', '材料计算', '用料', '算料', '裁切'],
    description: '排料计算',
  },
  query_project: {
    keywords: ['工程列表', '查看工程', '我的工程', '工程状态', '工程进度'],
    description: '工程查询',
  },
  query_statistics: {
    keywords: ['统计', '收入', '月度', '总额', '工程数', '完工数'],
    description: '统计查询',
  },
  query_settlement: {
    keywords: ['结算', '工资', '结算单', '待结算'],
    description: '结算查询',
  },
  query_advance: {
    keywords: ['预支', '借款', '借支'],
    description: '预支查询',
  },
  knowledge: {
    keywords: ['规范', '标准', '做法', '施工工艺', '安装方法', '怎么装', '如何施工', '材料区别', '什么材料'],
    description: '知识问答',
  },
};

/**
 * 识别用户意图
 * @param {string} message - 用户消息
 * @returns {{ intent: string, confidence: number }} 意图和置信度
 */
const detectIntent = (message) => {
  if (!message || typeof message !== 'string') {
    return { intent: 'chat', confidence: 0.5 };
  }

  const lowerMessage = message.toLowerCase();
  let bestIntent = 'chat';
  let bestScore = 0;

  for (const [intent, config] of Object.entries(INTENT_PATTERNS)) {
    let score = 0;
    for (const keyword of config.keywords) {
      if (lowerMessage.includes(keyword)) {
        score += 1;
      }
    }
    if (score > bestScore) {
      bestScore = score;
      bestIntent = intent;
    }
  }

  // 置信度：匹配关键词数/最大可能匹配数
  const confidence = bestScore > 0 ? Math.min(bestScore / 3, 1.0) : 0.3;

  logger.info('意图识别', { message: message.substring(0, 50), intent: bestIntent, confidence });

  return { intent: bestIntent, confidence };
};

/**
 * 获取意图对应的FunctionCall工具列表
 * @param {string} intent - 识别的意图
 * @returns {Array} 工具定义列表
 */
const getToolsForIntent = (intent) => {
  const allTools = {
    layout: ['calculate_layout'],
    query_project: ['query_projects'],
    query_statistics: ['query_statistics'],
    query_settlement: ['query_settlements'],
    query_advance: ['query_advances'],
    knowledge: [],
    chat: [],
  };

  // 排料意图同时提供查询工具
  if (intent === 'layout') {
    return [...(allTools.layout || []), ...(allTools.query_project || [])];
  }

  return allTools[intent] || [];
};

module.exports = { detectIntent, getToolsForIntent, INTENT_PATTERNS };
