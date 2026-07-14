const pool = require('../config/database');
const { createSettlementSnapshot } = require('./settlements');
const { isAdmin, isDocumenter, isConstructor } = require('../middleware/rbac');
const errorCodes = require('../config/error-codes');
const logger = require('../config/logger');
// 引入计算服务，统一使用 calculateUserWage 计算个人分摊金额
const calculation = require('../services/calculation');

const getConstructionPlans = async (ctx) => {
  try {
    const result = await pool.query(`
      SELECT id, name, unit, price 
      FROM construction_plans 
      ORDER BY id
    `);

    ctx.success(result.rows);
  } catch (error) {
    logger.error('获取施工方案失败:', error);
    ctx.fail(5001, '获取施工方案失败');
  }
};

const getProjects = async (ctx) => {
  try {
    const userId = ctx.state.user?.id;

    // 查询子项目明细数据（包含空间类型）
    const subprojectsResult = await pool.query(`
      SELECT
        p.id as project_id,
        p.name as project_name,
        p.created_at,
        p.salary_distribution,
        sp.id as subproject_id,
        sp.quantity,
        sp.length,
        sp.width,
        sp.amount,
        st.id as space_type_id,
        st.name as space_type_name,
        cp.id as plan_id,
        cp.name as plan_name,
        cp.unit,
        cp.price,
        (SELECT COUNT(DISTINCT pw.user_id) FROM project_workers pw WHERE pw.project_id = p.id) as worker_count
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $1
      INNER JOIN subprojects sp ON p.id = sp.project_id
      LEFT JOIN space_types st ON sp.space_type_id = st.id
      LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      WHERE pus.settlement_status = 'settling'
      ORDER BY p.id, cp.id, sp.id
    `, [userId]);

    // 查询当前用户在各工程的工日数（用于 work_days 分配方式计算个人分摊金额）
    const projectIdSet = new Set(subprojectsResult.rows.map(r => r.project_id));
    const projectIds = Array.from(projectIdSet);
    const userWorkdays = {};
    const totalWorkdays = {};
    if (projectIds.length > 0) {
      const userWorkdaysResult = await pool.query(`
        SELECT pw.project_id, COALESCE(pw.workdays, 1) as user_workdays
        FROM project_workers pw
        WHERE pw.project_id = ANY($1) AND pw.user_id = $2
      `, [projectIds, userId]);
      userWorkdaysResult.rows.forEach(row => {
        userWorkdays[row.project_id] = row.user_workdays;
      });

      const totalWorkdaysResult = await pool.query(`
        SELECT pw.project_id, SUM(COALESCE(pw.workdays, 1)) as total_workdays
        FROM project_workers pw
        WHERE pw.project_id = ANY($1)
        GROUP BY pw.project_id
      `, [projectIds]);
      totalWorkdaysResult.rows.forEach(row => {
        totalWorkdays[row.project_id] = row.total_workdays;
      });
    }

    // 按工程分组，构建工程列表和子项目明细
    const projectMap = new Map();
    const planTotals = {};

    subprojectsResult.rows.forEach(row => {
      const projectId = row.project_id;
      const planId = row.plan_id;
      const workerCount = row.worker_count || 1;

      // 计算子项目数量（根据单位类型）
      let subprojectQuantity = parseFloat(row.quantity) || 0;
      const subprojectAmount = parseFloat(row.amount) || 0;

      // 统一使用 calculateUserWage 计算个人分摊数量和金额（与 calculateSettlementPreview 保持一致）
      // 修复：原逻辑仅处理 average 分配方式，work_days 方式直接使用工程总量导致与选择后计算结果不一致
      const userWorkday = userWorkdays[projectId] || 0;
      const totalWorkday = totalWorkdays[projectId] || 1;
      const { userQuantity: rawUserQuantity, userAmount } = calculation.calculateUserWage(
        subprojectAmount,
        subprojectQuantity,
        row.salary_distribution,
        workerCount,
        userWorkday,
        totalWorkday
      );
      let userQuantity = Math.round(rawUserQuantity * 100) / 100; // 保留两位小数
      
      // 初始化工程
      if (!projectMap.has(projectId)) {
        projectMap.set(projectId, {
          id: projectId,
          project_name: row.project_name,
          created_at: row.created_at,
          salary_distribution: row.salary_distribution,
          subprojects: [],
          planQuantities: {}  // 按施工方案汇总的数量
        });
      }
      
      const project = projectMap.get(projectId);
      
      // 添加子项目明细
      if (planId) {
        // 金额直接使用 calculateUserWage 返回的 userAmount，避免 userQuantity*price 的浮点误差
        const roundedUserAmount = Math.round(userAmount * 10000) / 10000; // 保留4位小数，与 NUMERIC(14,4) 对齐
        project.subprojects.push({
          subproject_id: row.subproject_id,
          space_type_name: row.space_type_name,
          plan_id: planId,
          plan_name: row.plan_name,
          unit: row.unit,
          price: row.price,
          quantity: subprojectQuantity,
          user_quantity: userQuantity,
          user_amount: roundedUserAmount
        });

        // 累加施工方案汇总
        if (!project.planQuantities[planId]) {
          project.planQuantities[planId] = {
            total_quantity: 0,
            total_amount: 0,
            unit: row.unit,
            price: row.price,
            plan_name: row.plan_name
          };
        }
        project.planQuantities[planId].total_quantity += userQuantity;
        project.planQuantities[planId].total_amount += roundedUserAmount;

        // 累加施工方案总计
        const planIdStr = String(planId);
        if (!planTotals[planIdStr]) {
          planTotals[planIdStr] = {
            total_quantity: 0,
            total_amount: 0
          };
        }
        planTotals[planIdStr].total_quantity += userQuantity;
        planTotals[planIdStr].total_amount += roundedUserAmount;
      }
    });

    // 转换为数组
    const projects = Array.from(projectMap.values());

    const grandTotal = Object.values(planTotals).reduce((sum, pt) => sum + pt.total_amount, 0);

    const advancesResult = await pool.query(`
      SELECT 
        wa.id,
        wa.user_id,
        u.username,
        u.nickname,
        wa.advance_amount,
        wa.advance_date,
        wa.remark
      FROM wage_advances wa
      LEFT JOIN users u ON wa.user_id = u.id
      WHERE wa.settled = false AND wa.user_id = $1
      ORDER BY wa.advance_date DESC
    `, [userId]);

    const advances = advancesResult.rows;
    const totalAdvance = advances.reduce((sum, a) => sum + parseFloat(a.advance_amount), 0);

    ctx.success({
      projects,
      plan_totals: planTotals,
      grand_total: grandTotal,
      total_advance: totalAdvance,
      advances,
      final_total: grandTotal - totalAdvance
    });
  } catch (error) {
    logger.error('获取工程数据失败:', error);
    ctx.fail(5001, '获取工程数据失败');
  }
};

