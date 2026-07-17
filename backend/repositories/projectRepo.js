/**
 * 工程数据访问层 (Repository)
 *
 * 负责所有工程相关的数据库操作，从 controllers/projects.js 中抽离。
 * 所有 SQL 均使用参数化查询，禁止字符串拼接。
 */

const pool = require('../config/database');
const logger = require('../config/logger');

// ========== 工程基础 CRUD ==========

/**
 * 根据ID查询工程
 * @param {number} id - 工程ID
 * @returns {Promise<object|null>} 工程记录，不存在则返回 null
 */
const findById = async (id) => {
  const result = await pool.query(
    `SELECT id, name, description, status, COALESCE(total_amount, 0) AS total_amount,
            salary_distribution, total_work_days, settled_by, created_by,
            TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') AS created_at,
            TO_CHAR(updated_at, 'YYYY-MM-DD HH24:MI') AS updated_at
     FROM projects WHERE id = $1`,
    [id]
  );
  return result.rows[0] || null;
};

/**
 * 根据名称查询工程
 * @param {string} name - 工程名称
 * @returns {Promise<object|null>} 工程记录，不存在则返回 null
 */
const findByName = async (name) => {
  const result = await pool.query(
    'SELECT id FROM projects WHERE name = $1',
    [name]
  );
  return result.rows[0] || null;
};

/**
 * 创建工程
 * @param {object} projectData - 工程数据 { name, description, createdBy, status, salaryDistribution }
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<object>} 创建的工程记录（含 id）
 */
const create = async (projectData, client) => {
  const executor = client || pool;
  const result = await executor.query(
    `INSERT INTO projects (name, description, created_by, status, salary_distribution)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id`,
    [
      projectData.name,
      projectData.description || null,
      projectData.createdBy,
      projectData.status || 'constructing',
      projectData.salaryDistribution || 'average'
    ]
  );
  return result.rows[0];
};

/**
 * 更新工程
 * @param {number} id - 工程ID
 * @param {object} updates - 需要更新的字段键值对，如 { name, description, status, salaryDistribution, totalWorkDays }
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const update = async (id, updates, client) => {
  const executor = client || pool;
  const setClauses = [];
  const params = [];
  let paramIndex = 1;

  // 字段名到数据库列名的映射
  // 注意：键必须使用 camelCase，与 projectService.updateProject 传入的键名保持一致
  const fieldToColumn = {
    name: 'name',
    description: 'description',
    status: 'status',
    salaryDistribution: 'salary_distribution',
    totalWorkDays: 'total_work_days',
    remark: 'remark'
  };

  for (const [field, value] of Object.entries(updates)) {
    const column = fieldToColumn[field];
    if (column && value !== undefined) {
      setClauses.push(`${column} = $${paramIndex}`);
      params.push(value);
      paramIndex++;
    }
  }

  if (setClauses.length === 0) {
    return;
  }

  // 自动更新 updated_at
  setClauses.push(`updated_at = CURRENT_TIMESTAMP`);

  params.push(id);
  await executor.query(
    `UPDATE projects SET ${setClauses.join(', ')} WHERE id = $${paramIndex}`,
    params
  );
};

/**
 * 软删除工程（将 status 设为 'deleted'）
 * @param {number} id - 工程ID
 * @returns {Promise<void>}
 */
const softDelete = async (id) => {
  await pool.query(
    'UPDATE projects SET status = $1 WHERE id = $2',
    ['deleted', id]
  );
};

// ========== 工程列表（带筛选 + 分页） ==========

/**
 * 工程列表复杂筛选查询
 * @param {object} filters - 筛选参数
 * @param {number} filters.userId - 当前用户ID（必须）
 * @param {string} filters.role - 用户角色
 * @param {number} [filters.page=1] - 页码
 * @param {number} [filters.size=10] - 每页数量
 * @param {number} [filters.month] - 月份筛选
 * @param {string} [filters.yearMonth] - 年月筛选 (YYYY-MM 或 YYYY-MM-DD)
 * @param {number} [filters.year] - 年份筛选
 * @param {string} [filters.keyword] - 关键词搜索
 * @param {string} [filters.status] - 状态筛选
 * @param {string} [filters.creatorNickname] - 创建人昵称
 * @param {string} [filters.workerNickname] - 施工员昵称
 * @param {string} [filters.startDate] - 开始日期
 * @param {string} [filters.endDate] - 结束日期
 * @param {string} [filters.settlementStatus] - 结算状态
 * @returns {Promise<{list: Array, total: number}>}
 */
