/**
 * 工程业务逻辑层 (Service)
 *
 * 从 controller 中抽离工程相关的业务逻辑，负责：
 * - 业务规则校验（权限、数据完整性）
 * - 调用 projectRepo 进行数据访问
 * - 调用 calculation 进行金额计算
 * - 调用 cacheService 进行缓存管理
 * - 业务异常通过 BusinessError 抛出，由 controller 捕获
 */

const projectRepo = require('../repositories/projectRepo');
const calculation = require('./calculation');
const cache = require('./cacheService');
const logger = require('../config/logger');
// V2.0: 权限辅助判断函数（用于 uploadFile / deleteFile 业务校验）
const { isAdmin, isConstructor } = require('../middleware/rbac');

/**
 * 异步刷新物化视图（不阻塞当前操作，失败只记录日志）
 * 工程状态变更后调用，确保结算状态数据立即更新
 * （物化视图 mv_project_user_settlement_status 依赖工程状态计算 settlement_status，
 *   不触发刷新则最长5分钟后才更新，导致用户看到陈旧状态）
 */
let refreshMvAsync;
try {
  const { refreshMaterializedView } = require('../scripts/refresh-mv');
  refreshMvAsync = () => {
    refreshMaterializedView().catch(err => {
      logger.warn('工程状态变更后刷新物化视图失败', { error: err.message });
    });
  };
} catch (e) {
  // refresh-mv.js 不存在时降级为空函数，不影响主流程
  refreshMvAsync = () => {};
}

/**
 * 校验附件路径格式，防止路径伪造和路径穿越攻击
 *
 * 合法路径格式：/upload/... 或 /uploads/...，且不允许包含 .. 路径穿越符
 *
 * @param {string} filePath - 文件路径
 * @returns {boolean} 是否合法
 */
const isValidFilePath = (filePath) => {
  if (!filePath || typeof filePath !== 'string') return false;
  // 必须以 /upload/ 或 /uploads/ 开头
  if (!filePath.startsWith('/upload/') && !filePath.startsWith('/uploads/')) return false;
  // 禁止路径穿越符
  if (filePath.includes('..')) return false;
  // 禁止null字节
  if (filePath.includes('\0')) return false;
  return true;
};

/**
 * 安全解析附件物理文件绝对路径，防止路径穿越
 *
 * @param {string} filePath - 数据库中存储的文件路径（形如 /upload/YYYYMM/xxx）
 * @returns {{absPath: string|null}} 规范化后的绝对路径，若穿越 upload 目录则返回 null
 */
const safeResolveUploadPath = (filePath) => {
  if (!isValidFilePath(filePath)) return { absPath: null };
  const fs = require('fs');
  const path = require('path');
  // 统一移除 /upload 或 /uploads 前缀
  const relativePath = filePath.startsWith('/uploads/')
    ? filePath.substring('/uploads'.length)
    : filePath.substring('/upload'.length);
  // upload 目录绝对路径（与 index.js 静态文件服务一致：backend/upload）
  const uploadDir = path.resolve(__dirname, '..', 'upload');
  const absolutePath = path.resolve(uploadDir, '.' + relativePath);
  // 验证规范化后的路径仍在 upload 目录内，防止路径穿越
  if (!absolutePath.startsWith(uploadDir + path.sep) && absolutePath !== uploadDir) {
    return { absPath: null };
  }
  return { absPath: absolutePath };
};

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
  const isParticipant = await projectRepo.isProjectParticipant(projectId, userId);
  return isParticipant;
};

/**
 * 清除工程相关缓存
 * @param {number} [userId] - 用户ID，传入则清除该用户的工程列表缓存
 */
const invalidateCache = async (userId) => {
  try {
    await cache.invalidateProjectCache(userId);
  } catch (error) {
    logger.warn('清除工程缓存失败', { userId, error: error.message });
  }
};

/**
 * 重新计算工程总额（所有子项目金额之和）
 * @param {number} projectId - 工程ID
 * @param {object} [client] - pg 事务客户端，不传则使用连接池
 * @returns {Promise<number>} 新的工程总额
 */
const recalculateProjectTotal = async (projectId, client) => {
  const totalAmount = await projectRepo.getSubprojectsTotalAmount(projectId, client);
  await projectRepo.updateProject(projectId, { total_amount: totalAmount }, client);
  return totalAmount;
};

// ========== 导出的业务方法 ==========

