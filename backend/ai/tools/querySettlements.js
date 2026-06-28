/**
 * FunctionCall工具 - 结算查询
 * 带权限过滤
 */

const pool = require('../../../config/database');

const execute = async (args, user) => {
  const { status } = args;
  const { id: userId, role } = user;

  let query = `
    SELECT s.id, s.total_amount, s.advance_amount, s.actual_amount,
           s.status, s.created_at,
           COALESCE(json_agg(json_build_object('projectId', sp.project_id, 'projectName', p.name))
           FILTER (WHERE sp.project_id IS NOT NULL), '[]') as projects
    FROM settlements s
    LEFT JOIN settlement_projects sp ON s.id = sp.settlement_id
    LEFT JOIN projects p ON sp.project_id = p.id
    WHERE 1=1
  `;
  const params = [];
  let paramIndex = 1;

  // 权限过滤
  if (role === 'constructor') {
    query += ` AND s.user_id = $${paramIndex}`;
    params.push(userId);
    paramIndex++;
  }

  if (status) {
    query += ` AND s.status = $${paramIndex}`;
    params.push(status);
    paramIndex++;
  }

  query += ` GROUP BY s.id ORDER BY s.created_at DESC LIMIT 10`;

  const result = await pool.query(query, params);
  return { settlements: result.rows };
};

module.exports = { execute };