const listWithFilters = async (filters) => {
  const {
    userId,
    userRole,
    page = 1,
    size = 10,
    month,
    yearMonth,
    year,
    keyword,
    status,
    creatorNickname,
    workerNickname,
    startDate,
    endDate,
    settlementStatus
  } = filters;

  const pageNum = parseInt(page, 10) || 1;
  const sizeNum = parseInt(size, 10) || 10;

  // 判断是否为施工员角色，施工员只能看到自己参与的工程
  // "自己参与" = 工程创建者是自己(projects.created_by) OR 自己是工程施工人员(project_workers.user_id)
  const isConstructorRole = userRole === 'constructor';

  // ---------- 构建数据查询 ----------
  const conditions = [];
  const params = [userId];
  let paramIndex = 2;

  let query = `
    SELECT
      p.id,
      p.name,
      p.description,
      p.status,
      COALESCE(p.total_amount, 0) AS total_amount,
      p.salary_distribution,
      p.remark,
      p.created_by,
      TO_CHAR(p.created_at, 'YYYY-MM-DD HH24:MI') AS created_at,
      TO_CHAR(p.updated_at, 'YYYY-MM-DD HH24:MI') AS updated_at,
      pus.settlement_status AS settlement_status,
      pus.settlement_id AS settlement_id,
      f.files_count,
      COALESCE(w.workers, '[]') AS workers,
      COALESCE(sp.sub_projects, '[]') AS sub_projects
    FROM projects p
    LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $1
    -- 性能优化：3个相关子查询改为LATERAL JOIN，优化器可统一调度执行计划
    LEFT JOIN LATERAL (
      SELECT COUNT(*) AS files_count
      FROM files WHERE project_id = p.id
    ) f ON true
    LEFT JOIN LATERAL (
      SELECT JSON_AGG(JSON_BUILD_OBJECT('id', u.id, 'username', u.username, 'nickname', u.nickname, 'workdays', pw2.workdays)) AS workers
      FROM project_workers pw2
      JOIN users u ON pw2.user_id = u.id
      WHERE pw2.project_id = p.id
    ) w ON true
    LEFT JOIN LATERAL (
      SELECT JSON_AGG(JSON_BUILD_OBJECT(
          'id', sp.id,
          'space_type_name', st.name,
          'construction_plan_name', cp.name,
          'length', sp.length,
          'width', sp.width,
          'quantity', sp.quantity,
          'amount', sp.amount,
          'status', sp.status
        ) ORDER BY sp.created_at DESC) AS sub_projects
      FROM subprojects sp
      LEFT JOIN space_types st ON sp.space_type_id = st.id
      LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
      WHERE sp.project_id = p.id
    ) sp ON true`;

  // 施工员角色：只能查看自己参与的工程（创建者 OR 施工人员）
  if (isConstructorRole) {
    query += ` WHERE (p.created_by = $${paramIndex} OR EXISTS (SELECT 1 FROM project_workers pw WHERE pw.project_id = p.id AND pw.user_id = $${paramIndex}))`;
    params.push(userId);
    paramIndex++;
  } else {
    query += ' WHERE 1=1';
  }

  // 月份筛选
  if (month) {
    conditions.push(`EXTRACT(MONTH FROM p.created_at) = $${paramIndex}`);
    params.push(parseInt(month, 10));
    paramIndex++;
  }

  // 日期筛选（支持 YYYY-MM 和 YYYY-MM-DD 两种格式）
  if (yearMonth) {
    if (/^\d{4}-\d{2}$/.test(yearMonth)) {
      // YYYY-MM 格式，按月份筛选（使用Asia/Shanghai时区，与前端一致）
      conditions.push(`TO_CHAR(p.created_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM') = $${paramIndex}`);
    } else {
      // YYYY-MM-DD 格式，按日期筛选
      conditions.push(`TO_CHAR(p.created_at, 'YYYY-MM-DD') = $${paramIndex}`);
    }
    params.push(yearMonth);
    paramIndex++;
  }

  // 年份筛选
  if (year) {
    conditions.push(`EXTRACT(YEAR FROM p.created_at) = $${paramIndex}`);
    params.push(parseInt(year, 10));
    paramIndex++;
  }

  // 关键词搜索
  if (keyword) {
    conditions.push(`(p.name ILIKE $${paramIndex} OR p.description ILIKE $${paramIndex})`);
    params.push(`%${keyword}%`);
    paramIndex++;
  }

  // 状态筛选
  if (status) {
    conditions.push(`p.status = $${paramIndex}`);
    params.push(status);
    paramIndex++;
  }

  // 创建人昵称搜索
  if (creatorNickname) {
    query += ` JOIN users cu ON p.created_by = cu.id`;
    conditions.push(`cu.nickname ILIKE $${paramIndex}`);
    params.push(`%${creatorNickname}%`);
    paramIndex++;
  }

  // 施工员昵称搜索
  if (workerNickname) {
    query += ` JOIN project_workers pw3 ON p.id = pw3.project_id JOIN users wu ON pw3.user_id = wu.id`;
    conditions.push(`wu.nickname ILIKE $${paramIndex}`);
    params.push(`%${workerNickname}%`);
    paramIndex++;
  }

  // 日期范围筛选 - 开始日期
  if (startDate) {
    conditions.push(`p.created_at >= $${paramIndex}::date`);
    params.push(startDate);
    paramIndex++;
  }

  // 日期范围筛选 - 结束日期
  if (endDate) {
    conditions.push(`p.created_at <= $${paramIndex}::date`);
    params.push(endDate);
    paramIndex++;
  }

  // 结算状态筛选
  if (settlementStatus) {
    if (settlementStatus === 'settled') {
      conditions.push(`pus.settlement_status = 'settled'`);
    } else if (settlementStatus === 'unsettled') {
      conditions.push(`(pus.settlement_status IS NULL OR pus.settlement_status = 'unsettled')`);
    } else if (settlementStatus === 'settling') {
      conditions.push(`pus.settlement_status = 'settling'`);
    }
  }

  // 拼接所有筛选条件
  if (conditions.length > 0) {
    query += ' AND ' + conditions.join(' AND ');
  }

  // 排序
  query += ' ORDER BY p.created_at DESC';

  // 分页
  const offset = (pageNum - 1) * sizeNum;
  query += ` LIMIT $${paramIndex} OFFSET $${paramIndex + 1}`;
  params.push(sizeNum, offset);

  const result = await pool.query(query, params);

  // ---------- 构建总数查询 ----------
  // 性能优化：按需JOIN视图（仅 settlementStatus 传入时才JOIN，避免无条件展开物化视图）
  // 创建人/施工员昵称搜索改用EXISTS子查询，避免INNER JOIN导致的行膨胀
  const countConditions = [];
  const countParams = [];
  let countParamIndex = 1;
  let countQuery;

  if (settlementStatus) {
    // 需要视图过滤结算状态
    countQuery = `SELECT COUNT(*) FROM projects p
                  LEFT JOIN v_project_user_settlement_status pus ON p.id = pus.project_id AND pus.user_id = $${countParamIndex}`;
    countParams.push(userId);
    countParamIndex++;
  } else {
    // 无需视图，直接查projects表
    countQuery = `SELECT COUNT(*) FROM projects p`;
  }

  // 施工员角色过滤（用EXISTS避免JOIN膨胀）
  if (isConstructorRole) {
    countConditions.push(`EXISTS (SELECT 1 FROM project_workers pw WHERE pw.project_id = p.id AND pw.user_id = $${countParamIndex})`);
    countParams.push(userId);
    countParamIndex++;
  }

  // 月份筛选
  if (month) {
    countConditions.push(`EXTRACT(MONTH FROM p.created_at) = $${countParamIndex}`);
    countParams.push(parseInt(month, 10));
    countParamIndex++;
  }

  // 日期筛选（支持 YYYY-MM 和 YYYY-MM-DD 两种格式）
  if (yearMonth) {
    if (/^\d{4}-\d{2}$/.test(yearMonth)) {
      countConditions.push(`TO_CHAR(p.created_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM') = $${countParamIndex}`);
    } else {
      countConditions.push(`TO_CHAR(p.created_at, 'YYYY-MM-DD') = $${countParamIndex}`);
    }
    countParams.push(yearMonth);
    countParamIndex++;
  }

  // 年份筛选
  if (year) {
    countConditions.push(`EXTRACT(YEAR FROM p.created_at) = $${countParamIndex}`);
    countParams.push(parseInt(year, 10));
    countParamIndex++;
  }

  // 关键词搜索
  if (keyword) {
    countConditions.push(`(p.name ILIKE $${countParamIndex} OR p.description ILIKE $${countParamIndex})`);
    countParams.push(`%${keyword}%`);
    countParamIndex++;
  }

  // 状态筛选
  if (status) {
    countConditions.push(`p.status = $${countParamIndex}`);
    countParams.push(status);
    countParamIndex++;
  }

  // 创建人昵称搜索（改用EXISTS，避免INNER JOIN膨胀）
  if (creatorNickname) {
    countConditions.push(`EXISTS (SELECT 1 FROM users cu WHERE cu.id = p.created_by AND cu.nickname ILIKE $${countParamIndex})`);
    countParams.push(`%${creatorNickname}%`);
    countParamIndex++;
  }

  // 施工员昵称搜索（改用EXISTS，避免INNER JOIN膨胀）
  if (workerNickname) {
    countConditions.push(`EXISTS (SELECT 1 FROM project_workers pw3 JOIN users wu ON pw3.user_id = wu.id WHERE pw3.project_id = p.id AND wu.nickname ILIKE $${countParamIndex})`);
    countParams.push(`%${workerNickname}%`);
    countParamIndex++;
  }

  // 开始日期
  if (startDate) {
    countConditions.push(`p.created_at >= $${countParamIndex}::date`);
    countParams.push(startDate);
    countParamIndex++;
  }

  // 结束日期
  if (endDate) {
    countConditions.push(`p.created_at <= $${countParamIndex}::date`);
    countParams.push(endDate);
    countParamIndex++;
  }

  // 结算状态筛选
  if (settlementStatus) {
    if (settlementStatus === 'settled') {
      countConditions.push(`pus.settlement_status = 'settled'`);
    } else if (settlementStatus === 'unsettled') {
      countConditions.push(`(pus.settlement_status IS NULL OR pus.settlement_status = 'unsettled')`);
    } else if (settlementStatus === 'settling') {
      countConditions.push(`pus.settlement_status = 'settling'`);
    }
  }

  if (countConditions.length > 0) {
    countQuery += ' WHERE ' + countConditions.join(' AND ');
  }

  const countResult = await pool.query(countQuery, countParams);
  const total = parseInt(countResult.rows[0].count, 10);

  return { list: result.rows, total };
};

