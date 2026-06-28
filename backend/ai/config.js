/**
 * AI模块配置
 *
 * 支持的国内大模型提供商:
 * - tongyi: 通义千问 (阿里云)
 * - wenxin: 文心一言 (百度)
 * - deepseek: DeepSeek
 * - glm: 智谱ChatGLM
 * - doubao: 豆包 (字节跳动)
 */

require('dotenv').config();

const aiConfig = {
  // 默认提供商（从环境变量读取）
  defaultProvider: process.env.AI_PROVIDER || 'deepseek',

  // 各提供商配置
  providers: {
    tongyi: {
      name: '通义千问',
      apiKey: process.env.TONGYI_API_KEY || '',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      model: process.env.TONGYI_MODEL || 'qwen-plus',
      maxTokens: 4096,
      temperature: 0.7,
    },
    wenxin: {
      name: '文心一言',
      apiKey: process.env.WENXIN_API_KEY || '',
      secretKey: process.env.WENXIN_SECRET_KEY || '',
      baseUrl: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop',
      model: process.env.WENXIN_MODEL || 'ernie-4.0-8k',
      maxTokens: 4096,
      temperature: 0.7,
    },
    deepseek: {
      name: 'DeepSeek',
      apiKey: process.env.DEEPSEEK_API_KEY || '',
      baseUrl: 'https://api.deepseek.com/v1',
      model: process.env.DEEPSEEK_MODEL || 'deepseek-chat',
      maxTokens: 4096,
      temperature: 0.7,
    },
    glm: {
      name: '智谱ChatGLM',
      apiKey: process.env.GLM_API_KEY || '',
      baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
      model: process.env.GLM_MODEL || 'glm-4',
      maxTokens: 4096,
      temperature: 0.7,
    },
    doubao: {
      name: '豆包',
      apiKey: process.env.DOUBAO_API_KEY || '',
      baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
      model: process.env.DOUBAO_MODEL || 'doubao-pro-4k',
      maxTokens: 4096,
      temperature: 0.7,
    },
  },

  // 对话配置
  chat: {
    maxHistoryMessages: 20,    // 最大历史消息数
    systemPrompt: `你是"三人行吊顶管理系统"的AI助手。你的职责是：
1. 帮助用户查询工程、统计、结算、预支等业务数据
2. 根据空间尺寸和材料参数，进行排料计算
3. 回答吊顶施工相关的专业问题
4. 提供材料推荐和采购建议

注意事项：
- 你只能查询用户有权限查看的数据
- 金额计算由后端引擎完成，你不参与核心计算
- 排料计算由本地引擎完成，你只提供优化建议
- 回答要简洁专业，使用中文`,
  },

  // 知识库配置
  knowledge: {
    chunkSize: 500,           // 文档分块大小（字符）
    chunkOverlap: 50,         // 分块重叠大小
    topK: 5,                  // 检索返回的top-K结果
    similarityThreshold: 0.7, // 相似度阈值
  },
};

/**
 * 获取指定提供商的配置
 * @param {string} providerName - 提供商名称
 * @returns {object} 提供商配置
 */
const getProviderConfig = (providerName) => {
  const name = providerName || aiConfig.defaultProvider;
  const config = aiConfig.providers[name];
  if (!config) {
    throw new Error(`不支持的AI提供商: ${name}`);
  }
  return { name, ...config };
};

module.exports = { aiConfig, getProviderConfig };
