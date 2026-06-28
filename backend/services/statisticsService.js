/**
 * 统计业务逻辑层 (Service)
 *
 * 从 controllers/statistics.js 中抽离统计相关的业务逻辑，负责：
 * - 业务规则校验（权限、参数完整性）
 * - 使用 pg 连接池进行数据访问（统计查询复杂，暂不抽 Repository）
 * - 调用 cacheService 进行缓存管理（月度统计缓存30分钟）
 * - 业务异常通过 BusinessError 抛出，由 controller 捕获
 *
 * 关键规则：
 * - 施工员数据过滤：通过 project_workers 表关联，只统计自己参与的工程
 * - 所有 SQL 必须参数化，禁止字符串拼接
 * - 缓存键格式：statistics:${userId}:monthly:${months.join(',')}
 */

const pool = require('../config/database');
const cache = require('./cacheService');
const { isConstructor } = require('../middleware/rbac');
const logger = require('../config/logger');

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
 * 构建施工员数据过滤的 SQL 片段和参数
 * 施工员只能查看自己参与的工程，通过 project_workers 表关联
 *
 * @param {number} userId - 施工员用户ID
 * @param {number} paramIndex - 当前参数索引（从该索引开始追加参数）
 * @returns {{ joinSql: string, whereSql: string, params: number[] }}
 */
const buildConstructorFilter = (userId, paramIndex) => {
  return {
    // JOIN 子句：关联 project_workers 表
    joinSql: ' JOIN project_workers pw ON p.id = pw.project_id ',
    // WHERE 子句：限定施工员用户
    whereSql: ` AND pw.user_id = $${paramIndex} `,
    params: [userId]
  };
};

/**
 * 解析月份列表为日期范围参数
 * 将 ['2025-01', '2025-02'] 转换为 ['2025-01-01', '2025-01-31', '2025-02-01', '2025-02-31']
 *
 * @param {string[]} monthList - 月份列表，格式 'YYYY-MM'
 * @returns {string[]} 日期参数数组，每个月份产生 [月初, 月末] 两个参数
 */
const parseMonthDateParams = (monthList) => {
  const params = [];
  for (const m of monthList) {
    params.push(`${m}-01`);
    params.push(`${m}-31`);
  }
  return params;
};

/**
 * 构建多月份日期条件（参数化）
 * 生成类似 (sp.created_at >= $1 AND sp.created_at <= $2) OR (sp.created_at >= $3 AND sp.created_at <= $4)
 *
 * @param {string[]} monthList - 月份列表
 * @param {number} startIndex - 参数起始索引
 * @param {string} dateColumn - 日期列名（含表别名）
 * @returns {string} 参数化的日期条件 SQL
 */
const buildMonthDateConditions = (monthList, startIndex, dateColumn) => {
  return monthList
    .map((_, i) => `(${dateColumn} >= $${startIndex + i * 2} AND ${dateColumn} <= $${startIndex + i * 2 + 1})`)
    .join(' OR ');
};

// ========== 导出方法 ==========

