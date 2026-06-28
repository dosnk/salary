const AverageDistributionStrategy = require('./AverageDistributionStrategy');
const WorkDaysDistributionStrategy = require('./WorkDaysDistributionStrategy');

// 工资分配策略上下文
class WageDistributionContext {
  constructor() {
    this.strategies = {
      'average': new AverageDistributionStrategy(),
      'work_days': new WorkDaysDistributionStrategy()
    };
  }
  
  /**
   * 获取策略
   * @param {String} distributionType - 分配方式
   * @returns {WageDistributionStrategy} - 策略实例
   */
  getStrategy(distributionType) {
    const strategy = this.strategies[distributionType];
    if (!strategy) {
      throw new Error(`不支持的工资分配方式: ${distributionType}`);
    }
    return strategy;
  }
  
  /**
   * 计算工资分配
   * @param {Object} params - 计算参数
   * @param {Array} params.workers - 施工人员列表
   * @param {Number} params.totalAmount - 总金额
   * @param {String} params.distributionType - 分配方式
   * @returns {Array} - 工资分配结果
   */
  calculate(params) {
    const { distributionType } = params;
    const strategy = this.getStrategy(distributionType);
    return strategy.calculate(params);
  }
}

module.exports = new WageDistributionContext();
