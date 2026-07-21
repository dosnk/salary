const Joi = require('joi');
const validation = require('../middleware/validation');
const settlementService = require('../services/settlementService');
const logger = require('../config/logger');

// 以下依赖仅用于 createSettlementSnapshot 和 exportSettlementByIdToExcel 的原有实现
// 后续重构时将移除
const pool = require('../config/database');

// 尝试使用 xlsx-js-style（支持样式），如果不存在则使用 xlsx
let XLSX;
try {
  XLSX = require('xlsx-js-style');
} catch (e) {
  XLSX = require('xlsx');
}

// ==================== 业务方法（调用 settlementService） ====================

/**
 * 创建工资分配记录
 * 提取请求参数，调用 settlementService 处理业务逻辑
 */
const createWageDistribution = async (ctx) => {
  const { subprojectId, userId, workdays, amount, remark } = ctx.request.body;
  const currentUserId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    const result = await settlementService.createWageDistribution({
      subprojectId,
      userId,
      workdays,
      amount,
      remark,
      currentUserId,
      user
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('创建工资分配记录失败:', error);
    ctx.fail(5001, '创建工资分配记录失败');
  }
};

/**
 * 批量创建工资分配记录（为子项目的所有施工人员）
 * 提取请求参数，调用 settlementService 处理业务逻辑
 */
const createBatchWageDistributions = async (ctx) => {
  const { subprojectId, distributionType } = ctx.request.body;
  const currentUserId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    const result = await settlementService.createBatchWageDistributions({
      subprojectId,
      distributionType,
      currentUserId,
      user
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('批量创建工资分配记录失败:', error);
    ctx.fail(5001, '批量创建工资分配记录失败');
  }
};

/**
 * 工资结算（支持多施工人员独立结算）
 * 提取请求参数，调用 settlementService 处理业务逻辑
 */
const createSettlement = async (ctx) => {
  const { startMonth, endMonth, remark } = ctx.request.body;
  const currentUserId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    const result = await settlementService.createSettlement({
      startMonth,
      endMonth,
      remark,
      currentUserId,
      user
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('工资结算失败:', error);
    ctx.fail(5001, '工资结算失败');
  }
};

/**
 * 获取结算记录列表（支持多施工人员独立结算）
 * 提取查询参数，调用 settlementService 获取分页结果
 */
const getSettlements = async (ctx) => {
  const { page = 1, size = 10, startDate, endDate, projectId, userId } = ctx.query;
  const user = ctx.state.user;
  const currentUserId = ctx.state.user.id;
  const pageNum = parseInt(page);
  const sizeNum = parseInt(size);

  try {
    const result = await settlementService.getSettlements({
      page: pageNum,
      size: sizeNum,
      startDate,
      endDate,
      projectId,
      userId,
      user,
      currentUserId
    });
    ctx.paginate(result.list, result.total, result.page, result.size);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取结算记录列表失败:', error);
    ctx.fail(5001, '获取结算记录列表失败');
  }
};

/**
 * 获取结算详情（支持多施工人员独立结算）
 * 提取路径参数，调用 settlementService 获取详情
 */
const getSettlementDetail = async (ctx) => {
  const { id } = ctx.params;
  const user = ctx.state.user;
  const currentUserId = ctx.state.user.id;

  try {
    const result = await settlementService.getSettlementDetail({
      id,
      user,
      currentUserId
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取结算详情失败:', error);
    ctx.fail(5001, '获取结算详情失败');
  }
};

/**
 * 获取用户工资汇总（支持多施工人员独立结算）
 * 提取查询参数，调用 settlementService 获取汇总数据
 */
const getUserWageSummary = async (ctx) => {
  const { userId, startMonth, endMonth } = ctx.query;
  const user = ctx.state.user;
  const currentUserId = ctx.state.user.id;

  try {
    const result = await settlementService.getUserWageSummary({
      userId,
      startMonth,
      endMonth,
      user,
      currentUserId
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取用户工资汇总失败:', error);
    ctx.fail(5001, '获取用户工资汇总失败');
  }
};

/**
 * 用户确认结算
 * 提取路径参数，调用 settlementService 处理确认逻辑
 */
const confirmSettlement = async (ctx) => {
  const { id } = ctx.params;
  const currentUserId = ctx.state.user.id;

  try {
    const result = await settlementService.confirmSettlement({
      id,
      currentUserId
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('确认结算失败:', error);
    ctx.fail(5001, '确认结算失败');
  }
};

/**
 * 获取用户结算历史列表
 * 提取查询参数，调用 settlementService 获取分页结果
 */
const getUserSettlementHistory = async (ctx) => {
  const { page = 1, size = 10, confirmed } = ctx.query;
  const currentUserId = ctx.state.user.id;
  const pageNum = parseInt(page);
  const sizeNum = parseInt(size);

  try {
    const result = await settlementService.getUserSettlementHistory({
      page: pageNum,
      size: sizeNum,
      confirmed,
      currentUserId
    });
    ctx.paginate(result.list, result.total, result.page, result.size);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取用户结算历史失败:', error);
    ctx.fail(5001, '获取用户结算历史失败');
  }
};

/**
 * 计算结算金额（前端结算单预览使用）
 * 提取请求参数，调用 settlementService 计算预览数据
 */
const calculateSettlement = async (ctx) => {
  const { projectIds } = ctx.request.body;
  const currentUserId = ctx.state.user.id;

  try {
    // 注意：service方法签名为 calculateSettlement(projectIds, currentUserId)
    // 必须传递两个独立参数，不能传对象，否则 Array.isArray 判断失败
    const result = await settlementService.calculateSettlement(projectIds, currentUserId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('计算结算金额失败:', error);
    ctx.fail(5001, '计算结算金额失败');
  }
};

// ==================== 保留原有实现的方法 ====================

/**
 * 创建结算历史快照
 * 保留原有实现（内部调用方法），后续将迁移至 settlementService
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

    // 工程ID去重（避免重复处理）
    projectIds = [...new Set(projectIds)];

    // 2. 查询工程数据快照（根据工资分配方案计算当前用户的工程量）

    // 查询工资分配记录，获取当前用户在每个子项目的分配金额和工日
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

    // 根据子项目的 length 和 width 重新计算 quantity
    const projectsSnapshot = [];
    const planTotals = {};

    for (const project of projectIds) {
      const projectWDs = wdResult.rows.filter(wd => wd.project_id === project);

      if (projectWDs.length === 0) continue;

      // 获取工程信息
      const projectInfo = projectWDs[0];

      // 遍历该工程的所有施工方案
      for (const wd of projectWDs) {
        const planId = wd.plan_id;

        // 直接使用 wage_distributions 表中已保存的 quantity 字段
        // 这是结算时已经正确分配给用户的工程量
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
        const planIdStr = String(planId);  // 转换为字符串键
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

    // 3. 查询所有施工方案数据快照（包含所有施工方案，即使没有使用）
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

    // 4. 查询预支记录数据快照（查询所有施工人员的预支记录）
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

    // 5. 计算计算结果快照（根据当前用户计算）
    // 注意：planTotals 已经在遍历工资分配记录时累加过了，不需要再遍历 projectsSnapshot 累加
    const grandTotal = Object.values(planTotals).reduce((sum, pt) => sum + pt.totalAmount, 0);

    // 计算当前用户的预支金额（只计算当前用户的预支）
    const userAdvancesSnapshot = advancesSnapshot.filter(a => a.user_id === currentUserId);
    const totalAdvance = userAdvancesSnapshot.reduce((sum, a) => sum + parseFloat(a.advance_amount), 0);

    // 当前用户的实际金额
    const userActualAmount = grandTotal - totalAdvance;

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

    // 6. 插入快照数据：一个结算单对应一条快照记录
    // 注意：total_amount、advance_amount、actual_amount 使用当前用户的独立金额
    await query(`
      INSERT INTO wage_settlement_snapshots (
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
      grandTotal,  // 当前用户的工程总额
      totalAdvance,  // 当前用户的预支金额
      userActualAmount,  // 当前用户的实际金额
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

/**
 * 辅助函数：获取单位名称
 */
const getUnitName = (unit) => {
  const unitMap = {
    length: '米',
    perimeter: '米',
    area: '㎡'
  };
  return unitMap[unit] || unit;
};

/**
 * 辅助函数：格式化日期
 */
const formatDate = (date) => {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

/**
 * 按结算单ID导出Excel
 * 暂时保留原有实现（涉及Excel生成，后续改为异步队列）
 */
const exportSettlementByIdToExcel = async (ctx) => {
  const { settlementId } = ctx.params;
  const currentUserId = ctx.state.user.id;

  try {
    // 验证结算单ID
    if (!settlementId) {
      ctx.fail(1001, '结算单ID不能为空');
      return;
    }

    // 查询当前用户昵称
    const userResult = await pool.query('SELECT nickname, username FROM users WHERE id = $1', [currentUserId]);
    const userName = userResult.rows[0]?.nickname || userResult.rows[0]?.username || '未知用户';

    // 查询结算历史快照
    const snapshotResult = await pool.query(`
      SELECT
        wss.settlement_id,
        wss.settlement_no,
        wss.start_month,
        wss.end_month,
        wss.total_amount,
        wss.advance_amount,
        wss.actual_amount,
        wss.remark,
        wss.projects_snapshot,
        wss.advances_snapshot,
        wss.calculation_snapshot,
        wss.plans_snapshot
      FROM wage_settlement_snapshots wss
      WHERE wss.settlement_id = $1 AND wss.user_id = $2
    `, [settlementId, currentUserId]);

    if (snapshotResult.rows.length === 0) {
      ctx.fail(3001, '结算单不存在');
      return;
    }

    const snapshot = snapshotResult.rows[0];

    // 解析工程数据快照
    const projectsSnapshot = typeof snapshot.projects_snapshot === 'string'
      ? JSON.parse(snapshot.projects_snapshot)
      : snapshot.projects_snapshot;
    const projectsData = Array.isArray(projectsSnapshot) ? projectsSnapshot : [];

    // 解析预支记录快照
    const advancesSnapshot = typeof snapshot.advances_snapshot === 'string'
      ? JSON.parse(snapshot.advances_snapshot)
      : snapshot.advances_snapshot;
    const advancesData = Array.isArray(advancesSnapshot) ? advancesSnapshot : [];

    // 解析计算结果快照
    const calculationSnapshot = typeof snapshot.calculation_snapshot === 'string'
      ? JSON.parse(snapshot.calculation_snapshot)
      : snapshot.calculation_snapshot;
    const planTotals = calculationSnapshot.planTotals || {};

    // 解析施工方案快照
    const plansSnapshot = typeof snapshot.plans_snapshot === 'string'
      ? JSON.parse(snapshot.plans_snapshot)
      : snapshot.plans_snapshot;
    const constructionPlans = Array.isArray(plansSnapshot) ? plansSnapshot : [];

    // 如果没有施工方案快照，从工程数据中提取
    if (constructionPlans.length === 0) {
      const planMap = new Map();
      projectsData.forEach(item => {
        if (item.plan_id && !planMap.has(item.plan_id)) {
          planMap.set(item.plan_id, {
            id: item.plan_id,
            name: item.plan_name,
            unit: item.unit,
            price: item.price
          });
        }
      });
      constructionPlans.push(...Array.from(planMap.values()));
    }

    // 按工程分组
    const projectMap = new Map();
    projectsData.forEach((item) => {
      const projectId = item.id;
      if (!projectMap.has(projectId)) {
        projectMap.set(projectId, {
          id: projectId,
          project_name: item.project_name,
          subprojects: [],
          planQuantities: {}
        });
      }

      const project = projectMap.get(projectId);

      if (item.plan_id) {
        project.subprojects.push({
          space_type_name: item.space_type_name || '',
          plan_id: item.plan_id,
          plan_name: item.plan_name,
          unit: item.unit,
          price: item.price,
          user_quantity: item.user_quantity,
          user_amount: item.user_amount
        });

        if (!project.planQuantities[item.plan_id]) {
          project.planQuantities[item.plan_id] = {
            totalQuantity: 0,
            totalAmount: 0,
            unit: item.unit,
            price: item.price,
            plan_name: item.plan_name
          };
        }
        project.planQuantities[item.plan_id].totalQuantity += parseFloat(item.user_quantity) || 0;
        project.planQuantities[item.plan_id].totalAmount += parseFloat(item.user_amount) || 0;
      }
    });

    // 构建Excel数据 - 使用二维数组方式
    const aoaData = [];

    // 构建列头：序号、工程名称、各施工方案、总额
    const headers = ['序号', '工程名称'];
    constructionPlans.forEach(plan => { headers.push(plan.name); });
    headers.push('总额');

    // 第一行：文件名格式（用户全名 开始日期 至 结束日期）
    const titleRow = [`${userName} ${formatDate(snapshot.start_month)} 至 ${formatDate(snapshot.end_month)}`];
    // 中间填充空格，保持列数一致
    for (let i = 1; i < headers.length; i++) {
      titleRow.push('');
    }
    aoaData.push(titleRow);

    // 第二行：空行
    const emptyRow = [];
    for (let i = 0; i < headers.length; i++) {
      emptyRow.push('');
    }
    aoaData.push(emptyRow);

    // 第三行：表头
    aoaData.push(headers);

    // 记录工程名称行的行号（用于高亮显示）
    const projectRows = [];

    // 工程数据行
    let index = 1;
    for (const project of projectMap.values()) {
      // 记录工程汇总行的行号（当前行号 = aoaData.length）
      projectRows.push(aoaData.length);

      // 汇总行（只显示工程合计，不显示子项目明细）
      const projectRow = [String(index++), project.project_name];
      constructionPlans.forEach(plan => {
        const planQty = project.planQuantities[plan.id];
        if (planQty) {
          const unitName = getUnitName(planQty.unit);
          projectRow.push(`${planQty.totalQuantity.toFixed(2)}${unitName}`);
        } else {
          projectRow.push('-');
        }
      });
      projectRow.push('-');
      aoaData.push(projectRow);
    }

    // 单价行
    const priceRow = ['', '单价'];
    constructionPlans.forEach(plan => {
      priceRow.push(`¥${parseFloat(plan.price).toFixed(2)}/${getUnitName(plan.unit)}`);
    });
    priceRow.push('-');
    aoaData.push(priceRow);

    // 合计行
    const totalQtyRow = ['', '合计'];
    constructionPlans.forEach(plan => {
      const pt = planTotals[String(plan.id)];
      if (pt) {
        totalQtyRow.push(`${parseFloat(pt.totalQuantity).toFixed(2)}${getUnitName(plan.unit)}`);
      } else {
        totalQtyRow.push('-');
      }
    });
    totalQtyRow.push('-');
    aoaData.push(totalQtyRow);

    // 总计行
    const totalAmountRow = ['', '总计'];
    let grandTotal = 0;
    constructionPlans.forEach(plan => {
      const pt = planTotals[String(plan.id)];
      if (pt) {
        totalAmountRow.push(`¥${parseFloat(pt.totalAmount).toFixed(2)}`);
        grandTotal += parseFloat(pt.totalAmount);
      } else {
        totalAmountRow.push('-');
      }
    });
    totalAmountRow.push(`¥${grandTotal.toFixed(2)}`);
    aoaData.push(totalAmountRow);

    // 预支行
    for (const advance of advancesData) {
      const advanceRow = ['', `${formatDate(advance.advance_date)} 预支`];
      constructionPlans.forEach(plan => { advanceRow.push('-'); });
      advanceRow.push(`¥${parseFloat(advance.advance_amount).toFixed(2)}`);
      aoaData.push(advanceRow);
    }

    // 总额行
    const finalTotal = calculationSnapshot.finalTotal || 0;
    const finalRow = ['', '总额'];
    constructionPlans.forEach(plan => { finalRow.push('-'); });
    finalRow.push(`¥${parseFloat(finalTotal).toFixed(2)}`);
    aoaData.push(finalRow);

    // 备注行：仅当结算时填写了备注才追加，位于总额行下方
    // 备注内容跨整行合并显示，记录行号用于后续设置合并区间和样式
    const settlementRemark = snapshot.remark && String(snapshot.remark).trim()
      ? String(snapshot.remark).trim()
      : '';
    let remarkRowIndex = -1;
    if (settlementRemark) {
      remarkRowIndex = aoaData.length;
      const remarkRow = [`备注：${settlementRemark}`];
      // 填充空列保持列数一致（内容在合并单元格中左对齐显示）
      for (let i = 1; i < headers.length; i++) {
        remarkRow.push('');
      }
      aoaData.push(remarkRow);
    }

    // 创建Excel工作簿
    const worksheet = XLSX.utils.aoa_to_sheet(aoaData);

    // 合并第一行的所有列（标题行）
    worksheet['!merges'] = [
      { s: { r: 0, c: 0 }, e: { r: 0, c: headers.length - 1 } }
    ];
    // 若存在备注行，合并备注行所有列（整行显示备注内容）
    if (remarkRowIndex >= 0) {
      worksheet['!merges'].push(
        { s: { r: remarkRowIndex, c: 0 }, e: { r: remarkRowIndex, c: headers.length - 1 } }
      );
    }

    // 设置列宽：第一列1.3厘米≈4.91字符，第二列4.5厘米≈17.01字符，其余列4厘米≈15.12字符
    worksheet['!cols'] = headers.map((_, index) => {
      if (index === 0) return { wch: 4.91 };   // 第一列：1.3厘米
      if (index === 1) return { wch: 17.01 };  // 第二列：4.5厘米
      return { wch: 15.12 };                    // 其余列：4厘米
    });

    // 设置行高：0.75厘米 ≈ 21.26 磅
    // 备注行因内容可能较长且开启自动换行，按内容长度与总列宽估算所需行数动态放大高度
    const rowHeight = 21.26;
    worksheet['!rows'] = aoaData.map((_, rowIdx) => {
      if (rowIdx === remarkRowIndex && settlementRemark) {
        // 合并单元格总字符宽度 = 各列 wch 之和；按此估算备注需要的行数（中文按较宽估算）
        const totalWch = headers.reduce((sum, _h, idx) => {
          if (idx === 0) return sum + 4.91;
          if (idx === 1) return sum + 17.01;
          return sum + 15.12;
        }, 0);
        // "备注："前缀 + 内容长度，除以可容纳字符数向上取整为行数，至少1行、最多兜底6行
        const contentLen = `备注：${settlementRemark}`.length;
        const lines = Math.min(6, Math.max(1, Math.ceil(contentLen / Math.max(1, totalWch))));
        return { hpt: rowHeight * lines };
      }
      return { hpt: rowHeight };
    });

    // 定义样式
    const borderStyle = {
      top: { style: 'thin', color: { rgb: '666666' } },
      bottom: { style: 'thin', color: { rgb: '666666' } },
      left: { style: 'thin', color: { rgb: '666666' } },
      right: { style: 'thin', color: { rgb: '666666' } }
    };

    const dataStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'F5F5F5' } },
      border: borderStyle
    };

    const titleStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'FFFFFF' } },
      font: { bold: true, sz: 14 },
      border: borderStyle
    };

    // 工程名称行高亮样式（淡绿色）
    const projectRowStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'E8F5E9' } },
      font: { bold: true },
      border: borderStyle
    };

    // 表头样式
    const headerStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'E3F2FD' } },
      font: { bold: true },
      border: borderStyle
    };

    // 总计行样式（淡蓝色）
    const totalRowStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'E3F2FD' } },
      font: { bold: true },
      border: borderStyle
    };

    // 预支行样式（淡黄色）
    const advanceRowStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'FFFDE7' } },
      font: { bold: true },
      border: borderStyle
    };

    // 总额行样式（淡粉色）
    const finalRowStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'FCE4EC' } },
      font: { bold: true, sz: 12 },
      border: borderStyle
    };

    // 单价行样式（文字颜色淡一点）
    const priceRowStyle = {
      alignment: { horizontal: 'center', vertical: 'center' },
      fill: { fgColor: { rgb: 'F5F5F5' } },
      font: { color: { rgb: '888888' }, sz: 10 },
      border: borderStyle
    };

    // 备注行样式（左对齐、灰底、支持自动换行，与程序内备注行视觉一致）
    const remarkRowStyle = {
      alignment: { horizontal: 'left', vertical: 'center', wrapText: true },
      fill: { fgColor: { rgb: 'F9FAFB' } },
      font: { color: { rgb: '333333' }, sz: 11 },
      border: borderStyle
    };

    // 应用样式到单元格
    const range = XLSX.utils.decode_range(worksheet['!ref'] || 'A1');

    // 先检测哪些行需要特殊样式
    const specialRows = new Map();
    for (let R = range.s.r; R <= range.e.r; ++R) {
      for (let C = range.s.c; C <= range.e.c; ++C) {
        const cellAddress = XLSX.utils.encode_cell({ r: R, c: C });
        if (!worksheet[cellAddress]) continue;

        const cellValue = worksheet[cellAddress].v;
        const cellValueStr = cellValue ? String(cellValue) : '';

        if (cellValueStr === '单价') {
          specialRows.set(R, 'price');
          break;
        } else if (cellValueStr === '总计' || cellValueStr === '合计') {
          specialRows.set(R, 'total');
          break;
        } else if (cellValueStr.includes('预支')) {
          specialRows.set(R, 'advance');
          break;
        } else if (cellValueStr === '总额') {
          specialRows.set(R, 'final');
          break;
        }
      }
    }

    // 应用样式到每个单元格
    for (let R = range.s.r; R <= range.e.r; ++R) {
      for (let C = range.s.c; C <= range.e.c; ++C) {
        const cellAddress = XLSX.utils.encode_cell({ r: R, c: C });
        if (!worksheet[cellAddress]) continue;

        // 第一行（结算单号行）不设置背景色，左对齐
        if (R === 0) {
          worksheet[cellAddress].s = titleStyle;
        }
        // 备注行 - 整行应用备注样式（左对齐、支持换行）
        else if (R === remarkRowIndex) {
          worksheet[cellAddress].s = remarkRowStyle;
        }
        // 第二行（空行）设置边框
        else if (R === 1) {
          worksheet[cellAddress].s = { border: borderStyle };
        }
        // 第三行（表头行）
        else if (R === 2) {
          worksheet[cellAddress].s = headerStyle;
        }
        // 工程名称行高亮显示
        else if (projectRows.includes(R)) {
          worksheet[cellAddress].s = projectRowStyle;
        }
        // 单价行 - 整行应用样式（文字颜色淡一点）
        else if (specialRows.get(R) === 'price') {
          worksheet[cellAddress].s = priceRowStyle;
        }
        // 总计/合计行 - 整行应用样式
        else if (specialRows.get(R) === 'total') {
          worksheet[cellAddress].s = totalRowStyle;
        }
        // 预支行 - 整行应用样式
        else if (specialRows.get(R) === 'advance') {
          worksheet[cellAddress].s = advanceRowStyle;
        }
        // 总额行 - 整行应用样式
        else if (specialRows.get(R) === 'final') {
          worksheet[cellAddress].s = finalRowStyle;
        }
        // 其他行设置背景色和居中
        else {
          worksheet[cellAddress].s = dataStyle;
        }
      }
    }

    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, '结算单');

    // 生成Excel文件
    const excelBuffer = XLSX.write(workbook, { type: 'buffer', bookType: 'xlsx' });

    // 设置响应头
    const fileName = encodeURIComponent(`结算单_${snapshot.settlement_no}.xlsx`);
    ctx.set('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    ctx.set('Content-Disposition', `attachment; filename*=UTF-8''${fileName}`);
    ctx.body = excelBuffer;

  } catch (error) {
    logger.error('导出结算单Excel失败:', error);
    ctx.fail(5001, `导出结算单Excel失败: ${error.message}`);
  }
};

