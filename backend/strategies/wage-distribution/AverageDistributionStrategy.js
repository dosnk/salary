const WageDistributionStrategy = require('./WageDistributionStrategy');

// 平均分配策略
class AverageDistributionStrategy extends WageDistributionStrategy {
  /**
   * 计算平均分配
   * @param {Object} params - 计算参数
   * @param {Array} params.workers - 施工人员列表
   * @param {Number} params.totalAmount - 总金额
   * @returns {Array} - 工资分配结果
   */
  calculate(params) {
    const { workers, totalAmount } = params;
    
    if (!workers || workers.length === 0) {
      return [];
    }
    
    const averageAmount = totalAmount / workers.length;
    
    return workers.map(worker => ({
      userId: worker.id,
      workdays: 1,
      amount: averageAmount
    }));
  }
}

module.exports = AverageDistributionStrategy;