// ========== 工程详情 ==========

/**
 * 获取工程详情（含子项目、施工人员、附件）
 * @param {number} id - 工程ID
 * @returns {Promise<object|null>} 工程详情对象，不存在则返回 null
 */
const getDetailById = async (id) => {
  // 获取工程基本信息
  const projectResult = await pool.query(
    `SELECT p.*,
            TO_CHAR(p.created_at, 'YYYY-MM-DD HH24:MI') AS created_at,
            TO_CHAR(p.updated_at, 'YYYY-MM-DD HH24:MI') AS updated_at
     FROM projects p
     WHERE p.id = $1`,
    [id]
  );

  if (projectResult.rows.length === 0) {
    return null;
  }

  const project = projectResult.rows[0];

  // 获取子项目信息
  const subprojectsResult = await pool.query(
    `SELECT sp.*,
            st.name AS space_type_name,
            cp.name AS construction_plan_name,
            cp.unit,
            cp.price
     FROM subprojects sp
     LEFT JOIN space_types st ON sp.space_type_id = st.id
     LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
     WHERE sp.project_id = $1
     ORDER BY sp.created_at DESC`,
    [id]
  );

  // 获取施工人员信息
  const workers = await getWorkers(id);

  // 获取附件信息
  const filesResult = await pool.query(
    `SELECT f.id, f.filename, f.original_name AS "originalName", f.path, f.size, f.type,
            TO_CHAR(f.created_at, 'YYYY-MM-DD HH24:MI') AS created_at
     FROM files f
     WHERE f.project_id = $1
     ORDER BY f.created_at DESC`,
    [id]
  );

  // 转换子项目字段名以匹配前端期望
  const subProjects = subprojectsResult.rows.map(sp => ({
    ...sp,
    space_type: sp.space_type_name,
    construction_scheme: sp.construction_plan_name,
    unit_price: sp.price,
    unit_type: sp.unit
  }));

  return {
    ...project,
    sub_projects: subProjects,
    workers,
    constructors: workers,
    files: filesResult.rows
  };
};

