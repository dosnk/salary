const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const Joi = require('joi');
const pool = require('../config/database');
const validation = require('../middleware/validation');
const logger = require('../config/logger');
const { getRedisClient, isRedisAvailable } = require('../config/redis');

// JWT配置
const ACCESS_TOKEN_EXPIRY = '2h';       // 短期Token 2小时
const REFRESH_TOKEN_EXPIRY = '30d';     // 长期Token 30天

/**
 * 生成Token对
 * @param {object} user 用户对象
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

// 登录
const login = async (ctx) => {
  const { username, password } = ctx.request.body;

  // 参数校验 - 用户名必须是中文，密码6-20位 含字母和数字
  const loginSchema = Joi.object({
    username: Joi.string().pattern(/^[\u4e00-\u9fa5]{2,10}$/).required().messages({
      'string.pattern.base': '用户名必须是2-10位中文字符',
      'any.required': '用户名不能为空'
    }),
    password: Joi.string().min(6).max(20).pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/).required().messages({
      'string.min': '密码最少6位',
      'string.max': '密码最多20位',
      'string.pattern.base': '密码必须包含字母和数字',
      'any.required': '密码不能为空'
    })
  });

  try {
    await loginSchema.validateAsync({ username, password }, { abortEarly: false });

    // 登录失败次数检查 (Redis限流)
    const redis = getRedisClient();
    const lockKey = `login_lock:${username}`;
    const attemptKey = `login_attempts:${username}`;

    if (isRedisAvailable()) {
      // 检查是否被锁定
      const isLocked = await redis.get(lockKey);
      if (isLocked) {
        const ttl = await redis.ttl(lockKey);
        ctx.fail(2002, `登录失败次数过多，请${Math.ceil(ttl / 60)}分钟后重试`);
        return;
      }
    }

    // 查询用户
    const result = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
    if (result.rows.length === 0) {
      ctx.fail(2002);
      return;
    }

    const user = result.rows[0];

    // 验证密码
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      // 记录失败次数
      if (isRedisAvailable()) {
        const attempts = await redis.incr(attemptKey);
        await redis.expire(attemptKey, 1800); // 30分钟
        if (attempts >= 5) {
          await redis.set(lockKey, '1', 'EX', 1800); // 锁定30分钟
          logger.warn(`用户 ${username} 登录失败${attempts}次，已锁定30分钟`);
        }
      }
      ctx.fail(2002);
      return;
    }

    // 登录成功，清除失败记录
    if (isRedisAvailable()) {
      await redis.del(attemptKey);
      await redis.del(lockKey);
    }

    // 生成Token对
    const tokens = generateTokens(user);

    // 存储refreshToken到Redis (用于登出时失效)
    if (isRedisAvailable()) {
      await redis.set(
        `refresh_token:${user.id}`,
        tokens.refreshToken,
        'EX',
        30 * 24 * 60 * 60 // 30天
      );
    }

    ctx.success({
      ...tokens,
      user: {
        id: user.id,
        username: user.username,
        nickname: user.nickname,
        role: user.role
      }
    });
  } catch (error) {
    if (error.name === 'ValidationError') {
      const errors = error.details.map(detail => detail.message).join(', ');
      ctx.fail(1001, `参数错误: ${errors}`);
    } else {
      logger.error('登录失败:', error);
      ctx.fail(5001, '数据库异常');
    }
  }
};

// 刷新Token
const refreshToken = async (ctx) => {
  const { refreshToken } = ctx.request.body;

  if (!refreshToken) {
    ctx.fail(1001, 'refreshToken不能为空');
    return;
  }

  try {
    const decoded = jwt.verify(refreshToken, process.env.JWT_SECRET);
    if (decoded.type !== 'refresh') {
      ctx.fail(4002, '无效的refreshToken');
      return;
    }

    // 验证Redis中的refreshToken是否有效
    const redis = getRedisClient();
    if (isRedisAvailable()) {
      const storedToken = await redis.get(`refresh_token:${decoded.id}`);
      if (storedToken !== refreshToken) {
        ctx.fail(4002, 'refreshToken已失效');
        return;
      }
    }

    // 查询用户信息
    const result = await pool.query(
      'SELECT id, username, nickname, role FROM users WHERE id = $1',
      [decoded.id]
    );
    if (result.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    // 生成新Token对
    const tokens = generateTokens(result.rows[0]);

    // 更新Redis中的refreshToken
    if (isRedisAvailable()) {
      await redis.set(
        `refresh_token:${decoded.id}`,
        tokens.refreshToken,
        'EX',
        30 * 24 * 60 * 60
      );
    }

    ctx.success(tokens);
  } catch (error) {
    if (error.name === 'JsonWebTokenError' || error.name === 'TokenExpiredError') {
      ctx.fail(4002, 'refreshToken无效或已过期');
    } else {
      logger.error('刷新Token失败:', error);
      ctx.fail(5001, '刷新Token失败');
    }
  }
};

// 注册
const register = async (ctx) => {
  const { username, password, phone, nickname } = ctx.request.body;

  // 参数校验 - 用户名必须是中文，密码6-20位 含字母和数字
  const registerSchema = Joi.object({
    username: Joi.string().pattern(/^[\u4e00-\u9fa5]{2,10}$/).required().messages({
      'string.pattern.base': '用户名必须是2-10位中文字符',
      'any.required': '用户名不能为空'
    }),
    password: Joi.string().min(6).max(20).pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/).required().messages({
      'string.min': '密码最少6位',
      'string.max': '密码最多20位',
      'string.pattern.base': '密码必须包含字母和数字',
      'any.required': '密码不能为空'
    }),
    phone: Joi.string().pattern(/^1[3-9]\d{9}$/).required().messages({
      'string.pattern.base': '手机号格式不正确',
      'any.required': '手机号不能为空'
    }),
    nickname: Joi.string().min(2).max(50)
  });

  try {
    await registerSchema.validateAsync({ username, password, phone, nickname }, { abortEarly: false });
    // 检查用户名是否已存在
    const userResult = await pool.query('SELECT id FROM users WHERE username = $1', [username]);
    if (userResult.rows.length > 0) {
      ctx.fail(2001);
      return;
    }

    // 检查手机号是否已存在
    const phoneResult = await pool.query('SELECT id FROM users WHERE phone = $1', [phone]);
    if (phoneResult.rows.length > 0) {
      ctx.fail(2003);
      return;
    }

    // 加密密码
    const saltRounds = 10;
    const hashedPassword = await bcrypt.hash(password, saltRounds);

    // 创建用户
    const result = await pool.query(
      'INSERT INTO users (username, password, phone, nickname, role) VALUES ($1, $2, $3, $4, $5) RETURNING *',
      [username, hashedPassword, phone, nickname || username, 'constructor']
    );

    const user = result.rows[0];
    const tokens = generateTokens(user);

    // 存储refreshToken
    const redis = getRedisClient();
    if (isRedisAvailable()) {
      await redis.set(
        `refresh_token:${user.id}`,
        tokens.refreshToken,
        'EX',
        30 * 24 * 60 * 60
      );
    }

    ctx.success({
      ...tokens,
      user: { id: user.id, username: user.username, nickname: user.nickname, role: user.role }
    });
  } catch (error) {
    if (error.name === 'ValidationError') {
      const errors = error.details.map(detail => detail.message).join(', ');
      ctx.fail(1001, `参数错误: ${errors}`);
    } else {
      logger.error('注册失败:', error);
      ctx.fail(5001, '数据库异常');
    }
  }
};

// 获取用户信息
const getUserInfo = async (ctx) => {
  const userId = ctx.state.user.id;

  logger.info('获取用户信息:', { userId, state: ctx.state.user });

  try {
    const result = await pool.query('SELECT id, username, nickname, phone, role, created_at FROM users WHERE id = $1', [userId]);
    logger.info('查询用户结果:', { rowCount: result.rowCount });
    
    if (result.rows.length === 0) {
      ctx.fail(2004);
      return;
    }

    ctx.success(result.rows[0]);
  } catch (error) {
    logger.error('获取用户信息失败:', error);
    ctx.fail(5001, '数据库异常');
  }
};

// 退出登录
const logout = async (ctx) => {
  const userId = ctx.state.user.id;
  
  logger.info('用户退出登录:', { userId });

  try {
    // 清除Redis中的refreshToken
    const redis = getRedisClient();
    if (isRedisAvailable()) {
      await redis.del(`refresh_token:${userId}`);
    }

    ctx.success({ message: '退出登录成功' });
  } catch (error) {
    logger.error('退出登录失败:', error);
    ctx.fail(5001, '服务器内部错误');
  }
};

module.exports = {
  login,
  register,
  refreshToken,
  getUserInfo,
  logout
};