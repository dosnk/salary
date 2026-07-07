/**
 * FunctionCall工具 - 结算查询
 * 带权限过滤
 *
 * 修复说明：
 * 1. 原SQL错误引用了不存在的表名 `settlements` 和 `settlement_projects`，
 *    实际数据库表名为 `wage_settlements`，工程ID存储在 `project_ids` JSONB数组字段。
 * 2. 状态字段不存在，实际由 `confirmed`(boolean) + `paid`(boolean) 表示。
 * 3. 新增 month 参数支持，让AI可按月份过滤（用户问"这个月工资"时需要）。
 * 4. 添加详细日志便于诊断问题。
 */

const pool = require('../../config/database');
const logger = require('../../config/logger');

const execute = async (args, user) => {
  const { status, month } = args || {};
  const { id: userId, role } = user || {};

  logger.info('query_settlements 工具执行', {
    args: JSON.stringify(args),
    userId,
    role,
    status,
    month,
  });

  try {
    // 查询工资结算记录
    // 注意：project_ids 是 JSONB 数组字段（如 [1,2,3]），直接返回由AI解析
    // 状态映射：paid=true→已支付，confirmed=true→已确认，否则→待确认
    let query = `
      SELECT ws.id, ws.settlement_no, ws.user_id, ws.start_month, ws.end_month,
             ws.total_amount, ws.advance_amount, ws.actual_amount,
             ws.confirmed, ws.paid, ws.settled_at, ws.project_ids,
             ws.settled_by,
             u.nickname AS user_nickname,
             su.nickname AS settled_by_nickname,
             CASE
               WHEN ws.paid = TRUE THEN '已支付'
               WHEN ws.confirmed = TRUE THEN '已确认'
               ELSE '待确认'
             END AS status_text
      FROM wage_settlements ws
      LEFT JOIN users u ON ws.user_id = u.id
      LEFT JOIN users su ON ws.settled_by = su.id
      WHERE 1=1
    `;
    const params = [];
    let paramIndex = 1;

    // 权限过滤：施工员仅能查看自己的结算记录
    if (role === 'constructor') {
      query += ` AND ws.user_id = $${paramIndex}`;
      params.push(userId);
      paramIndex++;
    }

    // 状态过滤（兼容前端传入的中文/英文状态）
    if (status) {
      if (status === 'paid' || status === '已支付') {
        query += ` AND ws.paid = TRUE`;
      } else if (status === 'confirmed' || status === '已确认') {
        query += ` AND ws.confirmed = TRUE AND ws.paid = FALSE`;
      } else if (status === 'pending' || status === '待确认') {
        query += ` AND ws.confirmed = FALSE`;
      }
    }

    // 月份过滤（支持 "2026-07" 或 "2026-7" 格式）
    // 同时匹配 start_month 和 end_month 的范围（结算覆盖的月份）
    if (month) {
      // 标准化月份格式为 "YYYY-MM"
      const normalizedMonth = String(month).replace(/(\d{4})-(\d{1,2})/, (m, y, mo) =>
        `${y}-${mo.padStart(2, '0')}`
      );
      query += ` AND TO_CHAR(ws.start_month, 'YYYY-MM') <= $${paramIndex}`;
      params.push(normalizedMonth);
      paramIndex++;
      query += ` AND TO_CHAR(ws.end_month, 'YYYY-MM') >= $${paramIndex}`;
      params.push(normalizedMonth);
      paramIndex++;
      logger.info('query_settlements 月份过滤', { month: normalizedMonth });
    }

    query += ` ORDER BY ws.settled_at DESC NULLS LAST LIMIT 10`;

    logger.info('query_settlements SQL', { query, params: JSON.stringify(params) });

    const result = await pool.query(query, params);

    logger.info('query_settlements 查询结果', {
      rowCount: result.rowCount,
      firstRow: result.rows[0] ? JSON.stringify(result.rows[0]).substring(0, 200) : 'null',
    });

    // 关联查询工程名称（project_ids 是JSONB数组，需单独查询工程名）
    // 避免在主查询中用 jsonb_array_elements_text 导致行数膨胀影响分页
    const projectIds = result.rows
      .flatMap(r => {
        // 兼容 project_ids 为数组或字符串的情况
        const ids = r.project_ids;
        if (Array.isArray(ids)) return ids;
        if (typeof ids === 'string') {
          try { return JSON.parse(ids); } catch (e) { return []; }
        }
        return [];
      })
      .filter(id => id != null);

    let projectMap = {};
    if (projectIds.length > 0) {
      const projectRes = await pool.query(
        `SELECT id, name FROM projects WHERE id = ANY($1::int[])`,
        [projectIds]
      );
      projectMap = Object.fromEntries(projectRes.rows.map(p => [p.id, p.name]));
    }

    // 为每条结算记录附加工程名称列表
    // 同时将日期格式化为更友好的字符串
    const settlements = result.rows.map(row => ({
      id: row.id,
      settlementNo: row.settlement_no,
      userId: row.user_id,
      userNickname: row.user_nickname,
      startMonth: row.start_month ? new Date(row.start_month).toISOString().substring(0, 7) : null,
      endMonth: row.end_month ? new Date(row.end_month).toISOString().substring(0, 7) : null,
      totalAmount: parseFloat(row.total_amount) || 0,
      advanceAmount: parseFloat(row.advance_amount) || 0,
      actualAmount: parseFloat(row.actual_amount) || 0,
      confirmed: row.confirmed,
      paid: row.paid,
      statusText: row.status_text,
      settledAt: row.settled_at,
      settledByNickname: row.settled_by_nickname,
      projectNames: (Array.isArray(row.project_ids) ? row.project_ids : [])
        .map(pid => projectMap[pid])
        .filter(Boolean),
    }));

    logger.info('query_settlements 返回数据', {
      count: settlements.length,
      hasData: settlements.length > 0,
    });

    return { settlements };
  } catch (error) {
    logger.error('query_settlements 执行异常', {
      message: error.message,
      stack: error.stack,
      code: error.code,
      detail: error.detail,
      position: error.position,
    });
    // 将详细错误信息返回给AI，让AI能告诉用户具体出了什么问题
    throw new Error(`查询结算记录失败: ${error.message} (代码: ${error.code || 'unknown'})`);
  }
};

module.exports = { execute };
