/**
 * 结算数据访问层（Repository）
 *
 * 将 controller 中的数据库操作抽离到这一层，实现数据访问逻辑与业务逻辑的分离。
 * 所有 SQL 均使用参数化查询，禁止字符串拼接，防止 SQL 注入。
 */

const pool = require('../config/database');
const logger = require('../config/logger');

/**
 * 获取查询执行器
 * 如果传入了 client（事务场景），则使用 client；否则使用连接池
 * @param {object|null} client - pg Client 实例（事务场景）
 * @returns {Function} query 函数
 */
const getQuery = (client) => {
  return client ? client.query.bind(client) : pool.query.bind(pool);
};

module.exports = {
  // ==================== 结算记录 ====================

  /**
   * 创建结算记录
   * @param {object} data - 结算记录数据
   * @param {string} data.settlementNo - 结算单号
   * @param {number} data.projectId - 主工程ID
   * @param {string} data.projectIds - 工程ID列表（JSON字符串）
   * @param {number} data.userId - 用户ID
   * @param {string} data.startMonth - 开始月份
   * @param {string} data.endMonth - 结束月份
   * @param {number} data.totalAmount - 总金额
   * @param {number} data.advanceAmount - 预支金额
   * @param {number} data.actualAmount - 实发金额
   * @param {boolean} data.confirmed - 是否已确认
   * @param {number} data.settledBy - 结算操作人ID
   * @param {string} data.remark - 备注
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<object>} 创建的结算记录
   */
  async createSettlement(data, client = null) {
    const query = getQuery(client);
    const sql = `
      INSERT INTO wage_settlements (
        settlement_no, project_id, project_ids, user_id,
        start_month, end_month, total_amount, advance_amount, actual_amount,
        confirmed, confirmed_at, settled_by, remark
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, CURRENT_TIMESTAMP, $11, $12)
      RETURNING *
    `;
    const params = [
      data.settlementNo,
      data.projectId,
      data.projectIds,
      data.userId,
      data.startMonth,
      data.endMonth,
      data.totalAmount,
      data.advanceAmount,
      data.actualAmount,
      data.confirmed || false,
      data.settledBy,
      data.remark || ''
    ];
    const result = await query(sql, params);
    return result.rows[0];
  },

  /**
   * 根据ID查询结算记录（含关联信息）
   * @param {number} id - 结算记录ID
   * @returns {Promise<object|null>} 结算记录
   */
  async findSettlementById(id) {
    const sql = `
      SELECT
        ws.*,
        p.name as project_name,
        u.username as user_name,
        u.nickname as user_nickname,
        su.username as settled_by_name
      FROM wage_settlements ws
      LEFT JOIN projects p ON ws.project_id = p.id
      LEFT JOIN users u ON ws.user_id = u.id
      LEFT JOIN users su ON ws.settled_by = su.id
      WHERE ws.id = $1
    `;
    const result = await pool.query(sql, [id]);
    return result.rows[0] || null;
  },

  /**
   * 更新结算记录
   * @param {number} id - 结算记录ID
   * @param {object} updates - 需要更新的字段键值对
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<object|null>} 更新后的结算记录
   */
  async updateSettlement(id, updates, client = null) {
    const query = getQuery(client);
    const keys = Object.keys(updates);
    if (keys.length === 0) {
      // 没有需要更新的字段，直接查询返回
      return await this.findSettlementById(id);
    }

    // 动态构建 SET 子句，参数化赋值
    const setClauses = [];
    const params = [];
    let paramIndex = 1;

    for (const key of keys) {
      // 将驼峰命名转为下划线命名（如 confirmedAt -> confirmed_at）
      const columnName = key.replace(/[A-Z]/g, (match) => '_' + match.toLowerCase());
      setClauses.push(`${columnName} = $${paramIndex}`);
      params.push(updates[key]);
      paramIndex++;
    }

    // id 作为 WHERE 条件
    params.push(id);
    const sql = `
      UPDATE wage_settlements
      SET ${setClauses.join(', ')}
      WHERE id = $${paramIndex}
      RETURNING *
    `;
    const result = await query(sql, params);
    return result.rows[0] || null;
  },

  // ==================== 结算列表 ====================

  /**
   * 筛选查询结算列表（带分页）
   * @param {object} filters - 筛选条件
   * @param {number} [filters.userId] - 用户ID（施工员只能看自己的记录）
   * @param {number} [filters.projectId] - 工程ID
   * @param {string} [filters.startDate] - 开始日期
   * @param {string} [filters.endDate] - 结束日期
   * @param {boolean} [filters.confirmed] - 确认状态
   * @param {number} [filters.page=1] - 页码
   * @param {number} [filters.size=10] - 每页条数
   * @returns {Promise<{rows: Array, total: number, page: number, size: number}>}
   */
  async listSettlements(filters) {
    const {
      userId,
      projectId,
      startDate,
      endDate,
      confirmed,
      page = 1,
      size = 10
    } = filters;

    let whereClauses = ['1=1'];
    const params = [];
    let paramIndex = 1;

    // 按用户筛选（施工员只能查看自己的结算记录）
    if (userId) {
      whereClauses.push(`ws.user_id = $${paramIndex}`);
      params.push(userId);
      paramIndex++;
    }

    // 按工程筛选
    if (projectId) {
      whereClauses.push(`ws.project_id = $${paramIndex}`);
      params.push(projectId);
      paramIndex++;
    }

    // 按开始月份筛选
    // Bug12修复：原TO_CHAR与传入的日期格式不匹配（前端传 'YYYY-MM-DD' 时比较失败）
    // 改为：统一取前7位 'YYYY-MM' 格式比较，兼容月份和日期两种入参
    if (startDate) {
      const startMonthStr = String(startDate).substring(0, 7); // 'YYYY-MM'
      whereClauses.push(`TO_CHAR(ws.start_month, 'YYYY-MM') = $${paramIndex}`);
      params.push(startMonthStr);
      paramIndex++;
    }

    // 按结束月份筛选
    // Bug12修复：同 startDate，统一取 'YYYY-MM' 格式比较
    if (endDate) {
      const endMonthStr = String(endDate).substring(0, 7); // 'YYYY-MM'
      whereClauses.push(`TO_CHAR(ws.end_month, 'YYYY-MM') = $${paramIndex}`);
      params.push(endMonthStr);
      paramIndex++;
    }

    // 按确认状态筛选
    if (confirmed !== undefined) {
      whereClauses.push(`ws.confirmed = $${paramIndex}`);
      params.push(confirmed);
      paramIndex++;
    }

    const whereClause = whereClauses.join(' AND ');

    // 查询总数
    const countSql = `
      SELECT COUNT(*) as total
      FROM wage_settlements ws
      LEFT JOIN projects p ON ws.project_id = p.id
      LEFT JOIN users u ON ws.user_id = u.id
      LEFT JOIN users su ON ws.settled_by = su.id
      WHERE ${whereClause}
    `;
    const countResult = await pool.query(countSql, params);
    const total = parseInt(countResult.rows[0].total);

    // 查询分页数据
    const dataSql = `
      SELECT
        ws.id,
        ws.settlement_no,
        ws.project_id,
        p.name as project_name,
        ws.user_id,
        u.username as user_name,
        u.nickname as user_nickname,
        ws.start_month,
        ws.end_month,
        ws.total_amount,
        ws.advance_amount,
        ws.actual_amount,
        ws.confirmed,
        ws.confirmed_at,
        ws.paid,
        ws.paid_at,
        ws.settled_by,
        su.username as settled_by_name,
        ws.settled_at,
        ws.remark
      FROM wage_settlements ws
      LEFT JOIN projects p ON ws.project_id = p.id
      LEFT JOIN users u ON ws.user_id = u.id
      LEFT JOIN users su ON ws.settled_by = su.id
      WHERE ${whereClause}
      ORDER BY ws.settled_at DESC
      LIMIT $${paramIndex} OFFSET $${paramIndex + 1}
    `;
    params.push(size, (page - 1) * size);

    const result = await pool.query(dataSql, params);
    return {
      rows: result.rows,
      total,
      page: parseInt(page),
      size: parseInt(size)
    };
  },

  // ==================== 工资分配 ====================

  /**
   * 创建工资分配记录
   * @param {object} data - 工资分配数据
   * @param {number} data.subprojectId - 子项目ID
   * @param {number} data.userId - 用户ID
   * @param {number} data.workdays - 工日数
   * @param {number} data.amount - 金额
   * @param {number} [data.quantity] - 数量
   * @param {number} [data.settlementId] - 结算ID
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<object>} 创建的工资分配记录
   */
  async createWageDistribution(data, client = null) {
    const query = getQuery(client);
    const sql = `
      INSERT INTO wage_distributions (subproject_id, user_id, workdays, amount, quantity, settlement_id, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, CURRENT_TIMESTAMP)
      RETURNING *
    `;
    const params = [
      data.subprojectId,
      data.userId,
      data.workdays,
      data.amount,
      data.quantity || null,
      data.settlementId || null
    ];
    const result = await query(sql, params);
    return result.rows[0];
  },

  /**
   * 查询工资分配记录
   * @param {number} subprojectId - 子项目ID
   * @param {number} userId - 用户ID
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<object|null>} 工资分配记录
   */
  async findWageDistribution(subprojectId, userId, client = null) {
    const query = getQuery(client);
    const sql = `
      SELECT *
      FROM wage_distributions
      WHERE subproject_id = $1 AND user_id = $2
    `;
    const result = await query(sql, [subprojectId, userId]);
    return result.rows[0] || null;
  },

  /**
   * 更新工资分配记录
   * @param {number} subprojectId - 子项目ID
   * @param {number} userId - 用户ID
   * @param {object} updates - 需要更新的字段键值对（如 settlementId, quantity, amount）
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<number>} 受影响的行数
   */
  async updateWageDistribution(subprojectId, userId, updates, client = null) {
    const query = getQuery(client);
    const keys = Object.keys(updates);
    if (keys.length === 0) return 0;

    const setClauses = [];
    const params = [];
    let paramIndex = 1;

    for (const key of keys) {
      // 将驼峰命名转为下划线命名
      const columnName = key.replace(/[A-Z]/g, (match) => '_' + match.toLowerCase());
      setClauses.push(`${columnName} = $${paramIndex}`);
      params.push(updates[key]);
      paramIndex++;
    }

    params.push(subprojectId, userId);
    const sql = `
      UPDATE wage_distributions
      SET ${setClauses.join(', ')}
      WHERE subproject_id = $${paramIndex} AND user_id = $${paramIndex + 1} AND settlement_id IS NULL
    `;
    const result = await query(sql, params);
    return result.rowCount;
  },

  /**
   * 批量插入工资分配记录（每批1000条，避免单次SQL过大）
   * @param {Array<Array>} values - 二维数组，每行格式: [subprojectId, userId, workdays, amount, quantity, settlementId]
   * @param {object|null} client - pg Client 实例（事务场景，必须传入以保持事务一致性）
   * @returns {Promise<number>} 插入的总行数
   */
  async batchInsertWageDistributions(values, client = null) {
    if (!values || values.length === 0) return 0;

    const query = getQuery(client);
    const BATCH_SIZE = 1000; // 每批最多1000条
    let totalInserted = 0;

    for (let i = 0; i < values.length; i += BATCH_SIZE) {
      const batch = values.slice(i, i + BATCH_SIZE);

      // 构建参数化占位符：每行6个参数
      const placeholders = batch.map((_, idx) =>
        `($${idx * 6 + 1}, $${idx * 6 + 2}, $${idx * 6 + 3}, $${idx * 6 + 4}, $${idx * 6 + 5}, $${idx * 6 + 6}, CURRENT_TIMESTAMP)`
      ).join(', ');

      // 将二维数组展平为一维参数数组
      const flatParams = batch.flat();

      const sql = `
        INSERT INTO wage_distributions (subproject_id, user_id, workdays, amount, quantity, settlement_id, created_at)
        VALUES ${placeholders}
      `;
      const result = await query(sql, flatParams);
      totalInserted += result.rowCount;
    }

    return totalInserted;
  },

  // ==================== 预支 ====================

  /**
   * 获取用户未结算的预支记录
   * @param {number} userId - 用户ID
   * @returns {Promise<Array>} 未结算的预支记录列表
   */
  async getUserUnsettledAdvances(userId) {
    const sql = `
      SELECT id, user_id, advance_amount, advance_date, remark
      FROM wage_advances
      WHERE user_id = $1 AND settled = false
      ORDER BY advance_date DESC
    `;
    const result = await pool.query(sql, [userId]);
    return result.rows;
  },

  /**
   * 获取用户预支总额（未结算）
   * @param {number} userId - 用户ID
   * @returns {Promise<number>} 未结算预支总额
   */
  async getUserAdvanceTotal(userId) {
    const sql = `
      SELECT COALESCE(SUM(advance_amount), 0) as total
      FROM wage_advances
      WHERE user_id = $1 AND settled = false
    `;
    const result = await pool.query(sql, [userId]);
    return parseFloat(result.rows[0].total);
  },

  /**
   * 标记预支记录为已结算
   * @param {Array<number>} advanceIds - 预支记录ID数组
   * @param {number} settlementId - 结算记录ID
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<number>} 受影响的行数
   */
  async settleAdvances(advanceIds, settlementId, client = null) {
    if (!advanceIds || advanceIds.length === 0) return 0;

    const query = getQuery(client);
    const sql = `
      UPDATE wage_advances
      SET settled = true, settlement_id = $1
      WHERE id = ANY($2)
    `;
    const result = await query(sql, [settlementId, advanceIds]);
    return result.rowCount;
  },

  // ==================== 结算快照 ====================

  /**
   * 创建结算快照
   * 查询结算记录、工资分配、预支记录后组装快照数据并插入快照表
   * @param {object} snapshotData - 快照数据
   * @param {number} snapshotData.settlementId - 结算ID
   * @param {number} snapshotData.userId - 用户ID
   * @param {string} snapshotData.settlementNo - 结算单号
   * @param {number} snapshotData.projectId - 主工程ID
   * @param {string} snapshotData.projectName - 工程名称
   * @param {string} snapshotData.startMonth - 开始月份
   * @param {string} snapshotData.endMonth - 结束月份
   * @param {number} snapshotData.totalAmount - 总金额
   * @param {number} snapshotData.advanceAmount - 预支金额
   * @param {number} snapshotData.actualAmount - 实发金额
   * @param {boolean} snapshotData.confirmed - 是否已确认
   * @param {string} snapshotData.confirmedAt - 确认时间
   * @param {number} snapshotData.settledBy - 结算操作人ID
   * @param {string} snapshotData.settledByUsername - 结算操作人用户名
   * @param {string} snapshotData.settledByNickname - 结算操作人昵称
   * @param {string} snapshotData.settledAt - 结算时间
   * @param {string} snapshotData.remark - 备注
   * @param {string} snapshotData.username - 用户名
   * @param {string} snapshotData.nickname - 用户昵称
   * @param {object} snapshotData.projectsSnapshot - 工程快照数据
   * @param {object} snapshotData.plansSnapshot - 施工方案快照数据
   * @param {object} snapshotData.advancesSnapshot - 预支快照数据
   * @param {object} snapshotData.calculationSnapshot - 计算结果快照数据
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<void>}
   */
  async createSnapshot(snapshotData, client = null) {
    const query = getQuery(client);
    const sql = `
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
    `;
    const params = [
      snapshotData.settlementId,
      snapshotData.settlementNo,
      snapshotData.projectId,
      snapshotData.projectName,
      snapshotData.userId,
      snapshotData.username || '',
      snapshotData.nickname || '',
      snapshotData.startMonth,
      snapshotData.endMonth,
      snapshotData.totalAmount,
      snapshotData.advanceAmount,
      snapshotData.actualAmount,
      snapshotData.confirmed,
      snapshotData.confirmedAt || null,
      snapshotData.settledBy,
      snapshotData.settledByUsername || '',
      snapshotData.settledByNickname || '',
      snapshotData.settledAt || null,
      snapshotData.remark || '',
      JSON.stringify(snapshotData.projectsSnapshot || []),
      JSON.stringify(snapshotData.plansSnapshot || []),
      JSON.stringify(snapshotData.advancesSnapshot || []),
      JSON.stringify(snapshotData.calculationSnapshot || {})
    ];
    await query(sql, params);
  },

  /**
   * 获取用户结算快照列表（带分页）
   * @param {number} userId - 用户ID
   * @param {object} filters - 筛选条件
   * @param {boolean} [filters.confirmed] - 确认状态
   * @param {number} [filters.page=1] - 页码
   * @param {number} [filters.size=10] - 每页条数
   * @returns {Promise<{rows: Array, total: number, page: number, size: number}>}
   */
  async getSnapshotsByUser(userId, filters = {}) {
    const { confirmed, page = 1, size = 10 } = filters;

    let whereClauses = ['user_id = $1'];
    const params = [userId];
    let paramIndex = 2;

    // 按确认状态筛选
    if (confirmed !== undefined) {
      whereClauses.push(`confirmed = $${paramIndex}`);
      params.push(confirmed);
      paramIndex++;
    }

    const whereClause = whereClauses.join(' AND ');

    // 查询总数
    const countSql = `SELECT COUNT(*) as total FROM wage_settlement_snapshots WHERE ${whereClause}`;
    const countResult = await pool.query(countSql, params);
    const total = parseInt(countResult.rows[0].total);

    // 查询分页数据
    const dataSql = `
      SELECT
        id, settlement_id, settlement_no, project_id, project_name,
        user_id, username, nickname,
        start_month, end_month,
        total_amount, advance_amount, actual_amount,
        confirmed, confirmed_at,
        settled_by, settled_by_username, settled_by_nickname,
        settled_at, remark,
        projects_snapshot, plans_snapshot, advances_snapshot, calculation_snapshot
      FROM wage_settlement_snapshots
      WHERE ${whereClause}
      ORDER BY settled_at DESC
      LIMIT $${paramIndex} OFFSET $${paramIndex + 1}
    `;
    params.push(size, (page - 1) * size);

    const result = await pool.query(dataSql, params);
    return {
      rows: result.rows,
      total,
      page: parseInt(page),
      size: parseInt(size)
    };
  },

  /**
   * 获取指定结算单的快照（按用户）
   * @param {number} settlementId - 结算ID
   * @param {number} userId - 用户ID
   * @returns {Promise<object|null>} 快照记录
   */
  async getSnapshotBySettlement(settlementId, userId) {
    const sql = `
      SELECT
        id, settlement_id, settlement_no, project_id, project_name,
        user_id, username, nickname,
        start_month, end_month,
        total_amount, advance_amount, actual_amount,
        confirmed, confirmed_at,
        settled_by, settled_by_username, settled_by_nickname,
        settled_at, remark,
        projects_snapshot, plans_snapshot, advances_snapshot, calculation_snapshot
      FROM wage_settlement_snapshots
      WHERE settlement_id = $1 AND user_id = $2
    `;
    const result = await pool.query(sql, [settlementId, userId]);
    return result.rows[0] || null;
  },

  // ==================== 结算状态 ====================

  /**
   * 更新工程用户结算状态（upsert 模式）
   * 如果记录已存在则更新，不存在则插入
   * @param {Array<number>} projectIds - 工程ID数组
   * @param {number} userId - 用户ID
   * @param {number} settlementId - 结算ID
   * @param {string} status - 结算状态（如 'settled'）
   * @param {object|null} client - pg Client 实例（事务场景）
   * @returns {Promise<void>}
   */
  async updateProjectUserStatus(projectIds, userId, settlementId, status, client = null) {
    if (!projectIds || projectIds.length === 0) return;

    const query = getQuery(client);

    // 动态构建 VALUES 子句，每个工程一条记录
    const valuesPlaceholders = projectIds.map((_, idx) =>
      `($${idx + 1}, $${projectIds.length + 1}, $${projectIds.length + 2}, $${projectIds.length + 3}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`
    ).join(', ');

    const sql = `
      INSERT INTO project_user_status (project_id, user_id, settlement_status, settlement_id, settled_at, created_at, updated_at)
      VALUES ${valuesPlaceholders}
      ON CONFLICT (project_id, user_id)
      DO UPDATE SET
        settlement_status = EXCLUDED.settlement_status,
        settlement_id = EXCLUDED.settlement_id,
        settled_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    `;
    const params = [...projectIds, userId, status, settlementId];
    await query(sql, params);
  },

  // ==================== 工资汇总 ====================

  /**
   * 获取用户工资汇总
   * @param {number} userId - 用户ID
   * @param {string} startMonth - 开始月份
   * @param {string} endMonth - 结束月份
   * @returns {Promise<object>} 工资汇总数据
   */
  async getUserWageSummary(userId, startMonth, endMonth) {
    // 查询完工的子项目工资
    const completedWagesSql = `
      SELECT
        SUM(sp.amount) as total_completed_amount,
        COUNT(DISTINCT sp.id) as completed_count
      FROM subprojects sp
      JOIN project_workers pw ON sp.project_id = pw.project_id
      WHERE pw.user_id = $1
        AND sp.status = 'completed'
        AND sp.created_at >= $2::date
        AND sp.created_at <= $3::date
    `;
    const completedWagesResult = await pool.query(completedWagesSql, [userId, startMonth, endMonth]);

    // 查询预支工资
    const advancesSql = `
      SELECT
        SUM(CASE WHEN settled = false THEN advance_amount ELSE 0 END) as unsettled_advance,
        SUM(advance_amount) as total_advance
      FROM wage_advances
      WHERE user_id = $1
        AND advance_date >= $2::date
        AND advance_date <= $3::date
    `;
    const advancesResult = await pool.query(advancesSql, [userId, startMonth, endMonth]);

    // 查询已结算工资
    const settledWagesSql = `
      SELECT
        SUM(ws.actual_amount) as total_settled_amount
      FROM wage_settlements ws
      WHERE ws.user_id = $1
        AND ws.start_month >= $2::date
        AND ws.end_month <= $3::date
    `;
    const settledWagesResult = await pool.query(settledWagesSql, [userId, startMonth, endMonth]);

    const completedAmount = parseFloat(completedWagesResult.rows[0].total_completed_amount) || 0;
    const completedCount = parseInt(completedWagesResult.rows[0].completed_count) || 0;
    const unsettledAdvance = parseFloat(advancesResult.rows[0].unsettled_advance) || 0;
    const totalAdvance = parseFloat(advancesResult.rows[0].total_advance) || 0;
    const settledAmount = parseFloat(settledWagesResult.rows[0].total_settled_amount) || 0;

    return {
      userId,
      startMonth,
      endMonth,
      completedAmount,
      completedCount,
      unsettledAdvance,
      totalAdvance,
      settledAmount,
      remainingAmount: completedAmount - totalAdvance
    };
  },

  // ==================== 完工子项目查询 ====================

  /**
   * 查询可结算的完工子项目
   * 排除已结算的子项目（wage_distributions 中 settlement_id IS NOT NULL 的记录）
   * @param {string} startMonth - 开始月份
   * @param {string} endMonth - 结束月份
   * @param {number} [userId] - 用户ID（施工员只能查看自己参与的工程）
   * @param {string} [role] - 用户角色（用于判断是否需要过滤工程参与关系）
   * @returns {Promise<Array>} 可结算的完工子项目列表
   */
  async getCompletedSubprojects(startMonth, endMonth, userId, role) {
    let sql = `
      SELECT
        sp.id as subproject_id,
        sp.amount,
        sp.quantity,
        sp.project_id,
        p.name as project_name,
        p.salary_distribution as wage_distribution_code,
        st.name as space_type_name,
        cp.name as construction_plan_name
      FROM subprojects sp
      JOIN projects p ON sp.project_id = p.id
      JOIN space_types st ON sp.space_type_id = st.id
      JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      WHERE sp.status = 'completed'
        AND sp.created_at >= $1::date
        AND sp.created_at <= $2::date
        AND sp.id NOT IN (
          SELECT DISTINCT wd.subproject_id
          FROM wage_distributions wd
          WHERE wd.settlement_id IS NOT NULL
        )
    `;
    const params = [startMonth, endMonth];

    // 施工员只能查看自己参与的工程
    if (userId && role === 'constructor') {
      sql += `
        AND sp.project_id IN (
          SELECT pw.project_id FROM project_workers pw WHERE pw.user_id = $3
        )
      `;
      params.push(userId);
    }

    sql += ` ORDER BY sp.project_id, sp.created_at ASC`;

    const result = await pool.query(sql, params);
    return result.rows;
  }
};
