/**
 * 龙骨计算引擎
 *
 * 根据空间尺寸和龙骨参数，计算主龙骨和副龙骨用量
 */

/**
 * 计算龙骨用量
 * @param {object} params
 * @param {number} params.roomLength - 房间长度(cm)
 * @param {number} params.roomWidth - 房间宽度(cm)
 * @param {object} params.mainKeel - 主龙骨参数 {length_cm, keel_spacing_cm, unit_price}
 * @param {object} params.subKeel - 副龙骨参数 {length_cm, keel_spacing_cm, unit_price}
 * @returns {{ mainKeel: object, subKeel: object }}
 */
const calculateKeel = ({ roomLength, roomWidth, mainKeel, subKeel }) => {
  // 主龙骨：沿房间长度方向，按间距排列
  const mainKeelSpacing = parseFloat(mainKeel?.keel_spacing_cm) || 100;
  const mainKeelLength = parseFloat(mainKeel?.length_cm) || 300;
  const mainKeelPrice = parseFloat(mainKeel?.unit_price) || 0;

  // 主龙骨根数 = 宽度方向/间距 + 1（边龙骨）
  const mainKeelCount = Math.ceil(roomWidth / mainKeelSpacing) + 1;
  // 每根主龙骨需要的标准长度根数
  const mainKeelPerRow = Math.ceil(roomLength / mainKeelLength);
  const mainKeelTotal = mainKeelCount * mainKeelPerRow;

  // 副龙骨：沿房间宽度方向，按间距排列
  const subKeelSpacing = parseFloat(subKeel?.keel_spacing_cm) || 40;
  const subKeelLength = parseFloat(subKeel?.length_cm) || 300;
  const subKeelPrice = parseFloat(subKeel?.unit_price) || 0;

  // 副龙骨根数 = 长度方向/间距 + 1
  const subKeelCount = Math.ceil(roomLength / subKeelSpacing) + 1;
  // 每根副龙骨需要的标准长度根数
  const subKeelPerRow = Math.ceil(roomWidth / subKeelLength);
  const subKeelTotal = subKeelCount * subKeelPerRow;

  return {
    mainKeel: {
      name: mainKeel?.name || '主龙骨',
      count: mainKeelTotal,
      unitPrice: mainKeelPrice,
      amount: (mainKeelTotal * mainKeelPrice).toFixed(2),
      spacing: `${mainKeelSpacing}cm`,
    },
    subKeel: {
      name: subKeel?.name || '副龙骨',
      count: subKeelTotal,
      unitPrice: subKeelPrice,
      amount: (subKeelTotal * subKeelPrice).toFixed(2),
      spacing: `${subKeelSpacing}cm`,
    },
  };
};

module.exports = { calculateKeel };
