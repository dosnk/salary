/**
 * FunctionCall工具 - 工程查询（只读工具）
 * 带权限过滤：admin看全部，constructor看自己参与的，documenter看全部(只读)
 *
 * 【硬性规定·只读】本工具只执行 SELECT 查询，禁止任何写操作。
 *
 * 返回字段说明：
 * - 工程基本信息（id, name, status, statusText, totalAmount, remark, createdAt）
 * - 子项目统计（subprojectCount, completedSubprojectCount）
 * - 施工人员列表（workers）
 */

const pool = require('../../config/database');

// 工程状态中文映射
const STATUS_TEXT = {
  preparing: '备料中',
  constructing: '施工中',
  completed: '已完工',
  settled: '已结算',
  canceled: '已取消',
  deleted: '已删除',
};

// 日期格式化（YYYY-MM-DD）
const formatDate = (date) => {
  if (!date) return '';
  const d = new Date(date);
  if (isNaN(d.getTime())) return '';
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const execute = async (args, user) => {
  const { keyword, status } = args;
  const { id: userId, role } = user;

  // 使用 LATERAL 子查询聚合子项目统计，避免 GROUP BY 笛卡尔积
  let query = `
    SELECT
      p.id,
      p.name,
      p.status,
      p.total_amount,
      p.remark,
      p.created_at,
      COALESCE(sp_agg.subproject_count, 0) AS subproject_count,
      COALESCE(sp_agg.completed_subproject_count, 0) AS completed_subproject_count,
      COALESCE(
        json_agg(
          json_build_object('userId', pw.user_id, 'nickname', u.nickname)
        ) FILTER (WHERE pw.user_id IS NOT NULL), '[]'
      ) AS workers
    FROM projects p
    LEFT JOIN project_workers pw ON p.id = pw.project_id
    LEFT JOIN users u ON pw.user_id = u.id
    LEFT JOIN LATERAL (
      SELECT
        COUNT(*) AS subproject_count,
        COUNT(*) FILTER (WHERE status = 'completed') AS completed_subproject_count
      FROM subprojects sp
      WHERE sp.project_id = p.id AND sp.status != 'deleted'
    ) sp_agg ON TRUE
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

  query += ` GROUP BY p.id, sp_agg.subproject_count, sp_agg.completed_subproject_count ORDER BY p.created_at DESC LIMIT 10`;

  const result = await pool.query(query, params);

  return {
    total: result.rows.length,
    projects: result.rows.map(row => ({
      id: row.id,
      name: row.name,
      status: row.status,
      statusText: STATUS_TEXT[row.status] || row.status,
      totalAmount: parseFloat(row.total_amount) || 0,
      remark: row.remark || '',
      createdAt: formatDate(row.created_at),
      subprojectCount: parseInt(row.subproject_count) || 0,
      completedSubprojectCount: parseInt(row.completed_subproject_count) || 0,
      workers: row.workers || [],
    })),
  };
};

module.exports = { execute };
