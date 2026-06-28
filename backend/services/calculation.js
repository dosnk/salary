/**
 * 计算服务 - 统一处理所有业务计算逻辑
 * 
 * 计算规则：
 * 1. 子项目金额计算：根据施工方案单位类型选择计算公式
 *    - 按面积计算：长 × 宽 × 单价
 *    - 按周长计算：(长 + 宽) × 2 × 单价
 *    - 按长度计算：长 × 单价
 * 
 * 2. 用户工资计算：根据工资分配方式计算
 *    - 平均分配：总额 ÷ 人数
 *    - 按工日分配：总额 × (个人工日 ÷ 总工日)
 * 
 * 3. 预支总额计算：累加选中的预支记录金额
 * 
 * 4. 最终金额计算：工资总额 - 预支总额
 */

const pool = require('../config/database');

/**
 * 标准化单位类型
 * 将各种单位别名统一为标准类型：area / perimeter / length
 * @param {string} unit - 原始单位字符串
 * @returns {string} 标准化后的单位类型
 */
const normalizeUnit = (unit) => {
  if (!unit) return 'area';
  const normalized = unit.trim().toLowerCase();
  // 面积类
  if (['area', 'm²', '㎡', '平方米'].includes(normalized)) return 'area';
  // 周长类
  if (normalized === 'perimeter') return 'perimeter';
  // 长度类
  if (['length', 'm', '米'].includes(normalized)) return 'length';
  // 默认按面积
  return 'area';
};

/**
 * 根据施工方案单位类型计算子项目数量
 * @param {string} unit - 单位类型 (length/perimeter/area 或其别名)
 * @param {number} length - 长度（厘米）
 * @param {number} width - 宽度（厘米）
 * @returns {number} 计算后的数量（米或平方米）
 */
const calculateQuantity = (unit, length, width) => {
  const lengthM = length / 100;
  const widthM = width / 100;
  const standardUnit = normalizeUnit(unit);

  switch (standardUnit) {
    case 'length':
      return lengthM;
    case 'perimeter':
      return (lengthM + widthM) * 2;
    case 'area':
    default:
      return lengthM * widthM;
  }
};

/**
 * 计算子项目金额
 * @param {string} unit - 单位类型
 * @param {number} length - 长度（厘米）
 * @param {number} width - 宽度（厘米）
 * @param {number} price - 单价
 * @returns {object} { quantity, amount }
 */
const calculateSubprojectAmount = (unit, length, width, price) => {
  const quantity = calculateQuantity(unit, length, width);
  const amount = quantity * price;
  return { quantity, amount };
};

/**
 * 计算用户工资分配金额
 * @param {number} totalAmount - 子项目总金额
 * @param {number} totalQuantity - 子项目总数量
 * @param {string} salaryDistribution - 工资分配方式 (average/work_days)
 * @param {number} workerCount - 施工人员数量
 * @param {number} userWorkdays - 用户工作天数
 * @param {number} totalWorkdays - 总工作天数
 * @returns {object} { userAmount, userQuantity }
 */
const calculateUserWage = (totalAmount, totalQuantity, salaryDistribution, workerCount, userWorkdays, totalWorkdays) => {
  let userAmount = 0;
  let userQuantity = 0;

  if (salaryDistribution === 'average') {
    userAmount = totalAmount / workerCount;
    userQuantity = totalQuantity / workerCount;
  } else if (salaryDistribution === 'work_days') {
    if (totalWorkdays > 0) {
      userAmount = totalAmount * (userWorkdays / totalWorkdays);
      userQuantity = totalQuantity * (userWorkdays / totalWorkdays);
    } else {
      userAmount = totalAmount / workerCount;
      userQuantity = totalQuantity / workerCount;
    }
  } else {
    userAmount = totalAmount / workerCount;
    userQuantity = totalQuantity / workerCount;
  }

  return { userAmount, userQuantity };
};

/**
 * 计算预支总额
 * @param {number[]} advanceIds - 预支记录ID数组
 * @returns {Promise<number>} 预支总额
 */
const calculateAdvanceTotal = async (advanceIds) => {
  if (!advanceIds || !Array.isArray(advanceIds) || advanceIds.length === 0) {
    return 0;
  }

  const result = await pool.query(`
    SELECT COALESCE(SUM(advance_amount), 0) as total
    FROM wage_advances
    WHERE id = ANY($1) AND settled = false
  `, [advanceIds]);

  return parseFloat(result.rows[0].total);
};

/**
 * 获取用户所有未结算的预支记录
 * @param {number} userId - 用户ID
 * @returns {Promise<Array>} 预支记录列表
 */
const getUserUnsettledAdvances = async (userId) => {
  const result = await pool.query(`
    SELECT id, user_id, advance_amount, advance_date, remark, settled, created_at
    FROM wage_advances
    WHERE user_id = $1 AND settled = false
    ORDER BY advance_date DESC, created_at DESC
  `, [userId]);

  return result.rows;
};

/**
 * 计算用户所有未结算预支总额
 * @param {number} userId - 用户ID
 * @returns {Promise<number>} 预支总额
 */
