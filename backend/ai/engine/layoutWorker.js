/**
 * 排料计算Worker
 *
 * 将CPU密集的排料计算移入Worker Thread，避免阻塞主线程
 * 使用Node.js worker_threads模块
 */

const { workerData, parentPort } = require('worker_threads');
const { calculatePanelLayout } = require('./panelLayout');
const { calculateKeel } = require('./keelCalculator');
const { calculateTrim } = require('./trimCalculator');
const { calculateAccessories } = require('./accessoriesCalc');

if (!workerData || !parentPort) {
  process.exit(1);
}

try {
  const { roomLength, roomWidth, materials } = workerData;
  const startTime = Date.now();

  // 面材计算
  const panelResult = calculatePanelLayout({
    roomLength,
    roomWidth,
    panel: materials.panel,
  });

  // 龙骨计算
  const keelResult = calculateKeel({
    roomLength,
    roomWidth,
    mainKeel: materials.mainKeel,
    subKeel: materials.subKeel,
  });

  // 收边条计算
  const trimResult = calculateTrim({
    roomLength,
    roomWidth,
    trim: materials.trim,
  });

  // 配件计算
  const accessoriesResult = calculateAccessories({
    panelCount: panelResult.totalPanels,
    mainKeelCount: keelResult.mainKeel.count,
    subKeelCount: keelResult.subKeel.count,
    roomArea: parseFloat(panelResult.roomArea),
    accessories: materials.accessories,
  });

  // 汇总金额
  const totalAmount =
    parseFloat(panelResult.amount) +
    parseFloat(keelResult.mainKeel.amount) +
    parseFloat(keelResult.subKeel.amount) +
    parseFloat(trimResult.amount) +
    accessoriesResult.reduce((sum, a) => sum + parseFloat(a.amount), 0);

  const elapsed = Date.now() - startTime;

  parentPort.postMessage({
    success: true,
    data: {
      room: { length: roomLength, width: roomWidth, area: panelResult.roomArea },
      materials: {
        panel: { ...materials.panel, ...panelResult },
        mainKeel: keelResult.mainKeel,
        subKeel: keelResult.subKeel,
        trim: trimResult,
        accessories: accessoriesResult,
      },
      totalAmount: totalAmount.toFixed(2),
      layout: panelResult.layout,
      calculationTime: `${elapsed}ms`,
    },
  });
} catch (error) {
  parentPort.postMessage({
    success: false,
    error: error.message,
  });
}
