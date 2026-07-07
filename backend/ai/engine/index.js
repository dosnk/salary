/**
 * 排料引擎入口
 *
 * 协调各子引擎完成完整排料计算:
 * 1. 从知识库加载材料参数
 * 2. 计算面材排料
 * 3. 计算龙骨用量
 * 4. 计算收边条用量
 * 5. 计算配件用量
 * 6. 汇总材料清单和金额
 */

const { loadLayoutMaterials } = require('./materialLoader');
const { calculatePanelLayout } = require('./panelLayout');
const { calculateKeel } = require('./keelCalculator');
const { calculateTrim } = require('./trimCalculator');
const { calculateAccessories } = require('./accessoriesCalc');
const logger = require('../../config/logger');

/**
 * 执行完整排料计算
 * @param {object} params
 * @param {number} params.roomLength - 房间长度(cm)
 * @param {number} params.roomWidth - 房间宽度(cm)
 * @param {object} [params.materialOptions] - 指定材料ID
 * @returns {Promise<object>} 排料结果
 */
const calculateLayout = async ({ roomLength, roomWidth, materialOptions = {} }) => {
  const startTime = Date.now();

  // 1. 加载材料参数
  const materials = await loadLayoutMaterials(materialOptions);

  if (!materials.panel) {
    throw new Error('未找到可用的面材参数，请先在知识库中录入材料数据');
  }

  // 2. 计算面材
  const panelResult = calculatePanelLayout({
    roomLength,
    roomWidth,
    panel: materials.panel,
  });

  // 3. 计算龙骨
  const keelResult = calculateKeel({
    roomLength,
    roomWidth,
    mainKeel: materials.mainKeel,
    subKeel: materials.subKeel,
  });

  // 4. 计算收边条
  const trimResult = calculateTrim({
    roomLength,
    roomWidth,
    trim: materials.trim,
  });

  // 5. 计算配件
  const accessoriesResult = calculateAccessories({
    panelCount: panelResult.totalPanels,
    mainKeelCount: keelResult.mainKeel.count,
    subKeelCount: keelResult.subKeel.count,
    roomArea: parseFloat(panelResult.roomArea),
    accessories: materials.accessories,
  });

  // 6. 汇总
  const totalAmount =
    parseFloat(panelResult.amount) +
    parseFloat(keelResult.mainKeel.amount) +
    parseFloat(keelResult.subKeel.amount) +
    parseFloat(trimResult.amount) +
    accessoriesResult.reduce((sum, a) => sum + parseFloat(a.amount), 0);

  const elapsed = Date.now() - startTime;
  logger.info(`排料计算完成，耗时 ${elapsed}ms`, { roomLength, roomWidth, totalAmount });

  return {
    room: {
      length: roomLength,
      width: roomWidth,
      area: panelResult.roomArea,
    },
    materials: {
      panel: {
        ...materials.panel,
        ...panelResult,
      },
      mainKeel: keelResult.mainKeel,
      subKeel: keelResult.subKeel,
      trim: trimResult,
      accessories: accessoriesResult,
    },
    totalAmount: totalAmount.toFixed(2),
    layout: panelResult.layout,
    calculationTime: `${elapsed}ms`,
  };
};

module.exports = { calculateLayout };
