/**
 * RBAC 统一权限中间件 (V2.0)
 * 
 * 权限矩阵（V2.0 重新界定版 - 资料员可查看全部统计与预支）:
 * | 权限              | admin              | constructor       | documenter            |
 * |-------------------|:------------------:|:-----------------:|:---------------------:|
 * | 用户管理          | 全部               | 仅自己            | 仅自己                |
 * | 工程查看          | 全部               | 自己参与的         | 全部(只读)             |
 * | 工程创建          | ✓                  | ✓                 | ✓                    |
 * | 工程修改          | ✗                  | 自己参与的         | ✗                    |
 * | 工程删除          | ✗                  | 自己参与的         | ✗                    |
 * | 子项目管理        | ✗                  | 自己参与的         | ✗                    |
 * | 附件上传/删除     | ✗                  | 自己参与的         | ✗                    |
 * | 结算操作          | ✗                  | 工程施工人员       | ✗                    |
 * | 预支创建/删除     | ✗(只能查看)        | 自己              | ✗(只能查看)           |
 * | 预支查看          | 全部               | 自己              | 全部(按人员筛选)       |
 * | 统计查看          | ✓                  | ✓(自己的)         | ✓(全部只读)           |
 * | 字典管理          | ✓                  | ✗                 | ✗                    |
 * | 数据迁移          | ✓                  | ✗                 | ✗                    |
 * | AI助手            | ✓(全部数据)        | ✓(自己数据)       | ✓(查看全部数据)        |
 * | AI配置/知识管理   | ✓                  | ✗                 | ✗                    |
 * | 数据一致性校验    | ✓                  | ✗                 | ✗                    |
 */

const pool = require('../config/database');
const logger = require('../config/logger');

// 角色常量
const ROLES = {
  ADMIN: 'admin',
  CONSTRUCTOR: 'constructor',
  DOCUMENTER: 'documenter'
};

/**
 * 根据角色确定用户可查看的工程范围
 * admin: 全部工程
 * constructor: 只查自己参与的工程
 * documenter: 全部工程（只读）
 */
const getProjectScope = (user) => {
  return {
    role: user.role,
    userId: user.id,
    isAllProjects: user.role === ROLES.ADMIN || user.role === ROLES.DOCUMENTER,
    isOwnOnly: user.role === ROLES.CONSTRUCTOR
  };
};

// ========== 权限辅助判断函数 ==========

/** 判断用户是否是管理员 */
const isAdmin = (user) => user?.role === ROLES.ADMIN;

/** 判断用户是否是施工员 */
const isConstructor = (user) => user?.role === ROLES.CONSTRUCTOR;

/** 判断用户是否是资料员 */
const isDocumenter = (user) => user?.role === ROLES.DOCUMENTER;

/** 
 * V2.0: 判断用户是否可以修改工程
 * 只有施工员可以修改自己参与的工程，admin不能修改
 */
const canModifyProject = (user) => isConstructor(user);

/** 
 * V2.0: 判断用户是否可以结算
 * 只有施工员可以结算
 */
const canSettle = (user) => isConstructor(user);

/** 
 * V2.0: 判断用户是否可以管理预支
 * 只有施工员可以创建自己的预支，admin只能查看
 */
const canAdvance = (user) => isConstructor(user);

/** 资料员可以创建工程（但不能修改/删除） */
const canCreateProject = (user) => true; // 所有角色都可以创建

// ========== 路由级中间件工厂函数 ==========

/**
 * 要求特定角色列表
 * @param {string[]} allowedRoles - 允许的角色数组
 */
const requireRole = (allowedRoles) => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    if (!allowedRoles.includes(user.role)) {
      ctx.fail(4002, '权限不足');
      return;
    }
    await next();
  };
};

/** 仅管理员 */
const requireAdmin = () => requireRole([ROLES.ADMIN]);

/** 管理员或施工员 */
const requireAdminOrConstructor = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR]);

/** 所有角色（仅需登录） */
const requireAuth = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR, ROLES.DOCUMENTER]);

// ========== 工程操作权限中间件 ==========

/**
 * 工程查看权限检查（V2.0: admin和documenter可查全部，constructor只能查自己参与的）
 * 路由层中间件：constructor 需进一步校验是否参与该工程
 * documenter 权限已重新界定：可查看所有工程（包括子项目、附件、施工人员）
 */
const requireProjectView = () => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    // admin 和 documenter 可查看全部工程
    if (isAdmin(user) || isDocumenter(user)) {
      await next();
      return;
    }
    // constructor 只能查看自己参与的工程
    if (isConstructor(user)) {
      const projectId = parseInt(ctx.params.id, 10);
      if (!projectId) {
        ctx.fail(1001, '工程ID无效');
        return;
      }
      const isParticipant = await isProjectParticipant(user.id, projectId);
      if (!isParticipant) {
        ctx.fail(4002, '您未参与此工程，无权查看');
        return;
      }
    }
    await next();
  };
};

