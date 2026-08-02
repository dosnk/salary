/**
 * FunctionCall工具 - 统计查询（只读工具）
 * 带权限过滤
 *
 * 【硬性规定·只读】本工具只执行 SELECT 查询，禁止任何写操作。
 *
 * 权限规则（V2.0 重新界定）：
 * - admin: 可查询全部统计数据
 * - constructor: 只能查询自己参与工程的数据
 * - documenter: 可查询全部统计数据（只读，查看所有施工人员的工程）
 *
 * 数据来源对齐 services/statisticsService.js：
 * - 金额源：subprojects.amount 聚合（非 projects.total_amount 冗余字段）
 * - 状态过滤：sp.status = 'completed' AND p.status = 'completed'
 * - 月份维度：子项目创建时间 sp.created_at
 * - 已结算金额：通过 v_project_user_settlement_status 视图过滤
 * - 预支金额：wage_advances.advance_amount 聚合
 */

const pool = require('../../config/database');

/**
 * 将 YYYY-MM 月份字符串转换为日期范围参数 [月初, 下月初)
 * @param {string} month - 月份字符串，如 '2026-07'
 * @returns {[string, string]} [月初, 下月初)
 */
const monthToDateRange = (month) => {
  const [year, m] = month.split('-').map(Number);
  const monthStart = `${year}-${String(m).padStart(2, '0')}-01`;
  const nextMonth = m === 12 ? 1 : m + 1;
  const nextYear = m === 12 ? year + 1 : year;
  const nextMonthStart = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;
  return [monthStart, nextMonthStart];
};

const execute = async (args, user) => {
  const { month } = args;
  const { id: userId, role } = user;
  const isConstructorUser = role === 'constructor';

  // 月份日期范围参数（用于子项目创建时间过滤）
  let dateCondition = '';
  let dateParams = [];
  if (month) {
    const [monthStart, nextMonthStart] = monthToDateRange(month);
    dateCondition = `sp.created_at >= $1 AND sp.created_at < $2`;
    dateParams = [monthStart, nextMonthStart];
  } else {
    // 无月份时无条件（查全部），使用占位以保持参数索引一致性
    dateCondition = 'TRUE';
    dateParams = [];
  }
  let paramIndex = dateParams.length + 1;

  // ===== 1. 统计已完工子项目金额（总额）=====
  // 对齐 statisticsService.getMonthlyStatistics 的 distributionQuery
  let distributionQuery = `
    SELECT
      SUM(sp.amount) AS total_amount,
      COUNT(DISTINCT sp.id) AS total_distributions,
      COUNT(DISTINCT p.id) AS project_count
    FROM subprojects sp
    JOIN projects p ON sp.project_id = p.id
    WHERE sp.status = 'completed'
      AND p.status = 'completed'
      AND (${dateCondition})
  `;
  let distributionParams = [...dateParams];

  // 施工员只能查看自己参与的工程数据
  if (isConstructorUser) {
    distributionQuery += `
      AND EXISTS (
        SELECT 1 FROM project_workers pw
        WHERE pw.project_id = p.id
          AND pw.user_id = $${paramIndex}
      )
    `;
    distributionParams.push(userId);
    paramIndex++;
  }

  const distributionResult = await pool.query(distributionQuery, distributionParams);

  // ===== 2. 统计已结算金额（通过 v_project_user_settlement_status 视图过滤）=====
  let settledQuery = `
    SELECT
      SUM(sp.amount) AS settled_amount
    FROM subprojects sp
    JOIN projects p ON sp.project_id = p.id
    WHERE sp.status = 'completed'
      AND p.status = 'completed'
      AND (${dateCondition})
  `;
  let settledParams = [...dateParams];
  let settledParamIndex = settledParams.length + 1;

  if (isConstructorUser) {
    // 施工员：只统计自己参与且自己已结算的工程
    settledQuery += `
      AND EXISTS (
        SELECT 1 FROM project_workers pw
        WHERE pw.project_id = p.id
          AND pw.user_id = $${settledParamIndex}
      )
      AND EXISTS (
        SELECT 1 FROM v_project_user_settlement_status pus
        WHERE pus.project_id = p.id
          AND pus.user_id = $${settledParamIndex}
          AND pus.settlement_status = 'settled'
      )
    `;
    settledParams.push(userId);
    settledParamIndex++;
  } else {
    // 管理员/资料员：统计存在已结算记录的工程（看全部）
    settledQuery += `
      AND EXISTS (
        SELECT 1 FROM v_project_user_settlement_status pus
        WHERE pus.project_id = p.id
          AND pus.settlement_status = 'settled'
      )
    `;
  }

  const settledResult = await pool.query(settledQuery, settledParams);

  // ===== 3. 统计预支金额 =====
  let advanceQuery = '';
  let advanceParams = [];
  if (month) {
    const [monthStart, nextMonthStart] = monthToDateRange(month);
    advanceQuery = `
      SELECT
        COUNT(DISTINCT wa.id) AS total_advances,
        COALESCE(SUM(wa.advance_amount), 0) AS advance_amount
      FROM wage_advances wa
      WHERE wa.advance_date >= $1 AND wa.advance_date < $2
    `;
    advanceParams = [monthStart, nextMonthStart];
  } else {
    advanceQuery = `
      SELECT
        COUNT(DISTINCT wa.id) AS total_advances,
        COALESCE(SUM(wa.advance_amount), 0) AS advance_amount
      FROM wage_advances wa
      WHERE TRUE
    `;
    advanceParams = [];
  }
  let advanceParamIndex = advanceParams.length + 1;

  // 施工员只能查看自己的预支记录
  if (isConstructorUser) {
    advanceQuery += ` AND wa.user_id = $${advanceParamIndex} `;
    advanceParams.push(userId);
    advanceParamIndex++;
  }

  const advanceResult = await pool.query(advanceQuery, advanceParams);

  // ===== 4. 组装结果 =====
  const totalAmount = parseFloat(distributionResult.rows[0].total_amount) || 0;
  const settledAmount = parseFloat(settledResult.rows[0].settled_amount) || 0;
  const totalAdvances = parseInt(advanceResult.rows[0].total_advances) || 0;
  const advanceAmount = parseFloat(advanceResult.rows[0].advance_amount) || 0;

  return {
    month: month || '全部',
    projectCount: parseInt(distributionResult.rows[0].project_count) || 0,
    subprojectCount: parseInt(distributionResult.rows[0].total_distributions) || 0,
    // 总金额（已完工子项目金额之和）
    totalIncome: totalAmount,
    // 已结算金额
    settledAmount: settledAmount,
    // 未结算金额 = 总额 - 已结算
    unsettledAmount: totalAmount - settledAmount,
    // 预支记录数
    advanceCount: totalAdvances,
    // 预支总金额
    advanceAmount: advanceAmount,
  };
};

module.exports = { execute };
