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
 * @returns {Promise<number>} 新的工程总额
 */
const recalculateProjectTotal = async (projectId) => {
  const totalAmount = await projectRepo.getSubprojectsTotalAmount(projectId);
  await projectRepo.updateProject(projectId, { total_amount: totalAmount });
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
  }) {
    // 1. 验证施工人员ID存在性
    const constructorIds = constructors.map((c) => c.userId);
    const existingUsers = await projectRepo.findUsersByIds(constructorIds);
    if (existingUsers.length !== constructorIds.length) {
      const existingIds = existingUsers.map((u) => u.id);
      const invalidIds = constructorIds.filter((id) => !existingIds.includes(id));
      throw new BusinessError(3002, `施工人员ID不存在: ${invalidIds.join(', ')}`);
    }

    // 2. 空间类型名称转ID
    const spaceTypeRecord = await projectRepo.findSpaceTypeByName(spaceType);
    if (!spaceTypeRecord) {
      throw new BusinessError(3007, `空间类型不存在: ${spaceType}`);
    }
    const spaceTypeId = spaceTypeRecord.id;

    // 3. 施工方案名称转ID，同时获取单价和单位
    const constructionPlan = await projectRepo.findConstructionPlanByName(constructionScheme);
    if (!constructionPlan) {
      throw new BusinessError(3008, `施工方案不存在: ${constructionScheme}`);
    }
    const constructionPlanId = constructionPlan.id;
    const unitPrice = constructionPlan.price;
    const unit = constructionPlan.unit;

    // 4. 验证宽度：只有当施工方案不是length类型时才需要宽度
    if (unit !== 'length' && (!width || width <= 0)) {
      throw new BusinessError(1001, '请输入有效的宽度');
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
    const widthToUse = unit === 'length' ? 100 : width;
    const { quantity, amount } = calculation.calculateSubprojectAmount(unit, length, widthToUse, unitPrice);

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
      quantity,
      amount,
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
      settlementStatus || ''
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
  async getProjectDetail(projectId) {
    // 尝试从缓存获取
    const cacheKeyStr = cache.cacheKey('projects', 'detail', projectId);
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    // 查询工程基本信息
    const project = await projectRepo.findProjectById(projectId);
    if (!project) {
      throw new BusinessError(3001, '工程不存在');
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
      quantity: sp.quantity !== null ? parseFloat(sp.quantity) : null,
      amount: sp.amount !== null ? parseFloat(sp.amount) : null,
      price: sp.price !== null ? parseFloat(sp.price) : null,
      space_type: sp.space_type_name,
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
    if (updates.remark !== undefined) {
      updateFields.remark = updates.remark;
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

    // 工程完工时同步子项目状态
    if (updates.status === 'completed' && project.status !== 'completed') {
      await projectRepo.updateSubprojectsStatus(projectId, 'completed');
    }

    // 工程从已完成恢复为其他状态时，子项目恢复为施工中
    if (project.status === 'completed' && updates.status && updates.status !== 'completed') {
      await projectRepo.updateSubprojectsStatus(projectId, 'constructing');
    }

    // 添加历史记录
    await projectRepo.addProjectHistory(projectId, 'UPDATE_PROJECT', '更新工程信息', userId);

    // 清除缓存
    await invalidateCache(userId);

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

    // 添加历史记录
    await projectRepo.addProjectHistory(projectId, 'DELETE_PROJECT', '删除工程', userId);

    // 清除缓存
    await invalidateCache(userId);

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

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法修改子项目');
    }

    // 构建更新字段
    const updateFields = {};

    // 空间类型名称转ID
    if (updates.spaceType !== undefined) {
      const spaceTypeRecord = await projectRepo.findSpaceTypeByName(updates.spaceType);
      if (!spaceTypeRecord) {
        throw new BusinessError(3007, `空间类型不存在: ${updates.spaceType}`);
      }
      updateFields.space_type_id = spaceTypeRecord.id;
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

    // 备注
    if (updates.remark !== undefined) {
      updateFields.remark = updates.remark;
    }

    // 检查是否有可更新的内容
    if (Object.keys(updateFields).length === 0) {
      throw new BusinessError(1001, '参数错误');
    }

    // 在事务中执行更新
    await projectRepo.updateSubprojectInTransaction(subprojectId, updateFields);

    // 重新计算子项目金额
    // 获取更新后的子项目完整信息
    const updatedSubproject = await projectRepo.findSubprojectDetailById(subprojectId);
    if (updatedSubproject) {
      // 数据库存储单位为厘米，calculation 服务接收厘米，无需单位转换
      const lengthCm = parseFloat(updatedSubproject.length) || 0;
      const widthCm = parseFloat(updatedSubproject.width) || 0;
      const unit = updatedSubproject.unit || 'area';
      const unitPrice = parseFloat(updatedSubproject.price) || 0;

      // 调用 calculation 服务计算新的数量和金额
      const { quantity, amount } = calculation.calculateSubprojectAmount(
        unit,
        lengthCm,
        widthCm,
        unitPrice
      );

      // 更新子项目的数量和金额
      await projectRepo.updateSubprojectAmount(subprojectId, quantity, amount);

      logger.info('重新计算子项目金额', {
        subprojectId,
        lengthCm,
        widthCm,
        unit,
        unitPrice,
        quantity,
        amount,
      });
    }

    // 重新计算工程总额
    await recalculateProjectTotal(projectId);

    // 添加历史记录
    await projectRepo.addProjectHistory(projectId, 'UPDATE_SUBPROJECT', '更新子项目信息', userId);

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

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法删除子项目');
    }

    // 在事务中执行删除
    await projectRepo.deleteSubprojectInTransaction(subprojectId);

    // 重新计算工程总额
    await recalculateProjectTotal(projectId);

    // 添加历史记录
    await projectRepo.addProjectHistory(projectId, 'DELETE_SUBPROJECT', '删除子项目', userId);

    // 清除缓存
    await invalidateCache(userId);

    logger.info('删除子项目成功', { projectId, subprojectId, userId });

    return null;
  },

  /**
   * 转交子项目
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

    // 检查用户是否参与该工程
    const isParticipant = await checkProjectParticipant(projectId, userId);
    if (!isParticipant) {
      throw new BusinessError(4002, '您未参与此工程，无法修改子项目状态');
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

    // 清除缓存
    await invalidateCache(userId);

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
    // 需要移除 /upload 前缀后，与后端 upload 目录拼接得到绝对路径
    try {
      const filePath = fileRecord.path || '';
      if (filePath.startsWith('/upload/')) {
        const fs = require('fs');
        const path = require('path');
        // 移除 /upload 前缀，保留 YYYYMM/工程名/uuid.ext 相对路径
        const relativePath = filePath.substring('/upload'.length);
        // 与 index.js 中静态文件服务保持一致：path.join(__dirname, 'upload', decodedPath)
        // 注意：service 层 __dirname 为 backend/services，需回退一级到 backend 目录
        const absolutePath = path.join(__dirname, '..', 'upload', relativePath);
        if (fs.existsSync(absolutePath)) {
          fs.unlinkSync(absolutePath);
          logger.info(`删除附件物理文件成功: ${absolutePath}`);
        } else {
          logger.warn(`附件物理文件不存在（可能已被删除）: ${absolutePath}`);
        }
      }
    } catch (fileErr) {
      logger.warn(`删除附件物理文件失败（不影响数据库记录）: ${fileErr.message}`);
    }

    logger.info('删除附件成功', { projectId, fileId, userId });

    return { fileId: parseInt(fileId) };
  },
};