const getAdvances = async (ctx) => {
  try {
    const userId = ctx.state.user?.id;

    if (!userId) {
      ctx.fail(4001, '用户未登录');
      return;
    }

    const result = await pool.query(`
      SELECT 
        wa.id,
        wa.user_id,
        u.username,
        u.nickname,
        wa.advance_amount,
        wa.advance_date,
        wa.remark
      FROM wage_advances wa
      LEFT JOIN users u ON wa.user_id = u.id
      WHERE wa.settled = false AND wa.user_id = $1
      ORDER BY wa.advance_date DESC
    `, [userId]);

    ctx.success(result.rows);
  } catch (error) {
    logger.error('获取预支记录失败:', error);
    ctx.fail(5001, '获取预支记录失败');
  }
};

const settle = async (ctx) => {
  const client = await pool.connect();
  try {
    const { projectIds } = ctx.request.body;
    const userId = ctx.state.user?.id;
    const user = ctx.state.user;

    if (!userId) {
      ctx.fail(4001, '用户未登录');
      return;
    }

    if (!projectIds || projectIds.length === 0) {
      ctx.fail(1001, '请选择要结算的工程');
      return;
    }

    await client.query('BEGIN');

    // 检查用户是否参与了所有要结算的工程（管理员需要是工程施工人员）
    const workerCheckResult = await client.query(`
      SELECT pw.project_id
      FROM project_workers pw
      WHERE pw.project_id = ANY($1) AND pw.user_id = $2
    `, [projectIds, userId]);

    const userProjectIds = workerCheckResult.rows.map(r => r.project_id);
    const isProjectWorker = userProjectIds.length > 0;

    // 权限检查：资料员不能结算，管理员需要是工程施工人员才能结算
    if (isDocumenter(user)) {
      await client.query('ROLLBACK');
      ctx.fail(4002, '资料员只能查看和新建工程，无法进行结算');
      return;
    }

    if (isAdmin(user) && !isProjectWorker) {
      await client.query('ROLLBACK');
      ctx.fail(4002, '管理员需要是工程施工人员才能进行结算');
      return;
    }

    if (!isConstructor(user) && !isProjectWorker) {
      await client.query('ROLLBACK');
      ctx.fail(4002, '只有工程施工人员才能进行结算');
      return;
    }

    // 检查用户是否参与了所有要结算的工程
    const missingProjects = projectIds.filter(id => !userProjectIds.includes(id));
    if (missingProjects.length > 0) {
      await client.query('ROLLBACK');
      ctx.fail(4002, `您未参与以下工程: ${missingProjects.join(', ')}`);
      return;
    }

    // 查询可结算的工程（用户必须是工程施工人员）
    const projectResult = await client.query(`
      SELECT 
        p.id,
        p.name,
        p.created_at,
        p.salary_distribution
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $2
      INNER JOIN project_workers pw ON p.id = pw.project_id AND pw.user_id = $2
      WHERE p.id = ANY($1) 
        AND pus.settlement_status = 'settling'
    `, [projectIds, userId]);

    if (projectResult.rows.length === 0) {
      await client.query('ROLLBACK');
      ctx.fail(1001, '没有可结算的工程');
      return;
    }

    const projects = projectResult.rows;

    // 为所有工程创建一个结算记录
    const allSettlements = [];
    const dates = projects.map(p => p.created_at);
    const startDate = new Date(Math.min(...dates));
    const endDate = new Date(Math.max(...dates));

    // 格式化日期为 YYYY-MM-DD 格式
    const formatDate = (date) => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };

    const formattedStartDate = formatDate(startDate);
    const formattedEndDate = formatDate(endDate);

    // 生成结算单号：年月日_用户ID，如20260301_7
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    
    // 查询当天该用户已有结算单数量，用于生成序号
    const countResult = await client.query(`
      SELECT COUNT(*) as count 
      FROM wage_settlements 
      WHERE settled_by = $1 
        AND DATE(settled_at) = CURRENT_DATE
    `, [userId]);
    const seq = String(parseInt(countResult.rows[0].count) + 1).padStart(2, '0');
    
    const settlementNo = `${year}${month}${day}_${userId}_${seq}`;

    // 获取每个工程的施工人员（按工程分组），包含工日数
    const projectWorkersResult = await client.query(`
      SELECT 
        pw.project_id,
        u.id, 
        u.username, 
        u.nickname, 
        COALESCE(pw.workdays, 1) as workdays
      FROM users u
      JOIN project_workers pw ON u.id = pw.user_id
      WHERE pw.project_id = ANY($1)
    `, [projects.map(p => p.id)]);

    // 按工程ID分组施工人员
    const workersByProject = {};
    for (const row of projectWorkersResult.rows) {
      if (!workersByProject[row.project_id]) {
        workersByProject[row.project_id] = [];
      }
      workersByProject[row.project_id].push({
        id: row.id,
        username: row.username,
        nickname: row.nickname,
        workdays: parseFloat(row.workdays)
      });
    }

    // 获取所有参与工程的施工人员（去重）
    const allWorkersMap = new Map();
    for (const row of projectWorkersResult.rows) {
      if (!allWorkersMap.has(row.id)) {
        allWorkersMap.set(row.id, {
          id: row.id,
          username: row.username,
          nickname: row.nickname
        });
      }
    }
    const allWorkers = Array.from(allWorkersMap.values());

    if (allWorkers.length === 0) {
      await client.query('ROLLBACK');
      ctx.fail(3012, '没有施工人员');
      return;
    }

    // 批量查询所有工程的子项目（优化：避免N+1查询）
    const allSubprojectsResult = await client.query(`
      SELECT 
        sp.id,
        sp.project_id,
        sp.amount,
        sp.quantity,
        sp.construction_plan_id
      FROM subprojects sp
      WHERE sp.project_id = ANY($1)
    `, [projects.map(p => p.id)]);

    // 按工程ID分组子项目
    const subprojectsByProject = {};
    for (const sp of allSubprojectsResult.rows) {
      if (!subprojectsByProject[sp.project_id]) {
        subprojectsByProject[sp.project_id] = [];
      }
      subprojectsByProject[sp.project_id].push(sp);
    }

    // 计算所有工程的总金额（每个工程独立计算工日）
    let totalAmount = 0;
    const wageDistributionValues = [];

    for (const project of projects) {
      const subprojects = subprojectsByProject[project.id] || [];
      const projectWorkers = workersByProject[project.id] || [];
      
      if (projectWorkers.length === 0) {
        continue;
      }

      // 计算该工程的总金额
      const projectTotalAmount = subprojects.reduce((sum, sp) => sum + parseFloat(sp.amount || 0), 0);

      // 计算该工程的总工日数
      const projectTotalWorkdays = projectWorkers.reduce((sum, w) => sum + w.workdays, 0);

      // 为该工程的每个施工人员分配工资
      for (const worker of projectWorkers) {
        let workerAmount = 0;

        if (project.salary_distribution === 'average') {
          // 平均分配
          workerAmount = projectTotalAmount / projectWorkers.length;
        } else if (project.salary_distribution === 'work_days') {
          // 按工日比例分配
          workerAmount = projectTotalAmount * (worker.workdays / projectTotalWorkdays);
        }

        totalAmount += workerAmount;

        // 为每个子项目创建工资分配记录
        for (const subproject of subprojects) {
          let subprojectWorkerAmount = 0;
          let subprojectWorkerQuantity = 0;

          if (project.salary_distribution === 'average') {
            subprojectWorkerAmount = parseFloat(subproject.amount) / projectWorkers.length;
            subprojectWorkerQuantity = parseFloat(subproject.quantity) / projectWorkers.length;
          } else if (project.salary_distribution === 'work_days') {
            subprojectWorkerAmount = parseFloat(subproject.amount) * (worker.workdays / projectTotalWorkdays);
            subprojectWorkerQuantity = parseFloat(subproject.quantity) * (worker.workdays / projectTotalWorkdays);
          }

          wageDistributionValues.push([
            subproject.id,
            worker.id,
            worker.workdays,
            subprojectWorkerAmount,
            subprojectWorkerQuantity,
            null // settlement_id 稍后填入
          ]);
        }
      }
    }

    // 只获取当前结算用户的未结算预支记录（每个用户独立结算）
    const userAdvancesResult = await client.query(`
      SELECT id, user_id, advance_amount
      FROM wage_advances
      WHERE user_id = $1 AND settled = false
    `, [userId]);

    const userAdvances = userAdvancesResult.rows;
    const advanceAmount = userAdvances.reduce((sum, a) => sum + parseFloat(a.advance_amount), 0);

    const actualAmount = totalAmount - advanceAmount;

    // 创建结算记录（工程ID去重）
    const projectIdsArray = [...new Set(projects.map(p => p.id))];
    const settlementResult = await client.query(`
      INSERT INTO wage_settlements (
        settlement_no, 
        project_id,
        project_ids,
        user_id,
        start_month, 
        end_month, 
        total_amount, 
        advance_amount, 
        actual_amount, 
        confirmed,
        confirmed_at,
        settled_by
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, CURRENT_TIMESTAMP, $11)
      RETURNING id
    `, [settlementNo, projectIdsArray[0], JSON.stringify(projectIdsArray), userId, formattedStartDate, formattedEndDate, totalAmount, advanceAmount, actualAmount, true, userId]);

    const settlementId = settlementResult.rows[0].id;

    // 更新工资分配记录中的settlement_id
    for (let i = 0; i < wageDistributionValues.length; i++) {
      wageDistributionValues[i][5] = settlementId;
    }

    // 批量插入工资分配记录
    if (wageDistributionValues.length > 0) {
      // 分批插入，每批1000条，避免单次SQL过大
      const batchSize = 1000;
      for (let i = 0; i < wageDistributionValues.length; i += batchSize) {
        const batch = wageDistributionValues.slice(i, i + batchSize);
        
        // 构建批量插入SQL
        const placeholders = batch.map((_, idx) => 
          `($${idx * 6 + 1}, $${idx * 6 + 2}, $${idx * 6 + 3}, $${idx * 6 + 4}, $${idx * 6 + 5}, $${idx * 6 + 6}, CURRENT_TIMESTAMP)`
        ).join(', ');
        
        const flatValues = batch.flat();
        
        await client.query(`
          INSERT INTO wage_distributions (subproject_id, user_id, workdays, amount, quantity, settlement_id, created_at)
          VALUES ${placeholders}
        `, flatValues);
      }
    }

    // 只更新当前结算用户的预支记录为已结算（每个用户独立结算）
    if (userAdvances.length > 0) {
      await client.query(`
        UPDATE wage_advances 
        SET settled = true, 
            settlement_id = $1 
        WHERE id = ANY($2)
      `, [settlementId, userAdvances.map(a => a.id)]);
    }

    allSettlements.push({
      settlementId,
      settlementNo,
      projectId: projectIdsArray[0],
      projectName: projects.map(p => p.name).join('、'),
      userId,
      username: allWorkers.map(w => w.nickname || w.username).join('、'),
      nickname: allWorkers.map(w => w.nickname || w.username).join('、'),
      totalAmount,
      advanceAmount,
      actualAmount
    });

    // 只为当前结算用户创建结算历史快照（每个用户独立结算）
    await createSettlementSnapshot(settlementId, userId, client);

    // 只更新当前结算用户的结算状态为已结算（每个用户独立结算）
    await client.query(`
      INSERT INTO project_user_status (project_id, user_id, settlement_status, settlement_id, settled_at, created_at, updated_at)
      VALUES ${projectIdsArray.map((_, idx) => `($${idx + 1}, $${projectIdsArray.length + 1}, 'settled', $${projectIdsArray.length + 2}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`).join(', ')}
      ON CONFLICT (project_id, user_id) 
      DO UPDATE SET 
        settlement_status = 'settled',
        settlement_id = EXCLUDED.settlement_id,
        settled_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    `, [...projectIdsArray, userId, settlementId]);

    await client.query('COMMIT');

    ctx.success({
      settlement_id: allSettlements.length > 0 ? allSettlements[0].settlementId : null,
      settlement_no: allSettlements.length > 0 ? allSettlements[0].settlementNo : '',
      total_amount: allSettlements.reduce((sum, s) => sum + s.totalAmount, 0),
      advance_amount: allSettlements.reduce((sum, s) => sum + s.advanceAmount, 0),
      actual_amount: allSettlements.reduce((sum, s) => sum + s.actualAmount, 0),
      settlements: allSettlements
    });
  } catch (error) {
    // 事务回滚
    if (client) {
      try {
        await client.query('ROLLBACK');
        logger.info('[结算] 事务已回滚');
      } catch (rollbackErr) {
        logger.error('[结算] 事务回滚失败:', rollbackErr);
      }
    }
    
    // 详细错误日志
    logger.error('[结算] 结算失败:', {
      error: error.message,
      code: error.code,
      stack: error.stack,
      userId: ctx.state.user?.id,
      projectIds: ctx.request.body?.projectIds
    });
    
    // 使用统一错误码管理（符合架构文档设计原则）
    const errorCode = errorCodes.getPgErrorCode(error);
    const errorMessage = errorCodes.getPgErrorMessage(error);
    ctx.fail(errorCode, errorMessage);
  } finally {
    // 释放数据库连接
    if (client) {
      try {
        client.release();
        logger.info('[结算] 数据库连接已释放');
      } catch (releaseErr) {
        logger.error('[结算] 释放数据库连接失败:', releaseErr);
      }
    }
  }
};

