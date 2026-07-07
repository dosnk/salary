/**
 * 用户业务逻辑层 (Service)
 *
 * 从 controllers/users.js 和 controllers/auth.js 中抽离用户相关的业务逻辑，负责：
 * - 业务规则校验（密码强度、用户名唯一、登录锁定等）
 * - 调用 pg 连接池进行数据访问
 * - 调用 cacheService 进行缓存管理
 * - 业务异常通过 BusinessError 抛出，由 controller 捕获
 */

const pool = require('../config/database');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
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

// ========== 常量定义 ==========

/** JWT Token 过期时间 */
const ACCESS_TOKEN_EXPIRY = '2h';   // 短期Token 2小时
const REFRESH_TOKEN_EXPIRY = '7d';  // 长期Token 7天

/** 登录失败锁定策略 */
const MAX_LOGIN_ATTEMPTS = 5;            // 最大失败次数
const LOGIN_LOCK_DURATION = 30 * 60;     // 锁定时长30分钟（秒）
const LOGIN_ATTEMPT_TTL = 30 * 60;       // 失败计数过期时间30分钟（秒）

/** 密码加密轮次 */
// bcrypt盐值轮数：12轮（约250ms/次），符合2026年安全标准
// 10轮已偏低，12轮提供约16倍更强的安全保护，且对登录性能影响可接受
// 旧密码仍可正常验证（bcrypt自包含salt rounds），仅新密码使用12轮
const SALT_ROUNDS = 12;

/** 密码格式正则：至少6位，必须包含字母和数字 */
const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d).{6,}$/;

// ========== 内部辅助函数 ==========

/**
 * 生成 JWT Token 对（access_token + refresh_token）
 * @param {object} user - 用户对象，需包含 id, username, role
 * @returns {{ accessToken: string, refreshToken: string }}
 */
const generateTokens = (user) => {
  const payload = {
    id: user.id,
    username: user.username,
    role: user.role,
    iat: Math.floor(Date.now() / 1000),
  };

  const accessToken = jwt.sign(
    { ...payload, type: 'access' },
    process.env.JWT_SECRET,
    { expiresIn: ACCESS_TOKEN_EXPIRY }
  );

  const refreshToken = jwt.sign(
    { ...payload, type: 'refresh' },
    process.env.JWT_SECRET,
    { expiresIn: REFRESH_TOKEN_EXPIRY }
  );

  return { accessToken, refreshToken };
};

/**
 * 获取登录锁定的缓存键
 * @param {string} username - 用户名
 * @returns {string} 锁定键
 */
const getLockKey = (username) => cache.cacheKey('login_lock', username);

/**
 * 获取登录失败次数的缓存键
 * @param {string} username - 用户名
 * @returns {string} 失败次数键
 */
const getAttemptKey = (username) => cache.cacheKey('login_attempts', username);

/**
 * 获取 refreshToken 的缓存键
 * @param {number} userId - 用户ID
 * @returns {string} refreshToken键
 */
const getRefreshTokenKey = (userId) => cache.cacheKey('refresh_token', userId);

/**
 * 获取用户缓存键
 * @param {number} userId - 用户ID
 * @returns {string} 用户缓存键
 */
const getUserCacheKey = (userId) => cache.cacheKey('user', userId);

/**
 * 清除用户相关缓存
 * @param {number} userId - 用户ID
 */
const invalidateUserCache = async (userId) => {
  try {
    await cache.del(getUserCacheKey(userId));
    // 同时清除用户列表缓存
    await cache.delByPrefix('users:');
  } catch (error) {
    logger.warn('清除用户缓存失败', { userId, error: error.message });
  }
};

/**
 * 密码加密
 * @param {string} password - 明文密码
 * @returns {Promise<string>} 加密后的密码
 */
const hashPassword = async (password) => {
  return bcrypt.hash(password, SALT_ROUNDS);
};

/**
 * 密码比对
 * @param {string} password - 明文密码
 * @param {string} hashedPassword - 加密后的密码
 * @returns {Promise<boolean>} 是否匹配
 */
