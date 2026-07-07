/**
 * AI模块配置
 *
 * 支持的国内大模型提供商:
 * - tongyi: 通义千问 (阿里云)
 * - wenxin: 文心一言 (百度)
 * - deepseek: DeepSeek
 * - glm: 智谱ChatGLM
 * - doubao: 豆包 (字节跳动)
 *
 * 重要：所有配置项通过 getter 动态读取 process.env，
 * 确保运行时更新 .env / process.env 后无需重启进程或清除 require 缓存即可立即生效。
 * （修复：原实现将 process.env 固化到对象属性，导致保存配置后连接测试仍使用旧配置）
 */

require('dotenv').config();

// 提供商基础配置（不随环境变量变化的静态部分）
const providerStaticConfig = {
  tongyi: {
    name: '通义千问',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    maxTokens: 4096,
    temperature: 0.7,
  },
  wenxin: {
    name: '文心一言',
    baseUrl: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop',
    maxTokens: 4096,
    temperature: 0.7,
  },
  deepseek: {
    name: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com/v1',
    maxTokens: 4096,
    temperature: 0.7,
  },
  glm: {
    name: '智谱ChatGLM',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    maxTokens: 4096,
    temperature: 0.7,
  },
  doubao: {
    name: '豆包',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    maxTokens: 4096,
    temperature: 0.7,
  },
};

// 各提供商环境变量映射
const providerEnvKeys = {
  tongyi: { apiKey: 'TONGYI_API_KEY', model: 'TONGYI_MODEL', defaultModel: 'qwen-plus' },
  wenxin: { apiKey: 'WENXIN_API_KEY', secretKey: 'WENXIN_SECRET_KEY', model: 'WENXIN_MODEL', defaultModel: 'ernie-4.0-8k' },
  deepseek: { apiKey: 'DEEPSEEK_API_KEY', model: 'DEEPSEEK_MODEL', defaultModel: 'deepseek-chat' },
  glm: { apiKey: 'GLM_API_KEY', model: 'GLM_MODEL', defaultModel: 'glm-4' },
  doubao: { apiKey: 'DOUBAO_API_KEY', model: 'DOUBAO_MODEL', defaultModel: 'doubao-pro-4k' },
};

/**
 * aiConfig - 使用 getter 动态读取 process.env
 * 每次访问属性都会读取最新的 process.env 值，
 * 保证配置更新（updateConfig 写入 process.env）后立即生效。
 */
const aiConfig = {
  // 默认提供商（动态读取）
  get defaultProvider() {
    return process.env.AI_PROVIDER || 'deepseek';
  },

  // 各提供商配置（动态读取环境变量，合并静态配置）
  get providers() {
    const result = {};
    for (const [key, staticCfg] of Object.entries(providerStaticConfig)) {
      const envKeys = providerEnvKeys[key];
      result[key] = {
        ...staticCfg,
        apiKey: process.env[envKeys.apiKey] || '',
        model: process.env[envKeys.model] || envKeys.defaultModel,
      };
      // 文心一言额外有 secretKey
      if (envKeys.secretKey) {
        result[key].secretKey = process.env[envKeys.secretKey] || '';
      }
    }
    return result;
  },

  // 对话配置（静态）
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

  // 知识库配置（静态）
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
