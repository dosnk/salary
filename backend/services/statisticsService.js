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
 * 解析月份列表为日期参数
 * 将 ['2025-01', '2025-02'] 转换为 ['2025-01-01', '2025-02-01']
 *
 * Bug13修复：原实现使用 `${m}-31` 构造月末日期，2月会产生 '2025-02-31' 非法日期
 * PostgreSQL 会自动转换为 '2025-03-03'，导致月份越界统计错误
 * 改为：每个月份只传一个 'YYYY-MM-01' 参数，配合 date_trunc 按月比较
 *
 * @param {string[]} monthList - 月份列表，格式 'YYYY-MM'
 * @returns {string[]} 日期参数数组，每个月份产生一个 'YYYY-MM-01' 参数
 */
const parseMonthDateParams = (monthList) => {
  return monthList.map(m => `${m}-01`);
};

/**
 * 构建多月份日期条件（参数化）
 * 生成类似 date_trunc('month', sp.created_at::date) = $1::date
 * OR date_trunc('month', sp.created_at::date) = $2::date
 *
 * Bug13修复：改用 date_trunc 按月比较，避免月末日期计算问题
 *
 * @param {string[]} monthList - 月份列表
 * @param {number} startIndex - 参数起始索引
 * @param {string} dateColumn - 日期列名（含表别名）
 * @returns {string} 参数化的日期条件 SQL
 */
