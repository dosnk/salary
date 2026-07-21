const pool = require('../config/database');
const logger = require('../config/logger');

// 诊断API
const diagnose = async (ctx) => {
  try {
    const userId = ctx.state.user?.id;
    if (!userId) {
      ctx.fail(4001, '用户未登录');
      return;
    }

    const result = {};

    // 1. 用户信息
    const userResult = await pool.query('SELECT * FROM users WHERE id = $1', [userId]);
    result.user = userResult.rows[0];

    // 2. 统计中的工程（基于用户独立的结算状态）
    const projectsResult = await pool.query(`
      SELECT p.id, p.name, pus.settlement_status 
      FROM projects p
      INNER JOIN project_user_status pus ON p.id = pus.project_id
      WHERE pus.settlement_status = 'settling'
    `);
    result.settlingProjects = projectsResult.rows;

    // 3. 用户参与的工程
    const userProjectsResult = await pool.query('SELECT * FROM project_workers WHERE user_id = $1', [userId]);
    result.userProjects = userProjectsResult.rows;

    // 4. 用户的工资分配
    const distributionsResult = await pool.query('SELECT * FROM wage_distributions WHERE user_id = $1', [userId]);
    result.distributions = distributionsResult.rows;

    // 5. 未结算的工资分配
    const unsettledResult = await pool.query(`
      SELECT wd.*, sp.project_id, cp.name as plan_name 
      FROM wage_distributions wd 
      INNER JOIN subprojects sp ON wd.subproject_id = sp.id 
      LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id 
      WHERE wd.user_id = $1 AND wd.settlement_id IS NULL
    `, [userId]);
    result.unsettledDistributions = unsettledResult.rows;

    // 6. 执行getProjects查询
    const queryResult = await pool.query(`
      SELECT 
        p.id,
        p.name as project_name,
        p.created_at,
        cp.id as plan_id,
        cp.name as plan_name,
        cp.unit,
        COALESCE(SUM(wd.amount), 0) as user_amount,
        COALESCE(SUM(
          CASE 
            WHEN cp.unit = 'area' THEN sp.area
            WHEN cp.unit = 'length' THEN sp.length
            WHEN cp.unit = 'perimeter' THEN (sp.length + sp.width) * 2
            ELSE sp.area
          END
        ) / COUNT(DISTINCT pw.user_id), 0) as user_quantity
      FROM projects p
      INNER JOIN project_workers pw ON p.id = pw.project_id AND pw.user_id = $1
      INNER JOIN project_user_status pus ON p.id = pus.project_id AND pus.user_id = $1
      INNER JOIN subprojects sp ON p.id = sp.project_id
      INNER JOIN wage_distributions wd ON sp.id = wd.subproject_id AND wd.user_id = $1
      LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      WHERE pus.settlement_status = 'settling'
        AND wd.settlement_id IS NULL
      GROUP BY p.id, p.name, p.created_at, cp.id, cp.name, cp.unit
      ORDER BY p.id, cp.id
    `, [userId]);
    result.queryResult = queryResult.rows;

    ctx.success(result);

  } catch (error) {
    logger.error('诊断失败:', error);
    ctx.fail(5001, '诊断失败');
  }
};

module.exports = {
  diagnose
};
