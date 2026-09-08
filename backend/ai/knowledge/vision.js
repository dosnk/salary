/**
 * 图片视觉识别器
 *
 * 调用当前提供商的视觉模型生成图片文字描述，用于知识库图片检索
 * 消息格式采用 OpenAI vision 规范（content 数组 + image_url base64），
 * 通义（qwen-vl）/智谱（glm-4v）/豆包（doubao-vision）的兼容接口均支持
 *
 * 提供商支持情况：
 * - 文心（非OpenAI兼容格式）、DeepSeek（无视觉模型）: 不支持，返回 unsupported，
 *   图片降级为 标题+手动描述 参与检索
 */

const { aiConfig, getProviderConfig } = require('../config');
const logger = require('../../config/logger');

/** 视觉识别请求超时（毫秒） */
const VISION_TIMEOUT_MS = 20000;

/** 发送给视觉模型的 base64 大小上限（约对应10MB原图，超过则跳过识别） */
const MAX_BASE64_LENGTH = 14 * 1024 * 1024;

/** 视觉识别提示词：生成的描述将作为图片的知识库检索文本 */
const VISION_PROMPT = '请详细描述这张图片的内容，包括场景、物体、材料、施工工艺等关键信息，用于吊顶行业知识库的语义检索。直接输出描述内容，不要任何前缀和解释，300字以内。';

/**
 * 获取当前生效的视觉模型名
 * @returns {string|null} 模型名，null表示当前提供商不支持视觉能力
 */
const getVisionModel = () => aiConfig.visionModel;

/**
 * 识别图片生成文字描述
 *
 * @param {string} imageBase64 - 图片 base64 编码（不含 data:xxx;base64, 前缀）
 * @param {string} mimeType - 图片 MIME 类型（如 image/jpeg）
 * @returns {Promise<{status: string, description: string, message: string}>}
 *   status: ok(识别成功)/failed(调用失败)/unsupported(提供商不支持)
 */
const describeImage = async (imageBase64, mimeType) => {
  const model = getVisionModel();

  // 当前提供商无视觉模型（文心/DeepSeek），降级处理
  if (!model) {
    return {
      status: 'unsupported',
      description: '',
      message: '当前AI提供商不支持图片识别，图片将以标题和手动描述参与检索',
    };
  }

  // base64 过大（超过模型接口限制），跳过识别
  if (!imageBase64 || imageBase64.length > MAX_BASE64_LENGTH) {
    return {
      status: 'failed',
      description: '',
      message: '图片过大，跳过自动识别',
    };
  }

  const config = getProviderConfig();

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), VISION_TIMEOUT_MS);

    // OpenAI vision 消息格式：content 为数组，含文本指令与图片 base64
    const response = await fetch(`${config.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify({
        model,
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: VISION_PROMPT },
              { type: 'image_url', image_url: { url: `data:${mimeType || 'image/jpeg'};base64,${imageBase64}` } },
            ],
          },
        ],
        max_tokens: 600,
        temperature: 0.2,
      }),
      signal: controller.signal,
    });

    clearTimeout(timeout);

    if (!response.ok) {
      const errBody = await response.text().catch(() => '');
      logger.warn(`视觉识别接口不可用(HTTP ${response.status}): ${errBody.substring(0, 200)}`);
      return {
        status: 'failed',
        description: '',
        message: `图片识别失败(HTTP ${response.status})，图片将以标题和手动描述参与检索`,
      };
    }

    const data = await response.json();
    const description = (data.choices?.[0]?.message?.content || '').trim();

    if (!description) {
      return {
        status: 'failed',
        description: '',
        message: '图片识别返回内容为空',
      };
    }

    return {
      status: 'ok',
      description,
      message: '图片识别成功',
    };
  } catch (error) {
    logger.warn(`图片视觉识别失败(模型:${model}): ${error.message}`);
    return {
      status: 'failed',
      description: '',
      message: `图片识别失败: ${error.message}，图片将以标题和手动描述参与检索`,
    };
  }
};

module.exports = { describeImage, getVisionModel };
