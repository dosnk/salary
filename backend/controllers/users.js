const pool = require('../config/database');
const Joi = require('joi');
const validation = require('../middleware/validation');
const dataMasking = require('../utils/dataMasking');
const bcrypt = require('bcryptjs');
const { isAdmin } = require('../middleware/rbac');
const logger = require('../config/logger');

/**
 * 获取用户列表
 */
const getUsers = async (ctx) => {
  const { role, page = 1, size = 10 } = ctx.query;
  const offset = (page - 1) * size;
  const user = ctx.state.user;

  try {
    let query = 'SELECT id, username, nickname, phone, role, TO_CHAR(created_at, \'YYYY-MM-DD HH24:MI\') as created_at, TO_CHAR(updated_at, \'YYYY-MM-DD HH24:MI\') as updated_at FROM users';
    let params = [];

    if (role) {
      query += ' WHERE role = $1';
      params.push(role);
    }

    query += ' ORDER BY created_at DESC LIMIT $' + (params.length + 1) + ' OFFSET $' + (params.length + 2);
    params.push(size, offset);

    const result = await pool.query(query, params);

    const countQuery = role ? 'SELECT COUNT(*) FROM users WHERE role = $1' : 'SELECT COUNT(*) FROM users';
    const countResult = await pool.query(countQuery, role ? [role] : []);

    let users = result.rows;

    if (!isAdmin(user)) {
      users = dataMasking.数组脱敏(users, ['phone']);
    }

    ctx.paginate(users, parseInt(countResult.rows[0].count), parseInt(page), parseInt(size));
  } catch (error) {
    logger.error('获取用户列表失败:', error);
    ctx.fail(5001, '获取用户列表失败');
  }
};

/**
 * 获取施工员列表（所有登录用户可访问）
 */
const getConstructors = async (ctx) => {
  try {
    const result = await pool.query(
      'SELECT id, username, nickname, phone FROM users WHERE role = $1 ORDER BY id',
      ['constructor']
    );

    let users = result.rows;

    if (!isAdmin(ctx.state.user)) {
      users = dataMasking.数组脱敏(users, ['phone']);
    }

    ctx.success(users);
  } catch (error) {
    logger.error('获取施工员列表失败:', error);
    ctx.fail(5001, '获取施工员列表失败');
  }
};

/**
 * 获取用户详情
 */
const getUser = async (ctx) => {
  const { id } = ctx.params;
  const currentUserId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    const result = await pool.query(
      'SELECT id, username, nickname, phone, role, TO_CHAR(created_at, \'YYYY-MM-DD HH24:MI\') as created_at, TO_CHAR(updated_at, \'YYYY-MM-DD HH24:MI\') as updated_at FROM users WHERE id = $1',
      [id]
    );

    if (result.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    // 权限检查：只有管理员或用户本人可以查看用户详情
    if (!isAdmin(user) && parseInt(id) !== currentUserId) {
      ctx.fail(4002, '您只能查看自己的用户信息');
      return;
    }

    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('获取用户详情失败:', error);
    ctx.fail(5001, '获取用户详情失败');
  }
};

/**
 * 更新用户信息
 */