const calculateUserAdvanceTotal = async (userId) => {
  const result = await pool.query(`
    SELECT COALESCE(SUM(advance_amount), 0) as total
    FROM wage_advances
    WHERE user_id = $1 AND settled = false
  `, [userId]);

  return parseFloat(result.rows[0].total);
};

/**
 * 计算最终金额
 * @param {number} grandTotal - 工资总额
 * @param {number} advanceTotal - 预支总额
 * @returns {number} 最终金额
 */
const calculateFinalAmount = (grandTotal, advanceTotal) => {
  return grandTotal - advanceTotal;
};

/**
 * 计算结算单预览数据
 * @param {number[]} projectIds - 工程ID数组
 * @param {number} currentUserId - 当前用户ID
 * @returns {Promise<object>} 结算计算结果
 */
const calculateSettlementPreview = async (projectIds, currentUserId) => {
  if (!projectIds || !Array.isArray(projectIds) || projectIds.length === 0) {
    return {
      plan_totals: {},
      grand_total: 0,
      total_advance: 0,
      final_total: 0,
      advances: []
    };
  }

  const projectsResult = await pool.query(`
    SELECT 
      p.id as project_id,
      p.name as project_name,
      p.salary_distribution
    FROM projects p
    WHERE p.id = ANY($1)
  `, [projectIds]);

  if (projectsResult.rows.length === 0) {
    return {
      plan_totals: {},
      grand_total: 0,
      total_advance: 0,
      final_total: 0,
      advances: []
    };
  }

  const projects = projectsResult.rows;

  const subprojectsResult = await pool.query(`
    SELECT 
      sp.id as subproject_id,
      sp.project_id,
      sp.construction_plan_id,
      sp.length,
      sp.width,
      sp.quantity,
      sp.amount,
      cp.name as construction_plan_name,
      cp.price,
      cp.unit
    FROM subprojects sp
    JOIN construction_plans cp ON sp.construction_plan_id = cp.id
    WHERE sp.project_id = ANY($1)
  `, [projectIds]);

  const subprojects = subprojectsResult.rows;

  const workerCountsResult = await pool.query(`
    SELECT 
      p.id as project_id,
      COUNT(DISTINCT pw.user_id) as worker_count
    FROM projects p
    LEFT JOIN project_workers pw ON p.id = pw.project_id
    WHERE p.id = ANY($1)
    GROUP BY p.id
  `, [projectIds]);

  const workerCounts = {};
  workerCountsResult.rows.forEach(row => {
    workerCounts[row.project_id] = row.worker_count;
  });

  const userWorkdaysResult = await pool.query(`
    SELECT 
      pw.project_id,
      COALESCE(pw.workdays, 1) as user_workdays
    FROM project_workers pw
    WHERE pw.project_id = ANY($1) AND pw.user_id = $2
  `, [projectIds, currentUserId]);

  const userWorkdays = {};
  userWorkdaysResult.rows.forEach(row => {
    userWorkdays[row.project_id] = row.user_workdays;
  });

  const totalWorkdaysResult = await pool.query(`
    SELECT 
      pw.project_id,
      SUM(COALESCE(pw.workdays, 1)) as total_workdays
    FROM project_workers pw
    WHERE pw.project_id = ANY($1)
    GROUP BY pw.project_id
  `, [projectIds]);

  const totalWorkdays = {};
  totalWorkdaysResult.rows.forEach(row => {
    totalWorkdays[row.project_id] = row.total_workdays;
  });

  const planTotals = {};
  for (const sp of subprojects) {
    const planId = String(sp.construction_plan_id);
    if (!planTotals[planId]) {
      planTotals[planId] = {
        total_quantity: 0,
        total_amount: 0
      };
    }

    const project = projects.find(p => p.project_id === sp.project_id);
    const workerCount = workerCounts[sp.project_id] || 1;
    const userWorkday = userWorkdays[sp.project_id] || 0;
    const totalWorkday = totalWorkdays[sp.project_id] || 1;

    const { userQuantity } = calculateUserWage(
      sp.amount,
      sp.quantity,
      project?.salary_distribution,
      workerCount,
      userWorkday,
      totalWorkday
    );

    planTotals[planId].total_quantity += userQuantity;
    planTotals[planId].total_amount += userQuantity * sp.price;
  }

  const grandTotal = Object.values(planTotals).reduce((sum, pt) => sum + pt.total_amount, 0);
  
  // 自动获取用户所有未结算预支记录
  const advances = await getUserUnsettledAdvances(currentUserId);
  const totalAdvance = await calculateUserAdvanceTotal(currentUserId);
  const finalTotal = calculateFinalAmount(grandTotal, totalAdvance);

  return {
    plan_totals: planTotals,
    grand_total: grandTotal,
    total_advance: totalAdvance,
    final_total: finalTotal,
    advances
  };
};

module.exports = {
  normalizeUnit,
  calculateQuantity,
  calculateSubprojectAmount,
  calculateUserWage,
  calculateAdvanceTotal,
  getUserUnsettledAdvances,
  calculateUserAdvanceTotal,
  calculateFinalAmount,
  calculateSettlementPreview
};
