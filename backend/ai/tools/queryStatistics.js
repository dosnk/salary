/**
 * FunctionCall工具 - 统计查询
 * 带权限过滤
 *
 * 权限规则（V2.0 重新界定）：
 * - admin: 可查询全部统计数据
 * - constructor: 只能查询自己参与工程的数据
 * - documenter: 可查询全部统计数据（只读，查看所有施工人员的工程）
 */

const pool = require('../../config/database');

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

  // 权限过滤：施工员只能查询自己参与的工程；admin 和 documenter 可查全部
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
