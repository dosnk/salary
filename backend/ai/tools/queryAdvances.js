/**
 * FunctionCall工具 - 预支记录查询（只读工具）
 * 带权限过滤
 *
 * 【硬性规定·只读】本工具只执行 SELECT 查询，禁止任何写操作。
 *
 * 权限规则（V2.0 重新界定）：
 * - admin: 可查询全部预支记录
 * - constructor: 只能查询自己的预支记录
 * - documenter: 可查询全部预支记录（只读，可按人员筛选）
 *
 * 历史修复：
 * - 原 SQL 引用了不存在的字段 wa.amount 和 wa.status，实际为 wa.advance_amount 和 wa.settled
 * - 现已对齐 wage_advances 表真实字段
 * - 2026-08 增加 month 参数，按预支日期过滤
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

  let query = `
    SELECT
      wa.id,
      wa.advance_amount,
      wa.advance_date,
      wa.settled,
      wa.remark,
      wa.created_at,
      u.name as user_name
    FROM wage_advances wa
    JOIN users u ON wa.user_id = u.id
    WHERE 1=1
  `;
  const params = [];
  let paramIndex = 1;

  // 月份筛选（按预支日期过滤）
  if (month) {
    const [monthStart, nextMonthStart] = monthToDateRange(month);
    query += ` AND wa.advance_date >= $${paramIndex} AND wa.advance_date < $${paramIndex + 1}`;
    params.push(monthStart, nextMonthStart);
    paramIndex += 2;
  }

  // 权限过滤：施工员只能查看自己的预支记录
  if (role === 'constructor') {
    query += ` AND wa.user_id = $${paramIndex}`;
    params.push(userId);
    paramIndex++;
  }

  query += ` ORDER BY wa.created_at DESC LIMIT 20`;

  const result = await pool.query(query, params);

  return {
    count: result.rows.length,
    month: month || '全部',
    advances: result.rows.map(row => ({
      id: row.id,
      userName: row.user_name,
      amount: parseFloat(row.advance_amount) || 0,
      date: row.advance_date ? new Date(row.advance_date).toISOString().slice(0, 10) : '',
      settled: row.settled,
      remark: row.remark || '',
    })),
  };
};

module.exports = { execute };
