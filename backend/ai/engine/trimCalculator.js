/**
 * 收边条计算引擎
 *
 * 根据空间尺寸计算收边条用量
 */

/**
 * 计算收边条用量
 * @param {object} params
 * @param {number} params.roomLength - 房间长度(cm)
 * @param {number} params.roomWidth - 房间宽度(cm)
 * @param {object} params.trim - 收边条参数 {length_cm, unit_price}
 * @returns {{ count: number, amount: string, perimeter: number }}
 */
const calculateTrim = ({ roomLength, roomWidth, trim }) => {
  const trimLength = parseFloat(trim?.length_cm) || 300;
  const trimPrice = parseFloat(trim?.unit_price) || 0;

  // 房间周长(cm)
  const perimeter = 2 * (roomLength + roomWidth);

  // 收边条根数
  const count = Math.ceil(perimeter / trimLength);

  return {
    name: trim?.name || '收边条',
    count,
    unitPrice: trimPrice,
    amount: (count * trimPrice).toFixed(2),
    perimeter: perimeter,
    perimeterDisplay: `${(perimeter / 100).toFixed(2)}m`,
  };
};

module.exports = { calculateTrim };
