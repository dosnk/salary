const Joi = require('joi');
const validation = require('../middleware/validation');
const projectService = require('../services/projectService');
const logger = require('../config/logger');

// 以下两个方法暂未迁移到 projectService，保留原有数据库操作
const pool = require('../config/database');
const { isAdmin, isConstructor } = require('../middleware/rbac');

// ========== Controller 方法 ==========

/**
 * 创建工程
 * 从请求体提取参数，调用 projectService.createProject 处理业务逻辑
 */
const createProject = async (ctx) => {
  const { name, spaceType, constructionScheme, length, width, salaryDistribution, constructors, remark } = ctx.request.body;
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
 * TODO: projectService 中暂无对应方法，保留原有逻辑，后续补充
 */
const updateSubprojectStatus = async (ctx) => {
  const { id, subprojectId } = ctx.params;
  const { status } = ctx.request.body;
  const userId = ctx.state.user.id;
  const user = ctx.state.user;

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 检查子项目是否存在
    const subprojectResult = await client.query(
      'SELECT sp.*, p.created_by FROM subprojects sp JOIN projects p ON sp.project_id = p.id WHERE sp.id = $1',
      [subprojectId]
    );
    if (subprojectResult.rows.length === 0) {
      await client.query('ROLLBACK');
      ctx.fail(3003, '子项目不存在');
      return;
    }

    const subproject = subprojectResult.rows[0];

    // V2.0: 权限由路由层requireSubprojectManage中间件控制
    // controller层检查：只有子项目创建者可以操作
    if (userId !== subproject.created_by) {
      await client.query('ROLLBACK');
      ctx.fail(4002, '只有子项目创建者可以修改子项目状态');
      return;
    }

    // 更新子项目状态
    await client.query(
      'UPDATE subprojects SET status = $1 WHERE id = $2',
      [status, subprojectId]
    );

    // 添加历史记录
    await client.query(
      'INSERT INTO project_history (project_id, action, description, performed_by) VALUES ($1, $2, $3, $4)',
      [id, 'UPDATE_SUBPROJECT_STATUS', `更新子项目状态为${status}`, userId]
    );

    await client.query('COMMIT');
    ctx.success({ id: subprojectId });
  } catch (error) {
    if (client) {
      try {
        await client.query('ROLLBACK');
        logger.info('[更新子项目状态] 事务已回滚');
      } catch (rollbackErr) {
        logger.error('[更新子项目状态] 事务回滚失败:', rollbackErr);
      }
    }

    logger.error('更新子项目状态失败:', error);
    ctx.fail(5001, '更新子项目状态失败');
  } finally {
    if (client) {
      try {
        client.release();
        logger.info('[更新子项目状态] 数据库连接已释放');
      } catch (releaseErr) {
        logger.error('[更新子项目状态] 释放数据库连接失败:', releaseErr);
      }
    }
  }
};

/**
 * 上传文件（支持两种方式：multipart/form-data 或 JSON）
 * TODO: 涉及文件上传逻辑，暂不重构，保留原有实现
 */
const uploadFile = async (ctx) => {
  const { id } = ctx.params;
  const userId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    // 检查工程是否存在
    const projectResult = await pool.query('SELECT * FROM projects WHERE id = $1', [id]);
    if (projectResult.rows.length === 0) {
      ctx.fail(3001, '工程不存在');
      return;
    }

    const project = projectResult.rows[0];

    // V2.0: 工程创建者、施工员或管理员可以上传文件
    if (userId !== project.created_by && !isAdmin(user) && !isConstructor(user)) {
      ctx.fail(4002, '无操作权限');
      return;
    }

    const uploadedFiles = [];

    // 方式1：接收 JSON 数据（前端已上传文件，只需保存记录）
    if (ctx.request.body && ctx.request.body.path) {
      const { filename, originalName, path: filePath, size, type } = ctx.request.body;

      await pool.query(
        'INSERT INTO files (project_id, filename, original_name, path, size, type, uploaded_by) VALUES ($1, $2, $3, $4, $5, $6, $7)',
        [id, filename, originalName || filename, filePath, size || 0, type || '', userId]
      );

      uploadedFiles.push({
        filename: originalName || filename,
        url: filePath,
        size: size,
        type: type
      });
    }
    // 方式2：接收 multipart/form-data 文件（原有方式）
    else {
      const files = ctx.request.files?.files;
      if (!files) {
        ctx.fail(1001, '参数错误');
        return;
      }

      const fileArray = Array.isArray(files) ? files : [files];

      for (const file of fileArray) {
        const fileUrl = `/uploads/${file.newFilename}`;

        await pool.query(
          'INSERT INTO files (project_id, filename, original_name, path, size, type, uploaded_by) VALUES ($1, $2, $3, $4, $5, $6, $7)',
          [id, file.originalFilename, file.originalFilename, fileUrl, file.size || 0, file.mimetype || '', userId]
        );

        uploadedFiles.push({
          filename: file.originalFilename,
          url: fileUrl
        });
      }
    }

    ctx.success(uploadedFiles);
  } catch (error) {
    logger.error('上传文件失败:', error);
    ctx.fail(5001, '上传文件失败');
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
  })
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
