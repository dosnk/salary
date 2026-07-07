const Joi = require('joi');
const validation = require('../middleware/validation');
const projectService = require('../services/projectService');
const logger = require('../config/logger');

// ========== Controller 方法 ==========

/**
 * 创建工程
 * 从请求体提取参数，调用 projectService.createProject 处理业务逻辑
 */
const createProject = async (ctx) => {
  const { name, spaceType, constructionScheme, length, width, salaryDistribution, constructors, remark, workerWorkDays } = ctx.request.body;
  const userId = ctx.state.user.id;
  const userRole = ctx.state.user.role;

  try {
    const result = await projectService.createProject({
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
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('创建工程失败:', error);
    ctx.fail(5001, '创建工程失败');
  }
};

/**
 * 获取工程列表
 * 从查询参数提取筛选条件，调用 projectService.getProjects 返回分页结果
 */
const getProjects = async (ctx) => {
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
    settlementStatus
  } = ctx.query;

  const userId = ctx.state.user.id;
  const userRole = ctx.state.user.role;

  try {
    const result = await projectService.getProjects({
      page,
      size,
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
    });
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取工程列表失败:', error);
    ctx.fail(5001, '获取工程列表失败');
  }
};

/**
 * 获取工程详情
 * 从路径参数提取工程ID，调用 projectService.getProjectDetail
 */
const getProjectDetail = async (ctx) => {
  const { id } = ctx.params;

  try {
    const result = await projectService.getProjectDetail(id);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取工程详情失败:', error);
    ctx.fail(5001, '获取工程详情失败');
  }
};

/**
 * 更新工程
 * 从路径参数、请求体和用户信息提取参数，调用 projectService.updateProject
 */
const updateProject = async (ctx) => {
  const { id } = ctx.params;
  const { name, description, status, salaryDistribution, constructors, totalWorkDays, workerWorkDays } = ctx.request.body;
  const userId = ctx.state.user.id;

  try {
    const result = await projectService.updateProject(id, {
      name,
      description,
      status,
      salaryDistribution,
      constructors,
      totalWorkDays,
      workerWorkDays,
    }, userId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('更新工程失败:', error);
    ctx.fail(5001, '更新工程失败');
  }
};

/**
 * 删除工程（软删除）
 * 从路径参数和用户信息提取参数，调用 projectService.deleteProject
 */
const deleteProject = async (ctx) => {
  const { id } = ctx.params;
  const userId = ctx.state.user.id;

  try {
    const result = await projectService.deleteProject(id, userId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('删除工程失败:', error);
    ctx.fail(5001, '删除工程失败');
  }
};

/**
 * 获取工程历史记录
 * 从路径参数提取工程ID，调用 projectService.getProjectHistory
 */
const getProjectHistory = async (ctx) => {
  const { id } = ctx.params;

  try {
    const result = await projectService.getProjectHistory(id);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取工程历史记录失败:', error);
    ctx.fail(5001, '获取工程历史记录失败');
  }
};

/**
 * 更新子项目
 * 从路径参数、请求体和用户信息提取参数，调用 projectService.updateSubproject
 */
const updateSubproject = async (ctx) => {
  const { id, subprojectId } = ctx.params;
  const { spaceType, constructionScheme, length, width, remark } = ctx.request.body;
  const userId = ctx.state.user.id;

  try {
    const result = await projectService.updateSubproject(id, subprojectId, {
      spaceType,
      constructionScheme,
      length,
      width,
      remark,
    }, userId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('更新子项目失败:', error);
    ctx.fail(5001, '更新子项目失败');
  }
};

/**
 * 删除子项目
 * 从路径参数和用户信息提取参数，调用 projectService.deleteSubproject
 */
const deleteSubproject = async (ctx) => {
  const { id, subprojectId } = ctx.params;
  const userId = ctx.state.user.id;

  try {
    const result = await projectService.deleteSubproject(id, subprojectId, userId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('删除子项目失败:', error);
    ctx.fail(5001, '删除子项目失败');
  }
};

/**
 * 转交子项目
 * 从路径参数、请求体和用户信息提取参数，调用 projectService.transferSubproject
 */
const transferSubproject = async (ctx) => {
  const { id, subprojectId } = ctx.params;
  const { toUserId } = ctx.request.body;
  const userId = ctx.state.user.id;

  try {
    const result = await projectService.transferSubproject(id, subprojectId, toUserId, userId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('转交子项目失败:', error);
    ctx.fail(5001, '转交子项目失败');
  }
};

/**
 * 更新子项目状态
 * 从路径参数、请求体和用户信息提取参数，调用 projectService.updateSubprojectStatus
 */
const updateSubprojectStatus = async (ctx) => {
  const { id, subprojectId } = ctx.params;
  const { status } = ctx.request.body;
  const userId = ctx.state.user.id;

  try {
    const result = await projectService.updateSubprojectStatus(id, subprojectId, status, userId);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('更新子项目状态失败:', error);
    ctx.fail(5001, '更新子项目状态失败');
  }
};

/**
 * 上传文件（支持两种方式：multipart/form-data 或 JSON）
 * 从路径参数、请求体和用户信息提取参数，调用 projectService.uploadFile
 */
const uploadFile = async (ctx) => {
  const { id } = ctx.params;
  const userId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    // 整合文件数据：
    //   - JSON 方式：ctx.request.body 自带 path 字段，直接透传
    //   - multipart 方式：从 ctx.request.files.files 取出文件对象封装为 { files }
    const fileData = ctx.request.body && ctx.request.body.path
      ? ctx.request.body
      : { files: ctx.request.files?.files };

    const result = await projectService.uploadFile(id, fileData, userId, user);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('上传文件失败:', error);
    ctx.fail(5001, '上传文件失败');
  }
};

/**
 * 删除工程附件
 * 从路径参数和用户信息提取参数，调用 projectService.deleteFile
 * 对应路由：DELETE /v1/projects/:id/files/:fileId
 */
const deleteFile = async (ctx) => {
  const { id, fileId } = ctx.params;
  const userId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    const result = await projectService.deleteFile(id, fileId, userId, user);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('删除附件失败:', error);
    ctx.fail(5001, '删除附件失败');
  }
};

/**
 * 获取工程施工人员
 * 从路径参数提取工程ID，调用 projectService.getProjectWorkers
 */
const getProjectWorkers = async (ctx) => {
  const { id } = ctx.params;

  try {
    const result = await projectService.getProjectWorkers(id);
    ctx.success(result);
  } catch (error) {
    if (error.name === 'BusinessError') {
      ctx.fail(error.code, error.message);
      return;
    }
    logger.error('获取工程施工人员失败:', error);
    ctx.fail(5001, '获取工程施工人员失败');
  }
};

// ========== 校验规则（保留不变） ==========

const createProjectSchema = Joi.object({
  name: Joi.string().min(1).max(100).required().messages({
    'string.min': '工程名称不能为空',
    'string.max': '工程名称不能超过100个文字',
    'any.required': '工程名称是必填项'
  }),
  spaceType: Joi.string().required().messages({
    'any.required': '空间类型是必填项'
  }),
  constructionScheme: Joi.string().required().messages({
    'any.required': '施工方案是必填项'
  }),
  length: Joi.number().positive().required().messages({
    'number.positive': '长度必须大于0',
    'any.required': '长度是必填项'
  }),
  width: Joi.number().allow(null, '').optional().messages({
    'number.positive': '宽度必须大于0'
  }),
  salaryDistribution: Joi.string().valid('average', 'work_days').default('average').messages({
    'any.only': '工资分配方式无效'
  }),
  constructors: Joi.array().items(Joi.object({
    userId: Joi.number().integer().positive().required()
  })).min(1).required().messages({
    'array.min': '至少需要选择一名施工员',
    'any.required': '施工员是必填项'
  }),
  remark: Joi.string().max(500).allow('').messages({
    'string.max': '工程备注不能超过500个文字'
  }),
  // 按工日分配模式下各施工人员的工日数（可选，仅work_days模式有效）
  workerWorkDays: Joi.array().items(Joi.object({
    userId: Joi.number().integer().positive().required(),
    workdays: Joi.number().min(0).required()
  })).optional()
});

const getProjectsSchema = Joi.object({
  page: validation.rules.page,
  size: validation.rules.size,
  month: Joi.number().integer().min(1).max(12),
  yearMonth: Joi.string().pattern(/^\d{4}-\d{2}(-\d{2})?$/),
  year: Joi.number().integer().min(2000).max(2100),
  keyword: Joi.string().max(100),
  status: Joi.string().valid('preparing', 'constructing', 'completed', 'canceled'),
  creatorNickname: Joi.string().max(50),
  workerNickname: Joi.string().max(50),
  startDate: Joi.date(),
  endDate: Joi.date(),
  settlementStatus: Joi.string().valid('settled', 'unsettled', 'settling')
});

const getProjectDetailSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

const updateProjectSchema = Joi.object({
  name: Joi.string().min(1).max(100).messages({
    'string.min': '工程名称不能为空',
    'string.max': '工程名称不能超过100个文字'
  }),
  description: Joi.string().max(500).allow('').messages({
    'string.max': '工程备注不能超过500个文字'
  }),
  remark: Joi.string().max(500).allow('').messages({
    'string.max': '工程备注不能超过500个文字'
  }),
  status: Joi.string().valid('preparing', 'constructing', 'completed', 'canceled').messages({
    'any.only': '工程状态无效'
  }),
  salaryDistribution: Joi.string().valid('average', 'work_days').messages({
    'any.only': '工资分配方式无效'
  }),
  totalWorkDays: Joi.number().min(0).messages({
    'number.min': '总工作天数不能为负数'
  }),
  workerWorkDays: Joi.array().items(Joi.object({
    userId: Joi.number().integer().positive().required(),
    workdays: Joi.number().min(0).required()
  })).messages({
    'array.base': '施工人员工作天数格式无效'
  }),
  constructors: Joi.array().items(Joi.object({
    userId: Joi.number().integer().positive().required()
  })).messages({
    'array.base': '施工人员格式无效'
  })
}).min(1).messages({
  'object.min': '至少需要提供一个字段进行更新'
});

const deleteProjectSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

const getProjectHistorySchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

const updateSubprojectSchema = Joi.object({
  id: Joi.number().integer().positive().required().messages({
    'number.positive': '工程ID必须为正整数'
  }),
  subprojectId: Joi.number().integer().positive().required().messages({
    'number.positive': '子项目ID必须为正整数'
  }),
  spaceType: Joi.string().messages({
    'string.base': '空间类型格式无效'
  }),
  constructionScheme: Joi.string().messages({
    'string.base': '施工方案格式无效'
  }),
  length: Joi.number().positive().messages({
    'number.positive': '长度必须大于0'
  }),
  width: Joi.number().allow(null, '').optional().messages({
    'number.positive': '宽度必须大于0'
  }),
  remark: Joi.string().max(500).allow('').messages({
    'string.max': '备注不能超过500个文字'
  })
}).min(3).messages({
  'object.min': '至少需要提供一个字段进行更新'
});

const deleteSubprojectSchema = Joi.object({
  id: Joi.number().integer().positive().required(),
  subprojectId: Joi.number().integer().positive().required()
});

const transferSubprojectSchema = Joi.object({
  id: Joi.number().integer().positive().required(),
  subprojectId: Joi.number().integer().positive().required(),
  toUserId: Joi.number().integer().positive().required()
});

const updateSubprojectStatusSchema = Joi.object({
  status: Joi.string().valid('preparing', 'constructing', 'completed', 'canceled').required()
});

module.exports = {
  createProject,
  getProjects,
  getProjectDetail,
  updateProject,
  deleteProject,
  getProjectHistory,
  updateSubproject,
  deleteSubproject,
  transferSubproject,
  updateSubprojectStatus,
  uploadFile,
  deleteFile,
  getProjectWorkers,
  createProjectSchema,
  getProjectsSchema,
  getProjectDetailSchema,
  updateProjectSchema,
  deleteProjectSchema,
  getProjectHistorySchema,
  updateSubprojectSchema,
  deleteSubprojectSchema,
  transferSubprojectSchema,
  updateSubprojectStatusSchema
};