// ==================== Joi 校验规则（不改动） ====================

const createWageDistributionSchema = Joi.object({
  subprojectId: Joi.number().integer().positive().required(),
  userId: Joi.number().integer().positive().required(),
  workdays: Joi.number().positive().precision(2).default(1),
  amount: Joi.number().positive().precision(4).required(),
  remark: Joi.string().allow('').max(500)
});

const createBatchWageDistributionsSchema = Joi.object({
  subprojectId: Joi.number().integer().positive().required(),
  distributionType: Joi.string().valid('average', 'work_days').required()
});

const createSettlementSchema = Joi.object({
  startMonth: Joi.date().required(),
  endMonth: Joi.date().required(),
  remark: Joi.string().allow('').max(500)
});

const getSettlementsSchema = Joi.object({
  page: validation.rules.page,
  size: validation.rules.size,
  startDate: Joi.date(),
  endDate: Joi.date()
});

const getUserWageSummarySchema = Joi.object({
  userId: Joi.number().integer().positive(),
  startMonth: Joi.date().required(),
  endMonth: Joi.date().required()
});

// 获取结算详情校验规则
const getSettlementDetailSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

// 计算结算金额校验规则
const calculateSettlementSchema = Joi.object({
  projectIds: Joi.array().items(Joi.number().integer().positive()).required()
});

// 用户确认结算校验规则（路径参数验证）
const confirmSettlementSchema = Joi.object({});

// 获取用户结算历史校验规则
const getUserSettlementHistorySchema = Joi.object({
  page: validation.rules.page,
  size: validation.rules.size,
  confirmed: Joi.string().valid('true', 'false')
});

// ==================== 模块导出（不改动函数名） ====================

module.exports = {
  createWageDistribution,
  createBatchWageDistributions,
  createSettlement,
  getSettlements,
  getSettlementDetail,
  getUserWageSummary,
  confirmSettlement,
  getUserSettlementHistory,
  calculateSettlement,
  createSettlementSnapshot,
  exportSettlementByIdToExcel,
  createWageDistributionSchema,
  createBatchWageDistributionsSchema,
  createSettlementSchema,
  getSettlementsSchema,
  getUserWageSummarySchema,
  getSettlementDetailSchema,
  confirmSettlementSchema,
  getUserSettlementHistorySchema,
  calculateSettlementSchema
};
