// 工资分配策略接口
class WageDistributionStrategy {
  /**
   * 计算工资分配
   * @param {Object} params - 计算参数
   * @param {Array} params.workers - 施工人员列表
   * @param {Number} params.totalAmount - 总金额
   * @param {String} params.distributionType - 分配方式
   * @returns {Array} - 工资分配结果
   */
  calculate(params) {
    throw new Error('子类必须实现 calculate 方法');
  }
}

module.exports = WageDistributionStrategy;
