/**
 * 结算业务逻辑层 (Service)
 *
 * 从 controllers/settlements.js 和 controllers/salarySheet.js 中抽离结算相关的业务逻辑，负责：
 * - 业务规则校验（权限、数据完整性、重复检查）
 * - 调用 settlementRepo 进行数据访问
 * - 调用 calculation 进行金额计算
 * - 调用 cacheService 进行缓存管理
 * - 业务异常通过 BusinessError 抛出，由 controller 捕获
 */

const settlementRepo = require('../repositories/settlementRepo');
const calculation = require('./calculation');
const cache = require('./cacheService');
const logger = require('../config/logger');
const pool = require('../config/database');

/**
 * 异步刷新物化视图（不阻塞当前操作，失败只记录日志）
 * 结算/确认操作后调用，确保结算状态数据在5分钟内（或立即）更新
 */
let refreshMvAsync;
try {
  const { refreshMaterializedView } = require('../scripts/refresh-mv');
  refreshMvAsync = () => {
    refreshMaterializedView().catch(err => {
      logger.warn('结算后刷新物化视图失败', { error: err.message });
    });
  };
} catch (e) {
  // refresh-mv.js 不存在时降级为空函数
  refreshMvAsync = () => {};
}

/**
 * 业务异常类
 * Controller 层捕获后调用 ctx.fail(error.code, error.message)
 */
class BusinessError extends Error {
  /**
   * @param {number} code - 业务错误码（对应 error-codes.js）
   * @param {string} message - 错误描述
   */
  constructor(code, message) {
    super(message);
    this.code = code;
    this.name = 'BusinessError';
  }
}

// ========== 内部辅助函数 ==========

/**
 * 检查用户是否参与了指定工程
 * @param {number} projectId - 工程ID
 * @param {number} userId - 用户ID
 * @returns {Promise<boolean>}
 */
const checkProjectParticipant = async (projectId, userId) => {
  const result = await pool.query(
    'SELECT id FROM project_workers WHERE project_id = $1 AND user_id = $2',
    [projectId, userId]
  );
  return result.rows.length > 0;
};

/**
 * 查询子项目信息（含所属工程ID）
 * @param {number} subprojectId - 子项目ID
 * @returns {Promise<object|null>} 子项目记录
 */
const findSubprojectWithProject = async (subprojectId) => {
  const result = await pool.query(
    'SELECT sp.*, p.id as project_id FROM subprojects sp JOIN projects p ON sp.project_id = p.id WHERE sp.id = $1',
    [subprojectId]
  );
  return result.rows[0] || null;
};

/**
 * 查询用户信息
 * @param {number} userId - 用户ID
 * @returns {Promise<object|null>} 用户记录
 */
const findUserById = async (userId) => {
  const result = await pool.query('SELECT id, nickname, username FROM users WHERE id = $1', [userId]);
  return result.rows[0] || null;
};

/**
 * 获取工程施工人员列表
 * @param {number} projectId - 工程ID
 * @returns {Promise<Array>} 施工人员列表
 */
const getProjectWorkers = async (projectId) => {
  const result = await pool.query(
    `SELECT u.id, u.username, u.nickname, COALESCE(pw.workdays, 1) as workdays
     FROM users u
     JOIN project_workers pw ON u.id = pw.user_id
     WHERE pw.project_id = $1`,
    [projectId]
  );
  return result.rows;
};

/**
 * 清除结算相关缓存
 * @param {number} [userId] - 用户ID，传入则清除该用户的结算缓存
 */
const invalidateCache = async (userId) => {
  try {
    await cache.delByPrefix(`settlements:${userId || '*'}`);
    await cache.delByPrefix('statistics:');
  } catch (error) {
    logger.warn('清除结算缓存失败', { userId, error: error.message });
  }
};

/**
 * 创建结算历史快照
 * 查询结算记录、工资分配、预支记录后组装快照数据并插入快照表
 * @param {number} settlementId - 结算ID
 * @param {number} currentUserId - 当前用户ID
 * @param {object|null} client - pg Client 实例（事务场景）
 * @returns {Promise<void>}
 */
