/**
 * 数据库清空脚本
 * 删除所有表和数据，用于重新初始化
 * 
 * 使用方法: node scripts/clear-db.js
 * 警告: 此操作会删除所有数据，请谨慎使用！
 */

const { Pool } = require('pg');
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

// ===================== 日志工具函数 =====================
const log = {
  info: (msg) => {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] [INFO] ${msg}`);
  },
  error: (msg) => {
    const timestamp = new Date().toISOString();
    console.error(`[${timestamp}] [ERROR] ${msg}`);
  },
  warn: (msg) => {
    const timestamp = new Date().toISOString();
    console.warn(`[${timestamp}] [WARN] ${msg}`);
  },
  success: (msg) => {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] [SUCCESS] ${msg}`);
  }
};

// ===================== 数据库连接配置 =====================
const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary_system',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false
});

// ===================== 清空数据库 =====================
const clearDatabase = async () => {
  const client = await pool.connect();
  
  try {
    log.warn('========================================');
    log.warn('警告：即将删除所有数据库表和数据！');
    log.warn('========================================');
    
    await client.query('BEGIN');

    // 删除所有表（按依赖顺序）
    const dropTables = `
      DROP TABLE IF EXISTS messages CASCADE;
      DROP TABLE IF EXISTS subproject_transfers CASCADE;
      DROP TABLE IF EXISTS wage_advances CASCADE;
      DROP TABLE IF EXISTS files CASCADE;
      DROP TABLE IF EXISTS wage_distributions CASCADE;
      DROP TABLE IF EXISTS wage_settlement_snapshots CASCADE;
      DROP TABLE IF EXISTS project_user_status CASCADE;
      DROP TABLE IF EXISTS wage_settlements CASCADE;
      DROP TABLE IF EXISTS project_history CASCADE;
      DROP TABLE IF EXISTS subprojects CASCADE;
      DROP TABLE IF EXISTS project_workers CASCADE;
      DROP TABLE IF EXISTS projects CASCADE;
      DROP TABLE IF EXISTS action_types CASCADE;
      DROP TABLE IF EXISTS wage_distribution_types CASCADE;
      DROP TABLE IF EXISTS construction_plans CASCADE;
      DROP TABLE IF EXISTS space_types CASCADE;
      DROP TABLE IF EXISTS users CASCADE;
      DROP TABLE IF EXISTS db_versions CASCADE;
      DROP VIEW IF EXISTS v_project_user_settlement_status CASCADE;
    `;

    log.info('正在删除所有表...');
    await client.query(dropTables);

    // 删除触发器函数
    const dropFunctions = `
      DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
    `;
    await client.query(dropFunctions);

    await client.query('COMMIT');
    
    log.success('数据库已清空！');
    log.info('请运行 node scripts/init-db.js 重新初始化数据库');

  } catch (error) {
    await client.query('ROLLBACK');
    log.error(`清空数据库失败: ${error.message}`);
    throw error;
  } finally {
    client.release();
    await pool.end();
  }
};

// 执行清空
clearDatabase().catch(err => {
  log.error(`清空脚本执行失败: ${err.message}`);
  process.exit(1);
});