const getSettledProjects = async (ctx) => {
  try {
    const userId = ctx.state.user?.id;

    if (!userId) {
      ctx.fail(4001, '用户未登录');
      return;
    }

    const result = await pool.query(`
      SELECT 
        p.id,
        p.name as project_name,
        TO_CHAR(p.created_at, 'YYYY-MM-DD HH24:MI') as created_at,
        cp.id as plan_id,
        cp.name as plan_name,
        cp.unit,
        COALESCE(SUM(wd.amount), 0) as user_amount,
        CASE 
          WHEN p.salary_distribution = 'average' THEN 
            ROUND(COALESCE(SUM(
              CASE 
                WHEN cp.unit = 'area' THEN sp.quantity
                WHEN cp.unit = 'length' THEN sp.length
                WHEN cp.unit = 'perimeter' THEN (sp.length + sp.width) * 2
                ELSE sp.quantity
              END
            ), 0) / COUNT(DISTINCT pw.user_id), 2)
          WHEN p.salary_distribution = 'work_days' THEN
            ROUND(COALESCE(SUM(
              CASE 
                WHEN cp.unit = 'area' THEN sp.quantity
                WHEN cp.unit = 'length' THEN sp.length
                WHEN cp.unit = 'perimeter' THEN (sp.length + sp.width) * 2
                ELSE sp.quantity
              END
            ), 0) * wd.workdays / (SUM(wd.workdays) OVER (PARTITION BY p.id, cp.id)), 2)
          ELSE 
            ROUND(COALESCE(SUM(
              CASE 
                WHEN cp.unit = 'area' THEN sp.quantity
                WHEN cp.unit = 'length' THEN sp.length
                WHEN cp.unit = 'perimeter' THEN (sp.length + sp.width) * 2
                ELSE sp.quantity
              END
            ), 0) / COUNT(DISTINCT pw.user_id), 2)
        END as user_quantity
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settled'
      INNER JOIN subprojects sp ON p.id = sp.project_id
      INNER JOIN wage_distributions wd ON sp.id = wd.subproject_id AND wd.user_id = $1
      LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      GROUP BY p.id, p.name, p.created_at, p.salary_distribution, cp.id, cp.name, cp.unit, wd.workdays
      ORDER BY p.id, cp.id
    `, [userId]);

    // 显式将 NUMERIC 类型字段转为 float，避免 pg 类型解析器未生效时返回字符串
    // 导致前端 Kotlin Double 解析失败变为 0
    const formattedRows = result.rows.map(row => ({
      ...row,
      user_amount: parseFloat(row.user_amount || 0),
      user_quantity: parseFloat(row.user_quantity || 0)
    }));

    ctx.success(formattedRows);
  } catch (error) {
    logger.error('获取已结算工程数据失败:', error);
    ctx.fail(5001, '获取已结算工程数据失败');
  }
};

