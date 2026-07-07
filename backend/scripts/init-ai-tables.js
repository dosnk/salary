/**
 * AI模块数据库表初始化脚本
 *
 * 创建以下表:
 * - material_categories: 材料分类
 * - material_params: 材料参数（知识库）
 * - ai_chat_history: AI对话历史
 * - ai_knowledge_chunks: 知识库文档分块
 *
 * 兼容性说明:
 * - 如果PostgreSQL安装了pgvector扩展，embedding列使用VECTOR(1536)类型
 * - 如果未安装pgvector，embedding列降级为TEXT类型，向量搜索功能不可用（仅关键词搜索）
 */

const pool = require('../config/database');

/**
 * 检查pgvector扩展是否可用
 * @returns {Promise<boolean>}
 */
const checkPgvectorAvailable = async () => {
  try {
    await pool.query(`CREATE EXTENSION IF NOT EXISTS vector;`);
    // 验证vector类型是否真的可用
    await pool.query(`SELECT NULL::vector;`);
    return true;
  } catch (e) {
    console.warn('pgvector扩展不可用，向量搜索功能将降级为关键词搜索。安装方法: https://github.com/pgvector/pgvector');
    return false;
  }
};

const createTables = async () => {
  const client = await pool.connect();
  try {
    // 先检查pgvector扩展是否可用（在事务外执行，因为CREATE EXTENSION不能在事务回滚后再次尝试）
    const hasPgvector = await checkPgvectorAvailable();

    await client.query('BEGIN');

    // 1. 材料分类表
    await client.query(`
      CREATE TABLE IF NOT EXISTS material_categories (
        id SERIAL PRIMARY KEY,
        name VARCHAR(50) NOT NULL UNIQUE,
        description TEXT,
        sort_order INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    // 2. 材料参数表（知识库核心）
    await client.query(`
      CREATE TABLE IF NOT EXISTS material_params (
        id SERIAL PRIMARY KEY,
        category_id INTEGER NOT NULL REFERENCES material_categories(id) ON DELETE CASCADE,
        name VARCHAR(100) NOT NULL,
        brand VARCHAR(100),
        specification VARCHAR(200),
        unit VARCHAR(20) NOT NULL DEFAULT '张',
        unit_price NUMERIC(14,4) NOT NULL DEFAULT 0,
        width_cm NUMERIC(10,2),           -- 板材宽度(cm)
        length_cm NUMERIC(10,2),          -- 板材长度(cm)
        thickness_cm NUMERIC(6,2),        -- 板材厚度(cm)
        coverage_area NUMERIC(10,4),      -- 单张覆盖面积(㎡)
        keel_spacing_cm NUMERIC(6,2),     -- 龙骨间距(cm)
        weight_per_unit NUMERIC(10,4),    -- 单位重量(kg)
        is_active BOOLEAN DEFAULT TRUE,
        remark TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    // 3. AI对话历史表
    await client.query(`
      CREATE TABLE IF NOT EXISTS ai_chat_history (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        session_id VARCHAR(50) NOT NULL,
        role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
        content TEXT NOT NULL,
        intent VARCHAR(50),               --