/**
 * 工程修改权限检查（V2.0: admin不能修改工程）
 * 路由层中间件：阻止admin和documenter，constructor在controller层进一步检查是否参与
 */
const requireProjectModify = () => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    if (isAdmin(user)) {
      ctx.fail(4002, '您的权限为管理员，只能查看工程和系统配置，无法修改工程');
      return;
    }
    if (isDocumenter(user)) {
      ctx.fail(4002, '您的权限为资料员，只能查看和新建工程，无法修改工程');
      return;
    }
    await next();
  };
};

/**
 * 工程删除权限检查（V2.0: admin不能删除工程）
 */
const requireProjectDelete = () => requireProjectModify(); // 与修改权限一致

/**
 * 子项目管理权限检查（V2.0: admin不能管理子项目）
 */
const requireSubprojectManage = () => requireProjectModify(); // 与修改权限一致

/**
 * 工程附件操作权限检查（V2.0: 仅constructor可上传/删除附件）
 * admin不能修改工程（含附件），documenter只能查看附件不能上传/删除
 * constructor 在 controller 层进一步检查是否参与该工程
 */
const requireFileModify = () => requireProjectModify(); // 与工程修改权限一致

// ========== 结算权限中间件 ==========

/**
 * 结算操作权限检查（V2.0: 只有施工员可以结算）
 */
const requireSettlementAccess = () => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    if (!canSettle(user)) {
      ctx.fail(4002, '您的权限不足，只有施工员可以进行结算操作');
      return;
    }
    await next();
  };
};

// ========== 预支权限中间件 ==========

/**
 * 预支操作权限检查（V2.0: 只有施工员可以创建预支，admin只能查看）
 */
const requireAdvanceCreate = () => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    if (isDocumenter(user)) {
      ctx.fail(4002, '您的权限为资料员，无法进行预支操作');
      return;
    }
    if (isAdmin(user)) {
      ctx.fail(4002, '您的权限为管理员，只能查看预支记录，无法创建预支');
      return;
    }
    await next();
  };
};

/**
 * 预支删除权限检查（V2.0: admin也不能删除预支）
 */
const requireAdvanceDelete = () => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    if (!isConstructor(user)) {
      ctx.fail(4002, '只有施工员可以删除自己的预支记录');
      return;
    }
    await next();
  };
};

// ========== 统计权限中间件 ==========

/**
 * 统计访问权限检查（V2.0 重新界定：documenter 可查看统计）
 * - admin: 可查看全部统计
 * - constructor: 可查看自己参与工程的统计（service 层过滤）
 * - documenter: 可查看所有施工人员的工程和结算统计（只读）
 */
const requireStatisticsAccess = () => {
  return async (ctx, next) => {
    const user = ctx.state.user;
    if (!user) {
      ctx.fail(4001, '用户未登录');
      return;
    }
    // 三种角色均可访问统计，数据范围由 service 层按角色过滤
    await next();
  };
};

// ========== 数据级权限辅助 ==========

/**
 * 检查用户是否参与了指定工程
 * "自己参与" = 工程创建者是自己(projects.created_by) OR 自己是工程施工人员(project_workers.user_id)
 * @param {number} userId - 用户ID
 * @param {number} projectId - 工程ID
 * @returns {Promise<boolean>}
 */
const isProjectParticipant = async (userId, projectId) => {
  try {
    const result = await pool.query(
      `SELECT 1 FROM projects p
       LEFT JOIN project_workers pw ON p.id = pw.project_id
       WHERE p.id = $1 AND (p.created_by = $2 OR pw.user_id = $2)
       LIMIT 1`,
      [projectId, userId]
    );
    return result.rows.length > 0;
  } catch (error) {
    logger.error('检查工程参与者失败:', error);
    return false;
  }
};

/**
 * 获取角色中文名称
 */
const getRoleName = (role) => {
  const roleNames = {
    [ROLES.ADMIN]: '管理员',
    [ROLES.CONSTRUCTOR]: '施工员',
    [ROLES.DOCUMENTER]: '资料员'
  };
  return roleNames[role] || '未知角色';
};

module.exports = {
  ROLES,
  getProjectScope,
  isAdmin,
  isConstructor,
  isDocumenter,
  canModifyProject,
  canSettle,
  canAdvance,
  canCreateProject,
  requireRole,
  requireAdmin,
  requireAdminOrConstructor,
  requireAuth,
  requireProjectView,
  requireProjectModify,
  requireProjectDelete,
  requireSubprojectManage,
  requireFileModify,
  requireSettlementAccess,
  requireAdvanceCreate,
  requireAdvanceDelete,
  requireStatisticsAccess,
  isProjectParticipant,
  getRoleName
};