module.exports = {
  /**
   * 创建工程（含子项目）
   *
   * 业务规则：
   * 1. 验证施工人员ID存在性
   * 2. 空间类型/施工方案名称转ID
   * 3. 如果工程名已存在，作为子项目添加（需检查权限和参与性）
   * 4. admin不能添加子项目（V2.0权限）
   * 5. 计算子项目金额
   * 6. 更新工程总额
   * 7. 添加历史记录
   * 8. 清除缓存
   *
   * @param {object} params - 创建参数
   * @param {string} params.name - 工程名称
   * @param {string} params.spaceType - 空间类型名称
   * @param {string} params.constructionScheme - 施工方案名称
   * @param {number} params.length - 长度（厘米）
   * @param {number} params.width - 宽度（厘米）
   * @param {string} params.salaryDistribution - 工资分配方式
   * @param {Array<{userId: number}>} params.constructors - 施工人员列表
   * @param {string} [params.remark] - 备注
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.userRole - 当前用户角色
   * @returns {Promise<{projectId: number}>} 创建的工程ID
   */
  async createProject({
    name,
    spaceType,
    constructionScheme,
    length,
    width,
    salaryDistribution,
    constructors,
    remark,
    workerWorkDays,
    userId,
    userRole,
    // 实测数量（异形空间现场实测值，提供时覆盖按长宽计算的 quantity）
    measuredQuantity,
    measuredNote,
    // 高度（厘米，仅梯形等需要三维参数的形状使用）
    height,
  }) {
    // 1. 验证施工人员ID存在性
    const constructorIds = constructors.map((c) => c.userId);
    const existingUsers = await projectRepo.findUsersByIds(constructorIds);
    if (existingUsers.length !== constructorIds.length) {
      const existingIds = existingUsers.map((u) => u.id);
      const invalidIds = constructorIds.filter((id) => !existingIds.includes(id));
      throw new BusinessError(3002, `施工人员ID不存在: ${invalidIds.join(', ')}`);
    }

    // 2. 空间类型名称转ID，同时获取 shape（决定计算公式）
    const spaceTypeRecord = await projectRepo.findSpaceTypeByName(spaceType);
    if (!spaceTypeRecord) {
      throw new BusinessError(3007, `空间类型不存在: ${spaceType}`);
    }
    const spaceTypeId = spaceTypeRecord.id;
    const shape = spaceTypeRecord.shape || 'rectangle';

    // 3. 施工方案名称转ID，同时获取单价和单位
    const constructionPlan = await projectRepo.findConstructionPlanByName(constructionScheme);
    if (!constructionPlan) {
      throw new BusinessError(3008, `施工方案不存在: ${constructionScheme}`);
    }
    const constructionPlanId = constructionPlan.id;
    const unitPrice = constructionPlan.price;
    const unit = constructionPlan.unit;

    // 4. 验证宽度：圆形不需要宽度，length 单位不需要宽度，其他形状需要
    //    梯形需要 height，此处先校验宽度，height 在计算前再校验
    const noWidthShapes = ['circle'];
    const needsWidth = unit !== 'length' && !noWidthShapes.includes(shape);
    if (needsWidth && (!width || width <= 0)) {
      throw new BusinessError(1001, '请输入有效的宽度');
    }

    // 梯形必须提供 height
    if (shape === 'trapezoid' && (!height || height <= 0)) {
      throw new BusinessError(1001, '梯形空间必须输入有效的高度');
    }

    // 5. 检查工程名是否已存在
    const existingProject = await projectRepo.findProjectByName(name);
    let projectId;
    let isNewProject = true;

    if (existingProject) {
      // 工程名已存在，作为子项目添加
      // V2.0: admin不能添加子项目
      if (userRole === 'admin') {
        throw new BusinessError(4002, '您的权限为管理员，只能查看工程和系统配置，无法添加子项目');
      }

      projectId = existingProject.id;
      isNewProject = false;

      // 安全检查：用户是否参与该工程
      const isParticipant = await checkProjectParticipant(projectId, userId);
      if (!isParticipant) {
        throw new BusinessError(4002, '您未参与此工程，无法添加子项目');
      }
    }

    // 6. 预先计算子项目金额（移到事务内确保数据一致性）
    // 若提供有效的实测数量，则覆盖按长宽计算的 quantity（适用于异形空间）
    // 圆形不需要宽度，width 兜底为 0；length 单位宽度兜底为 100（保持历史兼容）
    let widthToUse;
    if (unit === 'length') {
      widthToUse = 100;
    } else if (noWidthShapes.includes(shape)) {
      widthToUse = 0;
    } else {
      widthToUse = width;
    }
    const heightToUse = shape === 'trapezoid' ? height : 0;
    const { quantity, amount } = calculation.calculateSubprojectAmount(
      unit, length, widthToUse, unitPrice, measuredQuantity, shape, heightToUse
    );

    // 预先计算历史记录信息（将在事务内写入，确保与工程/子项目数据一致）
    const historyAction = isNewProject ? 'CREATE_PROJECT' : 'ADD_SUBPROJECT';
    const historyDesc = isNewProject
      ? `创建工程: ${name}`
      : `添加子项目: ${spaceType} - ${constructionScheme}`;

    // 7. 在事务中执行创建操作（包含金额计算、工程总额更新、历史记录写入）
    const result = await projectRepo.createProjectWithSubproject({
      // 工程信息（新工程时使用）
      name,
      description: remark || null,
      userId,
      salaryDistribution: salaryDistribution || 'average',
      // 子项目信息
      isNewProject,
      projectId,
      spaceTypeId,
      constructionPlanId,
      length,
      width,
      // height 字段仅梯形等需要三维参数的形状使用，其他形状为 null
      height: shape === 'trapezoid' ? height : null,
      quantity,
      amount,
      // 实测信息（异形空间现场实测值，null 表示按长宽计算）
      measuredQuantity: measuredQuantity ?? null,
      measuredNote: measuredNote ?? null,
      constructors,
      // 按工日分配模式下的工日数据（新工程时写入project_workers.workdays）
      workerWorkDays,
      // 历史记录信息（事务内写入）
      historyAction,
      historyDesc,
    });

    projectId = result.projectId;

    // 8. 新工程且按工日分配模式：更新施工人员工日（createProjectWithSubproject已处理新工程的workdays，这里处理追加子项目场景）
    // 注：新工程的workdays在事务内已写入，追加子项目时workdays保持不变（沿用首次设置）
    if (!isNewProject && workerWorkDays && workerWorkDays.length > 0) {
      for (const item of workerWorkDays) {
        await projectRepo.updateWorkerWorkdays(projectId, item.userId, item.workdays);
      }
    }

    // 9. 清除缓存（工程总额更新与历史记录已在事务内完成）
    await invalidateCache(userId);

    logger.info('创建工程成功', {
      projectId,
      subprojectId: result.subprojectId,
      isNewProject,
      userId,
    });

    return { projectId };
  },

  /**
   * 获取工程列表（带筛选+分页）
   *
   * 业务规则：
   * - 施工员只能看自己参与的工程
   * - 支持缓存（按用户缓存10分钟）
   *
   * @param {object} filters - 筛选参数
   * @param {number} [filters.page=1] - 页码
   * @param {number} [filters.size=10] - 每页条数
   * @param {string} [filters.month] - 月份筛选
   * @param {string} [filters.yearMonth] - 年月筛选（YYYY-MM 或 YYYY-MM-DD）
   * @param {number} [filters.year] - 年份筛选
   * @param {string} [filters.keyword] - 关键词搜索
   * @param {string} [filters.status] - 状态筛选
   * @param {string} [filters.creatorNickname] - 创建人昵称搜索
   * @param {string} [filters.workerNickname] - 施工员昵称搜索
   * @param {string} [filters.startDate] - 开始日期
   * @param {string} [filters.endDate] - 结束日期
   * @param {string} [filters.settlementStatus] - 结算状态筛选
   * @param {number} filters.userId - 当前用户ID
   * @param {string} filters.userRole - 当前用户角色
   * @returns {Promise<{list: Array, total: number, page: number, size: number, hasNext: boolean}>}
   */
  async getProjects(filters) {
    const {
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
      settlementStatus,
      userId,
      userRole,
    } = filters;

    const pageNum = parseInt(page, 10) || 1;
    const sizeNum = parseInt(size, 10) || 10;

    // 构建缓存键
    // 需包含所有影响查询结果的筛选参数，否则不同筛选条件会错误命中同一缓存
    const cacheKeyStr = cache.cacheKey(
      'projects',
      userId,
      'list',
      pageNum,
      sizeNum,
      month || '',
      yearMonth || '',
      year || '',
      keyword || '',
      status || '',
      settlementStatus || '',
      creatorNickname || '',
      workerNickname || '',
      startDate || '',
      endDate || ''
    );

    // 尝试从缓存获取
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    // 构建查询参数
    const queryParams = {
      userId,
      userRole,
      page: pageNum,
      size: sizeNum,
      month,
      yearMonth,
      year,
      keyword,
      status,
      creatorNickname,
      workerNickname,
      startDate,
      endDate,
      settlementStatus,
    };

    // 通过 repo 查询工程列表
    const { list, total } = await projectRepo.findProjects(queryParams);

    // repo 层已移除与 workers 完全相同的 constructors 子查询，此处补齐字段
    // 将 workers 同一引用作为 constructors 返回，保持前端响应结构不变
    // 同时显式将 total_amount 转为 float，避免 pg 类型解析器未生效时返回字符串
    // 导致前端 ProjectDto.totalAmount: Double 被 coerce 为 0.0
    const listWithConstructors = list.map((project) => ({
      ...project,
      total_amount: project.total_amount !== null && project.total_amount !== undefined
        ? parseFloat(project.total_amount)
        : 0,
      constructors: project.workers,
    }));

    const result = {
      list: listWithConstructors,
      total,
      page: pageNum,
      size: sizeNum,
      hasNext: pageNum * sizeNum < total,
    };

    // 写入缓存（10分钟）
    await cache.set(cacheKeyStr, result, cache.TTL.MEDIUM);

    return result;
  },

  /**
   * 获取工程详情
   *
   * @param {number} projectId - 工程ID
   * @returns {Promise<object>} 工程详情（含子项目、施工人员、附件）
   */
  async getProjectDetail(projectId, user) {
    // 尝试从缓存获取
    const cacheKeyStr = cache.cacheKey('projects', 'detail', projectId);
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      // 深度防御：即使路由层 requireProjectView 中间件已校验权限，
      // 此处仍补充 constructor 参与性校验，防止中间件被绕过或重构后缓存直接泄露
      // admin/documenter 可查看全部工程，无需校验
      if (user && user.role === 'constructor') {
        const isParticipant = await projectRepo.isParticipant(projectId, user.id);
        if (!isParticipant) {
          throw new BusinessError(4002, '您未参与此工程，无法查看详情');
        }
      }
      return cachedData;
    }

    // 查询工程基本信息
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    // 权限兜底校验：constructor 必须是工程参与者
    // admin/documenter 可查看全部工程，无需校验
    if (user && user.role === 'constructor') {
      const isParticipant = await projectRepo.isParticipant(projectId, user.id);
      if (!isParticipant) {
        throw new BusinessError(4002, '您未参与此工程，无法查看详情');
      }
    }

    // 查询子项目信息
    const subprojects = await projectRepo.findSubprojectsByProjectId(projectId);

    // 查询施工人员信息
    const workers = await projectRepo.findProjectWorkers(projectId);

    // 查询附件信息
    const files = await projectRepo.findProjectFiles(projectId);

    // 转换子项目字段名以匹配前端期望
    // 关键修复：显式将 NUMERIC 字段转为 float，避免 pg 类型解析器未生效时返回字符串
    // 导致前端 kotlinx.serialization + coerceInputValues=true 静默将字符串 coerce 为 null
    // 表现为子项目金额显示为 ¥0.00
    const subProjects = subprojects.map((sp) => ({
      ...sp,
      // NUMERIC 字段显式 parseFloat，确保返回 JSON number
      length: sp.length !== null ? parseFloat(sp.length) : null,
      width: sp.width !== null ? parseFloat(sp.width) : null,
      // height（NUMERIC(10,2)，梯形等异形空间场景使用，其他形状为 null）
      height: sp.height !== null && sp.height !== undefined ? parseFloat(sp.height) : null,
      quantity: sp.quantity !== null ? parseFloat(sp.quantity) : null,
      amount: sp.amount !== null ? parseFloat(sp.amount) : null,
      // 实测数量（NUMERIC(10,2)，异形空间场景使用）
      measured_quantity: sp.measured_quantity !== null && sp.measured_quantity !== undefined ? parseFloat(sp.measured_quantity) : null,
      price: sp.price !== null ? parseFloat(sp.price) : null,
      space_type: sp.space_type_name,
      // 空间形状（来自 space_types 表，前端按形状动态渲染参数组）
      space_type_shape: sp.space_type_shape || 'rectangle',
      construction_scheme: sp.construction_plan_name,
      unit_price: sp.price !== null ? parseFloat(sp.price) : null,
      unit_type: sp.unit,
    }));

    // 同样修复 workers 中的 workdays 字段（NUMERIC(6,2)）
    // 如果 workdays 为字符串，前端 WorkerDto.workdays: Double? 会被 coerce 为 null
    // 导致按工日分配时工费计算为 0
    const normalizedWorkers = workers.map((w) => ({
      ...w,
      workdays: w.workdays !== null && w.workdays !== undefined ? parseFloat(w.workdays) : null,
    }));

    // 修复 project 的 total_amount 字段（NUMERIC(14,4)）
    const normalizedProject = {
      ...project,
      total_amount: project.total_amount !== null && project.total_amount !== undefined
        ? parseFloat(project.total_amount)
        : 0,
    };

    const result = {
      ...normalizedProject,
      sub_projects: subProjects,
      workers: normalizedWorkers,
      constructors: normalizedWorkers,
      files,
    };

    // 写入缓存（10分钟）
    await cache.set(cacheKeyStr, result, cache.TTL.MEDIUM);

    return result;
  },

  /**
   * 更新工程
   *
   * 业务规则：
   * - 检查用户是否参与该工程
   * - 工程完工时同步子项目状态
   * - 清除缓存
   *
   * @param {number} projectId - 工程ID
   * @param {object} updates - 更新内容
   * @param {string} [updates.name] - 工程名称
   * @param {string} [updates.description] - 描述
   * @param {string} [updates.status] - 状态
   * @param {string} [updates.salaryDistribution] - 工资分配方式
   * @param {number} [updates.totalWorkDays] - 总工作天数
   * @param {Array} [updates.constructors] - 施工人员列表
   * @param {Array} [updates.workerWorkDays] - 施工人员工作天数
   * @param {number} userId - 当前用户ID
   * @returns {Promise<{id: number}>}
   */
  async updateProject(projectId, updates, userId) {
    // 检查工程是否存在
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法修改');
    }

    // 构建更新字段
    // 注意：键名必须使用 camelCase（如 salaryDistribution），
    // 因为 projectRepo.update 的 fieldToColumn 映射表使用 camelCase 键查找数据库列名
    const updateFields = {};
    if (updates.name !== undefined) updateFields.name = updates.name;
    if (updates.description !== undefined) updateFields.description = updates.description;
    if (updates.status !== undefined) updateFields.status = updates.status;
    if (updates.salaryDistribution !== undefined) {
      updateFields.salaryDistribution = updates.salaryDistribution;
    }
    if (updates.totalWorkDays !== undefined) {
      updateFields.totalWorkDays = updates.totalWorkDays;
    }
    // 工程备注字段后端使用 remark（数据库列名也是 remark，直接透传）
    // 空字符串转为 null 存储，保持数据库中无备注时统一为 null（避免 null/空字符串混用）
    if (updates.remark !== undefined) {
      updateFields.remark = updates.remark === '' ? null : updates.remark;
    }

    // 检查是否有可更新的内容
    const hasFieldsToUpdate = Object.keys(updateFields).length > 0;
    const hasConstructorsToUpdate = updates.constructors !== undefined;
    const hasWorkDaysToUpdate = updates.workerWorkDays !== undefined && updates.workerWorkDays.length > 0;

    if (!hasFieldsToUpdate && !hasConstructorsToUpdate && !hasWorkDaysToUpdate) {
      throw new BusinessError(1001, '参数错误');
    }

    // 更新工程基本信息
    if (hasFieldsToUpdate) {
      await projectRepo.updateProject(projectId, updateFields);
    }

    // 更新施工人员（合并工日数据，避免 replaceWorkers 替换后工日丢失）
    // 关键修复：原实现先调用 updateProjectWorkers（删除并重新插入工人，丢失 workdays），
    //          再调用 updateWorkerWorkDays 更新工日。但如果只传 constructors 不传 workerWorkDays，
    //          工日数据会丢失。现在合并 constructors 和 workerWorkDays，一次性写入。
    if (hasConstructorsToUpdate) {
      // 构建工日映射（按工日分配模式下使用）
      const workdaysMap = {};
      if (hasWorkDaysToUpdate) {
        for (const item of updates.workerWorkDays) {
          workdaysMap[item.userId] = item.workdays;
        }
      }
      // 合并 constructors 和 workdays，确保替换时保留工日数据
      // 未在 workdaysMap 中的施工人员，workdays 为 null（使用数据库默认值）
      const constructorsWithWorkdays = updates.constructors.map(c => {
        const userId = c.userId || c;
        const workdays = workdaysMap[userId];
        return workdays !== undefined ? { userId, workdays } : { userId };
      });
      await projectRepo.updateProjectWorkers(projectId, constructorsWithWorkdays);
    } else if (hasWorkDaysToUpdate) {
      // 只更新工日（未更新施工人员列表），单独更新工日
      await projectRepo.updateWorkerWorkDays(projectId, updates.workerWorkDays);
    }

    // 工程状态是否变更（用于判断是否需要刷新物化视图）
    let statusChanged = false;

    // 工程完工时同步子项目状态
    if (updates.status === 'completed' && project.status !== 'completed') {
      await projectRepo.updateSubprojectsStatus(projectId, 'completed');
      statusChanged = true;
    }

    // 工程从已完成恢复为其他状态时，子项目恢复为施工中
    if (project.status === 'completed' && updates.status && updates.status !== 'completed') {
      await projectRepo.updateSubprojectsStatus(projectId, 'constructing');
      statusChanged = true;
    }

    // 添加历史记录
    await projectRepo.addProjectHistory(projectId, 'UPDATE_PROJECT', '更新工程信息', userId);

    // 清除缓存
    await invalidateCache(userId);

    // 工程状态变更后异步刷新物化视图（不阻塞响应，失败仅记日志）
    // settlement_status 依赖工程 status 计算（completed → settling），需立即刷新避免数据陈旧
    if (statusChanged) {
      refreshMvAsync();
    }

    logger.info('更新工程成功', { projectId, userId });

    return { id: projectId };
  },

  /**
   * 删除工程（软删除）
   *
   * 业务规则：
   * - 检查用户是否参与该工程
   * - 清除缓存
   *
   * @param {number} projectId - 工程ID
   * @param {number} userId - 当前用户ID
   * @returns {Promise<null>}
   */
  async deleteProject(projectId, userId) {
    // 检查工程是否存在
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    // 检查工程是否已删除
    if (project.status === 'deleted') {
      throw new BusinessError(3004, '工程已删除');
    }

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法删除');
    }

    // 软删除工程
    await projectRepo.updateProject(projectId, { status: 'deleted' });

    // 同步子项目状态为 canceled
    // 修复：原实现未同步子项目状态，导致子项目状态与工程状态不一致
    // （对比 updateProject 完工/恢复时同步子项目状态的逻辑）
    await projectRepo.updateSubprojectsStatus(projectId, 'canceled');

    // 添加历史记录
    await projectRepo.addProjectHistory(projectId, 'DELETE_PROJECT', '删除工程', userId);

    // 清除缓存
    await invalidateCache(userId);

    // 异步刷新物化视图
    // 修复：原实现未刷新，物化视图 mv_project_user_settlement_status 依赖工程 status
    // 计算 settlement_status，不刷新则统计接口最长5分钟仍计入已删除工程
    refreshMvAsync();

    logger.info('删除工程成功', { projectId, userId });

    return null;
  },

  /**
   * 更新子项目
   *
   * 业务规则：
   * - 检查用户是否参与该工程
   * - 重新计算子项目金额
   * - 重新计算工程总额
   * - 清除缓存
   *
   * @param {number} projectId - 工程ID
   * @param {number} subprojectId - 子项目ID
   * @param {object} updates - 更新内容
   * @param {string} [updates.spaceType] - 空间类型名称
   * @param {string} [updates.constructionScheme] - 施工方案名称
   * @param {number} [updates.length] - 长度（厘米）
   * @param {number} [updates.width] - 宽度（厘米）
   * @param {string} [updates.remark] - 备注
   * @param {number} userId - 当前用户ID
   * @returns {Promise<{id: number}>}
   */
  async updateSubproject(projectId, subprojectId, updates, userId) {
    // 检查子项目是否存在
    const subproject = await projectRepo.findSubprojectById(subprojectId);
    if (!subproject) {
      throw new BusinessError(3009, '子项目不存在');
    }

    // 校验子项目归属当前工程，防止跨工程越权操作
    // 攻击场景：传入自己参与的 projectId + 他人工程的 subprojectId，绕过权限校验
    if (Number(subproject.project_id) !== Number(projectId)) {
      throw new BusinessError(4002, '子项目不属于该工程，无法操作');
    }

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法修改子项目');
    }

    // 构建更新字段
    const updateFields = {};
    // 记录最新 shape，用于重新计算金额时选择公式
    let latestShape = null;

    // 空间类型名称转ID
    if (updates.spaceType !== undefined) {
      const spaceTypeRecord = await projectRepo.findSpaceTypeByName(updates.spaceType);
      if (!spaceTypeRecord) {
        throw new BusinessError(3007, `空间类型不存在: ${updates.spaceType}`);
      }
      updateFields.space_type_id = spaceTypeRecord.id;
      // 记录新 shape 用于后续金额重算
      latestShape = spaceTypeRecord.shape || 'rectangle';
    }

    // 施工方案名称转ID
    if (updates.constructionScheme !== undefined) {
      const constructionPlan = await projectRepo.findConstructionPlanByName(updates.constructionScheme);
      if (!constructionPlan) {
        throw new BusinessError(3008, `施工方案不存在: ${updates.constructionScheme}`);
      }
      updateFields.construction_plan_id = constructionPlan.id;
    }

    // 长度（统一存储厘米，与创建工程时保持一致，不再转换为米）
    if (updates.length !== undefined) {
      updateFields.length = updates.length;
    }

    // 宽度（统一存储厘米，与创建工程时保持一致，不再转换为米）
    if (updates.width !== undefined) {
      updateFields.width = updates.width;
    }

    // 高度（厘米，仅梯形等需要三维参数的形状使用）
    // 空字符串/null/非梯形形状都置为 null，避免无意义数据残留
    if (updates.height !== undefined) {
      const heightVal = updates.height === '' || updates.height === null ? null : updates.height;
      updateFields.height = heightVal;
    }

    // 实测数量：空字符串/null 表示清除实测值，回退到按长宽计算
    if (updates.measuredQuantity !== undefined) {
      updateFields.measuredQuantity = updates.measuredQuantity === '' || updates.measuredQuantity === null
        ? null
        : updates.measuredQuantity;
    }

    // 实测备注：空字符串转为 null
    if (updates.measuredNote !== undefined) {
      updateFields.measuredNote = updates.measuredNote === '' ? null : updates.measuredNote;
    }

    // 备注
    // 空字符串转为 null 存储，保持数据库中无备注时统一为 null（避免 null/空字符串混用）
    if (updates.remark !== undefined) {
      updateFields.remark = updates.remark === '' ? null : updates.remark;
    }

    // 检查是否有可更新的内容
    if (Object.keys(updateFields).length === 0) {
      throw new BusinessError(1001, '参数错误');
    }

    // 在事务中执行：字段更新 → 金额重算 → 工程总额重算 → 历史记录
    // 修复：原实现四步分别独立执行无事务保护，并发更新下可能导致：
    //   1. 子项目字段更新成功但金额重算失败 → 工程总额与子项目明细不一致
    //   2. 两个并发更新同时读旧值并写入，丢失更新
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      // 1. 更新子项目字段
      await projectRepo.updateSubprojectInTransaction(subprojectId, updateFields, client);

      // 2. 重新计算子项目金额（事务内读取更新后的数据）
      const updatedSubproject = await projectRepo.findSubprojectDetailById(subprojectId, client);
      if (updatedSubproject) {
        // 数据库存储单位为厘米，calculation 服务接收厘米，无需单位转换
        const lengthCm = parseFloat(updatedSubproject.length) || 0;
        const widthCm = parseFloat(updatedSubproject.width) || 0;
        const heightCm = parseFloat(updatedSubproject.height) || 0;
        const unit = updatedSubproject.unit || 'area';
        const unitPrice = parseFloat(updatedSubproject.price) || 0;
        // shape 优先使用本次切换空间类型时记录的值，否则取数据库中现有的 shape
        const shape = latestShape || updatedSubproject.space_type_shape || 'rectangle';
        // 实测数量：若存在则覆盖按长宽计算的 quantity（异形空间场景）
        const measuredQuantity = updatedSubproject.measured_quantity !== null && updatedSubproject.measured_quantity !== undefined
          ? parseFloat(updatedSubproject.measured_quantity)
          : null;

        // 调用 calculation 服务计算新的数量和金额（传入 measuredQuantity 时优先使用实测值）
        const { quantity, amount } = calculation.calculateSubprojectAmount(
          unit,
          lengthCm,
          widthCm,
          unitPrice,
          measuredQuantity,
          shape,
          heightCm
        );

        // 更新子项目的数量和金额（事务内）
        await projectRepo.updateSubprojectAmount(subprojectId, quantity, amount, client);

        logger.info('重新计算子项目金额', {
          subprojectId,
          lengthCm,
          widthCm,
          heightCm,
          unit,
          unitPrice,
          shape,
          measuredQuantity,
          quantity,
          amount,
        });
      }

      // 3. 重新计算工程总额（事务内）
      await recalculateProjectTotal(projectId, client);

      // 4. 添加历史记录（事务内）
      await projectRepo.addProjectHistory(projectId, 'UPDATE_SUBPROJECT', '更新子项目信息', userId, client);

      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }

    // 清除缓存
    await invalidateCache(userId);

    logger.info('更新子项目成功', { projectId, subprojectId, userId });

    return { id: subprojectId };
  },

  /**
   * 删除子项目
   *
   * 业务规则：
   * - 检查用户是否参与该工程
   * - 重新计算工程总额
   * - 清除缓存
   *
   * @param {number} projectId - 工程ID
   * @param {number} subprojectId - 子项目ID
   * @param {number} userId - 当前用户ID
   * @returns {Promise<null>}
   */
  async deleteSubproject(projectId, subprojectId, userId) {
    // 检查子项目是否存在
    const subproject = await projectRepo.findSubprojectById(subprojectId);
    if (!subproject) {
      throw new BusinessError(3009, '子项目不存在');
    }

    // 校验子项目归属当前工程，防止跨工程越权操作
    if (Number(subproject.project_id) !== Number(projectId)) {
      throw new BusinessError(4002, '子项目不属于该工程，无法操作');
    }

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法删除子项目');
    }

    // 在事务中执行：删除子项目 → 重算工程总额 → 历史记录
    // 修复：原实现三步独立执行无事务保护，并发下可能导致工程总额与子项目明细不一致
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await projectRepo.deleteSubprojectInTransaction(subprojectId, client);
      await recalculateProjectTotal(projectId, client);
      await projectRepo.addProjectHistory(projectId, 'DELETE_SUBPROJECT', '删除子项目', userId, client);
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }

    // 清除缓存
    await invalidateCache(userId);

    logger.info('删除子项目成功', { projectId, subprojectId, userId });

    return null;
  },

  /**
   * 转交子项目
   *
   * 业务规则：
   * - 子项目必须存在且属于当前工程
   * - 只有子项目创建者可以转交（与 updateSubprojectStatus 权限模型一致）
   * - 目标用户必须存在
   * - 同时清除转出者和接收者缓存
   *
   * @param {number} projectId - 工程ID
   * @param {number} subprojectId - 子项目ID
   * @param {number} toUserId - 目标用户ID
   * @param {number} userId - 当前用户ID
   * @returns {Promise<{id: number}>}
   */
  async transferSubproject(projectId, subprojectId, toUserId, userId) {
    // 检查子项目是否存在
    const subproject = await projectRepo.findSubprojectById(subprojectId);
    if (!subproject) {
      throw new BusinessError(3009, '子项目不存在');
    }

    // 校验子项目归属当前工程，防止跨工程越权操作
    if (Number(subproject.project_id) !== Number(projectId)) {
      throw new BusinessError(4002, '子项目不属于该工程，无法操作');
    }

    // 权限校验：只有子项目创建者可以转交
    // 修复：原逻辑为"任何工程参与者可操作"，存在非创建者恶意转移他人子项目的风险
    // 与 updateSubprojectStatus 权限模型保持一致
    if (Number(subproject.created_by) !== Number(userId)) {
      throw new BusinessError(4002, '只有子项目创建者可以转交子项目');
    }

    // 检查目标用户是否存在
    const targetUser = await projectRepo.findUserById(toUserId);
    if (!targetUser) {
      throw new BusinessError(3002, '目标用户不存在');
    }

    // 转交子项目
    await projectRepo.updateSubproject(subprojectId, { created_by: toUserId });

    // 添加历史记录
    await projectRepo.addProjectHistory(
      projectId,
      'TRANSFER_SUBPROJECT',
      `转交子项目给用户${toUserId}`,
      userId
    );

    // 清除缓存：同时清除转出者和接收者的缓存，确保接收者能立即看到新转入的子项目
    // 修复：原逻辑只清除转出者缓存，接收者10分钟内看不到新转入的子项目
    await invalidateCache(userId);
    if (Number(toUserId) !== Number(userId)) {
      await invalidateCache(toUserId);
    }

    logger.info('转交子项目成功', { projectId, subprojectId, fromUserId: userId, toUserId });

    return { id: subprojectId };
  },

  /**
   * 获取工程历史记录
   *
   * @param {number} projectId - 工程ID
   * @returns {Promise<Array>} 历史记录列表
   */
  async getProjectHistory(projectId) {
    // 检查工程是否存在
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    const history = await projectRepo.findProjectHistory(projectId);
    return history;
  },

  /**
   * 获取工程施工人员
   *
   * @param {number} projectId - 工程ID
   * @returns {Promise<Array>} 施工人员列表
   */
  async getProjectWorkers(projectId) {
    // 检查工程是否存在
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    const workers = await projectRepo.findProjectWorkers(projectId);
    return workers;
  },

  /**
   * 更新子项目状态
   *
   * 业务规则：
   * - 子项目必须存在
   * - 只有子项目创建者可以修改状态
   * - 状态变更与历史记录在同一事务内完成（由 repo 保证）
   *
   * @param {number} projectId - 工程ID（用于历史记录）
   * @param {number} subprojectId - 子项目ID
   * @param {string} status - 新状态（preparing/constructing/completed/canceled）
   * @param {number} userId - 当前用户ID
   * @returns {Promise<{id: number}>} 返回子项目ID
   */
  async updateSubprojectStatus(projectId, subprojectId, status, userId) {
    // 调用 repo 在事务中完成「存在性校验 + 权限校验 + 状态更新 + 历史记录」
    const result = await projectRepo.updateSubprojectStatus(subprojectId, status, userId, projectId);

    // 子项目不存在
    if (result.notFound) {
      throw new BusinessError(3003, '子项目不存在');
    }

    // 权限不足
    if (result.forbidden) {
      throw new BusinessError(4002, '只有子项目创建者可以修改子项目状态');
    }

    // 清除缓存
    await invalidateCache(userId);

    // 异步刷新物化视图
    // 修复：子项目状态变更（completed/canceled）影响结算状态计算，
    // statisticsService 多处依赖 sp.status = 'completed' 计算工资，
    // 不刷新则统计接口最长5分钟才更新，影响实时性
    // （与 updateProject 状态变更后刷新物化视图的逻辑对齐）
    refreshMvAsync();

    logger.info('更新子项目状态成功', { projectId, subprojectId, status, userId });

    return { id: subprojectId };
  },

  /**
   * 上传工程附件
   *
   * 业务规则：
   * - 工程必须存在
   * - 工程创建者、施工员或管理员可上传
   * - 支持两种方式：
   *   1) JSON 方式：前端已上传文件，仅保存记录（fileData.path 存在）
   *   2) multipart/form-data 方式：通过 ctx.request.files 接收
   *
   * @param {number} projectId - 工程ID
   * @param {object} fileData - 文件数据（JSON 方式为请求体；multipart 方式为 { files } 对象）
   * @param {number} userId - 当前用户ID
   * @param {object} user - 当前用户对象（含 role，用于权限判断）
   * @returns {Promise<Array>} 上传成功的文件列表
   */
  async uploadFile(projectId, fileData, userId, user) {
    // 1. 检查工程是否存在
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    // 2. 权限校验：路由层 requireFileModify 中间件已拦截 admin/documenter
    //    此处补充参与者校验：constructor 只能上传自己参与工程的附件
    const isParticipant = await projectRepo.isParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无权上传附件');
    }

    const uploadedFiles = [];

    // 方式1：接收 JSON 数据（前端已上传文件，只需保存记录）
    if (fileData && fileData.path) {
      const { filename, originalName, path: filePath, size, type } = fileData;

      // 路径格式校验：防止前端伪造任意路径导致路径穿越或URL污染
      // 修复：原实现直接存入 fileData.path，前端可传任意字符串
      if (!isValidFilePath(filePath)) {
        throw new BusinessError(1001, '附件路径格式不合法');
      }

      await projectRepo.addFileRecord(projectId, {
        filename,
        originalName: originalName || filename,
        path: filePath,
        size: size || 0,
        type: type || '',
      }, userId);

      uploadedFiles.push({
        filename: originalName || filename,
        url: filePath,
        size: size,
        type: type,
      });
    }
    // 方式2：接收 multipart/form-data 文件（原有方式）
    else {
      const files = fileData && fileData.files;
      if (!files) {
        throw new BusinessError(1001, '参数错误');
      }

      const fileArray = Array.isArray(files) ? files : [files];

      for (const file of fileArray) {
        const fileUrl = `/uploads/${file.newFilename}`;

        await projectRepo.addFileRecord(projectId, {
          filename: file.originalFilename,
          originalName: file.originalFilename,
          path: fileUrl,
          size: file.size || 0,
          type: file.mimetype || '',
        }, userId);

        uploadedFiles.push({
          filename: file.originalFilename,
          url: fileUrl,
        });
      }
    }

    return uploadedFiles;
  },

  /**
   * 删除工程附件
   *
   * 业务规则：
   * - 工程必须存在
   * - 仅工程创建者、施工人员或管理员可删除
   * - 删除数据库记录
   * - 尝试删除物理文件（失败不影响接口结果，仅记录日志）
   *
   * 物理文件路径解析：files.path 存储形如 /upload/YYYYMM/工程名/uuid.ext 的 URL 路径
   * 需要移除 /upload 前缀后与后端 upload 目录拼接得到绝对路径
   *
   * @param {number} projectId - 工程ID
   * @param {number} fileId - 文件ID
   * @param {number} userId - 当前用户ID
   * @param {object} user - 当前用户对象（含 role，用于权限判断）
   * @returns {Promise<{fileId: number}>} 返回被删除的文件ID
   */
  async deleteFile(projectId, fileId, userId, user) {
    // 1. 检查工程是否存在
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
    }

    // 2. 权限校验：路由层 requireFileModify 中间件已拦截 admin/documenter
    //    此处补充参与者校验：constructor 只能删除自己参与工程的附件
    const isParticipant = await projectRepo.isParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无权删除附件');
    }

    // 3. 查询文件记录，确认归属并获取物理路径
    const fileRecord = await projectRepo.getFileRecordById(fileId, projectId);
    if (!fileRecord) {
      throw new BusinessError(3002, '附件不存在');
    }

    // 4. 删除数据库记录
    await projectRepo.deleteFileRecord(fileId);

    // 5. 尝试删除物理文件（失败不影响接口结果，仅记录日志）
    // 物理文件路径解析：files.path 存储的是 /upload/YYYYMM/工程名/uuid.ext 形式的URL路径
    // 安全修复：使用 safeResolveUploadPath 规范化路径并验证仍在 upload 目录内，
    //          防止路径穿越攻击删除任意文件（原实现仅 startsWith 校验可被 /upload/foo/../../config 绕过）
    try {
      const filePath = fileRecord.path || '';
      const { absPath } = safeResolveUploadPath(filePath);
      if (absPath) {
        const fs = require('fs');
        if (fs.existsSync(absPath)) {
          fs.unlinkSync(absPath);
          logger.info(`删除附件物理文件成功: ${absPath}`);
        } else {
          logger.warn(`附件物理文件不存在（可能已被删除）: ${absPath}`);
        }
      }
    } catch (fileErr) {
      logger.warn(`删除附件物理文件失败（不影响数据库记录）: ${fileErr.message}`);
    }

    logger.info('删除附件成功', { projectId, fileId, userId });

    return { fileId: parseInt(fileId) };
  },
};
