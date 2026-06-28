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

    // 7. 在事务中执行创建操作（包含金额计算）
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
    });

    projectId = result.projectId;

    // 8. 更新工程总额
    await recalculateProjectTotal(projectId);

    // 9. 添加历史记录
    const historyAction = isNewProject ? 'CREATE_PROJECT' : 'ADD_SUBPROJECT';
    const historyDesc = isNewProject
      ? `创建工程: ${name}`
      : `添加子项目: ${spaceType} - ${constructionScheme}`;
    await projectRepo.addProjectHistory(projectId, historyAction, historyDesc, userId);

    // 10. 清除缓存
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

    const result = {
      list,
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
    const subProjects = subprojects.map((sp) => ({
      ...sp,
      space_type: sp.space_type_name,
      construction_scheme: sp.construction_plan_name,
      unit_price: sp.price,
      unit_type: sp.unit,
    }));

    const result = {
      ...project,
      sub_projects: subProjects,
      workers,
      constructors: workers,
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
    const updateFields = {};
    if (updates.name !== undefined) updateFields.name = updates.name;
    if (updates.description !== undefined) updateFields.description = updates.description;
    if (updates.status !== undefined) updateFields.status = updates.status;
    if (updates.salaryDistribution !== undefined) {
      updateFields.salary_distribution = updates.salaryDistribution;
    }
    if (updates.totalWorkDays !== undefined) {
      updateFields.total_work_days = updates.totalWorkDays;
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

    // 更新施工人员
    if (hasConstructorsToUpdate) {
      await projectRepo.updateProjectWorkers(projectId, updates.constructors);
    }

    // 更新施工人员工作天数
    if (hasWorkDaysToUpdate) {
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

    // 长度转换（厘米转米）
    if (updates.length !== undefined) {
      updateFields.length = updates.length / 100;
    }

    // 宽度转换（厘米转米）
    if (updates.width !== undefined) {
      updateFields.width = updates.width / 100;
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
      const lengthM = parseFloat(updatedSubproject.length) || 0;
      const widthM = parseFloat(updatedSubproject.width) || 0;
      const unit = updatedSubproject.unit || 'area';
      const unitPrice = parseFloat(updatedSubproject.price) || 0;

      // 调用 calculation 服务计算新的数量和金额
      const { quantity, amount } = calculation.calculateSubprojectAmount(
        unit,
        lengthM * 100, // calculation 接收厘米
        widthM * 100,
        unitPrice
      );

      // 更新子项目的数量和金额
      await projectRepo.updateSubprojectAmount(subprojectId, quantity, amount);

      logger.info('重新计算子项目金额', {
        subprojectId,
        lengthM,
        widthM,
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
};
