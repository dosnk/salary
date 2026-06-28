/**
 * 集成测试环境配置
 *
 * 使用独立的测试数据库，避免污染生产数据
 * 测试前自动初始化数据库，测试后自动清理
 */

require('dotenv').config({ path: require('path').join(__dirname, '../.env.test') });

const { execSync } = require('child_process');
const path = require('path');

// 测试环境变量
process.env.NODE_ENV = 'test';
process.env.PORT = '3001'; // 使用不同端口避免冲突
process.env.JWT_SECRET = 'test-jwt-secret-for-integration-tests';
process.env.JWT_EXPIRES_IN = '2h';
process.env.JWT_REFRESH_EXPIRES_IN = '7d';

// 测试数据库配置（使用同一PG实例的测试库）
process.env.DB_HOST = process.env.DB_HOST || 'localhost';
process.env.DB_PORT = process.env.DB_PORT || '5432';
process.env.DB_NAME = process.env.DB_NAME || 'salary_test';
process.env.DB_USER = process.env.DB_USER || 'postgres';
process.env.DB_PASSWORD = process.env.DB_PASSWORD || '';

// Redis测试配置
process.env.REDIS_HOST = process.env.REDIS_HOST || 'localhost';
process.env.REDIS_PORT = process.env.REDIS_PORT || '6379';
process.env.REDIS_DB = '1'; // 使用Redis DB 1，避免与开发数据冲突

// 日志静默
process.env.LOG_LEVEL = 'error';

/**
 * 初始化测试数据库
 */
async function initTestDatabase() {
  try {
    const pool = require('../config/database');
    await pool.query('SELECT 1');
    console.log('测试数据库连接成功');

    // 运行数据库初始化脚本
    try {
      execSync(`node ${path.join(__dirname, '../scripts/init-db.js')}`, {
        env: { ...process.env },
        stdio: 'pipe'
      });
    } catch (e) {
      // 初始化脚本可能失败，不影响测试运行
      console.warn('测试数据库初始化警告:', e.message);
    }

    return pool;
  } catch (error) {
    console.error('测试数据库连接失败:', error.message);
    console.warn('部分测试将被跳过');
    return null;
  }
}

/**
 * 清理测试数据库
 */
async function cleanupTestDatabase(pool) {
  if (!pool) return;
  try {
    // 清空所有表数据但保留表结构
    const tables = [
      'ai_chat_messages', 'ai_chat_sessions', 'material_params',
      'wage_settlements', 'wage_advances', 'project_user_status',
      'sub_projects', 'project_workers', 'projects',
      'construction_plans', 'space_types', 'wage_distribution_types',
      'messages', 'users'
    ];
    for (const table of tables) {
      try { await pool.query(`TRUNCATE TABLE ${table} CASCADE`); } catch (e) { /* 表可能不存在 */ }
    }
  } catch (error) {
    console.warn('清理测试数据库失败:', error.message);
  }
}

/**
 * 创建测试用户并获取Token
 */
async function createTestUser(pool, role = 'constructor') {
  const bcrypt = require('bcryptjs');
  const jwt = require('jsonwebtoken');

  const password = await bcrypt.hash('Test123456', 10);
  const result = await pool.query(
    `INSERT INTO users (username, password, nickname, phone, role)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id, username, nickname, role`,
    [`test_${role}`, password, `测试${role === 'admin' ? '管理员' : role === 'constructor' ? '施工员' : '资料员'}`, '13800000000', role]
  );

  const user = result.rows[0];
  const token = jwt.sign(
    { id: user.id, username: user.username, role: user.role },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN }
  );

  return { user, token };
}

module.exports = {
  initTestDatabase,
  cleanupTestDatabase,
  createTestUser
};
