/**
 * 数据库连接配置 (TypeScript + Knex + PG Pool)
 * 
 * 保持原有 pg Pool 向后兼容，同时引入 Knex Query Builder
 * 所有新增 .ts 文件使用 Knex，旧 .js 文件继续使用 pool
 */
import { Pool, PoolConfig } from 'pg';
import knex, { Knex } from 'knex';
import path from 'path';
import dotenv from 'dotenv';

dotenv.config({ path: path.resolve(__dirname, '../.env') });

// 配置验证
const requiredConfig = ['DB_PASSWORD'];
const missingConfig = requiredConfig.filter(key => !process.env[key]);

if (missingConfig.length > 0) {
  console.error('❌ 以下必填配置缺失：', missingConfig.join(', '));
  console.error('💡 请在.env文件中配置这些参数');
  process.exit(1);
}

// ========== PG Pool 配置 (向后兼容旧 .js 文件) ==========
const poolConfig: PoolConfig = {
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'default_db',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT || '5432', 10),
  max: 20,
  min: 0,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
  statement_timeout: 300000,
  query_timeout: 300000,
};

const pool = new Pool(poolConfig);

// ========== Knex 配置 (新 .ts 文件使用) ==========
const knexConfig: Knex.Config = {
  client: 'pg',
  connection: {
    user: process.env.DB_USER || 'postgres',
    host: process.env.DB_HOST || 'localhost',
    database: process.env.DB_NAME || 'default_db',
    password: process.env.DB_PASSWORD,
    port: parseInt(process.env.DB_PORT || '5432', 10),
  },
  pool: {
    min: 0,
    max: 20,
    idleTimeoutMillis: 30000,
    acquireTimeoutMillis: 5000,
  },
};

const db: Knex = knex(knexConfig);

// ========== 工具函数 ==========

/**
 * 执行参数化查询 (推荐使用，防止SQL注入)
 * @param query SQL查询语句 ($1, $2 占位符)
 * @param params 参数数组
 * @returns 查询结果
 */
export async function query<T = any>(queryStr: string, params: any[] = []): Promise<{ rows: T[]; rowCount: number }> {
  return pool.query(queryStr, params);
}

/**
 * 获取单个查询结果行
 * @param queryStr SQL查询语句
 * @param params 参数数组
 * @returns 第一行数据，无结果返回null
 */
export async function queryOne<T = any>(queryStr: string, params: any[] = []): Promise<T | null> {
  const result = await pool.query(queryStr, params);
  return result.rows.length > 0 ? result.rows[0] : null;
}

export { pool, db };
export default pool;