const getSettledAdvances = async (ctx) => {
  try {
    const userId = ctx.state.user?.id;

    if (!userId) {
      ctx.fail(4001, '用户未登录');
      return;
    }

    const result = await pool.query(`
      SELECT 
        wa.id,
        wa.user_id,
        u.username,
        u.nickname,
        wa.advance_amount,
        wa.advance_date,
        wa.remark
      FROM wage_advances wa
      LEFT JOIN users u ON wa.user_id = u.id
      WHERE wa.settled = true AND wa.user_id = $1
      ORDER BY wa.advance_date DESC
    `, [userId]);

    ctx.success(result.rows);
  } catch (error) {
    logger.error('获取已结算预支记录失败:', error);
    ctx.fail(5001, '获取已结算预支记录失败');
  }
};

const getSettlementHistory = async (ctx) => {
  try {
    const userId = ctx.state.user?.id;

    if (!userId) {
      ctx.fail(4001, '用户未登录');
      return;
    }

    // 直接从快照表读取数据，提升查询性能
    const result = await pool.query(`
      SELECT 
        id,
        settlement_id,
        settlement_no,
        project_id,
        project_name,
        user_id,
        username,
        nickname,
        start_month,
        end_month,
        total_amount,
        advance_amount,
        actual_amount,
        confirmed,
        confirmed_at,
        settled_by,
        settled_by_username,
        settled_by_nickname,
        settled_at,
        remark,
        projects_snapshot,
        plans_snapshot,
        advances_snapshot,
        calculation_snapshot
      FROM wage_settlement_snapshots
      WHERE user_id = $1
      ORDER BY settled_at DESC
    `, [userId]);

    const snapshots = result.rows;

    // 直接返回每个结算历史快照，一份结算单对应一份结算历史快照
    const settlements = snapshots.map(snapshot => {
      const calculation = snapshot.calculation_snapshot || {};
      const planTotals = calculation.planTotals || {};

      // 将planTotals中的数值转换为数字类型
      for (const planId in planTotals) {
        if (planTotals[planId]) {
          planTotals[planId].total_quantity = parseFloat(planTotals[planId].total_quantity) || 0;
          planTotals[planId].total_amount = parseFloat(planTotals[planId].total_amount) || 0;
        }
      }

      const projectsData = snapshot.projects_snapshot || [];
      
      // 按工程分组，构建工程列表（包含子项目明细）
      const projectMap = new Map();
      projectsData.forEach((item) => {
        const projectId = item.id;
        if (!projectMap.has(projectId)) {
          projectMap.set(projectId, {
            id: projectId,
            project_name: item.project_name,
            created_at: item.created_at,
            subprojects: [],
            planQuantities: {}
          });
        }
        
        const project = projectMap.get(projectId);
        
        // 添加子项目明细
        if (item.plan_id) {
          project.subprojects.push({
            subproject_id: item.subproject_id || item.id,
            space_type_name: item.space_type_name || '',
            plan_id: item.plan_id,
            plan_name: item.plan_name,
            unit: item.unit,
            price: item.price,
            quantity: item.quantity || item.user_quantity,
            user_quantity: item.user_quantity,
            user_amount: parseFloat(item.user_amount) || 0
          });
          
          // 累加施工方案汇总
          if (!project.planQuantities[item.plan_id]) {
            project.planQuantities[item.plan_id] = {
              total_quantity: 0,
              total_amount: 0,
              unit: item.unit,
              price: item.price,
              plan_name: item.plan_name
            };
          }
          project.planQuantities[item.plan_id].total_quantity += item.user_quantity || 0;
          // 修复金额差异Bug：改用快照中已存储的 user_amount 直接累加，避免 quantity*price 反算精度损失
          project.planQuantities[item.plan_id].total_amount += parseFloat(item.user_amount) || 0;
        }
      });
      
      const projects = Array.from(projectMap.values());

      return {
        settlement_id: snapshot.settlement_id,
        settlement_no: snapshot.settlement_no,
        start_month: snapshot.start_month,
        end_month: snapshot.end_month,
        total_amount: parseFloat(snapshot.total_amount),
        advance_amount: parseFloat(snapshot.advance_amount),
        actual_amount: parseFloat(snapshot.actual_amount),
        confirmed: snapshot.confirmed,
        confirmed_at: snapshot.confirmed_at,
        settled_by: snapshot.settled_by,
        settled_by_username: snapshot.settled_by_username,
        settled_by_nickname: snapshot.settled_by_nickname,
        settled_at: snapshot.settled_at,
        remark: snapshot.remark,
        projects: projects,
        advances: snapshot.advances_snapshot || [],
        plan_totals: planTotals,
        grand_total: calculation.grandTotal || 0,
        total_advance: calculation.totalAdvance || 0,
        final_total: calculation.finalTotal || 0
      };
    });

    ctx.success(settlements);
  } catch (error) {
    logger.error('获取结算历史失败:', error);
    ctx.fail(5001, '获取结算历史失败');
  }
};

module.exports = {
  getConstructionPlans,
  getProjects,
  getAdvances,
  settle,
  getSettledProjects,
  getSettledAdvances,
  getSettlementHistory
};
