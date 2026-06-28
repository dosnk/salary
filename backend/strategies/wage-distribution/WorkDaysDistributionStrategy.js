const WageDistributionStrategy = require('./WageDistributionStrategy');

// 按工日分配策略
class WorkDaysDistributionStrategy extends WageDistributionStrategy {
  /**
   * 计算按工日分配
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
    
    // 计算总工日数
    const totalWorkDays = workers.reduce((sum, worker) => sum + (worker.workdays || 1), 0);
    
    // 计算每工日的金额
    const amountPerWorkDay = totalAmount / totalWorkDays;
    
    return workers.map(worker => ({
      userId: worker.id,
      workdays: worker.workdays || 1,
      amount: amountPerWorkDay * (worker.workdays || 1)
    }));
  }
}

module.exports = WorkDaysDistributionStrategy;
