/**
 * FunctionCall工具 - 统计查询
 * 带权限过滤
 */

const pool = require('../../../config/database');

const execute = async (args, user) => {
  const { month } = args;
  const { id: userId, role } = user;

  let query = `
    SELECT
      COUNT(*) as project_count,
      COALESCE(SUM(p.total_amount), 0) as total_income,
      COUNT(*) FILTER (WHERE p.status = 'completed') as completed_count
    FROM projects p
    WHERE p.status != 'deleted'
  `;
  const params = [];
  let paramIndex = 1;

  // 权限过滤
  if (role === 'constructor') {
    query += ` AND p.id IN (SELECT project_id FROM project_workers WHERE user_id = $${paramIndex})`;
    params.push(userId);
    paramIndex++;
  }

  // 月份筛选
  if (month) {
    query += ` AND TO_CHAR(p.created_at, 'YYYY-MM') = $${paramIndex}`;
    params.push(month);
    paramIndex++;
  }

  const result = await pool.query(query, params);
  const row = result.rows[0];

  return {
    projectCount: parseInt(row.project_count),
    totalIncome: row.total_income,
    completedCount: parseInt(row.completed_count),
    month: month || '全部',
  };
};

module.exports = { execute };