// ========== 施工人员 ==========

/**
 * 获取工程施工人员
 * @param {number} projectId - 工程ID
 * @returns {Promise<Array>} 施工人员列表
 */
const getWorkers = async (projectId) => {
  const result = await pool.query(
    `SELECT u.id, u.username, u.nickname, pw.workdays
     FROM users u
     JOIN project_workers pw ON u.id = pw.user_id
     WHERE pw.project_id = $1`,
    [projectId]
  );
  return result.rows;
};

/**
 * 批量添加施工人员（参数化查询，避免 SQL 注入）
 * 支持同时写入工日数（按工日分配模式下使用）
 * @param {number} projectId - 工程ID
 * @param {Array<number|{userId: number, workdays?: number}>} workers - 施工人员数组
 *        支持两种格式：纯ID数组 [1, 2, 3] 或对象数组 [{userId: 1, workdays: 2.0}, ...]
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const addWorkers = async (projectId, workers, client) => {
  if (!workers || workers.length === 0) {
    return;
  }

  const executor = client || pool;

  // 统一转换为对象格式，提取 userId 和 workdays
  const normalizedWorkers = workers.map(w => {
    if (typeof w === 'number') {
      return { userId: w, workdays: null };
    }
    return { userId: w.userId, workdays: w.workdays ?? null };
  });

  // 构建参数化批量插入：每个 (project_id, user_id, workdays) 使用独立占位符
  // workdays 为 null 时使用数据库默认值，不为 null 时写入指定工日数
  const valuePlaceholders = [];
  const params = [];
  let paramIndex = 1;

  for (const worker of normalizedWorkers) {
    if (worker.workdays !== null) {
      // 按工日分配模式：写入工日数
      valuePlaceholders.push(`($${paramIndex}, $${paramIndex + 1}, $${paramIndex + 2})`);
      params.push(projectId, worker.userId, worker.workdays);
      paramIndex += 3;
    } else {
      // 平均分配模式：不写入工日数，使用数据库默认值
      valuePlaceholders.push(`($${paramIndex}, $${paramIndex + 1})`);
      params.push(projectId, worker.userId);
      paramIndex += 2;
    }
  }

  // 根据是否有 workdays 动态构建 INSERT 语句
  // 由于不同行的占位符数量可能不同（有/无 workdays），需要分别处理
  // 简化方案：统一使用三列插入，workdays 为 null 时显式传入 NULL
  const unifiedPlaceholders = [];
  const unifiedParams = [];
  let unifiedIndex = 1;

  for (const worker of normalizedWorkers) {
    unifiedPlaceholders.push(`($${unifiedIndex}, $${unifiedIndex + 1}, $${unifiedIndex + 2})`);
    unifiedParams.push(projectId, worker.userId, worker.workdays);
    unifiedIndex += 3;
  }

  await executor.query(
    `INSERT INTO project_workers (project_id, user_id, workdays) VALUES ${unifiedPlaceholders.join(', ')} ON CONFLICT DO NOTHING`,
    unifiedParams
  );
};

/**
 * 替换施工人员（先删除旧的，再批量添加新的）
 * 支持同时写入工日数（按工日分配模式下使用）
 * @param {number} projectId - 工程ID
 * @param {Array<number|{userId: number, workdays?: number}>} workers - 施工人员数组
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const replaceWorkers = async (projectId, workers, client) => {
  const executor = client || pool;

  // 先删除旧的施工人员（包括其工日数据）
  await executor.query(
    'DELETE FROM project_workers WHERE project_id = $1',
    [projectId]
  );

  // 再批量添加新的施工人员（含工日数据）
  if (workers && workers.length > 0) {
    await addWorkers(projectId, workers, executor);
  }
};

/**
 * 更新施工人员工日
 * @param {number} projectId - 工程ID
 * @param {number} userId - 用户ID
 * @param {number} workdays - 工日数
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const updateWorkerWorkdays = async (projectId, userId, workdays, client) => {
  const executor = client || pool;
  await executor.query(
    'UPDATE project_workers SET workdays = $1 WHERE project_id = $2 AND user_id = $3',
    [workdays, projectId, userId]
  );
};

/**
 * 检查用户是否参与工程
 * @param {number} projectId - 工程ID
 * @param {number} userId - 用户ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<boolean>}
 */
const isParticipant = async (projectId, userId, client) => {
  const executor = client || pool;
  // "自己参与" = 工程创建者是自己(projects.created_by) OR 自己是工程施工人员(project_workers.user_id)
  const result = await executor.query(
    `SELECT 1 FROM projects p
     LEFT JOIN project_workers pw ON p.id = pw.project_id
     WHERE p.id = $1 AND (p.created_by = $2 OR pw.user_id = $2)
     LIMIT 1`,
    [projectId, userId]
  );
  return result.rows.length > 0;
};

// ========== 子项目 ==========