const buildMonthDateConditions = (monthList, startIndex, dateColumn) => {
  return monthList
    .map((_, i) => `date_trunc('month', ${dateColumn}::date) = $${startIndex + i}::date`)
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
      // Bug2修复：补充 p.status = 'completed' 过滤，与 getConstructionPlanStatistics 保持一致
      let distributionQuery = `
        SELECT
          SUM(sp.amount) AS total_amount,
          COUNT(DISTINCT sp.id) AS total_distributions
        FROM subprojects sp
        JOIN projects p ON sp.project_id = p.id
        WHERE sp.status = 'completed'
          AND p.status = 'completed'
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

      // ===== 1b. 统计已结算的工资分配金额（P6：通过 v_project_user_settlement_status 过滤已结算子项目金额） =====
      // Bug2修复：补充 p.status = 'completed' 过滤，与主统计查询保持一致
      let settledDistributionQuery = `
        SELECT
          SUM(sp.amount) AS settled_amount
        FROM subprojects sp
        JOIN projects p ON sp.project_id = p.id
        WHERE sp.status = 'completed'
          AND p.status = 'completed'
          AND (${dateConditions})
      `;
      let settledDistributionParams = [...dateParams];
      let settledParamIndex = settledDistributionParams.length + 1;

      if (isConstructorUser) {
        // 施工员：只统计自己参与且自己已结算的工程
        settledDistributionQuery += `
          AND EXISTS (
            SELECT 1 FROM project_workers pw
            WHERE pw.project_id = p.id
              AND pw.user_id = $${settledParamIndex}
          )
          AND EXISTS (
            SELECT 1 FROM v_project_user_settlement_status pus
            WHERE pus.project_id = p.id
              AND pus.user_id = $${settledParamIndex}
              AND pus.settlement_status = 'settled'
          )
        `;
        settledDistributionParams.push(userId);
        settledParamIndex++;
      } else {
        // 管理员：统计存在已结算记录的工程（看全部）
        settledDistributionQuery += `
          AND EXISTS (
            SELECT 1 FROM v_project_user_settlement_status pus
            WHERE pus.project_id = p.id
              AND pus.settlement_status = 'settled'
          )
        `;
      }

      const settledDistributionResult = await pool.query(settledDistributionQuery, settledDistributionParams);

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
      const settledAmount = parseFloat(settledDistributionResult.rows[0].settled_amount) || 0;
      const totalAdvances = parseInt(advanceResult.rows[0].total_advances) || 0;
      const advanceAmount = parseFloat(advanceResult.rows[0].advance_amount) || 0;

      return {
        total_distributions: parseInt(distributionResult.rows[0].total_distributions) || 0,
        total_amount: totalAmount,
        settled_amount: settledAmount,
        unsettled_amount: totalAmount - settledAmount,
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

    // Bug4/5修复：原SQL的视图与subprojects做LEFT JOIN产生笛卡尔积导致金额重复N×M倍
    // 改为：先在子查询中聚合每个工程的子项目总额，再与视图关联，避免笛卡尔积
    // Bug5修复：施工员分支的 JOIN project_workers 写在 LEFT JOIN 之后导致SQL结构错误，改为WHERE EXISTS
    let query = `
      SELECT
        p.status,
        pus.settlement_status,
        COUNT(DISTINCT p.id) AS project_count,
        COALESCE(SUM(p.total_amount), 0) AS total_amount,
        COALESCE(SUM(sp_agg.subproject_count), 0) AS subproject_count,
        COALESCE(SUM(sp_agg.subproject_amount), 0) AS subproject_amount
      FROM projects p
      LEFT JOIN LATERAL (
        SELECT
          sp.project_id,
          COUNT(sp.id) AS subproject_count,
          COALESCE(SUM(sp.amount), 0) AS subproject_amount
        FROM subprojects sp
        WHERE sp.project_id = p.id AND sp.status = 'completed'
        GROUP BY sp.project_id
      ) sp_agg ON true
      LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
    `;

    // 施工员只能查看自己参与的工程（用WHERE EXISTS替代JOIN，避免改变LEFT JOIN语义）
    if (isConstructorUser) {
      query += `
        WHERE EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $1
        )
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

    // P7：判断是否使用自定义日期范围（startDate/endDate 同时存在时按区间过滤，否则保持原有本年/本月逻辑）
    const useDateRange = !!(startDate && endDate);

    // ===== 1. 统计"统计中"(settling)的工程金额（P3：金额源改为 subprojects.amount，与月度统计口径一致） =====
    // Bug1修复：管理员不应过滤 pus.user_id（视图对管理员默认unsettled，导致看不到数据）
    // 改为：施工员按 pus.user_id 过滤自己的结算状态；管理员聚合所有用户的settling工程
    let settlingQuery = `
      SELECT
        COALESCE(SUM(sp.amount), 0) AS settling_amount,
        COUNT(DISTINCT p.id) AS settling_count,
        COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
          AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
          THEN p.id END) AS this_month_count
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      LEFT JOIN subprojects sp ON p.id = sp.project_id AND sp.status = 'completed'
      WHERE pus.settlement_status = 'settling'
    `;
    let settlingParams = [];

    // 施工员只能查看自己参与工程的统计（按自己的结算状态过滤）
    if (isConstructorUser) {
      settlingQuery += `
        AND pus.user_id = $1
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $1
        )
      `;
      settlingParams.push(userId);
    }

    const settlingResult = await pool.query(settlingQuery, settlingParams);

    // ===== 2. 统计"未结算"(unsettled)的工程金额（P3：金额源改 subprojects；P8：状态含已完工工程） =====
    // Bug1修复：管理员不过滤 pus.user_id（LEFT JOIN 条件不限制user_id，聚合所有用户状态）
    let unsettledQuery = `
      SELECT
        COALESCE(SUM(sp.amount), 0) AS unsettled_amount,
        COUNT(DISTINCT p.id) AS unsettled_count,
        COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
          AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
          THEN p.id END) AS this_month_count
      FROM projects p
      LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      LEFT JOIN subprojects sp ON p.id = sp.project_id AND sp.status = 'completed'
      WHERE p.status IN ('constructing', 'completed')
        AND (pus.settlement_status IS NULL OR pus.settlement_status = 'unsettled')
    `;
    let unsettledParams = [];

    // 施工员只能查看自己参与工程的统计（按自己结算状态过滤）
    if (isConstructorUser) {
      unsettledQuery = `
        SELECT
          COALESCE(SUM(sp.amount), 0) AS unsettled_amount,
          COUNT(DISTINCT p.id) AS unsettled_count,
          COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
            AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
            THEN p.id END) AS this_month_count
        FROM projects p
        LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $1
        LEFT JOIN subprojects sp ON p.id = sp.project_id AND sp.status = 'completed'
        WHERE p.status IN ('constructing', 'completed')
          AND (pus.settlement_status IS NULL OR pus.settlement_status = 'unsettled')
          AND EXISTS (
            SELECT 1 FROM project_workers pw
            WHERE pw.project_id = p.id
              AND pw.user_id = $1
          )
      `;
      unsettledParams.push(userId);
    }

    const unsettledResult = await pool.query(unsettledQuery, unsettledParams);

    // ===== 3. 统计"已结算"(settled)的工程金额（P3：金额源改 subprojects；P7：支持自定义日期范围） =====
    // Bug1修复：管理员不应过滤 pus.user_id；施工员按自己结算状态过滤
    let settledQuery = `
      SELECT
        COALESCE(SUM(sp.amount), 0) AS settled_amount,
        COUNT(DISTINCT p.id) AS settled_count,
        COUNT(DISTINCT CASE WHEN EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE)
          AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE)
          THEN p.id END) AS this_month_count
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus ON p.id = pus.project_id
      LEFT JOIN subprojects sp ON p.id = sp.project_id AND sp.status = 'completed'
      WHERE pus.settlement_status = 'settled'
    `;
    let settledParams = [];
    let settledParamIndex = 1;

    // P7：时间过滤，传入日期范围时按区间过滤，否则保持本年过滤
    if (useDateRange) {
      settledQuery += ` AND p.created_at >= $${settledParamIndex} AND p.created_at <= $${settledParamIndex + 1} `;
      settledParams.push(startDate, endDate);
      settledParamIndex += 2;
    } else {
      settledQuery += ` AND EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE) `;
    }

    // 施工员只能查看自己参与工程的统计（按自己结算状态过滤）
    if (isConstructorUser) {
      settledQuery += `
        AND pus.user_id = $${settledParamIndex}
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $${settledParamIndex}
        )
      `;
      settledParams.push(userId);
      settledParamIndex++;
    }

    const settledResult = await pool.query(settledQuery, settledParams);

    // ===== 4. 统计未结算的预支金额（P5：去除 project_workers/projects 笛卡尔积，直接查 wage_advances） =====
    let settledAdvanceQuery = `
      SELECT
        COALESCE(SUM(wa.advance_amount), 0) AS settled_advance_amount
      FROM wage_advances wa
      WHERE wa.user_id = $1
        AND wa.settled = false
    `;
    let settledAdvanceParams = [userId];

    const settledAdvanceResult = await pool.query(settledAdvanceQuery, settledAdvanceParams);

    // Bug6修复：已结算实际金额应使用 wage_settlements.actual_amount 聚合
    // 原公式"已结算工程总额 - 未结算预支"逻辑混乱（两个概念不相关）
    // 改为：从已确认的结算单中聚合 actual_amount
    let settledActualQuery = `
      SELECT
        COALESCE(SUM(ws.actual_amount), 0) AS settled_actual_amount
      FROM wage_settlements ws
      WHERE ws.confirmed = true
    `;
    let settledActualParams = [];

    // 施工员只能查看自己已确认结算单的实际金额
    if (isConstructorUser) {
      settledActualQuery += `
        AND EXISTS (
          SELECT 1 FROM wage_distributions wd
          JOIN subprojects sp ON wd.subproject_id = sp.id
          JOIN project_workers pw ON sp.project_id = pw.project_id
          WHERE wd.settlement_id = ws.id
            AND pw.user_id = $1
        )
      `;
      settledActualParams.push(userId);
    }

    const settledActualResult = await pool.query(settledActualQuery, settledActualParams);

    // 已结算工程总额（来自subprojects.amount聚合）
    const settledAmount = parseFloat(settledResult.rows[0].settled_amount) || 0;
    // 未结算预支总额
    const settledAdvanceAmount = parseFloat(settledAdvanceResult.rows[0].settled_advance_amount) || 0;
    // Bug6修复：已结算实际金额取自己确认结算单的actual_amount聚合
    const settledActualAmount = parseFloat(settledActualResult.rows[0].settled_actual_amount) || 0;

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

    // ===== 7. 统计本月/区间内工程（P3：金额源改 subprojects；P7：支持自定义日期范围） =====
    let thisMonthQuery = `
      SELECT
        COALESCE(SUM(sp.amount), 0) AS this_month_amount,
        COUNT(DISTINCT p.id) AS this_month_count
      FROM projects p
      LEFT JOIN subprojects sp ON p.id = sp.project_id AND sp.status = 'completed'
      WHERE 
    `;
    let thisMonthParams = [];
    let thisMonthParamIndex = 1;

    // P7：时间过滤，传入日期范围时按区间过滤，否则保持本月过滤
    if (useDateRange) {
      thisMonthQuery += ` p.created_at >= $${thisMonthParamIndex} AND p.created_at <= $${thisMonthParamIndex + 1} `;
      thisMonthParams.push(startDate, endDate);
      thisMonthParamIndex += 2;
    } else {
      thisMonthQuery += ` EXTRACT(YEAR FROM p.created_at) = EXTRACT(YEAR FROM CURRENT_DATE) AND EXTRACT(MONTH FROM p.created_at) = EXTRACT(MONTH FROM CURRENT_DATE) `;
    }

    // 施工员只能查看自己参与的工程
    if (isConstructorUser) {
      thisMonthQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id
            AND pw.user_id = $${thisMonthParamIndex}
        )
      `;
      thisMonthParams.push(userId);
      thisMonthParamIndex++;
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
   * 获取仪表盘卡片统计数据（统计页顶部4个卡片）
   * 所有计算由后端完成，前端只做展示。
   *
   * 卡片1 - 待结算工程（已完工未结算）：
   *   - unsettled_project_count: settling状态工程份数（工程级，按project_id去重）
   *   - unsettled_amount: 个人应收总额（wage_distributions金额合计，个人级）
   *
   * 卡片2 - 预支金额：
   *   - advance_count: 未结算预支条数
   *   - advance_total: 未结算预支总金额
   *
   * 卡片3 - 今年工程量（所有状态）：
   *   - year_project_count: 今年创建的所有工程份数（不限结算状态，按project_id去重）
   *   - year_project_amount: 今年创建的所有工程总额（projects.total_amount之和，工程级）
   *
   * 卡片4 - 月均工资：
   *   - monthly_avg_count: 月均份数 = 今年已结算工程数 / 当前月份（工程级）
   *   - monthly_avg_amount: 月均金额 = 今年个人已结算工资 / 当前月份（个人级）
   *
   * @param {Object} params
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @returns {Promise<Object>} 4个卡片的聚合数据
   */
  async getDashboard({ userId, role }) {
    const isConstructorUser = isConstructor({ role });
    const currentYear = new Date().getFullYear();
    const currentMonth = new Date().getMonth() + 1;
    const monthDivisor = Math.max(currentMonth, 1);

    logger.info(`[getDashboard] userId=${userId}, role=${role}, isConstructor=${isConstructorUser}, year=${currentYear}, month=${currentMonth}`);

    // ========== 卡片1：待结算工程（已完工未结算，settling状态） ==========
    // 份数（工程级）：COUNT(DISTINCT p.id)
    // 金额（个人级）：按 salary_distribution 分配方式计算个人分摊金额合计
    //   - average：子项目总额 / 施工人数
    //   - work_days：子项目总额 * (个人工日 / 总工日)
    // 关键：settling 工程尚未结算，wage_distributions 表无记录，
    //       必须从 subprojects.amount 聚合并按 calculateUserWage 逻辑计算个人分摊
    // 注意：pus必须限制 user_id，否则多用户时视图产生多行导致金额重复累加
    let settlingQuery = `
      SELECT
        COUNT(DISTINCT p.id) AS unsettled_project_count,
        COALESCE(SUM(
          CASE
            WHEN p.salary_distribution = 'average' THEN
              sp_agg.sp_total / GREATEST(COALESCE(wc.worker_count, 0), 1)
            WHEN p.salary_distribution = 'work_days' THEN
              CASE
                WHEN COALESCE(tw.total_workdays, 0) > 0 THEN
                  sp_agg.sp_total * (COALESCE(uw.user_workdays, 0) / tw.total_workdays)
                ELSE
                  sp_agg.sp_total / GREATEST(COALESCE(wc.worker_count, 0), 1)
              END
            ELSE
              sp_agg.sp_total / GREATEST(COALESCE(wc.worker_count, 0), 1)
          END
        ), 0) AS unsettled_amount
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus
        ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settling'
      -- 子项目金额聚合（与 getProjects 保持一致，不限制子项目状态）
      LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(sp.amount), 0) AS sp_total
        FROM subprojects sp
        WHERE sp.project_id = p.id
      ) sp_agg ON true
      -- 施工人数（与 getProjects 保持一致，用 COUNT(DISTINCT user_id)）
      LEFT JOIN LATERAL (
        SELECT COUNT(DISTINCT pw2.user_id) AS worker_count
        FROM project_workers pw2
        WHERE pw2.project_id = p.id
      ) wc ON true
      -- 总工日
      LEFT JOIN LATERAL (
        SELECT COALESCE(SUM(pw3.workdays), 0) AS total_workdays
        FROM project_workers pw3
        WHERE pw3.project_id = p.id
      ) tw ON true
      -- 当前用户个人工日（admin/documenter 可能不在 project_workers 中，用 LEFT JOIN 保留工程）
      LEFT JOIN LATERAL (
        SELECT COALESCE(pw4.workdays, 0) AS user_workdays
        FROM project_workers pw4
        WHERE pw4.project_id = p.id AND pw4.user_id = $1
      ) uw ON true
      WHERE 1=1
    `;
    let settlingParams = [userId];

    // 施工员额外校验必须是项目参与者
    if (isConstructorUser) {
      settlingQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
      `;
    }

    const settlingResult = await pool.query(settlingQuery, settlingParams);
    const unsettledProjectCount = parseInt(settlingResult.rows[0].unsettled_project_count) || 0;
    const unsettledAmount = parseFloat(settlingResult.rows[0].unsettled_amount) || 0;

    // ========== 卡片2：预支金额（未结算预支） ==========
    const advanceResult = await pool.query(`
      SELECT
        COUNT(*) AS advance_count,
        COALESCE(SUM(wa.advance_amount), 0) AS advance_total
      FROM wage_advances wa
      WHERE wa.user_id = $1 AND wa.settled = false
    `, [userId]);
    const advanceCount = parseInt(advanceResult.rows[0].advance_count) || 0;
    const advanceTotal = parseFloat(advanceResult.rows[0].advance_total) || 0;

    // ========== 卡片3：今年工程量（所有状态） ==========
    // 今年创建的所有工程（不限结算状态），份数按 project_id 去重，金额为工程级 total_amount 合计
    // 注意：不关联 pus 视图，直接查 projects 表，避免多用户视图产生重复行导致 total_amount 重复累加
    let yearProjectQuery = `
      SELECT
        COUNT(DISTINCT p.id) AS year_project_count,
        COALESCE(SUM(COALESCE(p.total_amount, 0)), 0) AS year_project_amount
      FROM projects p
      WHERE EXTRACT(YEAR FROM p.created_at) = $2
    `;
    let yearProjectParams = [userId, currentYear];

    if (isConstructorUser) {
      yearProjectQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
      `;
    }

    const yearProjectResult = await pool.query(yearProjectQuery, yearProjectParams);
    const yearProjectCount = parseInt(yearProjectResult.rows[0].year_project_count) || 0;
    const yearProjectAmount = parseFloat(yearProjectResult.rows[0].year_project_amount) || 0;

    // ========== 卡片4：月均工资 ==========
    // 份数（工程级）：今年已结算工程数 / 当前月份
    // 金额（个人级）：今年个人已结算工资合计 / 当前月份
    // 说明：wage_distributions 仅在结算时生成，天然限定为已结算数据

    // 4.1 今年已结算工程数（工程级，用于月均份数）
    // 注意：pus必须限制 user_id，否则多用户时同一工程多行导致计数重复
    let yearSettledCountQuery = `
      SELECT COUNT(DISTINCT p.id) AS year_settled_count
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus
        ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settled'
      WHERE EXTRACT(YEAR FROM p.created_at) = $2
    `;
    let yearSettledCountParams = [userId, currentYear];

    if (isConstructorUser) {
      yearSettledCountQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
      `;
    }

    const yearSettledCountResult = await pool.query(yearSettledCountQuery, yearSettledCountParams);
    const yearSettledCount = parseInt(yearSettledCountResult.rows[0].year_settled_count) || 0;

    // 4.2 今年个人已结算工资合计（个人级，用于月均金额）
    // 注意：pus必须限制 user_id，否则多用户时JOIN产生笛卡尔积导致 wd.amount 重复累加
    let yearSettledUserQuery = `
      SELECT COALESCE(SUM(wd.amount), 0) AS year_settled_user_amount
      FROM projects p
      INNER JOIN v_project_user_settlement_status pus
        ON p.id = pus.project_id AND pus.user_id = $1 AND pus.settlement_status = 'settled'
      INNER JOIN subprojects sp ON p.id = sp.project_id
      INNER JOIN wage_distributions wd ON sp.id = wd.subproject_id AND wd.user_id = $1
      WHERE EXTRACT(YEAR FROM p.created_at) = $2
    `;
    let yearSettledUserParams = [userId, currentYear];

    if (isConstructorUser) {
      yearSettledUserQuery += `
        AND EXISTS (
          SELECT 1 FROM project_workers pw
          WHERE pw.project_id = p.id AND pw.user_id = $1
        )
      `;
    }

    const yearSettledUserResult = await pool.query(yearSettledUserQuery, yearSettledUserParams);
    const yearSettledUserAmount = parseFloat(yearSettledUserResult.rows[0].year_settled_user_amount) || 0;

    // 月均工资 = 今年个人已结算金额 / 当前月份（至少为1避免除零）
    const monthlyAvgCount = yearSettledCount / monthDivisor;
    const monthlyAvgAmount = yearSettledUserAmount / monthDivisor;

    const result = {
      // 卡片1：待结算工程
      unsettled_project_count: unsettledProjectCount,
      unsettled_amount: Math.round(unsettledAmount * 100) / 100,
      // 卡片2：预支金额
      advance_count: advanceCount,
      advance_total: Math.round(advanceTotal * 100) / 100,
      // 卡片3：今年工程量（所有状态）
      year_project_count: yearProjectCount,
      year_project_amount: Math.round(yearProjectAmount * 100) / 100,
      // 卡片4：月均工资
      monthly_avg_count: Math.round(monthlyAvgCount * 10) / 10,
      monthly_avg_amount: Math.round(monthlyAvgAmount * 100) / 100
    };
    logger.info(`[getDashboard] result for user ${userId}: ${JSON.stringify(result)}`);
    return result;
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

    // Bug14修复：原SQL使用 CROSS JOIN subprojects sp + EXISTS 过滤，产生 users × subprojects 笛卡尔积，性能差
    // 改为：直接 JOIN project_workers 关联用户与工程，再 JOIN subprojects 获取子项目数据
    let query = `
      SELECT
        u.id AS user_id,
        u.username,
        u.nickname,
        COUNT(DISTINCT sp.id) AS completed_count,
        SUM(sp.amount) AS completed_amount,
        COUNT(DISTINCT p.id) AS project_count
      FROM users u
      JOIN project_workers pw ON u.id = pw.user_id
      JOIN projects p ON pw.project_id = p.id
      JOIN subprojects sp ON sp.project_id = p.id
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