const createSettlementSnapshot = async (settlementId, currentUserId, client = null) => {
  try {
    const query = client ? client.query.bind(client) : pool.query.bind(pool);

    // 1. 查询结算记录基本信息
    const settlementResult = await query(`
      SELECT
        ws.id,
        ws.settlement_no,
        ws.project_id,
        ws.project_ids,
        ws.user_id,
        u.username,
        u.nickname,
        ws.start_month,
        ws.end_month,
        ws.total_amount,
        ws.advance_amount,
        ws.actual_amount,
        ws.confirmed,
        ws.confirmed_at,
        ws.settled_by,
        su.username as settled_by_username,
        su.nickname as settled_by_nickname,
        ws.settled_at,
        ws.remark
      FROM wage_settlements ws
      LEFT JOIN users u ON ws.user_id = u.id
      LEFT JOIN users su ON ws.settled_by = su.id
      WHERE ws.id = $1
    `, [settlementId]);

    if (settlementResult.rows.length === 0) {
      return;
    }

    const settlement = settlementResult.rows[0];

    // 解析工程ID列表
    let projectIds;
    try {
      if (settlement.project_ids && Array.isArray(settlement.project_ids) && settlement.project_ids.length > 0) {
        projectIds = settlement.project_ids;
      } else if (settlement.project_ids && typeof settlement.project_ids === 'string' && settlement.project_ids.trim() !== '' && settlement.project_ids !== 'null') {
        projectIds = JSON.parse(settlement.project_ids);
      } else {
        projectIds = [settlement.project_id];
      }
    } catch (error) {
      projectIds = [settlement.project_id];
    }

    // 工程ID去重
    projectIds = [...new Set(projectIds)];

    // 2. 查询工资分配记录，获取当前用户在每个子项目的分配金额和工日
    const wdResult = await query(`
      SELECT
        sp.id as subproject_id,
        sp.project_id,
        p.name as project_name,
        p.created_at,
        sp.quantity,
        sp.length,
        sp.width,
        sp.construction_plan_id,
        st.id as space_type_id,
        st.name as space_type_name,
        cp.id as plan_id,
        cp.name as plan_name,
        cp.unit,
        cp.price,
        p.salary_distribution,
        wd.amount as user_amount,
        wd.workdays as user_workdays,
        wd.quantity as user_quantity
      FROM wage_distributions wd
      INNER JOIN subprojects sp ON wd.subproject_id = sp.id
      INNER JOIN projects p ON sp.project_id = p.id
      LEFT JOIN space_types st ON sp.space_type_id = st.id
      LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      WHERE wd.settlement_id = $1 AND wd.user_id = $2 AND p.id = ANY($3)
      ORDER BY sp.project_id, sp.id
    `, [settlementId, currentUserId, projectIds]);

    // 根据子项目构建工程快照
    const projectsSnapshot = [];
    const planTotals = {};

    for (const project of projectIds) {
      const projectWDs = wdResult.rows.filter(wd => wd.project_id === project);

      if (projectWDs.length === 0) continue;

      const projectInfo = projectWDs[0];

      for (const wd of projectWDs) {
        const planId = wd.plan_id;

        // 直接使用 wage_distributions 表中已保存的 quantity 字段
        let userQuantity = parseFloat(wd.user_quantity) || 0;

        // 跳过没有施工方案的子项目
        if (!planId) {
          continue;
        }

        // 添加到工程快照（每个施工方案一条记录）
        projectsSnapshot.push({
          id: project,
          project_name: projectInfo.project_name,
          created_at: projectInfo.created_at,
          subproject_id: wd.subproject_id,
          space_type_name: wd.space_type_name || '',
          plan_id: planId,
          plan_name: wd.plan_name,
          unit: wd.unit,
          price: wd.price,
          user_amount: wd.user_amount,
          user_quantity: userQuantity
        });

        // 累加施工方案汇总
        const planIdStr = String(planId);
        if (!planTotals[planIdStr]) {
          planTotals[planIdStr] = {
            totalQuantity: 0,
            totalAmount: 0
          };
        }
        planTotals[planIdStr].totalQuantity += userQuantity;
        // 修复金额差异Bug：改用 wd.amount 直接累加（NUMERIC(14,4) 高精度且已经是最终分摊金额）
        // 原代码 userQuantity * wd.price 会因 wage_distributions.quantity 为 NUMERIC(10,2)
        // 存储时被四舍五入丢失精度，累积后与工程管理页人均工费出现差异（如27.52元）
        planTotals[planIdStr].totalAmount += parseFloat(wd.user_amount) || 0;
      }
    }

    // 3. 查询所有施工方案数据快照
    const plansResult = await query(`
      SELECT
        cp.id,
        cp.name,
        cp.unit,
        cp.price
      FROM construction_plans cp
      ORDER BY cp.id
    `);

    const plansSnapshot = plansResult.rows;

    // 4. 查询预支记录数据快照
    const advancesResult = await query(`
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
      WHERE wa.settlement_id = $1
      ORDER BY wa.advance_date DESC
    `, [settlementId]);

    const advancesSnapshot = advancesResult.rows;

    // 5. 计算结果快照
    const grandTotal = Object.values(planTotals).reduce((sum, pt) => sum + pt.totalAmount, 0);

    // 计算当前用户的预支金额
    const userAdvancesSnapshot = advancesSnapshot.filter(a => a.user_id === currentUserId);
    const totalAdvance = userAdvancesSnapshot.reduce((sum, a) => sum + parseFloat(a.advance_amount), 0);

    // 当前用户的实际金额：允许为负数（预支可超出工程总额，反映真实欠款情况）
    const userActualAmount = grandTotal - totalAdvance;
    if (totalAdvance > grandTotal) {
      logger.warn(`[结算快照] 预支总额(${totalAdvance})超过工程总额(${grandTotal})，实付金额为负数`, { settlementId, currentUserId });
    }

    const calculationSnapshot = {
      planTotals,
      grandTotal,
      totalAdvance,
      finalTotal: userActualAmount
    };

    // 获取所有工程名称
    const projectNames = projectsSnapshot
      .map(p => p.project_name)
      .filter((name, index, self) => self.indexOf(name) === index)
      .join('、');

    // 6. 插入快照数据
    await query(`
      INSERT INTO wage_settlement_snapshots (
        settlement_id, settlement_no, project_id, project_name,
        user_id, username, nickname,
        start_month, end_month,
        total_amount, advance_amount, actual_amount,
        confirmed, confirmed_at,
        settled_by, settled_by_username, settled_by_nickname,
        settled_at, remark,
        projects_snapshot, plans_snapshot, advances_snapshot, calculation_snapshot
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23)
    `, [
      settlement.id,
      settlement.settlement_no,
      projectIds[0],
      projectNames,
      currentUserId,
      settlement.username || '',
      settlement.nickname || '',
      settlement.start_month,
      settlement.end_month,
      grandTotal,
      totalAdvance,
      userActualAmount,
      settlement.confirmed,
      settlement.confirmed_at,
      settlement.settled_by,
      settlement.settled_by_username || '',
      settlement.settled_by_nickname || '',
      settlement.settled_at,
      settlement.remark || '',
      JSON.stringify(projectsSnapshot),
      JSON.stringify(plansSnapshot),
      JSON.stringify(advancesSnapshot),
      JSON.stringify(calculationSnapshot)
    ]);

  } catch (error) {
    logger.error('[快照创建] 创建结算历史快照失败:', error);
    throw error;
  }
};

