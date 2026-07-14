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
 * 表名对照 backend/scripts/init-db.js 中的实际定义
 * 清空顺序：先清子表（有外键依赖），再清主表
 */
async function cleanupTestDatabase(pool) {
  if (!pool) return;
  try {
    // 按外键依赖顺序清空（子表优先），使用 RESTART IDENTITY 重置自增ID
    const tables = [
      'wage_distributions',           // 工资分配（依赖 subprojects/wage_settlements）
      'wage_settlement_snapshots',    // 结算快照（依赖 wage_settlements）
      'wage_settlements',             // 结算单（依赖 users/projects）
      'wage_advances',                // 预支（依赖 users/wage_settlements）
      'subproject_transfers',         // 子项目转交（依赖 subprojects）
      'subprojects',                  // 子项目（依赖 projects/space_types/construction_plans）
      'project_user_status',          // 用户工程结算状态（依赖 projects/users/wage_settlements）
      'project_history',              // 工程历史（依赖 projects/users）
      'project_workers',              // 施工人员关联（依赖 projects/users）
      'files',                        // 附件（依赖 projects/users）
      'messages',                     // 站内消息
      'projects',                     // 工程（依赖 users）
      'ai_chat_history',              // AI对话历史（依赖 users）
      'ai_knowledge_chunks'           // 知识库分块
      // 注意：users/space_types/construction_plans/wage_distribution_types/action_types
      //       material_categories/material_params 由 init-db.js 初始化，测试后保留
    ];
    for (const table of tables) {
      try {
        await pool.query(`TRUNCATE TABLE ${table} RESTART IDENTITY CASCADE`);
      } catch (e) {
        /* 表可能不存在（首次初始化未完成），跳过 */
      }
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
