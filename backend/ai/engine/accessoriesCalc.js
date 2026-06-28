/**
 * 配件计算引擎
 *
 * 根据面材和龙骨数量计算配件用量
 */

/**
 * 计算配件用量
 * @param {object} params
 * @param {number} params.panelCount - 面材总数
 * @param {number} params.mainKeelCount - 主龙骨总数
 * @param {number} params.subKeelCount - 副龙骨总数
 * @param {number} params.roomArea - 房间面积(㎡)
 * @param {Array} params.accessories - 配件参数列表 [{name, unit_price, ...}]
 * @returns {Array} 配件清单
 */
const calculateAccessories = ({ panelCount, mainKeelCount, subKeelCount, roomArea, accessories }) => {
  const results = [];

  for (const acc of accessories) {
    let count = 0;
    const name = acc.name || '';
    const price = parseFloat(acc.unit_price) || 0;

    // 根据配件类型计算用量
    if (name.includes('吊杆')) {
      // 吊杆：每根主龙骨2-3个吊点
      count = mainKeelCount * 3;
    } else if (name.includes('主龙骨吊件')) {
      count = mainKeelCount * 2;
    } else if (name.includes('副龙骨挂件')) {
      count = subKeelCount * 2;
    } else if (name.includes('自攻螺丝')) {
      // 每张板约20颗，每盒1000颗
      count = Math.ceil(panelCount * 20 / 1000);
    } else if (name.includes('膨胀螺栓')) {
      count = mainKeelCount * 2;
    } else if (name.includes('接长件') && name.includes('主')) {
      count = Math.max(0, mainKeelCount - Math.floor(mainKeelCount / 1)); // 每根接长1次
    } else if (name.includes('接长件') && name.includes('副')) {
      count = Math.max(0, subKeelCount - Math.floor(subKeelCount / 1));
    } else {
      // 默认：按面积估算
      count = Math.ceil(roomArea);
    }

    results.push({
      name,
      count,
      unit: acc.unit || '个',
      unitPrice: price,
      amount: (count * price).toFixed(2),
    });
  }

  return results;
};

module.exports = { calculateAccessories };
