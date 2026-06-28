/**
 * 面材排料引擎
 *
 * 根据空间尺寸和板材参数，计算面材用量并生成SVG布局数据
 * 纯本地计算，不依赖网络，<50ms
 */

const logger = require('../../config/logger');

/**
 * 计算面材排料
 * @param {object} params
 * @param {number} params.roomLength - 房间长度(cm)
 * @param {number} params.roomWidth - 房间宽度(cm)
 * @param {object} params.panel - 板材参数 {width_cm, length_cm, coverage_area, unit_price}
 * @returns {{ totalPanels: number, fullPanels: number, cutPanels: number, wasteRate: number, amount: number, layout: object }}
 */
const calculatePanelLayout = ({ roomLength, roomWidth, panel }) => {
  const panelW = parseFloat(panel.width_cm) || 120;
  const panelL = parseFloat(panel.length_cm) || 240;
  const unitPrice = parseFloat(panel.unit_price) || 0;

  // 沿长度方向排列的板材数
  const panelsAlongLength = Math.ceil(roomLength / panelL);
  // 沿宽度方向排列的板材数
  const panelsAlongWidth = Math.ceil(roomWidth / panelW);

  // 总板材数
  const totalPanels = panelsAlongLength * panelsAlongWidth;

  // 完整板材数（不需要裁切的）
  const fullLengthPanels = Math.floor(roomLength / panelL);
  const fullWidthPanels = Math.floor(roomWidth / panelW);
  const fullPanels = fullLengthPanels * fullWidthPanels;

  // 需要裁切的板材数
  const cutPanels = totalPanels - fullPanels;

  // 面积计算
  const roomArea = (roomLength / 100) * (roomWidth / 100); // ㎡
  const totalPanelArea = totalPanels * (panel.coverage_area || (panelW / 100) * (panelL / 100));
  const wasteRate = totalPanelArea > 0 ? ((totalPanelArea - roomArea) / totalPanelArea * 100) : 0;

  // 金额
  const amount = totalPanels * unitPrice;

  // 生成SVG布局数据
  const layout = generatePanelSVG(roomLength, roomWidth, panelW, panelL, panelsAlongLength, panelsAlongWidth);

  return {
    totalPanels,
    fullPanels,
    cutPanels,
    roomArea: roomArea.toFixed(4),
    totalPanelArea: totalPanelArea.toFixed(4),
    wasteRate: wasteRate.toFixed(2),
    amount: amount.toFixed(2),
    layout,
  };
};

/**
 * 生成面材排料SVG数据
 */
const generatePanelSVG = (roomL, roomW, panelW, panelL, cols, rows) => {
  const svgWidth = 600;
  const padding = 40;
  const drawWidth = svgWidth - 2 * padding;
  const drawHeight = (roomW / roomL) * drawWidth;
  const svgHeight = drawHeight + 2 * padding;

  const scaleX = drawWidth / roomL;
  const scaleY = drawHeight / roomW;

  const panels = [];
  for (let row = 0; row < rows; row++) {
    for (let col = 0; col < cols; col++) {
      const x = padding + col * panelL * scaleX;
      const y = padding + row * panelW * scaleY;
      const w = Math.min(panelL * scaleX, (roomL - col * panelL) * scaleX);
      const h = Math.min(panelW * scaleY, (roomW - row * panelW) * scaleY);
      const isFull = (col < Math.floor(roomL / panelL)) && (row < Math.floor(roomW / panelW));

      panels.push({ x, y, w, h, isFull });
    }
  }

  return {
    svgWidth,
    svgHeight,
    padding,
    roomRect: { x: padding, y: padding, w: drawWidth, h: drawHeight },
    panels,
    dimensions: {
      roomLength: roomL,
      roomWidth: roomW,
      panelLength: panelL,
      panelWidth: panelW,
    },
  };
};

module.exports = { calculatePanelLayout };
