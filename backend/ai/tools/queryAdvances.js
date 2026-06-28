/**
 * FunctionCall工具 - 预支查询
 * 带权限过滤
 */

const pool = require('../../../config/database');

const execute = async (args, user) => {
  const { id: userId, role } = user;

  let query = `
    SELECT wa.id, wa.amount, wa.remark, wa.created_at, wa.status,
           u.nickname as user_nickname
    FROM wage_advances wa
    JOIN users u ON wa.user_id = u.id
    WHERE 1=1
  `;
  const params = [];
  let paramIndex = 1;

  // 权限过滤
  if (role === 'constructor') {
    query += ` AND wa.user_id = $${paramIndex}`;
    params.push(userId);
    paramIndex++;
  }

  query += ` ORDER BY wa.created_at DESC LIMIT 20`;

  const result = await pool.query(query, params);
  return { advances: result.rows };
};

module.exports = { execute };