/**
 * 创建子项目
 * @param {object} subprojectData - 子项目数据
 * @param {number} subprojectData.projectId - 工程ID
 * @param {number} subprojectData.spaceTypeId - 空间类型ID
 * @param {number} subprojectData.constructionPlanId - 施工方案ID
 * @param {number} subprojectData.length - 长度（米）
 * @param {number} subprojectData.width - 宽度（米）
 * @param {number} subprojectData.quantity - 数量
 * @param {number} subprojectData.amount - 金额
 * @param {number} subprojectData.createdBy - 创建人ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<object>} 创建的子项目记录（含 id）
 */
const createSubproject = async (subprojectData, client) => {
  const executor = client || pool;
  const result = await executor.query(
    `INSERT INTO subprojects (project_id, space_type_id, construction_plan_id, length, width, quantity, amount, created_by)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     RETURNING id`,
    [
      subprojectData.projectId,
      subprojectData.spaceTypeId,
      subprojectData.constructionPlanId,
      subprojectData.length,
      subprojectData.width,
      subprojectData.quantity,
      subprojectData.amount,
      subprojectData.createdBy
    ]
  );
  return result.rows[0];
};

/**
 * 更新子项目
 * @param {number} subprojectId - 子项目ID
 * @param {object} updates - 需要更新的字段键值对
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const updateSubproject = async (subprojectId, updates, client) => {
  const executor = client || pool;
  const setClauses = [];
  const params = [];
  let paramIndex = 1;

  // 字段名到数据库列名的映射
  const fieldToColumn = {
    spaceTypeId: 'space_type_id',
    constructionPlanId: 'construction_plan_id',
    length: 'length',
    width: 'width',
    quantity: 'quantity',
    amount: 'amount',
    remark: 'remark',
    status: 'status',
    createdBy: 'created_by'
  };

  for (const [field, value] of Object.entries(updates)) {
    const column = fieldToColumn[field];
    if (column && value !== undefined) {
      setClauses.push(`${column} = $${paramIndex}`);
      params.push(value);
      paramIndex++;
    }
  }

  if (setClauses.length === 0) {
    return;
  }

  // 自动更新 updated_at
  setClauses.push(`updated_at = CURRENT_TIMESTAMP`);

  params.push(subprojectId);
  await executor.query(
    `UPDATE subprojects SET ${setClauses.join(', ')} WHERE id = $${paramIndex}`,
    params
  );
};

/**
 * 删除子项目
 * @param {number} subprojectId - 子项目ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const deleteSubproject = async (subprojectId, client) => {
  const executor = client || pool;
  await executor.query(
    'DELETE FROM subprojects WHERE id = $1',
    [subprojectId]
  );
};

/**
 * 重新计算工程总额（基于子项目金额之和）
 * @param {number} projectId - 工程ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<number>} 新的工程总额
 */
const recalcProjectTotal = async (projectId, client) => {
  const executor = client || pool;

  // 汇总所有子项目金额
  const totalResult = await executor.query(
    'SELECT COALESCE(SUM(amount), 0) AS total FROM subprojects WHERE project_id = $1',
    [projectId]
  );
  const newTotal = parseFloat(totalResult.rows[0].total) || 0;

  // 更新工程总额
  await executor.query(
    'UPDATE projects SET total_amount = $1 WHERE id = $2',
    [newTotal, projectId]
  );

  return newTotal;
};

// ========== 历史记录 ==========

/**
 * 添加工程历史记录
 * @param {number} projectId - 工程ID
 * @param {string} action - 操作类型（如 CREATE_PROJECT, UPDATE_PROJECT 等）
 * @param {string} description - 操作描述
 * @param {number} performedBy - 操作人ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const addHistory = async (projectId, action, description, performedBy, client) => {
  const executor = client || pool;
  await executor.query(
    'INSERT INTO project_history (project_id, action, description, performed_by) VALUES ($1, $2, $3, $4)',
    [projectId, action, description, performedBy]
  );
};

/**
 * 获取工程历史记录
 * @param {number} projectId - 工程ID
 * @returns {Promise<Array>} 历史记录列表
 */
const getHistory = async (projectId) => {
  const result = await pool.query(
    `SELECT ph.id,
            ph.project_id,
            ph.action,
            at.name AS action_name,
            ph.description,
            ph.performed_by,
            u.username,
            u.nickname,
            TO_CHAR(ph.created_at, 'YYYY-MM-DD HH24:MI') AS created_at
     FROM project_history ph
     LEFT JOIN users u ON ph.performed_by = u.id
     LEFT JOIN action_types at ON ph.action = at.code
     WHERE ph.project_id = $1
     ORDER BY ph.created_at DESC`,
    [projectId]
  );
  return result.rows;
};

// ========== 文件 ==========

/**
 * 添加文件记录
 * @param {object} fileData - 文件数据
 * @param {number} fileData.projectId - 工程ID
 * @param {string} fileData.filename - 文件名
 * @param {string} fileData.originalName - 原始文件名
 * @param {string} fileData.path - 文件路径
 * @param {number} [fileData.size] - 文件大小
 * @param {string} [fileData.type] - 文件类型
 * @param {number} fileData.uploadedBy - 上传人ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const addFile = async (fileData, client) => {
  const executor = client || pool;
  await executor.query(
    `INSERT INTO files (project_id, filename, original_name, path, size, type, uploaded_by)
     VALUES ($1, $2, $3, $4, $5, $6, $7)`,
    [
      fileData.projectId,
      fileData.filename,
      fileData.originalName || fileData.filename,
      fileData.path,
      fileData.size || 0,
      fileData.type || '',
      fileData.uploadedBy
    ]
  );
};

/**
 * 获取工程附件列表
 * @param {number} projectId - 工程ID
 * @returns {Promise<Array>} 附件列表
 */
