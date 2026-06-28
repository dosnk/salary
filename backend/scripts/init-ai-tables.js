/**
 * AI模块数据库表初始化脚本
 *
 * 创建以下表:
 * - material_categories: 材料分类
 * - material_params: 材料参数（知识库）
 * - ai_chat_history: AI对话历史
 * - ai_knowledge_chunks: 知识库文档分块
 */

const pool = require('../config/database');

const createTables = async () => {
  const client = await pool.connect();
  try {
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
        intent VARCHAR(50),               -- 意图标签
        tool_calls JSONB,                 -- FunctionCall记录
        token_count INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    // 4. 知识库文档分块表
    await client.query(`
      CREATE TABLE IF NOT EXISTS ai_knowledge_chunks (
        id SERIAL PRIMARY KEY,
        source_type VARCHAR(50) NOT NULL DEFAULT 'manual',
        source_id INTEGER,
        title VARCHAR(200),
        content TEXT NOT NULL,
        chunk_index INTEGER NOT NULL DEFAULT 0,
        embedding VECTOR(1536),           -- pgvector向量（需安装扩展）
        metadata JSONB DEFAULT '{}',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);

    // 创建索引
    await client.query(`CREATE INDEX IF NOT EXISTS idx_material_params_category ON material_params(category_id);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_material_params_active ON material_params(is_active);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_ai_chat_history_user ON ai_chat_history(user_id, session_id);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_ai_chat_history_created ON ai_chat_history(created_at);`);
    await client.query(`CREATE INDEX IF NOT EXISTS idx_ai_knowledge_source ON ai_knowledge_chunks(source_type, source_id);`);

    // 尝试创建pgvector扩展（如果可用）
    try {
      await client.query(`CREATE EXTENSION IF NOT EXISTS vector;`);
    } catch (e) {
      console.warn('pgvector扩展未安装，向量搜索功能不可用。安装方法: https://github.com/pgvector/pgvector');
    }

    await client.query('COMMIT');
    console.log('AI模块数据库表创建成功');
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('AI模块数据库表创建失败:', error.message);
    throw error;
  } finally {
    client.release();
  }
};

// 直接运行时执行
if (require.main === module) {
  createTables()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}

module.exports = { createTables };
