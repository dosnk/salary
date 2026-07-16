/**
 * FunctionCall工具 - 预支查询
 * 带权限过滤
 *
 * 权限规则（V2.0 重新界定）：
 * - admin: 可查看全部预支记录
 * - documenter: 可查看全部预支记录（按人员筛选查看）
 * - constructor: 只能查看自己的预支记录
 *
 * 修复说明：
 * 原SQL引用了不存在的字段 wa.amount 和 wa.status，
 * 实际数据库字段为 wa.advance_amount（金额）和 wa.settled（boolean，是否已结算）。
 */

const pool = require('../../config/database');

const execute = async (args, user) => {
  const { id: userId, role } = user;

  // 查询预支记录
  // 状态映射：settled=true→已结算，false→未结算
  let query = `
    SELECT wa.id, wa.advance_amount, wa.advance_date, wa.remark,
           wa.settled, wa.created_at,
           u.nickname AS user_nickname,
           CASE
             WHEN wa.settled = TRUE THEN '已结算'
             ELSE '未结算'
           END AS status_text
    FROM wage_advances wa
    JOIN users u ON wa.user_id = u.id
    WHERE 1=1
  `;
  const params = [];
  let paramIndex = 1;

  // 权限过滤：施工员仅能查看自己的预支记录
  // admin 和 documenter 可查看全部预支记录
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