const getFiles = async (projectId) => {
  const result = await pool.query(
    `SELECT f.id, f.filename, f.original_name AS "originalName", f.path, f.size, f.type,
            TO_CHAR(f.created_at, 'YYYY-MM-DD HH24:MI') AS created_at
     FROM files f
     WHERE f.project_id = $1
     ORDER BY f.created_at DESC`,
    [projectId]
  );
  return result.rows;
};

// ========== 辅助查询函数 ==========

/**
 * 根据ID列表查询用户
 * @param {number[]} ids - 用户ID数组
 * @returns {Promise<Array>} 用户列表
 */
const findUsersByIds = async (ids) => {
  if (!ids || ids.length === 0) return [];
  const placeholders = ids.map((_, i) => `$${i + 1}`).join(', ');
  const result = await pool.query(
    `SELECT id, username, nickname, role FROM users WHERE id IN (${placeholders})`,
    ids
  );
  return result.rows;
};

/**
 * 根据ID查询用户
 * @param {number} id - 用户ID
 * @returns {Promise<object|null>} 用户记录
 */
const findUserById = async (id) => {
  const result = await pool.query(
    'SELECT id, username, nickname, role FROM users WHERE id = $1',
    [id]
  );
  return result.rows[0] || null;
};

/**
 * 根据名称查询空间类型
 * @param {string} name - 空间类型名称
 * @returns {Promise<object|null>} 空间类型记录
 */
const findSpaceTypeByName = async (name) => {
  const result = await pool.query(
    'SELECT id, name FROM space_types WHERE name = $1',
    [name]
  );
  return result.rows[0] || null;
};

/**
 * 根据名称查询施工方案
 * @param {string} name - 施工方案名称
 * @returns {Promise<object|null>} 施工方案记录（含 unit, price）
 */
const findConstructionPlanByName = async (name) => {
  const result = await pool.query(
    'SELECT id, name, unit, price FROM construction_plans WHERE name = $1',
    [name]
  );
  return result.rows[0] || null;
};

/**
 * 根据工程ID查询子项目列表
 * @param {number} projectId - 工程ID
 * @returns {Promise<Array>} 子项目列表
 */
const findSubprojectsByProjectId = async (projectId) => {
  const result = await pool.query(
    `SELECT sp.*,
            st.name AS space_type_name,
            cp.name AS construction_plan_name,
            cp.unit,
            cp.price
     FROM subprojects sp
     LEFT JOIN space_types st ON sp.space_type_id = st.id
     LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
     WHERE sp.project_id = $1
     ORDER BY sp.created_at DESC`,
    [projectId]
  );
  return result.rows;
};

/**
 * 根据ID查询子项目
 * @param {number} id - 子项目ID
 * @returns {Promise<object|null>} 子项目记录
 */
const findSubprojectById = async (id) => {
  const result = await pool.query(
    'SELECT * FROM subprojects WHERE id = $1',
    [id]
  );
  return result.rows[0] || null;
};

/**
 * 根据ID查询子项目详情（含空间类型和施工方案信息）
 * @param {number} id - 子项目ID
 * @returns {Promise<object|null>} 子项目详情
 */
const findSubprojectDetailById = async (id) => {
  const result = await pool.query(
    `SELECT sp.*,
            st.name AS space_type_name,
            cp.name AS construction_plan_name,
            cp.unit,
            cp.price
     FROM subprojects sp
     LEFT JOIN space_types st ON sp.space_type_id = st.id
     LEFT JOIN construction_plans cp ON sp.construction_plan_id = cp.id
     WHERE sp.id = $1`,
    [id]
  );
  return result.rows[0] || null;
};

/**
 * 获取工程子项目总额
 * @param {number} projectId - 工程ID
 * @returns {Promise<number>} 子项目金额之和
 */
const getSubprojectsTotalAmount = async (projectId) => {
  const result = await pool.query(
    'SELECT COALESCE(SUM(amount), 0) AS total FROM subprojects WHERE project_id = $1',
    [projectId]
  );
  return parseFloat(result.rows[0].total) || 0;
};

/**
 * 在事务中创建工程（含子项目）
 * @param {object} data - 创建数据
 * @returns {Promise<{projectId: number, subprojectId: number}>}
 */