// ========== 导出的业务方法 ==========

module.exports = {
  /**
   * 创建工资分配记录
   *
   * 业务规则：
   * - 验证子项目存在
   * - 验证用户存在
   * - 施工员需检查是否参与该工程
   * - 检查是否已存在分配记录
   *
   * @param {object} params - 创建参数
   * @param {number} params.subprojectId - 子项目ID
   * @param {number} params.userId - 用户ID
   * @param {number} params.workdays - 工日数
   * @param {number} params.amount - 金额
   * @param {string} [params.remark] - 备注
   * @param {number} params.currentUserId - 当前用户ID
   * @param {string} params.userRole - 当前用户角色
   * @returns {Promise<object>} 创建的工资分配记录
   */
  async createWageDistribution({ subprojectId, userId, workdays, amount, remark, currentUserId, userRole }) {
    // 1. 验证子项目是否存在
    const subproject = await findSubprojectWithProject(subprojectId);
    if (!subproject) {
      throw new BusinessError(3009, '子项目不存在');
    }

    // 2. 验证用户是否存在
    const user = await findUserById(userId);
    if (!user) {
      throw new BusinessError(2004, '用户不存在');
    }

    // 3. 施工员需检查是否参与该工程
    if (userRole === 'constructor') {
      const isParticipant = await checkProjectParticipant(subproject.project_id, currentUserId);
      if (!isParticipant) {
        throw new BusinessError(4002, '您未参与此工程，无法操作');
      }
    }

    // 4. 检查是否已存在该用户在该子项目的工资分配记录
    const existingDistribution = await settlementRepo.findWageDistribution(subprojectId, userId);
    if (existingDistribution) {
      throw new BusinessError(3012, '该用户在该子项目已存在工资分配记录');
    }

    // 5. 创建工资分配记录
    const distribution = await settlementRepo.createWageDistribution({
      subprojectId,
      userId,
      workdays,
      amount,
      remark
    });

    logger.info('创建工资分配记录成功', { subprojectId, userId, currentUserId });

    return distribution;
  },

  /**
   * 批量创建工资分配记录
   *
   * 业务规则：
   * - 验证子项目存在
   * - 施工员需检查是否参与该工程
   * - 获取工程施工人员
   * - 按分配方式计算金额
   *
   * @param {object} params - 批量创建参数
   * @param {number} params.subprojectId - 子项目ID
   * @param {string} params.distributionType - 分配方式（average/work_days）
   * @param {number} params.currentUserId - 当前用户ID
   * @param {string} params.userRole - 当前用户角色
   * @returns {Promise<{message: string, distributions: Array}>} 批量创建结果
   */
  async createBatchWageDistributions({ subprojectId, distributionType, currentUserId, userRole }) {
    // 1. 验证子项目是否存在
    const subproject = await findSubprojectWithProject(subprojectId);
    if (!subproject) {
      throw new BusinessError(3009, '子项目不存在');
    }

    // 2. 施工员需检查是否参与该工程
    if (userRole === 'constructor') {
      const isParticipant = await checkProjectParticipant(subproject.project_id, currentUserId);
      if (!isParticipant) {
        throw new BusinessError(4002, '您未参与此工程，无法操作');
      }
    }

    // 3. 获取该子项目的施工人员
    const workers = await getProjectWorkers(subproject.project_id);

    if (workers.length === 0) {
      throw new BusinessError(3013, '该工程没有施工人员');
    }

    // 4. 计算工资分配
    const distributions = [];
    const totalWorkdays = workers.reduce((sum, w) => sum + parseFloat(w.workdays), 0);

    if (distributionType === 'average') {
      // 平均分配
      const amountPerWorker = subproject.amount / workers.length;
      for (const worker of workers) {
        distributions.push({
          subprojectId,
          userId: worker.id,
          workdays: 1,
          amount: amountPerWorker
        });
      }
    } else if (distributionType === 'work_days') {
      // 按工日分配
      for (const worker of workers) {
        const workerWorkdays = parseFloat(worker.workdays) || 1;
        const amountPerWorker = calculation.calculateUserWage(
          subproject.amount,
          subproject.quantity,
          'work_days',
          workers.length,
          workerWorkdays,
          totalWorkdays
        );
        distributions.push({
          subprojectId,
          userId: worker.id,
          workdays: workerWorkdays,
          amount: amountPerWorker.userAmount
        });
      }
    }

    // 5. 批量插入工资分配记录
    const insertedDistributions = [];
    for (const dist of distributions) {
      const distribution = await settlementRepo.createWageDistribution(dist);
      insertedDistributions.push(distribution);
    }

    logger.info('批量创建工资分配记录成功', { subprojectId, distributionType, count: insertedDistributions.length, currentUserId });

    return {
      message: '批量创建成功',
      distributions: insertedDistributions
    };
  },

  /**
   * 创建结算
   *
   * 业务规则：
   * - 查询指定月份范围内已完工子项目
   * - 施工员只能结算自己参与的工程
   * - 按工程分组，计算工资分配
   * - 创建结算记录
   * - 创建工资分配记录
   * - 更新预支记录结算状态
   * - 添加工程历史记录
   * - 清除缓存
   * 注意：此方法涉及多表操作，需要使用事务
   *
   * @param {object} params - 结算参数
   * @param {string} params.startMonth - 开始月份
   * @param {string} params.endMonth - 结束月份
   * @param {string} [params.remark] - 备注
   * @param {number} params.currentUserId - 当前用户ID
   * @param {string} params.userRole - 当前用户角色
   * @returns {Promise<object>} 结算创建结果
   */
  async createSettlement({ startMonth, endMonth, remark, currentUserId, userRole }) {
    const client = await pool.connect();

    try {
      await client.query('BEGIN');

      // 1. 查询指定月份范围内已完工的子项目
      const subprojects = await settlementRepo.getCompletedSubprojects(
        startMonth,
        endMonth,
        currentUserId,
        userRole
      );

      if (subprojects.length === 0) {
        await client.query('ROLLBACK');
        throw new BusinessError(3011, '指定月份范围内没有可结算的完工子项目');
      }

      // 2. 按工程分组子项目
      const projectSubprojects = {};
      for (const sp of subprojects) {
        if (!projectSubprojects[sp.project_id]) {
          projectSubprojects[sp.project_id] = {
            projectId: sp.project_id,
            projectName: sp.project_name,
            wageDistributionCode: sp.wage_distribution_code,
            subprojects: []
          };
        }
        projectSubprojects[sp.project_id].subprojects.push(sp);
      }

      // 3. 获取所有工程的施工人员（去重），包含工日数
      const projectIds = Object.keys(projectSubprojects).map(id => parseInt(id));
      const allWorkersResult = await client.query(`
        SELECT DISTINCT u.id, u.username, u.nickname, COALESCE(pw.workdays, 1) as workdays
        FROM users u
        JOIN project_workers pw ON u.id = pw.user_id
        WHERE pw.project_id = ANY($1)
      `, [projectIds]);

      const allWorkers = allWorkersResult.rows;

      if (allWorkers.length === 0) {
        await client.query('ROLLBACK');
        throw new BusinessError(3013, '没有施工人员');
      }

      // 4. 计算总工日数
      const totalWorkdays = allWorkers.reduce((sum, w) => sum + parseFloat(w.workdays), 0);

      // 5. 为所有工程计算工资分配
      let totalAmount = 0;
      const allWorkerSubprojects = {};

      for (const worker of allWorkers) {
        const workerSubprojects = [];
        let workerTotalAmount = 0;

        for (const projectId in projectSubprojects) {
          const project = projectSubprojects[projectId];

          for (const sp of project.subprojects) {
            // 调用 calculation 服务计算用户工资分配
            const { userAmount, userQuantity } = calculation.calculateUserWage(
              sp.amount,
              parseFloat(sp.quantity) || 0,
              project.wageDistributionCode,
              allWorkers.length,
              parseFloat(worker.workdays),
              totalWorkdays
            );

            workerTotalAmount += userAmount;
            workerSubprojects.push({
              subprojectId: sp.subproject_id,
              projectName: sp.project_name,
              spaceType: sp.space_type_name,
              constructionPlan: sp.construction_plan_name,
              amount: userAmount,
              quantity: userQuantity
            });
          }
        }

        totalAmount += workerTotalAmount;
        allWorkerSubprojects[worker.id] = {
          worker,
          totalAmount: workerTotalAmount,
          subprojects: workerSubprojects
        };
      }

      // 6. 查询所有施工人员的未结算预支记录
      const allAdvancesResult = await client.query(
        `SELECT id, user_id, advance_amount
         FROM wage_advances
         WHERE user_id = ANY($1) AND settled = false`,
        [allWorkers.map(w => w.id)]
      );
      const allAdvances = allAdvancesResult.rows;

      // 7. 生成结算单号
      const now = new Date();
      const year = now.getFullYear();
      const month = String(now.getMonth() + 1).padStart(2, '0');
      const day = String(now.getDate()).padStart(2, '0');
      const projectIdsStr = projectIds.sort((a, b) => a - b).join('');
      const settlementNo = `${year}${month}${day}${projectIdsStr}`;

      // 8. 创建结算记录
      // Bug8修复：计算实际的预支总额和实付金额（原写死 advanceAmount=0, actualAmount=totalAmount）
      const advanceAmountTotal = allAdvances.reduce((sum, a) => sum + parseFloat(a.advance_amount), 0);
      // 实付金额 = 工程总额 - 预支总额；允许为负数（预支可超出工程总额，反映真实欠款情况）
      const actualAmount = totalAmount - advanceAmountTotal;
      if (advanceAmountTotal > totalAmount) {
        logger.warn(`[结算] 预支总额(${advanceAmountTotal})超过工程总额(${totalAmount})，实付金额为负数`, { currentUserId, projectIds });
      }
      const settlement = await settlementRepo.createSettlement({
        settlementNo,
        projectId: projectIds[0],
        projectIds: JSON.stringify(projectIds),
        userId: currentUserId,
        startMonth,
        endMonth,
        totalAmount,
        advanceAmount: advanceAmountTotal,
        actualAmount,
        confirmed: false,
        settledBy: currentUserId,
        remark
      }, client);

      const settlementId = settlement.id;

      // 9. 为每个施工人员创建工资分配记录
      for (const workerId in allWorkerSubprojects) {
        const workerData = allWorkerSubprojects[workerId];
        const workerWorkdays = parseFloat(workerData.worker.workdays) || 1;

        for (const sp of workerData.subprojects) {
          // 先尝试更新已存在的记录
          const updateCount = await settlementRepo.updateWageDistribution(
            sp.subprojectId,
            parseInt(workerId),
            { settlementId, quantity: sp.quantity, amount: sp.amount },
            client
          );

          // 如果没有更新任何记录，说明记录不存在，需要插入新记录
          if (updateCount === 0) {
            await settlementRepo.createWageDistribution({
              subprojectId: sp.subprojectId,
              userId: parseInt(workerId),
              workdays: workerWorkdays,
              amount: sp.amount,
              quantity: sp.quantity,
              settlementId
            }, client);
          }
        }
      }

      // 10. 更新预支记录的结算状态
      if (allAdvances.length > 0) {
        const advanceIds = allAdvances.map(a => a.id);
        await settlementRepo.settleAdvances(advanceIds, settlementId, client);
      }

      // 11. 为每个工程添加历史记录
      for (const projectId in projectSubprojects) {
        await client.query(
          `INSERT INTO project_history (project_id, action, description, performed_by)
           VALUES ($1, 'SETTLE_WAGE', $2, $3)`,
          [projectId, `工资结算：${settlementNo}，结算金额${totalAmount.toFixed(2)}元`, currentUserId]
        );
      }

      await client.query('COMMIT');

      // 12. 清除缓存（事务提交后执行）
      await invalidateCache(currentUserId);

      // 13. 异步刷新物化视图（不等待完成，避免阻塞响应）
      refreshMvAsync();

      logger.info('创建结算成功', { settlementId, settlementNo, totalAmount, currentUserId });

      return {
        settlement,
        workers: allWorkers,
        total_amount: totalAmount,
        advance_amount: advanceAmountTotal,
        actual_amount: actualAmount,
        subprojects
      };
    } catch (error) {
      // 事务回滚
      try {
        await client.query('ROLLBACK');
        logger.info('[结算] 事务已回滚');
      } catch (rollbackErr) {
        logger.error('[结算] 事务回滚失败:', rollbackErr);
      }

      // 如果是 BusinessError，直接抛出
      if (error.name === 'BusinessError') {
        throw error;
      }

      logger.error('创建结算失败:', error);
      throw new BusinessError(5001, '创建结算失败');
    } finally {
      try {
        client.release();
      } catch (releaseErr) {
        logger.error('[结算] 释放数据库连接失败:', releaseErr);
      }
    }
  },

  /**
   * 获取结算列表
   *
   * 业务规则：
   * - 施工员只能看自己的结算记录
   *
   * @param {object} filters - 筛选参数
   * @param {number} [filters.userId] - 用户ID（施工员只能查看自己的记录）
   * @param {number} [filters.projectId] - 工程ID
   * @param {string} [filters.startDate] - 开始日期
   * @param {string} [filters.endDate] - 结束日期
   * @param {number} [filters.page=1] - 页码
   * @param {number} [filters.size=10] - 每页条数
   * @param {string} filters.userRole - 当前用户角色
   * @param {number} filters.currentUserId - 当前用户ID
   * @returns {Promise<{rows: Array, total: number, page: number, size: number}>}
   */
  async getSettlements(filters) {
    const {
      projectId,
      startDate,
      endDate,
      confirmed,
      page = 1,
      size = 10,
      userRole,
      currentUserId
    } = filters;

    // 施工员只能查看自己的结算记录
    const queryParams = {
      page: parseInt(page),
      size: parseInt(size),
      projectId,
      startDate,
      endDate
    };

    // Bug11修复：补充 confirmed 参数传递（原解构时丢失，导致前端传入的确认状态筛选无效）
    if (confirmed !== undefined && confirmed !== null && confirmed !== '') {
      // 兼容字符串 'true'/'false' 和布尔值
      queryParams.confirmed = confirmed === 'true' || confirmed === true;
    }

    if (userRole === 'constructor') {
      queryParams.userId = currentUserId;
    }

    // 尝试从缓存获取
    const cacheKeyStr = cache.cacheKey(
      'settlements',
      currentUserId,
      'list',
      queryParams.page,
      queryParams.size,
      queryParams.userId || '',
      queryParams.projectId || '',
      queryParams.startDate || '',
      queryParams.endDate || '',
      queryParams.confirmed !== undefined ? String(queryParams.confirmed) : ''
    );

    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    const result = await settlementRepo.listSettlements(queryParams);

    // 写入缓存（10分钟）
    await cache.set(cacheKeyStr, result, cache.TTL.MEDIUM);

    return result;
  },

  /**
   * 获取结算详情
   *
   * 业务规则：
   * - 施工员只能看自己的结算记录
   *
   * @param {number} settlementId - 结算记录ID
   * @param {number} currentUserId - 当前用户ID
   * @param {string} userRole - 当前用户角色
   * @returns {Promise<object>} 结算详情（含预支记录和工资分配记录）
   */
  async getSettlementDetail(settlementId, currentUserId, userRole) {
    // 1. 获取结算记录
    const settlement = await settlementRepo.findSettlementById(settlementId);
    if (!settlement) {
      throw new BusinessError(3010, '结算记录不存在');
    }

    // 2. 权限检查：施工员只能查看自己的结算记录
    if (userRole === 'constructor' && settlement.user_id !== currentUserId) {
      throw new BusinessError(4002, '您只能查看自己的结算记录');
    }

    // 3. 获取关联的预支记录
    const advancesResult = await pool.query(
      `SELECT
        wa.id,
        wa.user_id,
        u.username,
        u.nickname,
        wa.advance_amount,
        wa.advance_date,
        wa.remark
       FROM wage_advances wa
       LEFT JOIN users u ON wa.user_id = u.id
       WHERE wa.settlement_id = $1`,
      [settlementId]
    );

    // 4. 获取结算的工资分配记录
    const distributionsResult = await pool.query(
      `SELECT
        wd.id,
        wd.subproject_id,
        wd.user_id,
        wd.workdays,
        wd.quantity,
        wd.amount,
        sp.amount as subproject_amount,
        p.name as project_name,
        st.name as space_type_name,
        cp.name as construction_plan_name
       FROM wage_distributions wd
       JOIN subprojects sp ON wd.subproject_id = sp.id
       JOIN projects p ON sp.project_id = p.id
       JOIN space_types st ON sp.space_type_id = st.id
       JOIN construction_plans cp ON sp.construction_plan_id = cp.id
       WHERE wd.settlement_id = $1`,
      [settlementId]
    );

    return {
      ...settlement,
      advances: advancesResult.rows,
      distributions: distributionsResult.rows
    };
  },

  /**
   * 获取用户工资汇总
   *
   * 业务规则：
   * - 施工员只能看自己的
   *
   * @param {object} params - 查询参数
   * @param {number} params.userId - 目标用户ID
   * @param {string} params.startMonth - 开始月份
   * @param {string} params.endMonth - 结束月份
   * @param {number} params.currentUserId - 当前用户ID
   * @param {string} params.userRole - 当前用户角色
   * @returns {Promise<object>} 工资汇总数据
   */
  async getUserWageSummary({ userId, startMonth, endMonth, currentUserId, userRole }) {
    // 施工员只能查看自己的工资汇总
    const targetUserId = userRole === 'constructor' ? currentUserId : userId;

    if (!targetUserId) {
      throw new BusinessError(1001, '用户ID不能为空');
    }

    // 尝试从缓存获取
    const cacheKeyStr = cache.cacheKey('settlements', targetUserId, 'wage-summary', startMonth, endMonth);
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    // 通过 repo 查询工资汇总
    const result = await settlementRepo.getUserWageSummary(targetUserId, startMonth, endMonth);

    // 写入缓存（10分钟）
    await cache.set(cacheKeyStr, result, cache.TTL.MEDIUM);

    return result;
  },

  /**
   * 用户确认结算
   *
   * 业务规则：
   * - 检查结算记录存在
   * - 检查未重复确认
   * - 检查用户参与性
   * - 更新确认状态
   * - 更新预支记录
   * - 创建结算快照
   * - 清除缓存
   *
   * @param {number} settlementId - 结算记录ID
   * @param {number} currentUserId - 当前用户ID
   * @returns {Promise<object>} 确认结果
   */
  async confirmSettlement(settlementId, currentUserId) {
    // Bug9修复：整个确认流程必须在事务中完成，避免任一步失败导致数据不一致
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      // 1. 获取结算记录
      const settlement = await settlementRepo.findSettlementById(settlementId);
      if (!settlement) {
        throw new BusinessError(3010, '结算记录不存在');
      }

      // 2. 检查是否已经确认过
      if (settlement.confirmed) {
        throw new BusinessError(3014, '该结算已确认');
      }

      // 3. 检查用户是否参与了该结算相关的工程
      // Bug10修复：日期比较 off-by-one，改为 < (end_month + 1 day) 而非 <= end_month::date
      const participationResult = await client.query(
        `SELECT pw.id
         FROM project_workers pw
         JOIN subprojects sp ON pw.project_id = sp.project_id
         JOIN wage_distributions wd ON sp.id = wd.subproject_id
         WHERE pw.user_id = $1
           AND wd.created_at >= $2::timestamp
           AND wd.created_at < ($3::date + INTERVAL '1 day')::timestamp
         LIMIT 1`,
        [currentUserId, settlement.start_month, settlement.end_month]
      );

      if (participationResult.rows.length === 0) {
        throw new BusinessError(4002, '您无权确认此结算');
      }

      // 4. 更新结算记录的确认状态
      await settlementRepo.updateSettlement(settlementId, {
        confirmed: true,
        confirmedAt: new Date(),
        settledBy: currentUserId
      }, client);

      // 5. 更新预支记录的结算状态
      // Bug7修复：原SQL用 settlement.user_id 过滤预支（管理员创建时user_id是管理员，会错误更新管理员预支）
      // 改为：基于 wage_distributions 关联的实际施工员来更新预支
      await client.query(
        `UPDATE wage_advances
         SET settled = true, settlement_id = $1
         WHERE user_id IN (
           SELECT DISTINCT wd.user_id
           FROM wage_distributions wd
           JOIN subprojects sp ON wd.subproject_id = sp.id
           WHERE wd.settlement_id = $1
         )
         AND advance_date <= $2
         AND settled = false`,
        [settlementId, settlement.end_month]
      );

      // 6. 添加历史记录
      await client.query(
        `INSERT INTO project_history (project_id, action, description, performed_by)
         VALUES ($1, 'CONFIRM_SETTLEMENT', $2, $3)`,
        [settlement.project_id || 0, `用户确认结算：${settlement.settlement_no}`, currentUserId]
      );

      await client.query('COMMIT');

      // 7. 异步刷新物化视图（不等待完成，避免阻塞响应）
      refreshMvAsync();

      // 8. 创建结算历史快照（事务外执行，失败不影响主流程）
      try {
        await createSettlementSnapshot(settlementId, currentUserId);
      } catch (snapshotError) {
        // 快照创建失败不影响主流程，继续执行
        logger.warn('创建结算快照失败，不影响主流程', { settlementId, error: snapshotError.message });
      }

      // 8. 清除缓存
      await invalidateCache(currentUserId);

      logger.info('确认结算成功', { settlementId, currentUserId });

      return {
        message: '结算确认成功！',
        settlement_no: settlement.settlement_no,
        total_amount: settlement.total_amount,
        advance_amount: settlement.advance_amount,
        actual_amount: settlement.actual_amount,
        start_month: settlement.start_month,
        end_month: settlement.end_month,
        snapshot_info: '结算历史快照已保存',
        settlement_id: settlementId,
        confirmed_at: new Date()
      };
    } catch (error) {
      // 事务回滚
      try {
        await client.query('ROLLBACK');
        logger.info('[确认结算] 事务已回滚');
      } catch (rollbackErr) {
        logger.error('[确认结算] 事务回滚失败:', rollbackErr);
      }

      // 如果是 BusinessError，直接抛出
      if (error.name === 'BusinessError') {
        throw error;
      }

      logger.error('确认结算失败:', error);
      throw new BusinessError(5001, '确认结算失败');
    } finally {
      try {
        client.release();
      } catch (releaseErr) {
        logger.error('[确认结算] 释放数据库连接失败:', releaseErr);
      }
    }
  },

  /**
   * 获取用户结算历史
   *
   * @param {object} filters - 筛选参数
   * @param {number} filters.currentUserId - 当前用户ID
   * @param {boolean} [filters.confirmed] - 确认状态筛选
   * @param {number} [filters.page=1] - 页码
   * @param {number} [filters.size=10] - 每页条数
   * @returns {Promise<{rows: Array, total: number, page: number, size: number}>}
   */
  async getUserSettlementHistory(filters) {
    const { currentUserId, confirmed, page = 1, size = 10 } = filters;

    // 尝试从缓存获取
    const cacheKeyStr = cache.cacheKey(
      'settlements',
      currentUserId,
      'history',
      parseInt(page),
      parseInt(size),
      confirmed !== undefined ? String(confirmed) : ''
    );

    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    // 通过 repo 查询快照列表
    const result = await settlementRepo.getSnapshotsByUser(currentUserId, {
      confirmed: confirmed !== undefined ? confirmed === 'true' || confirmed === true : undefined,
      page: parseInt(page),
      size: parseInt(size)
    });

    // 构建返回数据结构
    const settlements = result.rows.map(snapshot => {
      // 解析快照数据
      let projectsData = [];
      let advancesData = [];
      let planTotals = {};
      let grandTotal = 0;
      let totalAdvance = 0;
      let finalTotal = 0;
      let projectPlanData = {};

      try {
        // 解析计算结果快照
        const calculationSnapshot = typeof snapshot.calculation_snapshot === 'string'
          ? JSON.parse(snapshot.calculation_snapshot)
          : snapshot.calculation_snapshot || {};

        grandTotal = calculationSnapshot.grandTotal || 0;
        totalAdvance = calculationSnapshot.totalAdvance || 0;
        finalTotal = calculationSnapshot.finalTotal || 0;
        planTotals = calculationSnapshot.planTotals || {};

        // 解析工程数据快照
        const projectsSnapshot = typeof snapshot.projects_snapshot === 'string'
          ? JSON.parse(snapshot.projects_snapshot)
          : snapshot.projects_snapshot || [];
        projectsData = Array.isArray(projectsSnapshot) ? projectsSnapshot : [];

        // 构建工程-方案数据映射
        const planDataObj = {};
        projectsData.forEach((project) => {
          if (!planDataObj[project.id]) {
            planDataObj[project.id] = {};
          }
          if (project.plan_id && project.user_quantity !== undefined) {
            planDataObj[project.id][project.plan_id] = project.user_quantity;
          }
        });
        projectPlanData = planDataObj;

        // 解析预支记录快照
        const advancesSnapshot = typeof snapshot.advances_snapshot === 'string'
          ? JSON.parse(snapshot.advances_snapshot)
          : snapshot.advances_snapshot || [];
        advancesData = Array.isArray(advancesSnapshot) ? advancesSnapshot : [];
      } catch (e) {
        logger.error('解析快照数据失败:', e);
      }

      return {
        settlement_id: snapshot.settlement_id,
        settlement_no: snapshot.settlement_no,
        start_month: snapshot.start_month,
        end_month: snapshot.end_month,
        total_amount: snapshot.total_amount,
        advance_amount: snapshot.advance_amount,
        actual_amount: snapshot.actual_amount,
        confirmed: snapshot.confirmed,
        confirmed_at: snapshot.confirmed_at,
        settled_by: snapshot.settled_by,
        settled_by_username: snapshot.settled_by_username,
        settled_by_nickname: snapshot.settled_by_nickname,
        settled_at: snapshot.settled_at,
        remark: snapshot.remark,
        user_amount: finalTotal,
        projects: projectsData,
        advances: advancesData,
        project_plan_data: projectPlanData,
        plan_totals: planTotals,
        grand_total: grandTotal,
        total_advance: totalAdvance,
        final_total: finalTotal
      };
    });

    const finalResult = {
      rows: settlements,
      total: result.total,
      page: result.page,
      size: result.size
    };

    // 写入缓存（10分钟）
    await cache.set(cacheKeyStr, finalResult, cache.TTL.MEDIUM);

    return finalResult;
  },

  /**
   * 计算结算预览
   *
   * 业务规则：
   * - 调用 calculation.calculateSettlementPreview
   *
   * @param {Array<number>} projectIds - 工程ID数组
   * @param {number} currentUserId - 当前用户ID
   * @returns {Promise<object>} 结算预览数据
   */
  async calculateSettlement(projectIds, currentUserId) {
    // 参数校验
    if (!projectIds || !Array.isArray(projectIds) || projectIds.length === 0) {
      throw new BusinessError(1001, '请选择要结算的工程');
    }

    // 尝试从缓存获取
    const cacheKeyStr = cache.cacheKey('settlements', currentUserId, 'preview', projectIds.sort().join(','));
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    // 调用计算服务
    const result = await calculation.calculateSettlementPreview(projectIds, currentUserId);

    // 写入缓存（5分钟，预览数据时效性较短）
    await cache.set(cacheKeyStr, result, cache.TTL.SHORT);

    return result;
  }
};