const updateUser = async (ctx) => {
  const { id } = ctx.params;
  const { nickname, phone, role } = ctx.request.body;
  const currentUserId = ctx.state.user.id;
  const user = ctx.state.user;

  try {
    // 检查用户是否存在
    const existingUser = await pool.query('SELECT id FROM users WHERE id = $1', [id]);
    if (existingUser.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    // 权限检查：只有管理员可以修改用户角色，其他角色只能修改自己的信息
    if (role && !isAdmin(user)) {
      ctx.fail(4002, '只有管理员可以修改用户角色');
      return;
    }

    // 非管理员只能修改自己的信息
    if (!isAdmin(user) && parseInt(id) !== currentUserId) {
      ctx.fail(4002, '您只能修改自己的用户信息');
      return;
    }

    // 更新用户信息
    const result = await pool.query(
      'UPDATE users SET nickname = $1, phone = $2, role = COALESCE($3, role), updated_at = CURRENT_TIMESTAMP WHERE id = $4 RETURNING *',
      [nickname, phone, isAdmin(user) ? role : null, id]
    );

    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('更新用户失败:', error);
    ctx.fail(5001, '更新用户失败');
  }
};

/**
 * 删除用户
 *
 * 业务规则：
 * 1. 仅管理员可操作
 * 2. 不能删除自己
 * 3. 有任何业务关联数据（工程分工、工资分配、工资结算、预支、
 *    工程结算状态、工程/子项目/结算/流水/子项目转派中的操作人字段等）时，
 *    直接拒绝删除，返回 2011，避免历史数据被级联清除或被置空丢失责任人。
 */
const deleteUser = async (ctx) => {
  const { id } = ctx.params;
  const user = ctx.state.user;
  const currentUserId = ctx.state.user.id;

  try {
    // 权限检查：只有管理员可以删除用户
    if (!isAdmin(user)) {
      ctx.fail(4002, '无操作权限');
      return;
    }

    // 检查用户是否存在
    const existingUser = await pool.query('SELECT id FROM users WHERE id = $1', [id]);
    if (existingUser.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    // 不能删除自己
    if (parseInt(id) === currentUserId) {
      ctx.fail(2010);
      return;
    }

    // 关联数据前置检查：所有引用当前用户的业务表都要检查
    // 说明：
    //   - CASCADE 类关联（一删就消失，风险最大）：project_workers、wage_settlements、
    //     project_user_status、wage_distributions、wage_advances
    //   - SET NULL 类关联（会丢失责任人）：projects.settled_by/created_by、
    //     subprojects.created_by、project_history.performed_by、
    //     files.uploaded_by、wage_settlements.settled_by、
    //     wage_advances.created_by、subproject_transfers.from_user_id/to_user_id/transferred_by
    const relations = [
      { table: 'project_workers', column: 'user_id', label: '参与的工程分工' },
      { table: 'wage_settlements', column: 'user_id', label: '工资结算主记录' },
      { table: 'project_user_status', column: 'user_id', label: '工程结算状态' },
      { table: 'wage_distributions', column: 'user_id', label: '工资分配明细' },
      { table: 'wage_advances', column: 'user_id', label: '预支工资流水' },
      { table: 'projects', column: 'created_by', label: '创建的工程' },
      { table: 'projects', column: 'settled_by', label: '结算过的工程' },
      { table: 'subprojects', column: 'created_by', label: '创建的子项目' },
      { table: 'project_history', column: 'performed_by', label: '操作过的工程历史' },
      { table: 'files', column: 'uploaded_by', label: '上传的附件' },
      { table: 'wage_settlements', column: 'settled_by', label: '执行过的工资结算' },
      { table: 'wage_advances', column: 'created_by', label: '录入的预支流水' },
      { table: 'subproject_transfers', column: 'from_user_id', label: '被转派出去的子项目' },
      { table: 'subproject_transfers', column: 'to_user_id', label: '被转派接收的子项目' },
      { table: 'subproject_transfers', column: 'transferred_by', label: '执行的子项目转派' }
    ];

    const blocked = [];
    for (const r of relations) {
      const countResult = await pool.query(
        `SELECT COUNT(*)::int AS cnt FROM ${r.table} WHERE ${r.column} = $1`,
        [id]
      );
      const cnt = countResult.rows[0].cnt;
      if (cnt > 0) {
        blocked.push(`${r.label}(${cnt})`);
      }
    }

    if (blocked.length > 0) {
      // 拼装详细提示，指出具体的关联业务与数量，方便管理员定位
      const detail = blocked.join('、');
      ctx.fail(2011, `该用户存在关联业务数据，无法删除：${detail}。请先转派或清理相关数据后再删除。`);
      return;
    }

    // 无关联数据，安全删除
    await pool.query('DELETE FROM users WHERE id = $1', [id]);

    ctx.success({ message: '删除成功' });
  } catch (error) {
    logger.error('删除用户失败:', error);
    ctx.fail(5001, '删除用户失败');
  }
};

/**
 * 获取当前用户信息
 */
const getProfile = async (ctx) => {
  const userId = ctx.state.user.id;

  try {
    const result = await pool.query(
      'SELECT id, username, nickname, phone, role, TO_CHAR(created_at, \'YYYY-MM-DD HH24:MI\') as created_at, TO_CHAR(updated_at, \'YYYY-MM-DD HH24:MI\') as updated_at FROM users WHERE id = $1',
      [userId]
    );

    if (result.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('获取用户信息失败:', error);
    ctx.fail(5001, '获取用户信息失败');
  }
};

/**
 * 更新当前用户信息
 */
const updateProfile = async (ctx) => {
  const userId = ctx.state.user.id;
  const { nickname, phone } = ctx.request.body;

  try {
    // 检查用户是否存在
    const existingUser = await pool.query('SELECT id FROM users WHERE id = $1', [userId]);
    if (existingUser.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    // 如果更新手机号，检查手机号是否已被其他用户使用
    if (phone) {
      const phoneCheck = await pool.query(
        'SELECT id FROM users WHERE phone = $1 AND id != $2',
        [phone, userId]
      );
      if (phoneCheck.rows.length > 0) {
        ctx.fail(2003);
        return;
      }
    }

    // 更新用户信息
    const result = await pool.query(
      'UPDATE users SET nickname = COALESCE($1, nickname), phone = COALESCE($2, phone), updated_at = CURRENT_TIMESTAMP WHERE id = $3 RETURNING id, username, nickname, phone, role, created_at, updated_at',
      [nickname, phone, userId]
    );

    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('更新用户信息失败:', error);
    ctx.fail(5001, '更新用户信息失败');
  }
};

/**
 * 修改密码
 */
const changePassword = async (ctx) => {
  const userId = ctx.state.user.id;
  const { old_password, new_password } = ctx.request.body;

  try {
    // 获取用户信息
    const userResult = await pool.query('SELECT id, password FROM users WHERE id = $1', [userId]);
    if (userResult.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    const user = userResult.rows[0];

    // 验证旧密码
    const isMatch = await bcrypt.compare(old_password, user.password);
    if (!isMatch) {
      ctx.fail(2006);
      return;
    }

    // 加密新密码
    // bcrypt盐值轮数：12轮（约250ms/次），符合2026年安全标准
    const saltRounds = 12;
    const hashedPassword = await bcrypt.hash(new_password, saltRounds);

    // 更新密码（同步设置 password_changed_at，标记用户已主动修改过密码）
    // 这样后续 init-db.js --reset-passwords 默认会跳过该用户，避免覆盖用户改过的密码
    await pool.query(
      'UPDATE users SET password = $1, password_changed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      [hashedPassword, userId]
    );

    ctx.success({ message: '密码修改成功' });
  } catch (error) {
    logger.error('修改密码失败:', error);
    ctx.fail(5001, '修改密码失败');
  }
};

/**
 * 创建用户（管理员）
 */
const createUser = async (ctx) => {
  const { username, password, nickname, phone, role } = ctx.request.body;

  try {
    const existingUser = await pool.query(
      'SELECT id FROM users WHERE username = $1',
      [username]
    );
    
    if (existingUser.rows.length > 0) {
      ctx.fail(2001);
      return;
    }

    if (phone) {
      const phoneCheck = await pool.query(
        'SELECT id FROM users WHERE phone = $1',
        [phone]
      );
      if (phoneCheck.rows.length > 0) {
        ctx.fail(2003);
        return;
      }
    }

    // bcrypt盐值轮数：12轮（约250ms/次），符合2026年安全标准
    const saltRounds = 12;
    const hashedPassword = await bcrypt.hash(password, saltRounds);

    const result = await pool.query(
      'INSERT INTO users (username, password, nickname, phone, role, password_changed_at) VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP) RETURNING id, username, nickname, phone, role, TO_CHAR(created_at, \'YYYY-MM-DD HH24:MI\') as created_at',
      [username, hashedPassword, nickname || null, phone || null, role || 'constructor']
    );

    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('创建用户失败:', error);
    ctx.fail(5001, '创建用户失败');
  }
};

/**
 * 重置用户密码（管理员）
 */
const resetPassword = async (ctx) => {
  const { id } = ctx.params;
  const { new_password } = ctx.request.body;

  try {
    const existingUser = await pool.query('SELECT id FROM users WHERE id = $1', [id]);
    if (existingUser.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    // bcrypt盐值轮数：12轮（约250ms/次），符合2026年安全标准
    const saltRounds = 12;
    const hashedPassword = await bcrypt.hash(new_password, saltRounds);

    // 重置密码（同步设置 password_changed_at，标记用户已主动修改过密码）
    // 这样后续 init-db.js --reset-passwords 默认会跳过该用户，避免覆盖用户改过的密码
    await pool.query(
      'UPDATE users SET password = $1, password_changed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      [hashedPassword, id]
    );

    ctx.success({ message: '密码重置成功' });
  } catch (error) {
    logger.error('重置密码失败:', error);
    ctx.fail(5001, '重置密码失败');
  }
};

module.exports = {
  getUsers,
  getConstructors,
  getUser,
  updateUser,
  deleteUser,
  getProfile,
  updateProfile,
  changePassword,
  createUser,
  resetPassword
};

// 校验规则
const getUsersSchema = Joi.object({
  page: validation.rules.page,
  size: validation.rules.size,
  role: Joi.string().valid('admin', 'documenter', 'constructor'),
  keyword: Joi.string().max(100)
});

const getUserSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

const updateUserSchema = Joi.object({
  id: Joi.number().integer().positive().required(),
  nickname: validation.rules.nicknameOptional,
  phone: validation.rules.phoneOptional,
  role: Joi.string().valid('admin', 'documenter', 'constructor')
}).min(1).messages({
  'object.min': '至少需要提供一个字段进行更新'
});

const deleteUserSchema = Joi.object({
  id: Joi.number().integer().positive().required()
});

const updateProfileSchema = Joi.object({
  nickname: validation.rules.nicknameOptional,
  phone: validation.rules.phoneOptional
}).min(1).messages({
  'object.min': '至少需要提供一个字段进行更新'
});

const changePasswordSchema = Joi.object({
  old_password: Joi.string().min(6).max(20).required().messages({
    'string.min': '密码长度至少6位',
    'string.max': '密码长度最多20位',
    'any.required': '旧密码不能为空'
  }),
  new_password: Joi.string().min(6).max(20).required().messages({
    'string.min': '密码长度至少6位',
    'string.max': '密码长度最多20位',
    'any.required': '新密码不能为空'
  })
});

const createUserSchema = Joi.object({
  username: Joi.string().min(2).max(50).required().messages({
    'string.min': '用户名长度至少2位',
    'string.max': '用户名长度最多50位',
    'any.required': '用户名不能为空'
  }),
  password: Joi.string().min(6).max(20).required().messages({
    'string.min': '密码长度至少6位',
    'string.max': '密码长度最多20位',
    'any.required': '密码不能为空'
  }),
  nickname: Joi.string().min(2).max(50).allow('').optional(),
  phone: Joi.string().pattern(/^1[3-9]\d{9}$/).allow('').optional().messages({
    'string.pattern.base': '手机号格式错误'
  }),
  role: Joi.string().valid('admin', 'documenter', 'constructor').default('constructor')
});

const resetPasswordSchema = Joi.object({
  id: Joi.number().integer().positive().required(),
  new_password: Joi.string().min(6).max(20).required().messages({
    'string.min': '密码长度至少6位',
    'string.max': '密码长度最多20位',
    'any.required': '新密码不能为空'
  })
});

module.exports.getUsersSchema = getUsersSchema;
module.exports.getUserSchema = getUserSchema;
module.exports.updateUserSchema = updateUserSchema;
module.exports.deleteUserSchema = deleteUserSchema;
module.exports.updateProfileSchema = updateProfileSchema;
module.exports.changePasswordSchema = changePasswordSchema;
module.exports.createUserSchema = createUserSchema;
module.exports.resetPasswordSchema = resetPasswordSchema;