const createProjectWithSubproject = async (data) => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    let projectId = data.projectId;
    let isNewProject = data.isNewProject;

    // 创建新工程
    if (isNewProject) {
      // 事务内并发防护：INSERT 前再次检查工程名是否已存在
      // service 层事务外的 findProjectByName 检查仅用于分支判断，
      // 此处防止两个并发请求同时通过 service 层检查后各自 INSERT 导致同名工程
      const nameCheck = await client.query(
        'SELECT id FROM projects WHERE name = $1 LIMIT 1',
        [data.name]
      );
      if (nameCheck.rows.length > 0) {
        // 抛出错误后事务会自动 ROLLBACK
        throw new Error('工程名已存在');
      }

      const projectResult = await client.query(
        `INSERT INTO projects (name, description, created_by, status, salary_distribution)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id`,
        [data.name, data.description, data.userId, 'constructing', data.salaryDistribution]
      );
      projectId = projectResult.rows[0].id;

      // 添加施工人员（新工程时支持按工日分配模式写入工日数）
      if (data.constructors && data.constructors.length > 0) {
        const constructorIds = data.constructors.map(c => c.userId || c);
        // 构建工日映射（按工日分配模式）
        const workdaysMap = {};
        if (data.workerWorkDays && data.workerWorkDays.length > 0) {
          for (const item of data.workerWorkDays) {
            workdaysMap[item.userId] = item.workdays;
          }
        }
        for (const uid of constructorIds) {
          // 按工日分配模式下写入对应工日数，否则使用数据库默认值1.0
          const workdays = workdaysMap[uid];
          if (workdays !== undefined) {
            await client.query(
              'INSERT INTO project_workers (project_id, user_id, workdays) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING',
              [projectId, uid, workdays]
            );
          } else {
            await client.query(
              'INSERT INTO project_workers (project_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
              [projectId, uid]
            );
          }
        }
      }
    }

    // 创建子项目（使用预先计算的金额，确保事务内数据一致性）
    const subprojectResult = await client.query(
      `INSERT INTO subprojects (project_id, space_type_id, construction_plan_id, length, width, quantity, amount, created_by)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       RETURNING id`,
      [projectId, data.spaceTypeId, data.constructionPlanId, data.length, data.width, data.quantity, data.amount, data.userId]
    );
    const subprojectId = subprojectResult.rows[0].id;

    // 重新计算工程总额并更新（事务内执行，确保与子项目明细一致）
    // 参考 recalcProjectTotal 方法的 SQL 逻辑
    await client.query(
      'UPDATE projects SET total_amount = (SELECT COALESCE(SUM(amount), 0) FROM subprojects WHERE project_id = $1) WHERE id = $2',
      [projectId, projectId]
    );

    // 写入工程历史记录（事务内执行，确保与工程/子项目数据一致）
    // 参考 addHistory 方法的 SQL 逻辑，action/description 由调用方传入
    if (data.historyAction && data.historyDesc) {
      await client.query(
        'INSERT INTO project_history (project_id, action, description, performed_by) VALUES ($1, $2, $3, $4)',
        [projectId, data.historyAction, data.historyDesc, data.userId]
      );
    }

    await client.query('COMMIT');
    return { projectId, subprojectId };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
};

/**
 * 更新子项目金额
 * @param {number} subprojectId - 子项目ID
 * @param {number} quantity - 数量
 * @param {number} amount - 金额
 * @returns {Promise<void>}
 */
const updateSubprojectAmount = async (subprojectId, quantity, amount) => {
  await pool.query(
    'UPDATE subprojects SET quantity = $1, amount = $2 WHERE id = $3',
    [quantity, amount, subprojectId]
  );
};

/**
 * 在事务中更新子项目
 * @param {number} subprojectId - 子项目ID
 * @param {object} updates - 更新字段
 * @returns {Promise<void>}
 */
const updateSubprojectInTransaction = async (subprojectId, updates) => {
  const setClauses = [];
  const params = [];
  let paramIndex = 1;

  const fieldToColumn = {
    space_type_id: 'space_type_id',
    construction_plan_id: 'construction_plan_id',
    length: 'length',
    width: 'width',
    quantity: 'quantity',
    amount: 'amount',
    remark: 'remark',
    status: 'status',
    created_by: 'created_by'
  };

  for (const [field, value] of Object.entries(updates)) {
    const column = fieldToColumn[field] || field;
    if (value !== undefined) {
      setClauses.push(`${column} = $${paramIndex}`);
      params.push(value);
      paramIndex++;
    }
  }

  if (setClauses.length === 0) return;

  setClauses.push('updated_at = CURRENT_TIMESTAMP');
  params.push(subprojectId);
  await pool.query(
    `UPDATE subprojects SET ${setClauses.join(', ')} WHERE id = $${paramIndex}`,
    params
  );
};

/**
 * 在事务中删除子项目
 * @param {number} subprojectId - 子项目ID
 * @returns {Promise<void>}
 */
const deleteSubprojectInTransaction = async (subprojectId) => {
  await pool.query('DELETE FROM subprojects WHERE id = $1', [subprojectId]);
};

/**
 * 批量更新子项目状态
 * @param {number} projectId - 工程ID
 * @param {string} status - 目标状态
 * @returns {Promise<void>}
 */
const updateSubprojectsStatus = async (projectId, status) => {
  await pool.query(
    'UPDATE subprojects SET status = $1 WHERE project_id = $2',
    [status, projectId]
  );
};

/**
 * 更新工程施工人员（替换模式，支持工日数据）
 * @param {number} projectId - 工程ID
 * @param {Array<number|{userId: number, workdays?: number}>} constructors - 施工人员列表
 *        支持两种格式：纯ID数组 [1, 2, 3] 或对象数组 [{userId: 1, workdays: 2.0}, ...]
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<void>}
 */
const updateProjectWorkers = async (projectId, constructors, client) => {
  // 直接传递 constructors 对象数组（含 workdays）给 replaceWorkers
  // replaceWorkers 内部会统一处理纯ID和对象两种格式
  await replaceWorkers(projectId, constructors, client);
};

/**
 * 批量更新施工人员工日
 * @param {number} projectId - 工程ID
 * @param {Array} workerWorkDays - 工日列表 [{userId, workdays}]
 * @returns {Promise<void>}
 */
const updateWorkerWorkDays = async (projectId, workerWorkDays) => {
  for (const item of workerWorkDays) {
    await updateWorkerWorkdays(projectId, item.userId, item.workdays);
  }
};

// ========== 子项目状态变更（事务） ==========

/**
 * 更新子项目状态（含权限校验，事务执行）
 * 业务规则：
 * 1. 检查子项目是否存在
 * 2. 校验操作者是否为创建者
 * 3. 更新状态
 * 4. 添加历史记录
 * 全部在事务内执行，确保数据一致性
 *
 * @param {number} subprojectId - 子项目ID
 * @param {string} status - 新状态
 * @param {number} userId - 操作者用户ID
 * @param {number} projectId - 工程ID（用于写入历史记录）
 * @returns {Promise<object>} 执行结果：
 *   - { notFound: true } 子项目不存在
 *   - { forbidden: true } 权限不足
 *   - { id: subprojectId } 成功
 */