const comparePassword = async (password, hashedPassword) => {
  return bcrypt.compare(password, hashedPassword);
};

/**
 * 校验密码格式
 * 业务规则：密码最小6位，必须包含字母和数字
 * @param {string} password - 明文密码
 * @throws {BusinessError} 密码格式不符合要求
 */
const validatePasswordFormat = (password) => {
  if (!password || password.length < 6) {
    throw new BusinessError(1001, '密码最少6位');
  }
  if (!PASSWORD_REGEX.test(password)) {
    throw new BusinessError(1001, '密码必须包含字母和数字');
  }
};

// ========== 导出的业务方法 ==========

module.exports = {
  /**
   * 用户注册
   *
   * 业务规则：
   * - 密码最小6位，字母数字组合
   * - 用户名唯一
   * - 默认角色 constructor
   *
   * @param {object} params - 注册参数
   * @param {string} params.username - 用户名
   * @param {string} params.password - 密码
   * @param {string} [params.nickname] - 昵称
   * @returns {Promise<{accessToken: string, refreshToken: string, user: object}>}
   */
  async register({ username, password, nickname }) {
    // 1. 校验密码格式
    validatePasswordFormat(password);

    // 2. 检查用户名是否已存在
    const existingUser = await pool.query(
      'SELECT id FROM users WHERE username = $1',
      [username]
    );
    if (existingUser.rows.length > 0) {
      throw new BusinessError(2001, '用户名已存在');
    }

    // 3. 加密密码
    const hashedPassword = await hashPassword(password);

    // 4. 创建用户（默认角色 constructor）
    const result = await pool.query(
      `INSERT INTO users (username, password, nickname, role)
       VALUES ($1, $2, $3, $4)
       RETURNING id, username, nickname, role`,
      [username, hashedPassword, nickname || username, 'constructor']
    );

    const user = result.rows[0];

    // 5. 生成 Token 对
    const tokens = generateTokens(user);

    // 6. 存储 refreshToken 到缓存（7天有效期）
    await cache.set(
      getRefreshTokenKey(user.id),
      tokens.refreshToken,
      7 * 24 * 60 * 60
    );

    logger.info('用户注册成功', { userId: user.id, username: user.username });

    return {
      ...tokens,
      user: {
        id: user.id,
        username: user.username,
        nickname: user.nickname,
        role: user.role,
      },
    };
  },

  /**
   * 用户登录
   *
   * 业务规则：
   * - 5次失败锁定30分钟
   * - 生成 access_token(2h) + refresh_token(7d)
   *
   * @param {object} params - 登录参数
   * @param {string} params.username - 用户名
   * @param {string} params.password - 密码
   * @returns {Promise<{accessToken: string, refreshToken: string, user: object}>}
   */
  async login({ username, password }) {
    const lockKey = getLockKey(username);
    const attemptKey = getAttemptKey(username);

    // 1. 检查是否被锁定
    const lockValue = await cache.get(lockKey);
    if (lockValue) {
      // 获取剩余锁定时间
      const { getRedisClient, isRedisAvailable } = require('../config/redis');
      let remainMinutes = 30; // 兜底值，防止Redis异常时无法获取TTL
      if (isRedisAvailable()) {
        try {
          const redis = getRedisClient();
          const ttl = await redis.ttl(lockKey);
          remainMinutes = Math.ceil(ttl / 60);
        } catch (error) {
          logger.warn('获取登录锁定剩余时间失败', { username, error: error.message });
        }
      }
      throw new BusinessError(2002, `登录失败次数过多，请${remainMinutes}分钟后重试`);
    }

    // 2. 查询用户
    const result = await pool.query(
      'SELECT id, username, password, nickname, role FROM users WHERE username = $1',
      [username]
    );
    if (result.rows.length === 0) {
      throw new BusinessError(2002, '用户名或密码错误');
    }

    const user = result.rows[0];

    // 3. 验证密码
    const isMatch = await comparePassword(password, user.password);
    if (!isMatch) {
      // 记录失败次数（Redis不可用时跳过，仅返回密码错误提示）
      const { getRedisClient, isRedisAvailable } = require('../config/redis');
      if (isRedisAvailable()) {
        try {
          const redis = getRedisClient();
          const attempts = await redis.incr(attemptKey);
          await redis.expire(attemptKey, LOGIN_ATTEMPT_TTL);

          // 达到最大失败次数，锁定账户
          if (attempts >= MAX_LOGIN_ATTEMPTS) {
            await cache.set(lockKey, '1', LOGIN_LOCK_DURATION);
            logger.warn(`用户 ${username} 登录失败${attempts}次，已锁定30分钟`);
          }
        } catch (error) {
          // Redis异常不阻塞登录失败流程，仅记录日志
          logger.warn('记录登录失败次数失败', { username, error: error.message });
        }
      }

      throw new BusinessError(2002, '用户名或密码错误');
    }

    // 4. 登录成功，清除失败记录
    await cache.del(attemptKey);
    await cache.del(lockKey);

    // 5. 生成 Token 对
    const tokens = generateTokens(user);

    // 6. 存储 refreshToken 到缓存（7天有效期）
    await cache.set(
      getRefreshTokenKey(user.id),
      tokens.refreshToken,
      7 * 24 * 60 * 60
    );

    logger.info('用户登录成功', { userId: user.id, username: user.username });

    return {
      ...tokens,
      user: {
        id: user.id,
        username: user.username,
        nickname: user.nickname,
        role: user.role,
      },
    };
  },

  /**
   * 刷新Token
   *
   * @param {string} refreshToken - 刷新令牌
   * @returns {Promise<{accessToken: string, refreshToken: string}>}
   */
  async refreshToken(refreshToken) {
    if (!refreshToken) {
      throw new BusinessError(1001, 'refreshToken不能为空');
    }

    // 1. 验证 refreshToken 有效性
    let decoded;
    try {
      decoded = jwt.verify(refreshToken, process.env.JWT_SECRET);
    } catch (error) {
      throw new BusinessError(4002, 'refreshToken无效或已过期');
    }

    // 2. 检查 token 类型
    if (decoded.type !== 'refresh') {
      throw new BusinessError(4002, '无效的refreshToken');
    }

    // 3. 验证缓存中的 refreshToken 是否一致
    const storedToken = await cache.get(getRefreshTokenKey(decoded.id));
    if (storedToken !== refreshToken) {
      throw new BusinessError(4002, 'refreshToken已失效');
    }

    // 4. 查询用户信息
    const result = await pool.query(
      'SELECT id, username, nickname, role FROM users WHERE id = $1',
      [decoded.id]
    );
    if (result.rows.length === 0) {
      throw new BusinessError(2004, '用户不存在');
    }

    // 5. 生成新 Token 对
    const tokens = generateTokens(result.rows[0]);

    // 6. 更新缓存中的 refreshToken
    await cache.set(
      getRefreshTokenKey(decoded.id),
      tokens.refreshToken,
      7 * 24 * 60 * 60
    );

    logger.info('刷新Token成功', { userId: decoded.id });

    return tokens;
  },

  /**
   * 获取用户列表
   *
   * 业务规则：
   * - 施工员只能看自己
   * - 资料员只能看自己
   * - 支持关键词搜索和角色筛选
   * - 支持缓存
   *
   * @param {object} params - 查询参数
   * @param {number} params.userId - 当前用户ID
   * @param {string} params.role - 当前用户角色
   * @param {string} [params.keyword] - 搜索关键词
   * @param {number} [params.page=1] - 页码
   * @param {number} [params.size=10] - 每页条数
   * @returns {Promise<{list: Array, total: number, page: number, size: number}>}
   */
  async getUsers({ userId, role, keyword, page = 1, size = 10 }) {
    const pageNum = parseInt(page, 10) || 1;
    const sizeNum = parseInt(size, 10) || 10;
    const offset = (pageNum - 1) * sizeNum;

    // 施工员和资料员只能看自己
    if (role === 'constructor' || role === 'documenter') {
      const result = await pool.query(
        `SELECT id, username, nickname, phone, role,
                TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') as created_at,
                TO_CHAR(updated_at, 'YYYY-MM-DD HH24:MI') as updated_at
         FROM users WHERE id = $1`,
        [userId]
      );
      return {
        list: result.rows,
        total: result.rows.length,
        page: pageNum,
        size: sizeNum,
      };
    }

    // 构建缓存键
    const cacheKeyStr = cache.cacheKey(
      'users', 'list', pageNum, sizeNum, keyword || '', role || ''
    );

    // 尝试从缓存获取
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    // 管理员：查询所有用户，支持筛选
    let query = `SELECT id, username, nickname, phone, role,
                 TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') as created_at,
                 TO_CHAR(updated_at, 'YYYY-MM-DD HH24:MI') as updated_at
                 FROM users`;
    let countQuery = 'SELECT COUNT(*) FROM users';
    const conditions = [];
    const params = [];
    let paramIndex = 1;

    // 角色筛选
    if (role) {
      conditions.push(`role = $${paramIndex}`);
      params.push(role);
      paramIndex++;
    }

    // 关键词搜索（匹配用户名或昵称）
    if (keyword) {
      conditions.push(`(username LIKE $${paramIndex} OR nickname LIKE $${paramIndex})`);
      params.push(`%${keyword}%`);
      paramIndex++;
    }

    // 拼接 WHERE 条件
    if (conditions.length > 0) {
      const whereClause = ' WHERE ' + conditions.join(' AND ');
      query += whereClause;
      countQuery += whereClause;
    }

    // 排序和分页
    query += ` ORDER BY created_at DESC LIMIT $${paramIndex} OFFSET $${paramIndex + 1}`;
    params.push(sizeNum, offset);

    // 执行查询
    const [result, countResult] = await Promise.all([
      pool.query(query, params),
      pool.query(countQuery, params.slice(0, -2)), // count查询不需要LIMIT和OFFSET参数
    ]);

    const responseData = {
      list: result.rows,
      total: parseInt(countResult.rows[0].count, 10),
      page: pageNum,
      size: sizeNum,
    };

    // 写入缓存（5分钟）
    await cache.set(cacheKeyStr, responseData, cache.TTL.SHORT);

    return responseData;
  },

  /**
   * 获取用户详情
   *
   * @param {number} userId - 用户ID
   * @returns {Promise<object>} 用户详情
   */
  async getUserById(userId) {
    // 尝试从缓存获取
    const cacheKeyStr = getUserCacheKey(userId);
    const cachedData = await cache.get(cacheKeyStr);
    if (cachedData) {
      return cachedData;
    }

    const result = await pool.query(
      `SELECT id, username, nickname, phone, role,
              TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') as created_at,
              TO_CHAR(updated_at, 'YYYY-MM-DD HH24:MI') as updated_at
       FROM users WHERE id = $1`,
      [userId]
    );

    if (result.rows.length === 0) {
      throw new BusinessError(2004, '用户不存在');
    }

    const user = result.rows[0];

    // 写入缓存（10分钟）
    await cache.set(cacheKeyStr, user, cache.TTL.MEDIUM);

    return user;
  },

  /**
   * 更新用户信息
   *
   * @param {number} userId - 用户ID
   * @param {object} updates - 更新内容
   * @param {string} [updates.nickname] - 昵称
   * @param {string} [updates.phone] - 手机号
   * @param {string} [updates.role] - 角色（仅管理员可修改）
   * @param {boolean} [updates.isAdmin] - 是否管理员操作
   * @returns {Promise<object>} 更新后的用户信息
   */
  async updateUser(userId, updates) {
    const { nickname, phone, role, isAdmin: isAdminUser } = updates;

    // 1. 检查用户是否存在
    const existingUser = await pool.query(
      'SELECT id FROM users WHERE id = $1',
      [userId]
    );
    if (existingUser.rows.length === 0) {
      throw new BusinessError(2004, '用户不存在');
    }

    // 2. 如果更新手机号，检查手机号是否已被其他用户使用
    if (phone) {
      const phoneCheck = await pool.query(
        'SELECT id FROM users WHERE phone = $1 AND id != $2',
        [phone, userId]
      );
      if (phoneCheck.rows.length > 0) {
        throw new BusinessError(2003, '该手机号已被其他用户使用');
      }
    }

    // 3. 更新用户信息（非管理员不能修改角色）
    const result = await pool.query(
      `UPDATE users
       SET nickname = COALESCE($1, nickname),
           phone = COALESCE($2, phone),
           role = COALESCE($3, role),
           updated_at = CURRENT_TIMESTAMP
       WHERE id = $4
       RETURNING id, username, nickname, phone, role,
                 TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') as created_at,
                 TO_CHAR(updated_at, 'YYYY-MM-DD HH24:MI') as updated_at`,
      [nickname, phone, isAdminUser ? role : null, userId]
    );

    // 4. 清除用户相关缓存
    await invalidateUserCache(userId);

    logger.info('更新用户信息成功', { userId });

    return result.rows[0];
  },

  /**
   * 修改密码
   *
   * @param {number} userId - 用户ID
   * @param {object} params - 密码参数
   * @param {string} params.oldPassword - 旧密码
   * @param {string} params.newPassword - 新密码
   * @returns {Promise<{message: string}>}
   */
  async changePassword(userId, { oldPassword, newPassword }) {
    // 1. 校验新密码格式
    validatePasswordFormat(newPassword);

    // 2. 获取用户信息
    const userResult = await pool.query(
      'SELECT id, password FROM users WHERE id = $1',
      [userId]
    );
    if (userResult.rows.length === 0) {
      throw new BusinessError(2004, '用户不存在');
    }

    const user = userResult.rows[0];

    // 3. 验证旧密码
    const isMatch = await comparePassword(oldPassword, user.password);
    if (!isMatch) {
      throw new BusinessError(2006, '旧密码错误');
    }

    // 4. 加密新密码
    const hashedPassword = await hashPassword(newPassword);

    // 5. 更新密码
    await pool.query(
      'UPDATE users SET password = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
      [hashedPassword, userId]
    );

    // 6. 清除 refreshToken，使已登录的其他设备需要重新登录
    await cache.del(getRefreshTokenKey(userId));

    // 7. 清除用户缓存
    await invalidateUserCache(userId);

    logger.info('修改密码成功', { userId });

    return { message: '密码修改成功' };
  },

  /**
   * 创建用户（管理员）
   *
   * @param {object} userData - 用户数据
   * @param {string} userData.username - 用户名
   * @param {string} userData.password - 密码
   * @param {string} [userData.nickname] - 昵称
   * @param {string} [userData.phone] - 手机号
   * @param {string} [userData.role='constructor'] - 角色
   * @returns {Promise<object>} 创建的用户信息
   */
  async createUser(userData) {
    const { username, password, nickname, phone, role } = userData;

    // 1. 校验密码格式
    validatePasswordFormat(password);

    // 2. 检查用户名是否已存在
    const existingUser = await pool.query(
      'SELECT id FROM users WHERE username = $1',
      [username]
    );
    if (existingUser.rows.length > 0) {
      throw new BusinessError(2001, '用户名已存在');
    }

    // 3. 检查手机号是否已存在
    if (phone) {
      const phoneCheck = await pool.query(
        'SELECT id FROM users WHERE phone = $1',
        [phone]
      );
      if (phoneCheck.rows.length > 0) {
        throw new BusinessError(2003, '该手机号已被其他用户使用');
      }
    }

    // 4. 加密密码
    const hashedPassword = await hashPassword(password);

    // 5. 创建用户
    const result = await pool.query(
      `INSERT INTO users (username, password, nickname, phone, role)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, username, nickname, phone, role,
                 TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI') as created_at`,
      [username, hashedPassword, nickname || null, phone || null, role || 'constructor']
    );

    // 6. 清除用户列表缓存
    await cache.delByPrefix('users:');

    logger.info('创建用户成功', { userId: result.rows[0].id, username });

    return result.rows[0];
  },
};
