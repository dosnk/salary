/**
 * FunctionCall工具 - 工程查询
 * 带权限过滤：admin看全部，constructor看自己参与的，documenter看全部(只读)
 */

const pool = require('../../../config/database');

const execute = async (args, user) => {
  const { keyword, status } = args;
  const { id: userId, role } = user;

  let query = `
    SELECT p.id, p.name, p.status, p.total_amount, p.created_at,
           COALESCE(json_agg(json_build_object('userId', pw.user_id, 'nickname', u.nickname))
           FILTER (WHERE pw.user_id IS NOT NULL), '[]') as workers
    FROM projects p
    LEFT JOIN project_workers pw ON p.id = pw.project_id
    LEFT JOIN users u ON pw.user_id = u.id
    WHERE p.status != 'deleted'
  `;
  const params = [];
  let paramIndex = 1;

  // 权限过滤：施工员只能看自己参与的工程
  if (role === 'constructor') {
    query += ` AND p.id IN (SELECT project_id FROM project_workers WHERE user_id = $${paramIndex})`;
    params.push(userId);
    paramIndex++;
  }

  // 关键词搜索
  if (keyword) {
    query += ` AND p.name ILIKE $${paramIndex}`;
    params.push(`%${keyword}%`);
    paramIndex++;
  }

  // 状态筛选
  if (status) {
    query += ` AND p.status = $${paramIndex}`;
    params.push(status);
    paramIndex++;
  }

  query += ` GROUP BY p.id ORDER BY p.created_at DESC LIMIT 10`;

  const result = await pool.query(query, params);
  return { projects: result.rows, total: result.rows.length };
};

module.exports = { execute };