const updateSubprojectStatus = async (subprojectId, status, userId, projectId) => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 检查子项目是否存在
    // 注意：原 controller 查询 SELECT sp.*, p.created_by，结果中 p.created_by 覆盖 sp.created_by
    // 这里保持原有 SQL 行为不变（事务外无法直接复用 findById，需在事务 client 上执行）
    const subprojectResult = await client.query(
      'SELECT sp.*, p.created_by FROM subprojects sp JOIN projects p ON sp.project_id = p.id WHERE sp.id = $1',
      [subprojectId]
    );
    if (subprojectResult.rows.length === 0) {
      await client.query('ROLLBACK');
      return { notFound: true };
    }

    const subproject = subprojectResult.rows[0];

    // 权限校验：只有子项目创建者可以操作（沿用原有逻辑）
    if (userId !== subproject.created_by) {
      await client.query('ROLLBACK');
      return { forbidden: true };
    }

    // 更新子项目状态
    await client.query(
      'UPDATE subprojects SET status = $1 WHERE id = $2',
      [status, subprojectId]
    );

    // 添加历史记录
    await client.query(
      'INSERT INTO project_history (project_id, action, description, performed_by) VALUES ($1, $2, $3, $4)',
      [projectId, 'UPDATE_SUBPROJECT_STATUS', `更新子项目状态为${status}`, userId]
    );

    await client.query('COMMIT');
    return { id: subprojectId };
  } catch (error) {
    // 异常时回滚事务
    try {
      await client.query('ROLLBACK');
      logger.info('[更新子项目状态] 事务已回滚');
    } catch (rollbackErr) {
      logger.error('[更新子项目状态] 事务回滚失败:', rollbackErr);
    }
    throw error;
  } finally {
    // 释放连接
    try {
      client.release();
      logger.info('[更新子项目状态] 数据库连接已释放');
    } catch (releaseErr) {
      logger.error('[更新子项目状态] 释放数据库连接失败:', releaseErr);
    }
  }
};

// ========== 文件记录操作（V2.0 分层） ==========

/**
 * 新增附件记录
 * @param {number} projectId - 工程ID
 * @param {object} fileData - 文件数据
 * @param {string} fileData.filename - 存储文件名
 * @param {string} fileData.originalName - 原始文件名
 * @param {string} fileData.path - 文件访问路径
 * @param {number} [fileData.size=0] - 文件大小
 * @param {string} [fileData.type=''] - 文件类型
 * @param {number} userId - 上传人ID
 * @returns {Promise<void>}
 */
const addFileRecord = async (projectId, fileData, userId) => {
  await pool.query(
    'INSERT INTO files (project_id, filename, original_name, path, size, type, uploaded_by) VALUES ($1, $2, $3, $4, $5, $6, $7)',
    [
      projectId,
      fileData.filename,
      fileData.originalName || fileData.filename,
      fileData.path,
      fileData.size || 0,
      fileData.type || '',
      userId
    ]
  );
};

/**
 * 根据文件ID和工程ID查询附件记录（用于权限/归属校验）
 * @param {number} fileId - 文件ID
 * @param {number} projectId - 工程ID
 * @returns {Promise<object|null>} 附件记录，不存在则返回 null
 */
const getFileRecordById = async (fileId, projectId) => {
  const result = await pool.query(
    'SELECT * FROM files WHERE id = $1 AND project_id = $2',
    [fileId, projectId]
  );
  return result.rows[0] || null;
};

/**
 * 删除附件记录
 * @param {number} fileId - 文件ID
 * @returns {Promise<void>}
 */
const deleteFileRecord = async (fileId) => {
  await pool.query('DELETE FROM files WHERE id = $1', [fileId]);
};

module.exports = {
  // 工程基础 CRUD
  findById,
  findByName,
  create,
  update,
  softDelete,

  // 工程列表（带筛选 + 分页）
  listWithFilters,

  // 工程详情
  getDetailById,

  // 施工人员
  getWorkers,
  addWorkers,
  replaceWorkers,
  updateWorkerWorkdays,
  isParticipant,

  // 子项目
  createSubproject,
  updateSubproject,
  deleteSubproject,
  recalcProjectTotal,

  // 历史记录
  addHistory,
  getHistory,

  // 文件
  addFile,
  getFiles,

  // ========== projectService 兼容别名 ==========
  findProjects: listWithFilters,
  findProjectById: findById,
  findProjectByName: findByName,
  updateProject: update,
  isProjectParticipant: isParticipant,
  findProjectWorkers: getWorkers,
  findProjectFiles: getFiles,
  findProjectHistory: getHistory,
  addProjectHistory: addHistory,
  updateProjectWorkers,
  updateWorkerWorkDays,
  updateSubprojectsStatus,

  // ========== 辅助查询函数 ==========
  findUsersByIds,
  findUserById,
  findSpaceTypeByName,
  findConstructionPlanByName,
  findSubprojectsByProjectId,
  findSubprojectById,
  findSubprojectDetailById,
  getSubprojectsTotalAmount,
  createProjectWithSubproject,
  updateSubprojectAmount,
  updateSubprojectInTransaction,
  deleteSubprojectInTransaction,

  // ========== V2.0 子项目状态变更（事务） ==========
  updateSubprojectStatus,

  // ========== V2.0 文件记录操作 ==========
  addFileRecord,
  getFileRecordById,
  deleteFileRecord,
};
