/**
 * 权限中间件
 * 统一处理角色权限验证
 */

// 角色定义
const ROLES = {
  ADMIN: 'admin',
  CONSTRUCTOR: 'constructor',
  DOCUMENTER: 'documenter'
}

// 权限级别（数字越大权限越高）
const ROLE_LEVELS = {
  [ROLES.DOCUMENTER]: 1,
  [ROLES.CONSTRUCTOR]: 2,
  [ROLES.ADMIN]: 3
}

/**
 * 检查用户是否拥有指定角色或更高权限
 * @param {string[]} allowedRoles - 允许的角色数组
 * @returns {Function} Koa中间件
 */
const requireRole = (allowedRoles) => {
  return async (ctx, next) => {
    const userRole = ctx.state.user?.role

    if (!userRole) {
      ctx.fail(4001, '用户未登录')
      return
    }

    if (!allowedRoles.includes(userRole)) {
      ctx.fail(4002, '权限不足')
      return
    }

    await next()
  }
}

/**
 * 检查用户是否是管理员
 */
const requireAdmin = () => requireRole([ROLES.ADMIN])

/**
 * 检查用户是否可以操作工程（管理员或施工员）
 */
const requireProjectOperator = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR])

/**
 * 检查用户是否可以查看统计（管理员或施工员）
 */
const requireStatisticsAccess = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR])

/**
 * 检查用户是否可以结算（管理员或施工员）
 */
const requireSettlementAccess = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR])

/**
 * 检查用户是否可以预支（管理员或施工员）
 */
const requireAdvanceAccess = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR])

/**
 * 检查用户是否可以管理用户（仅管理员）
 */
const requireUserManagement = () => requireRole([ROLES.ADMIN])

/**
 * 检查用户是否可以数据迁移（仅管理员）
 */
const requireMigration = () => requireRole([ROLES.ADMIN])

/**
 * 检查用户是否可以查看工程（所有角色）
 */
const requireProjectView = () => requireRole([ROLES.ADMIN, ROLES.CONSTRUCTOR, ROLES.DOCUMENTER])

/**
 * 判断用户是否是管理员
 * @param {object} user - 用户对象
 * @returns {boolean}
 */
const isAdmin = (user) => {
  return user?.role === ROLES.ADMIN
}

/**
 * 判断用户是否是施工员
 * @param {object} user - 用户对象
 * @returns {boolean}
 */
const isConstructor = (user) => {
  return user?.role === ROLES.CONSTRUCTOR
}

/**
 * 判断用户是否是资料员
 * @param {object} user - 用户对象
 * @returns {boolean}
 */
const isDocumenter = (user) => {
  return user?.role === ROLES.DOCUMENTER
}

/**
 * 判断用户是否可以修改工程（管理员或施工员，资料员不能）
 * @param {object} user - 用户对象
 * @returns {boolean}
 */
const canModifyProject = (user) => {
  return isAdmin(user) || isConstructor(user)
}

/**
 * 判断用户是否可以结算（仅施工员，管理员需要是工程施工人员才能结算）
 * @param {object} user - 用户对象
 * @returns {boolean}
 */
const canSettle = (user) => {
  // 施工员可以结算
  if (isConstructor(user)) {
    return true
  }
  // 管理员默认不能结算，需要在具体业务中检查是否是工程施工人员
  return false
}

/**
 * 判断用户是否可以预支（管理员或施工员，资料员不能）
 * @param {object} user - 用户对象
 * @returns {boolean}
 */
const canAdvance = (user) => {
  return isAdmin(user) || isConstructor(user)
}

/**
 * 获取角色中文名称
 * @param {string} role - 角色代码
 * @returns {string}
 */
const getRoleName = (role) => {
  const roleNames = {
    [ROLES.ADMIN]: '管理员',
    [ROLES.CONSTRUCTOR]: '施工员',
    [ROLES.DOCUMENTER]: '资料员'
  }
  return roleNames[role] || '未知角色'
}

module.exports = {
  ROLES,
  ROLE_LEVELS,
  requireRole,
  requireAdmin,
  requireProjectOperator,
  requireStatisticsAccess,
  requireSettlementAccess,
  requireAdvanceAccess,
  requireUserManagement,
  requireMigration,
  requireProjectView,
  isAdmin,
  isConstructor,
  isDocumenter,
  canModifyProject,
  canSettle,
  canAdvance,
  getRoleName
}