module.exports = {
  /**
   * 获取月度统计
   * 业务规则：
   * - 施工员只能看自己的数据（通过 project_workers 关联）
   * - 缓存30分钟
   * - 只统计已完工的子项目（sp.status = 'completed' 且 p.status = 'completed'）
   *
   * @param {Object} params - 参数对象
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @param {string[]} params.months - 月份列表，格式 ['YYYY-MM', ...]
   * @returns {Promise<Object>} 月度统计数据
   */
  async getMonthlyStatistics({ userId, role, months }) {
    // 参数校验
    if (!months || !Array.isArray(months) || months.length === 0) {
      throw new BusinessError(1001, '月份列表不能为空');
    }

    // 构建缓存键
    const cacheKey = cache.cacheKey('statistics', userId, 'monthly', months.join(','));

    // 尝试从缓存获取，缓存30分钟
    return cache.getOrSet(cacheKey, async () => {
      logger.info('月度统计缓存未命中，执行查询', { userId, role, months });

      const isConstructorUser = isConstructor({ role });
      const dateParams = parseMonthDateParams(months);
      const dateConditions = buildMonthDateConditions(months, 1, 'sp.created_at');

      // ===== 1. 统计工资分配金额 =====
      let distributionQuery = `
        SELECT
          SUM(sp.amount) AS total_amount,
          COUNT(DISTINCT sp.id) AS total_distributions
        FROM subprojects sp
        JOIN projects p ON sp.project_id = p.id
        WHERE sp.status = 'completed'
          AND (${dateConditions})
      `;
      let distributionParams = [...dateParams];
      let paramIndex = distributionParams.length + 1;

      // 施工员只能查看自己参与的工程数据
      if (isConstructorUser) {
        distributionQuery += `
          AND EXISTS (
            SELECT 1 FROM project_workers pw
            WHERE pw.project_id = p.id
              AND pw.user_id = $${paramIndex}
          )
        `;
        distributionParams.push(userId);
        paramIndex++;
      }

      const distributionResult = await pool.query(distributionQuery, distributionParams);

      // ===== 2. 统计预支金额 =====
      const advanceDateConditions = buildMonthDateConditions(months, 1, 'wa.advance_date');
      let advanceQuery = `
        SELECT
          COUNT(DISTINCT wa.id) AS total_advances,
          SUM(wa.advance_amount) AS advance_amount
        FROM wage_advances wa
        WHERE (${advanceDateConditions})
      `;
      let advanceParams = [...dateParams];
      let advanceParamIndex = advanceParams.length + 1;

      // 施工员只能查看自己的预支记录
      if (isConstructorUser) {
        advanceQuery += ` AND wa.user_id = $${advanceParamIndex} `;
        advanceParams.push(userId);
        advanceParamIndex++;
      }

      const advanceResult = await pool.query(advanceQuery, advanceParams);

      // ===== 3. 组装结果 =====
      const totalAmount = parseFloat(distributionResult.rows[0].total_amount) || 0;
      const totalAdvances = parseInt(advanceResult.rows[0].total_advances) || 0;
      const advanceAmount = parseFloat(advanceResult.rows[0].advance_amount) || 0;

      return {
        total_distributions: parseInt(distributionResult.rows[0].total_distributions) || 0,
        total_amount: totalAmount,
        settled_amount: 0,
        unsettled_amount: totalAmount,
        total_advances: totalAdvances,
        advance_amount: advanceAmount
      };
    }, cache.TTL.LONG); // 30分钟缓存
  },

  /**
   * 获取工程统计
   * 按工程状态和结算状态分组统计工程数量和金额
   * 业务规则：
   * - 施工员只能查看自己参与的工程
   * - 使用 v_project_user_settlement_status 视图获取结算状态
   *
   * @param {Object} params - 参数对象
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @returns {Promise<Object>} 工程统计数据
   */
  async getProjectStatistics({ userId, role }) {
    const isConstructorUser = isConstructor({ role });

    // 统计各工程状态和结算状态的工程数量和金额
    let query = `
      SELECT
        p.status,
        pus.settlement_status,
        COUNT(DISTINCT p.id) AS project_count,
        COALESCE(SUM(p.total_amount), 0) AS total_amount,
        COUNT(DISTINCT sp.id) AS subproject_count,
        COALESCE(SUM(sp.amount), 0) AS subproject_amount
      FROM projects p
      LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      LEFT JOIN subprojects sp ON p.id = sp.project_id AND sp.status = 'completed'
    `;

    // 施工员只能查看自己参与的工程
    if (isConstructorUser) {
      query += `
        JOIN project_workers pw ON p.id = pw.project_id
        WHERE pw.user_id = $1
      `;
    }

    query += ` GROUP BY p.status, pus.settlement_status ORDER BY p.status, pus.settlement_status`;

    const params = isConstructorUser ? [userId] : [];
    const result = await pool.query(query, params);

    // 初始化各状态分组的数据结构
    const statusGroups = {
      constructing: {
        status: 'constructing',
        status_name: '施工中',
        settlement_groups: {
          unsettled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settling: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
        },
        total: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
      },
      completed: {
        status: 'completed',
        status_name: '已完工',
        settlement_groups: {
          unsettled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settling: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
        },
        total: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
      },
      preparing: {
        status: 'preparing',
        status_name: '准备中',
        settlement_groups: {
          unsettled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settling: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
        },
        total: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
      },
      canceled: {
        status: 'canceled',
        status_name: '已取消',
        settlement_groups: {
          unsettled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settling: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 },
          settled: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
        },
        total: { count: 0, amount: 0, subproject_count: 0, subproject_amount: 0 }
      }
    };

    // 填充查询结果到分组结构
    result.rows.forEach(row => {
      const status = row.status || 'preparing';
      const settlementStatus = row.settlement_status || 'unsettled';

      if (statusGroups[status]) {
        const group = statusGroups[status];
        const settlementGroup = group.settlement_groups[settlementStatus];

        if (settlementGroup) {
          settlementGroup.count += parseInt(row.project_count) || 0;
          settlementGroup.amount += parseFloat(row.total_amount) || 0;
          settlementGroup.subproject_count += parseInt(row.subproject_count) || 0;
          settlementGroup.subproject_amount += parseFloat(row.subproject_amount) || 0;

          group.total.count += parseInt(row.project_count) || 0;
          group.total.amount += parseFloat(row.total_amount) || 0;
          group.total.subproject_count += parseInt(row.subproject_count) || 0;
          group.total.subproject_amount += parseFloat(row.subproject_amount) || 0;
        }
      }
    });

    return statusGroups;
  },

  /**
   * 获取收入统计（结算统计）
   * 业务规则：
   * - 施工员只能查看自己参与的工程
   * - 统计中(settling)、未结算(unsettled)、已结算(settled) 三种状态
   * - 已结算金额 = 工程总金额 - 未结算的预支金额
   * - 包含用户确认统计和本月工程统计
   *
   * @param {Object} params - 参数对象
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @param {string} params.startDate - 开始日期（可选）
   * @param {string} params.endDate - 结束日期（可选）
   * @returns {Promise<Object>} 收入统计数据
   */
  async getIncomeStatistics({ userId, role, startDate, endDate }) {
    const isConstructorUser = isConstructor({ role });

    // ===== 1. 统计"统计中"(settling)的工程总金额 =====
    let settlingQuery = `
      SELECT
        COALESCE(SUM(p.total_amount), 0) AS settling_amount,
        COUNT(DISTINCT p.id) AS settling_count,
        COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
          AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
          THEN p.id END) AS this_month_count
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      WHERE pus.settlement_status = 'settling'
        AND pus.user_id = $1
    `;
    let settlingParams = [userId];

    // 施工员只能查看自己参与工程的统计
    if (isConstructorUser) {
      settlingQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $2
        )
      `;
      settlingParams.push(userId);
    }

    const settlingResult = await pool.query(settlingQuery, settlingParams);

    // ===== 2. 统计"未结算"(unsettled)的工程总金额（只统计施工中的工程） =====
    let unsettledQuery = `
      SELECT
        COALESCE(SUM(p.total_amount), 0) AS unsettled_amount,
        COUNT(DISTINCT p.id) AS unsettled_count,
        COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
          AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
          THEN p.id END) AS this_month_count
      FROM projects p
      LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $1
      WHERE p.status = 'constructing'
        AND (pus.settlement_status IS NULL OR pus.settlement_status = 'unsettled')
    `;
    let unsettledParams = [userId];

    // 施工员只能查看自己参与工程的统计
    if (isConstructorUser) {
      unsettledQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $2
        )
      `;
      unsettledParams.push(userId);
    }

    const unsettledResult = await pool.query(unsettledQuery, unsettledParams);

    // ===== 3. 统计"已结算"(settled)的工程总金额（只统计本年度） =====
    let settledQuery = `
      SELECT
        COALESCE(SUM(p.total_amount), 0) AS settled_amount,
        COUNT(DISTINCT p.id) AS settled_count,
        COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
          AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
          THEN p.id END) AS this_month_count
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      WHERE pus.settlement_status = 'settled'
        AND pus.user_id = $1
        AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
    `;
    let settledParams = [userId];

    // 施工员只能查看自己参与工程的统计
    if (isConstructorUser) {
      settledQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $2
        )
      `;
      settledParams.push(userId);
    }

    const settledResult = await pool.query(settledQuery, settledParams);

    // ===== 4. 统计已结算工程的预支金额（本年度未结算的预支记录） =====
    let settledAdvanceQuery = `
      SELECT
        COALESCE(SUM(wa.advance_amount), 0) AS settled_advance_amount
      FROM wage_advances wa
      JOIN project_workers pw ON wa.user_id = pw.user_id
      JOIN projects p ON pw.project_id = p.id
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      WHERE pus.settlement_status = 'settled'
        AND pus.user_id = $1
        AND wa.settled = false
        AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
    `;
    let settledAdvanceParams = [userId];

    // 施工员只能查看自己参与工程的预支金额
    if (isConstructorUser) {
      settledAdvanceQuery += ` AND pw.user_id = $2 `;
      settledAdvanceParams.push(userId);
    }

    const settledAdvanceResult = await pool.query(settledAdvanceQuery, settledAdvanceParams);

    // 计算已结算实际金额（工程总金额 - 未结算的预支金额）
    const settledAmount = parseFloat(settledResult.rows[0].settled_amount) || 0;
    const settledAdvanceAmount = parseFloat(settledAdvanceResult.rows[0].settled_advance_amount) || 0;
    const settledActualAmount = settledAmount - settledAdvanceAmount;

    // ===== 5. 统计用户未确认的结算记录 =====
    let userUnconfirmedQuery = `
      SELECT
        COALESCE(SUM(ws.actual_amount), 0) AS unconfirmed_amount,
        COUNT(DISTINCT ws.id) AS unconfirmed_count
      FROM wage_settlements ws
      WHERE ws.confirmed = false
    `;
    let userUnconfirmedParams = [];

    // 施工员只能查看自己的未确认金额
    if (isConstructorUser) {
      userUnconfirmedQuery += `
        AND EXISTS (
          SELECT 1 FROM wage_distributions wd
          JOIN subprojects sp ON wd.subproject_id = sp.id
          JOIN project_workers pw ON sp.project_id = pw.project_id
          WHERE wd.created_at >= ws.start_month::timestamp
            AND wd.created_at < (ws.end_month + INTERVAL '1 day')::timestamp
            AND pw.user_id = $1
        )
      `;
      userUnconfirmedParams.push(userId);
    }

    const userUnconfirmedResult = await pool.query(userUnconfirmedQuery, userUnconfirmedParams);

    // ===== 6. 统计用户已确认的结算记录 =====
    let userConfirmedQuery = `
      SELECT
        COALESCE(SUM(ws.actual_amount), 0) AS confirmed_amount,
        COUNT(DISTINCT ws.id) AS confirmed_count
      FROM wage_settlements ws
      WHERE ws.confirmed = true
    `;
    let userConfirmedParams = [];

    // 施工员只能查看自己的已确认金额
    if (isConstructorUser) {
      userConfirmedQuery += `
        AND EXISTS (
          SELECT 1 FROM wage_distributions wd
          JOIN subprojects sp ON wd.subproject_id = sp.id
          JOIN project_workers pw ON sp.project_id = pw.project_id
          WHERE wd.created_at >= ws.start_month::timestamp
            AND wd.created_at < (ws.end_month + INTERVAL '1 day')::timestamp
            AND pw.user_id = $1
        )
      `;
      userConfirmedParams.push(userId);
    }

    const userConfirmedResult = await pool.query(userConfirmedQuery, userConfirmedParams);

    // ===== 7. 统计本月工程 =====
    let thisMonthQuery = `
      SELECT
        COALESCE(SUM(p.total_amount), 0) AS this_month_amount,
        COUNT(DISTINCT p.id) AS this_month_count
      FROM projects p
      WHERE EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
        AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
    `;
    let thisMonthParams = [];

    // 施工员只能查看自己参与的工程
    if (isConstructorUser) {
      thisMonthQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $1
        )
      `;
      thisMonthParams.push(userId);
    }

    const thisMonthResult = await pool.query(thisMonthQuery, thisMonthParams);

    // ===== 8. 组装结果 =====
    return {
      // 工程结算统计
      settling: {
        amount: parseFloat(settlingResult.rows[0].settling_amount) || 0,
        count: parseInt(settlingResult.rows[0].settling_count) || 0,
        thisMonthCount: parseInt(settlingResult.rows[0].this_month_count) || 0,
        description: '为结算项：所有"统计中"的工程总金额'
      },
      unsettled: {
        amount: parseFloat(unsettledResult.rows[0].unsettled_amount) || 0,
        count: parseInt(unsettledResult.rows[0].unsettled_count) || 0,
        thisMonthCount: parseInt(unsettledResult.rows[0].this_month_count) || 0,
        description: '未结算：所有"未结算"的工程总金额'
      },
      settled: {
        amount: settledActualAmount,
        count: parseInt(settledResult.rows[0].settled_count) || 0,
        thisMonthCount: parseInt(settledResult.rows[0].this_month_count) || 0,
        description: '今年结算：本年度已结算的工程总金额（已减去预支金额）'
      },
      // 用户确认统计
      userUnconfirmed: {
        amount: parseFloat(userUnconfirmedResult.rows[0].unconfirmed_amount) || 0,
        count: parseInt(userUnconfirmedResult.rows[0].unconfirmed_count) || 0,
        description: '财务已发放，用户未确认'
      },
      userConfirmed: {
        amount: parseFloat(userConfirmedResult.rows[0].confirmed_amount) || 0,
        count: parseInt(userConfirmedResult.rows[0].confirmed_count) || 0,
        description: '用户已确认'
      },
      // 本月工程统计
      thisMonth: {
        amount: parseFloat(thisMonthResult.rows[0].this_month_amount) || 0,
        count: parseInt(thisMonthResult.rows[0].this_month_count) || 0,
        description: '本月创建的工程总金额'
      }
    };
  },

  /**
   * 获取施工方案统计
   * 按施工方案分组统计已完工子项目的数量和金额
   * 业务规则：
   * - 施工员只能查看自己参与的工程数据
   * - 只统计已完工的子项目（sp.status = 'completed' 且 p.status = 'completed'）
   *
   * @param {Object} params - 参数对象
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @returns {Promise<Object>} 施工方案统计数据
   */
  async getConstructionPlanStatistics({ userId, role }) {
    const isConstructorUser = isConstructor({ role });

    let query = `
      SELECT
        cp.name AS construction_plan_name,
        COUNT(sp.id) AS completed_count,
        SUM(sp.amount) AS completed_amount
      FROM subprojects sp
      JOIN projects p ON sp.project_id = p.id
      JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      WHERE sp.status = 'completed'
        AND p.status = 'completed'
    `;
    let params = [];
    let paramIndex = 1;

    // 施工员只能查看自己参与的工程
    if (isConstructorUser) {
      query += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $${paramIndex}
        )
      `;
      params.push(userId);
      paramIndex++;
    }

    query += ` GROUP BY cp.name ORDER BY completed_amount DESC`;

    const result = await pool.query(query, params);

    return {
      constructionPlanStats: result.rows.map(row => ({
        construction_plan_name: row.construction_plan_name,
        completed_count: parseInt(row.completed_count) || 0,
        completed_amount: parseFloat(row.completed_amount) || 0
      }))
    };
  },

  /**
   * 获取人员统计
   * 按人员分组统计已完工子项目的数量、金额和参与工程数
   * 业务规则：
   * - 施工员只能查看自己的统计数据
   * - 只统计已完工的子项目（sp.status = 'completed' 且 p.status = 'completed'）
   * - 通过 project_workers 表关联用户和工程
   *
   * @param {Object} params - 参数对象
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @returns {Promise<Object>} 人员统计数据
   */
  async getWorkerStatistics({ userId, role }) {
    const isConstructorUser = isConstructor({ role });

    let query = `
      SELECT
        u.id AS user_id,
        u.username,
        u.nickname,
        COUNT(DISTINCT sp.id) AS completed_count,
        SUM(sp.amount) AS completed_amount,
        COUNT(DISTINCT p.id) AS project_count
      FROM subprojects sp
      JOIN projects p ON sp.project_id = p.id
      JOIN project_workers pw ON p.id = pw.project_id
      JOIN users u ON pw.user_id = u.id
      WHERE sp.status = 'completed'
        AND p.status = 'completed'
    `;
    let params = [];
    let paramIndex = 1;

    // 施工员只能查看自己的统计
    if (isConstructorUser) {
      query += ` AND u.id = $${paramIndex} `;
      params.push(userId);
      paramIndex++;
    }

    query += ` GROUP BY u.id, u.username, u.nickname ORDER BY completed_amount DESC`;

    const result = await pool.query(query, params);

    return {
      userStats: result.rows.map(row => ({
        user_id: row.user_id,
        username: row.username,
        nickname: row.nickname,
        completed_count: parseInt(row.completed_count) || 0,
        completed_amount: parseFloat(row.completed_amount) || 0,
        project_count: parseInt(row.project_count) || 0
      }))
    };
  }
};
