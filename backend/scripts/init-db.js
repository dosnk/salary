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

// ===================== 1. 安全配置校验 =====================
const requiredConfig = ['DB_PASSWORD'];
const recommendedConfig = ['DB_USER', 'DB_HOST', 'DB_NAME', 'DB_PORT'];
const missingConfig = requiredConfig.filter(key => !process.env[key]);
const notRecommended = recommendedConfig.filter(key => !process.env[key]);

if (missingConfig.length > 0) {
  log.error(`以下必填配置缺失：${missingConfig.join(', ')}`);
  log.error('请在.env文件中配置这些参数');
  process.exit(1);
}

// DB_PORT格式和范围校验
if (process.env.DB_PORT) {
  if (!/^\d+$/.test(process.env.DB_PORT)) {
    log.error('DB_PORT必须为纯数字（如5432）');
    process.exit(1);
  }
  const portNum = parseInt(process.env.DB_PORT, 10);
  if (portNum < 1 || portNum > 65535) {
    log.error('DB_PORT必须在有效端口范围内（1-65535）');
    process.exit(1);
  }
}

if (notRecommended.length > 0) {
  log.warn(`以下配置未设置，将使用默认值：${notRecommended.join(', ')}`);
}

// ===================== 2. 数据库连接配置 =====================
const isProduction = process.env.NODE_ENV === 'production';
// SSL配置：默认禁用SSL，除非明确设置DB_SSL=true
// 原因：很多PostgreSQL服务器未配置SSL，强制SSL会导致连接失败
const sslConfig = process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false;

const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary_system',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
  ssl: sslConfig
});

// ===================== 2.1 数据库连接预测试 =====================
const testConnection = async () => {
  let client;
  try {
    log.info('测试数据库连接...');
    client = await pool.connect();
    const result = await client.query('SELECT version()');
    log.success('数据库连接测试成功');
    log.info(`数据库版本: ${result.rows[0].version.split(' ')[0]} ${result.rows[0].version.split(' ')[1]}`);
    return true;
  } catch (error) {
    log.error(`数据库连接失败: ${error.message}`);
    log.error('请检查数据库配置是否正确');
    return false;
  } finally {
    if (client) {
      client.release();
    }
  }
};

// ===================== 3. 默认用户配置（从环境变量读取） =====================
// 默认密码与旧程序保持一致：990066（6位纯数字）
// 使用 bcryptjs 12轮加密生成（与项目规范 SALT_ROUNDS=12 一致）
// 注意：原hash值 $2b$10$P3wghHsPlXTX.IGPrxMmAeKNyeTHFBG6CKkpNFHnT1oPv.pFTHFpS 实际不对应990066，已修复
const DEFAULT_PASSWORD_HASH = process.env.DEFAULT_PASSWORD_HASH || '$2b$12$cUtNFBFtlBUKiq4psDFMx.uX7VLde5vWUdg3VTPzBy2.cIGA/eFRG';
const DEFAULT_PASSWORD = process.env.DEFAULT_PASSWORD || '990066';

// 默认用户列表（可从环境变量覆盖）
const DEFAULT_USERS = process.env.DEFAULT_USERS ? 
  JSON.parse(process.env.DEFAULT_USERS) : 
  [
    { username: '喜临门', nickname: '喜临门', phone: '13813813813', role: 'admin' },
    { username: '莫量波', nickname: '莫量波', phone: '13813813814', role: 'documenter' },
    { username: '梁祖霞', nickname: '梁祖霞', phone: '13813813815', role: 'documenter' },
    { username: '杨秀红', nickname: '阿红', phone: '13813813816', role: 'documenter' },
    { username: '杨耀贵', nickname: '贵', phone: '13813813817', role: 'constructor' },
    { username: '苏龙权', nickname: '权', phone: '13813813818', role: 'constructor' },
    { username: '梁达寅', nickname: '寅', phone: '13813813819', role: 'constructor' },
    { username: '朱文峰', nickname: '峰', phone: '13813813820', role: 'constructor' },
    { username: '张诗颂', nickname: '颂', phone: '13813813821', role: 'constructor' },
    { username: '阿朱', nickname: '朱', phone: '13813813822', role: 'constructor' },
    { username: '林概', nickname: '概', phone: '13813813823', role: 'constructor' }
  ];

// ===================== 4. 迁移版本定义 =====================
const MIGRATIONS = [
  {
    version: 'V1.0',
    description: '创建数据库版本控制表',
    up: `
      CREATE TABLE IF NOT EXISTS db_versions (
        id SERIAL PRIMARY KEY,
        version VARCHAR(20) NOT NULL UNIQUE,
        description TEXT,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
      );
    `,
    down: `DROP TABLE IF EXISTS db_versions CASCADE;`,
    tables: ['db_versions']
  },
  {
    version: 'V1.1',
    description: '创建核心业务表',
    up: `
      SET client_encoding = 'UTF8';
      SET timezone = 'Asia/Shanghai';

      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        nickname VARCHAR(50),
        phone VARCHAR(20) UNIQUE,
        role VARCHAR(20) NOT NULL DEFAULT 'constructor',
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS space_types (
        id SERIAL PRIMARY KEY,
        name VARCHAR(50) UNIQUE NOT NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS construction_plans (
        id SERIAL PRIMARY KEY,
        name VARCHAR(50) UNIQUE NOT NULL,
        unit VARCHAR(20) NOT NULL,
        price NUMERIC(10,2) NOT NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS wage_distribution_types (
        id SERIAL PRIMARY KEY,
        name VARCHAR(50) UNIQUE NOT NULL,
        code VARCHAR(20) UNIQUE NOT NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS action_types (
        code VARCHAR(20) PRIMARY KEY,
        name VARCHAR(50) NOT NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS projects (
        id SERIAL PRIMARY KEY,
        name VARCHAR(200) NOT NULL,
        description TEXT,
        status VARCHAR(20) NOT NULL DEFAULT 'constructing',
        total_amount NUMERIC(14,4) DEFAULT 0,
        salary_distribution VARCHAR(20) NOT NULL DEFAULT 'average',
        total_work_days NUMERIC(6,2) DEFAULT 0,
        settled_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
        created_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS project_workers (
        id SERIAL PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        workdays NUMERIC(6,2) NOT NULL DEFAULT 1,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_project_workers_project_user UNIQUE(project_id, user_id)
      );

      CREATE TABLE IF NOT EXISTS subprojects (
        id SERIAL PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        space_type_id INTEGER NOT NULL REFERENCES space_types(id) ON DELETE CASCADE,
        construction_plan_id INTEGER NOT NULL REFERENCES construction_plans(id) ON DELETE CASCADE,
        length NUMERIC(10,2),
        width NUMERIC(10,2),
        quantity NUMERIC(10,2) NOT NULL,
        amount NUMERIC(14,4) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'pending',
        remark TEXT,
        created_by INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT ck_subprojects_amount CHECK (amount >= 0)
      );

      CREATE TABLE IF NOT EXISTS project_history (
        id SERIAL PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        action VARCHAR(20) NOT NULL,
        description TEXT,
        performed_by INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_project_history_action FOREIGN KEY (action) 
            REFERENCES action_types(code) ON DELETE RESTRICT
      );

      CREATE TABLE IF NOT EXISTS wage_settlements (
        id SERIAL PRIMARY KEY,
        settlement_no VARCHAR(50) UNIQUE NOT NULL,
        project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,
        project_ids JSONB NOT NULL DEFAULT '[]'::JSONB,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        start_month DATE NOT NULL,
        end_month DATE NOT NULL,
        total_amount NUMERIC(14,4) NOT NULL,
        advance_amount NUMERIC(14,4) DEFAULT 0,
        actual_amount NUMERIC(14,4) NOT NULL,
        confirmed BOOLEAN DEFAULT FALSE,
        confirmed_at TIMESTAMPTZ,
        paid BOOLEAN DEFAULT FALSE,
        paid_at TIMESTAMPTZ,
        settled_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
        settled_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        remark TEXT,
        CONSTRAINT ck_settlement_amount CHECK (total_amount >= 0),
        CONSTRAINT uk_settlement_user_time UNIQUE(user_id, settled_at, settlement_no)
      );

      CREATE TABLE IF NOT EXISTS project_user_status (
        id SERIAL PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        settlement_status VARCHAR(20) NOT NULL DEFAULT 'unsettled',
        settlement_id INTEGER REFERENCES wage_settlements(id) ON DELETE SET NULL,
        settled_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uk_project_user_status_project_user UNIQUE(project_id, user_id),
        CONSTRAINT ck_project_user_status_settlement CHECK (settlement_status IN ('unsettled', 'settling', 'settled'))
      );

      CREATE TABLE IF NOT EXISTS wage_settlement_snapshots (
        id SERIAL PRIMARY KEY,
        settlement_id INTEGER NOT NULL REFERENCES wage_settlements(id) ON DELETE CASCADE,
        settlement_no VARCHAR(50) NOT NULL,
        project_id INTEGER NOT NULL,
        project_name VARCHAR(1000) NOT NULL,
        user_id INTEGER NOT NULL,
        username VARCHAR(500),
        nickname VARCHAR(500),
        start_month DATE NOT NULL,
        end_month DATE NOT NULL,
        total_amount NUMERIC(14,4) NOT NULL,
        advance_amount NUMERIC(14,4) DEFAULT 0,
        actual_amount NUMERIC(14,4) NOT NULL,
        confirmed BOOLEAN DEFAULT FALSE,
        confirmed_at TIMESTAMPTZ,
        settled_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
        settled_by_username VARCHAR(100),
        settled_by_nickname VARCHAR(100),
        settled_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        remark TEXT,
        projects_snapshot JSONB NOT NULL,
        plans_snapshot JSONB NOT NULL,
        advances_snapshot JSONB NOT NULL,
        calculation_snapshot JSONB NOT NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS wage_distributions (
        id SERIAL PRIMARY KEY,
        subproject_id INTEGER NOT NULL REFERENCES subprojects(id) ON DELETE CASCADE,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        workdays NUMERIC(6,2) NOT NULL DEFAULT 1,
        quantity NUMERIC(14,4) NOT NULL DEFAULT 0,
        amount NUMERIC(14,4) NOT NULL,
        settlement_id INTEGER REFERENCES wage_settlements(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT ck_wage_workdays CHECK (workdays >= 0),
        CONSTRAINT ck_wage_quantity CHECK (quantity >= 0),
        CONSTRAINT ck_wage_amount CHECK (amount >= 0)
      );

      CREATE TABLE IF NOT EXISTS files (
        id SERIAL PRIMARY KEY,
        project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        filename VARCHAR(255) NOT NULL,
        original_name VARCHAR(255) NOT NULL,
        path VARCHAR(255) NOT NULL,
        size BIGINT NOT NULL,
        type VARCHAR(50) NOT NULL,
        uploaded_by INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS wage_advances (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        advance_amount NUMERIC(14,4) NOT NULL,
        advance_date DATE NOT NULL,
        settled BOOLEAN DEFAULT FALSE,
        settlement_id INTEGER REFERENCES wage_settlements(id) ON DELETE SET NULL,
        created_by INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        remark TEXT,
        CONSTRAINT ck_advance_amount CHECK (advance_amount >= 0)
      );

      CREATE TABLE IF NOT EXISTS subproject_transfers (
        id SERIAL PRIMARY KEY,
        subproject_id INTEGER NOT NULL REFERENCES subprojects(id) ON DELETE CASCADE,
        from_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
        to_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
        transfer_reason TEXT,
        transferred_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        transferred_by INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL
      );

      CREATE TABLE IF NOT EXISTS messages (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        title VARCHAR(200) NOT NULL,
        content TEXT,
        type VARCHAR(50) NOT NULL,
        is_read BOOLEAN DEFAULT FALSE,
        related_type VARCHAR(50),
        related_id INTEGER,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        created_by INTEGER REFERENCES users(id) ON DELETE SET NULL
      );
    `,
    down: `
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
    `,
    tables: ['users', 'projects', 'subprojects', 'construction_plans', 'space_types', 'wage_settlements', 'wage_advances', 'wage_distributions']
  },
  {
    version: 'V1.2',
    description: '创建数据库索引',
    up: `
      CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);
      CREATE INDEX IF NOT EXISTS idx_subprojects_project_id ON subprojects(project_id);
      CREATE INDEX IF NOT EXISTS idx_files_project_id ON files(project_id);
      CREATE INDEX IF NOT EXISTS idx_project_workers_user_id ON project_workers(user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_user_id ON wage_distributions(user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_created_at ON wage_distributions(created_at);
      CREATE INDEX IF NOT EXISTS idx_projects_created_at ON projects(created_at DESC);
      CREATE INDEX IF NOT EXISTS idx_projects_created_by ON projects(created_by);
      CREATE INDEX IF NOT EXISTS idx_projects_name ON projects(name);
      CREATE INDEX IF NOT EXISTS idx_subprojects_space_type_id ON subprojects(space_type_id);
      CREATE INDEX IF NOT EXISTS idx_subprojects_construction_plan_id ON subprojects(construction_plan_id);
      CREATE INDEX IF NOT EXISTS idx_subprojects_project_status ON subprojects(project_id, status);
      CREATE INDEX IF NOT EXISTS idx_project_history_project_id ON project_history(project_id);
      CREATE INDEX IF NOT EXISTS idx_project_history_created_at ON project_history(created_at DESC);
      CREATE INDEX IF NOT EXISTS idx_subprojects_status ON subprojects(status);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_settled_by ON wage_settlements(settled_by);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_settled_at ON wage_settlements(settled_at);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_project_user ON wage_settlements(project_id, user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_user_id ON wage_settlements(user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_start_month ON wage_settlements(start_month);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_end_month ON wage_settlements(end_month);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_user_month ON wage_settlements(user_id, start_month, end_month);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_project_id ON wage_settlements(project_id);
      CREATE INDEX IF NOT EXISTS idx_wage_settlement_snapshots_settlement_id ON wage_settlement_snapshots(settlement_id);
      CREATE INDEX IF NOT EXISTS idx_wage_settlement_snapshots_user_id ON wage_settlement_snapshots(user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_settlement_snapshots_start_month ON wage_settlement_snapshots(start_month);
      CREATE INDEX IF NOT EXISTS idx_wage_settlement_snapshots_end_month ON wage_settlement_snapshots(end_month);
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_subproject_id ON wage_distributions(subproject_id);
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_subproject_user ON wage_distributions(subproject_id, user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_settlement_id ON wage_distributions(settlement_id);
      CREATE INDEX IF NOT EXISTS idx_wage_advances_user_id ON wage_advances(user_id);
      CREATE INDEX IF NOT EXISTS idx_wage_advances_settled ON wage_advances(settled);
      CREATE INDEX IF NOT EXISTS idx_wage_advances_settlement_id ON wage_advances(settlement_id);
      CREATE INDEX IF NOT EXISTS idx_wage_advances_user_settled ON wage_advances(user_id, settled);
      CREATE INDEX IF NOT EXISTS idx_wage_advances_advance_date ON wage_advances(advance_date);
      CREATE INDEX IF NOT EXISTS idx_project_workers_project_id ON project_workers(project_id);
      CREATE INDEX IF NOT EXISTS idx_project_workers_project_user ON project_workers(project_id, user_id);
      CREATE INDEX IF NOT EXISTS idx_files_created_at ON files(created_at DESC);
      CREATE INDEX IF NOT EXISTS idx_messages_user_read ON messages(user_id, is_read);
      CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
      CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
      CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
      CREATE INDEX IF NOT EXISTS idx_space_types_name ON space_types(name);
      CREATE INDEX IF NOT EXISTS idx_construction_plans_name ON construction_plans(name);
      CREATE INDEX IF NOT EXISTS idx_subproject_transfers_subproject_id ON subproject_transfers(subproject_id);
      CREATE INDEX IF NOT EXISTS idx_subproject_transfers_from_user_id ON subproject_transfers(from_user_id);
      CREATE INDEX IF NOT EXISTS idx_subproject_transfers_to_user_id ON subproject_transfers(to_user_id);
      CREATE INDEX IF NOT EXISTS idx_subproject_transfers_transferred_at ON subproject_transfers(transferred_at);
      CREATE INDEX IF NOT EXISTS idx_messages_type ON messages(type);
      CREATE INDEX IF NOT EXISTS idx_messages_is_read ON messages(is_read);
      CREATE INDEX IF NOT EXISTS idx_messages_related ON messages(related_type, related_id);
      CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);
      CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_unique ON messages(user_id, title, content, type, related_type, related_id);
    `,
    down: `
      DROP INDEX IF EXISTS idx_projects_status;
      DROP INDEX IF EXISTS idx_subprojects_project_id;
      DROP INDEX IF EXISTS idx_files_project_id;
      DROP INDEX IF EXISTS idx_project_workers_user_id;
      DROP INDEX IF EXISTS idx_wage_distributions_user_id;
      DROP INDEX IF EXISTS idx_wage_distributions_created_at;
      DROP INDEX IF EXISTS idx_projects_created_at;
      DROP INDEX IF EXISTS idx_projects_created_by;
      DROP INDEX IF EXISTS idx_projects_name;
      DROP INDEX IF EXISTS idx_subprojects_space_type_id;
      DROP INDEX IF EXISTS idx_subprojects_construction_plan_id;
      DROP INDEX IF EXISTS idx_subprojects_project_status;
      DROP INDEX IF EXISTS idx_project_history_project_id;
      DROP INDEX IF EXISTS idx_project_history_created_at;
      DROP INDEX IF EXISTS idx_subprojects_status;
      DROP INDEX IF EXISTS idx_wage_settlements_settled_by;
      DROP INDEX IF EXISTS idx_wage_settlements_settled_at;
      DROP INDEX IF EXISTS idx_wage_settlements_project_user;
      DROP INDEX IF EXISTS idx_wage_settlements_user_id;
      DROP INDEX IF EXISTS idx_wage_settlements_start_month;
      DROP INDEX IF EXISTS idx_wage_settlements_end_month;
      DROP INDEX IF EXISTS idx_wage_settlements_user_month;
      DROP INDEX IF EXISTS idx_wage_settlements_project_id;
      DROP INDEX IF EXISTS idx_wage_settlement_snapshots_settlement_id;
      DROP INDEX IF EXISTS idx_wage_settlement_snapshots_user_id;
      DROP INDEX IF EXISTS idx_wage_settlement_snapshots_start_month;
      DROP INDEX IF EXISTS idx_wage_settlement_snapshots_end_month;
      DROP INDEX IF EXISTS idx_wage_distributions_subproject_id;
      DROP INDEX IF EXISTS idx_wage_distributions_subproject_user;
      DROP INDEX IF EXISTS idx_wage_distributions_settlement_id;
      DROP INDEX IF EXISTS idx_wage_advances_user_id;
      DROP INDEX IF EXISTS idx_wage_advances_settled;
      DROP INDEX IF EXISTS idx_wage_advances_settlement_id;
      DROP INDEX IF EXISTS idx_wage_advances_user_settled;
      DROP INDEX IF EXISTS idx_wage_advances_advance_date;
      DROP INDEX IF EXISTS idx_project_workers_project_id;
      DROP INDEX IF EXISTS idx_project_workers_project_user;
      DROP INDEX IF EXISTS idx_files_created_at;
      DROP INDEX IF EXISTS idx_messages_user_read;
      DROP INDEX IF EXISTS idx_users_username;
      DROP INDEX IF EXISTS idx_users_role;
      DROP INDEX IF EXISTS idx_users_created_at;
      DROP INDEX IF EXISTS idx_space_types_name;
      DROP INDEX IF EXISTS idx_construction_plans_name;
      DROP INDEX IF EXISTS idx_subproject_transfers_subproject_id;
      DROP INDEX IF EXISTS idx_subproject_transfers_from_user_id;
      DROP INDEX IF EXISTS idx_subproject_transfers_to_user_id;
      DROP INDEX IF EXISTS idx_subproject_transfers_transferred_at;
      DROP INDEX IF EXISTS idx_messages_type;
      DROP INDEX IF EXISTS idx_messages_is_read;
      DROP INDEX IF EXISTS idx_messages_related;
      DROP INDEX IF EXISTS idx_messages_created_at;
      DROP INDEX IF EXISTS idx_messages_unique;
    `
  },
  {
    version: 'V1.3',
    description: '创建触发器和函数',
    up: `
      CREATE OR REPLACE FUNCTION update_updated_at_column()
      RETURNS TRIGGER AS $$
      BEGIN
        NEW.updated_at = CURRENT_TIMESTAMP;
        RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;

      DROP TRIGGER IF EXISTS update_users_updated_at ON users;
      CREATE TRIGGER update_users_updated_at
        BEFORE UPDATE ON users
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();

      DROP TRIGGER IF EXISTS update_projects_updated_at ON projects;
      CREATE TRIGGER update_projects_updated_at
        BEFORE UPDATE ON projects
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();

      DROP TRIGGER IF EXISTS update_subprojects_updated_at ON subprojects;
      CREATE TRIGGER update_subprojects_updated_at
        BEFORE UPDATE ON subprojects
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();

      DROP TRIGGER IF EXISTS update_space_types_updated_at ON space_types;
      CREATE TRIGGER update_space_types_updated_at
        BEFORE UPDATE ON space_types
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();

      DROP TRIGGER IF EXISTS update_construction_plans_updated_at ON construction_plans;
      CREATE TRIGGER update_construction_plans_updated_at
        BEFORE UPDATE ON construction_plans
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();

      DROP TRIGGER IF EXISTS update_wage_distribution_types_updated_at ON wage_distribution_types;
      CREATE TRIGGER update_wage_distribution_types_updated_at
        BEFORE UPDATE ON wage_distribution_types
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();
    `,
    down: `
      DROP TRIGGER IF EXISTS update_users_updated_at ON users;
      DROP TRIGGER IF EXISTS update_projects_updated_at ON projects;
      DROP TRIGGER IF EXISTS update_subprojects_updated_at ON subprojects;
      DROP TRIGGER IF EXISTS update_space_types_updated_at ON space_types;
      DROP TRIGGER IF EXISTS update_construction_plans_updated_at ON construction_plans;
      DROP TRIGGER IF EXISTS update_wage_distribution_types_updated_at ON wage_distribution_types;
      DROP FUNCTION IF EXISTS update_updated_at_column();
    `
  },
  {
    version: 'V1.4',
    description: '创建结算状态视图（优化版）',
    up: `
      CREATE OR REPLACE VIEW v_project_user_settlement_status AS
      -- 施工人员的结算状态
      SELECT 
        COALESCE(pus.id, ROW_NUMBER() OVER (ORDER BY p.id, u.id)) AS id,
        p.id AS project_id,
        u.id AS user_id,
        COALESCE(
          pus.settlement_status,
          CASE 
            WHEN ws.id IS NOT NULL THEN 'settled'
            WHEN p.status = 'completed' THEN 'settling'
            WHEN EXISTS (
              SELECT 1 FROM subprojects sp 
              WHERE sp.project_id = p.id 
              AND sp.status = 'completed'
            ) THEN 'settling'
            ELSE 'unsettled'
          END
        ) AS settlement_status,
        COALESCE(pus.settlement_id, ws.id) AS settlement_id,
        COALESCE(pus.settled_at, ws.settled_at) AS settled_at,
        COALESCE(pus.created_at, CURRENT_TIMESTAMP) AS created_at,
        COALESCE(pus.updated_at, CURRENT_TIMESTAMP) AS updated_at
      FROM projects p
      JOIN project_workers pw ON pw.project_id = p.id
      JOIN users u ON u.id = pw.user_id
      LEFT JOIN project_user_status pus ON pus.project_id = p.id AND pus.user_id = u.id
      LEFT JOIN wage_settlements ws ON 
        ws.user_id = u.id 
        AND (ws.project_id = p.id OR ws.project_ids::jsonb @> to_jsonb(p.id))
      UNION
      -- 管理员和资料员可以查看所有工程
      SELECT 
        COALESCE(pus.id, ROW_NUMBER() OVER (ORDER BY p.id, u.id) + 100000) AS id,
        p.id AS project_id,
        u.id AS user_id,
        COALESCE(pus.settlement_status, 'unsettled') AS settlement_status,
        pus.settlement_id,
        pus.settled_at,
        COALESCE(pus.created_at, CURRENT_TIMESTAMP) AS created_at,
        COALESCE(pus.updated_at, CURRENT_TIMESTAMP) AS updated_at
      FROM projects p
      CROSS JOIN users u
      LEFT JOIN project_user_status pus ON pus.project_id = p.id AND pus.user_id = u.id
      WHERE u.role IN ('admin', 'documenter');
    `,
    down: `DROP VIEW IF EXISTS v_project_user_settlement_status;`
  },
  {
    version: 'V1.5',
    description: '插入预置基础数据',
    up: `
      INSERT INTO action_types (code, name) VALUES 
      ('CREATE_PROJECT', '创建项目'),
      ('UPDATE_PROJECT', '更新项目'),
      ('DELETE_PROJECT', '删除项目'),
      ('ADD_SUBPROJECT', '添加子项目'),
      ('UPDATE_SUBPROJECT', '更新子项目'),
      ('DELETE_SUBPROJECT', '删除子项目'),
      ('SETTLE_WAGE', '结算工资'),
      ('CANCEL_WAGE', '作废薪资'),
      ('ADVANCE_WAGE', '预支工资'),
      ('UPLOAD_FILE', '上传文件'),
      ('TRANSFER_SUBPROJECT', '子项目转交')
      ON CONFLICT (code) DO NOTHING;

      INSERT INTO space_types (name) VALUES 
      ('客厅'),('餐厅'),('厨房'),('公卫'),('主卫'),
      ('大阳台'),('小阳台'),('房间'),('走道'),('入户'),
      ('房间凹位'),('公卫凹位'),('主卫凹位'),('楼梯口'),
      ('电梯口'),('凹位'),('其他（自定义）')
      ON CONFLICT (name) DO NOTHING;

      INSERT INTO construction_plans (name, unit, price) VALUES 
      ('蜂窝平面', 'area', 40.00),
      ('半吊', 'perimeter', 60.00),
      ('二级平面', 'area', 60.00),
      ('铝扣平面', 'area', 25.00),
      ('窗帘盒', 'length', 20.00),
      ('发光走边', 'perimeter', 30.00),
      ('水坑', 'perimeter', 35.00),
      ('工程板', 'area', 20.00),
      ('封梁', 'length', 60.00),
      ('装饰条', 'perimeter', 13.00)
      ON CONFLICT (name) DO NOTHING;

      INSERT INTO wage_distribution_types (name, code) VALUES 
      ('平均分配', 'average'),
      ('按工日', 'by_workday')
      ON CONFLICT (code) DO NOTHING;
    `,
    down: `
      DELETE FROM wage_distribution_types WHERE code IN ('average', 'by_workday');
      DELETE FROM construction_plans WHERE name IN ('蜂窝平面', '半吊', '二级平面', '铝扣平面', '窗帘盒', '发光走边', '水坑', '工程板', '封梁', '装饰条');
      DELETE FROM space_types WHERE name IN ('客厅','餐厅','厨房','公卫','主卫','大阳台','小阳台','房间','走道','入户','房间凹位','公卫凹位','主卫凹位','楼梯口','电梯口','凹位','其他（自定义）');
      DELETE FROM action_types WHERE code IN ('CREATE_PROJECT','UPDATE_PROJECT','DELETE_PROJECT','ADD_SUBPROJECT','UPDATE_SUBPROJECT','DELETE_SUBPROJECT','SETTLE_WAGE','CANCEL_WAGE','ADVANCE_WAGE','UPLOAD_FILE','TRANSFER_SUBPROJECT');
    `
  },
  {
    version: 'V1.6',
    description: '修复管理员视角结算状态视图（admin可正确看到settling/settled状态）',
    up: `
      CREATE OR REPLACE VIEW v_project_user_settlement_status AS
      -- 施工人员的结算状态
      SELECT
        COALESCE(pus.id, ROW_NUMBER() OVER (ORDER BY p.id, u.id)) AS id,
        p.id AS project_id,
        u.id AS user_id,
        COALESCE(
          pus.settlement_status,
          CASE
            WHEN ws.id IS NOT NULL THEN 'settled'
            WHEN p.status = 'completed' THEN 'settling'
            WHEN EXISTS (
              SELECT 1 FROM subprojects sp
              WHERE sp.project_id = p.id
              AND sp.status = 'completed'
            ) THEN 'settling'
            ELSE 'unsettled'
          END
        ) AS settlement_status,
        COALESCE(pus.settlement_id, ws.id) AS settlement_id,
        COALESCE(pus.settled_at, ws.settled_at) AS settled_at,
        COALESCE(pus.created_at, CURRENT_TIMESTAMP) AS created_at,
        COALESCE(pus.updated_at, CURRENT_TIMESTAMP) AS updated_at
      FROM projects p
      JOIN project_workers pw ON pw.project_id = p.id
      JOIN users u ON u.id = pw.user_id
      LEFT JOIN project_user_status pus ON pus.project_id = p.id AND pus.user_id = u.id
      LEFT JOIN wage_settlements ws ON
        ws.user_id = u.id
        AND (ws.project_id = p.id OR ws.project_ids::jsonb @> to_jsonb(p.id))
      UNION
      -- 管理员和资料员可以查看所有工程（修复：根据工程状态判断结算进度，而非固定unsettled）
      SELECT
        COALESCE(pus.id, ROW_NUMBER() OVER (ORDER BY p.id, u.id) + 100000) AS id,
        p.id AS project_id,
        u.id AS user_id,
        COALESCE(
          pus.settlement_status,
          CASE
            WHEN ws.id IS NOT NULL THEN 'settled'
            WHEN p.status = 'completed' THEN 'settling'
            WHEN EXISTS (
              SELECT 1 FROM subprojects sp
              WHERE sp.project_id = p.id
              AND sp.status = 'completed'
            ) THEN 'settling'
            ELSE 'unsettled'
          END
        ) AS settlement_status,
        pus.settlement_id,
        pus.settled_at,
        COALESCE(pus.created_at, CURRENT_TIMESTAMP) AS created_at,
        COALESCE(pus.updated_at, CURRENT_TIMESTAMP) AS updated_at
      FROM projects p
      CROSS JOIN users u
      LEFT JOIN project_user_status pus ON pus.project_id = p.id AND pus.user_id = u.id
      LEFT JOIN wage_settlements ws ON
        ws.user_id = u.id
        AND (ws.project_id = p.id OR ws.project_ids::jsonb @> to_jsonb(p.id))
      WHERE u.role IN ('admin', 'documenter');
    `,
    down: `DROP VIEW IF EXISTS v_project_user_settlement_status;`
  },
  {
    version: 'V1.7',
    description: '创建AI模块表（材料分类/材料参数/AI对话历史/知识库文档分块）',
    up: `
      -- 1. 材料分类表
      CREATE TABLE IF NOT EXISTS material_categories (
        id SERIAL PRIMARY KEY,
        name VARCHAR(50) NOT NULL UNIQUE,
        description TEXT,
        sort_order INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- 2. 材料参数表（知识库核心，排料引擎从此表读取材料数据）
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

      -- 3. AI对话历史表
      CREATE TABLE IF NOT EXISTS ai_chat_history (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        session_id VARCHAR(50) NOT NULL,
        role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
        content TEXT NOT NULL,
        intent VARCHAR(50),               -- 意图标签
        metadata JSONB,                   -- 附加元数据
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
      );

      -- 4. 知识库文档分块表（RAG检索核心）
      --    embedding列: 优先使用vector(1536)，pgvector不可用时降级为TEXT
      --    使用DO块动态创建，确保兼容所有环境
      DO $$
      BEGIN
        -- 尝试创建pgvector扩展（如果可用）
        BEGIN
          CREATE EXTENSION IF NOT EXISTS vector;
        EXCEPTION WHEN OTHERS THEN
          RAISE NOTICE 'pgvector扩展不可用，embedding列将使用TEXT类型，向量搜索功能降级为关键词搜索';
        END;

        -- 根据vector类型是否存在，选择不同的列类型创建表
        IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector') THEN
          -- pgvector可用：embedding列使用vector(1536)类型
          EXECUTE 'CREATE TABLE IF NOT EXISTS ai_knowledge_chunks (
            id SERIAL PRIMARY KEY,
            source_type VARCHAR(20) NOT NULL DEFAULT ''manual'',
            source_id INTEGER,
            title VARCHAR(200),
            content TEXT NOT NULL,
            chunk_index INTEGER NOT NULL DEFAULT 0,
            embedding vector(1536),
            metadata JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )';
        ELSE
          -- pgvector不可用：embedding列降级为TEXT类型
          EXECUTE 'CREATE TABLE IF NOT EXISTS ai_knowledge_chunks (
            id SERIAL PRIMARY KEY,
            source_type VARCHAR(20) NOT NULL DEFAULT ''manual'',
            source_id INTEGER,
            title VARCHAR(200),
            content TEXT NOT NULL,
            chunk_index INTEGER NOT NULL DEFAULT 0,
            embedding TEXT,
            metadata JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )';
        END IF;
      END $$;

      -- AI模块索引
      CREATE INDEX IF NOT EXISTS idx_material_categories_name ON material_categories(name);
      CREATE INDEX IF NOT EXISTS idx_material_params_category_id ON material_params(category_id);
      CREATE INDEX IF NOT EXISTS idx_material_params_active ON material_params(is_active);
      CREATE INDEX IF NOT EXISTS idx_ai_chat_history_user_id ON ai_chat_history(user_id);
      CREATE INDEX IF NOT EXISTS idx_ai_chat_history_session_id ON ai_chat_history(session_id);
      CREATE INDEX IF NOT EXISTS idx_ai_chat_history_created_at ON ai_chat_history(created_at DESC);
      CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_title ON ai_knowledge_chunks(title);
      CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_source ON ai_knowledge_chunks(source_type, source_id);
      CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_content ON ai_knowledge_chunks USING gin(to_tsvector('simple', content));

      -- 插入默认材料分类（如果不存在）
      INSERT INTO material_categories (name, description, sort_order) VALUES
        ('石膏板', '吊顶石膏板材料', 1),
        ('铝扣板', '铝扣板材料', 2),
        ('龙骨', '吊顶龙骨材料', 3),
        ('配件', '吊顶配件', 4)
      ON CONFLICT (name) DO NOTHING;
    `,
    down: `
      DROP TABLE IF EXISTS ai_knowledge_chunks CASCADE;
      DROP TABLE IF EXISTS ai_chat_history CASCADE;
      DROP TABLE IF EXISTS material_params CASCADE;
      DROP TABLE IF EXISTS material_categories CASCADE;
    `,
    tables: ['material_categories', 'material_params', 'ai_chat_history', 'ai_knowledge_chunks']
  },
  {
    version: 'V1.8',
    description: '允许结算实付金额为负数（预支可超出工程总额），移除actual_amount非负CHECK约束',
    up: `
      ALTER TABLE wage_settlements DROP CONSTRAINT IF EXISTS ck_settlement_actual;
      ALTER TABLE wage_settlement_snapshots DROP CONSTRAINT IF EXISTS ck_snapshot_actual;
    `,
    down: `
      ALTER TABLE wage_settlements ADD CONSTRAINT ck_settlement_actual CHECK (actual_amount >= 0);
      ALTER TABLE wage_settlement_snapshots ADD CONSTRAINT ck_snapshot_actual CHECK (actual_amount >= 0);
    `,
    tables: []
  },
  {
    version: 'V1.9',
    description: '性能优化：添加缺失索引 + 视图改为物化视图（解决大数据量下统计/列表页卡顿）',
    up: `
      -- 1. wage_settlements.confirmed 缺失索引（getIncomeStatistics 多处 WHERE confirmed = true/false）
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_confirmed ON wage_settlements(confirmed);
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_user_confirmed ON wage_settlements(user_id, confirmed);

      -- 2. wage_settlements.project_ids JSONB 列加 GIN 索引（视图 JOIN 条件 @> 操作可走索引）
      CREATE INDEX IF NOT EXISTS idx_wage_settlements_project_ids_gin ON wage_settlements USING GIN(project_ids);

      -- 3. wage_distributions 部分索引（卡片4: WHERE settlement_id IS NOT NULL + user_id + created_at 过滤）
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_user_settled_year
        ON wage_distributions(user_id, created_at)
        WHERE settlement_id IS NOT NULL;

      -- 4. wage_distributions 组合索引（结算历史查询: user_id + settlement_id 过滤）
      CREATE INDEX IF NOT EXISTS idx_wage_distributions_user_settlement
        ON wage_distributions(user_id, settlement_id);

      -- 5. 将 v_project_user_settlement_status 视图改造为物化视图
      --    原视图每次被 JOIN 都重新计算（CROSS JOIN users + JSONB @> + EXISTS 子查询），数据量大时极慢
      --    物化视图缓存计算结果，通过 REFRESH CONCURRENTLY 异步刷新
      DROP VIEW IF EXISTS v_project_user_settlement_status;
      -- 注意：使用 DROP + CREATE 而非 CREATE IF NOT EXISTS
      --       因为上次迁移可能 CREATE 成功但创建唯一索引失败，IF NOT EXISTS 会跳过重建导致定义陈旧
      DROP MATERIALIZED VIEW IF EXISTS mv_project_user_settlement_status;

      CREATE MATERIALIZED VIEW mv_project_user_settlement_status AS
      -- 施工人员的结算状态
      SELECT
        ROW_NUMBER() OVER (ORDER BY p.id, u.id) AS id,
        p.id AS project_id,
        u.id AS user_id,
        COALESCE(
          pus.settlement_status,
          CASE
            WHEN ws.id IS NOT NULL THEN 'settled'
            WHEN p.status = 'completed' THEN 'settling'
            WHEN EXISTS (
              SELECT 1 FROM subprojects sp
              WHERE sp.project_id = p.id
              AND sp.status = 'completed'
            ) THEN 'settling'
            ELSE 'unsettled'
          END
        ) AS settlement_status,
        COALESCE(pus.settlement_id, ws.id) AS settlement_id,
        COALESCE(pus.settled_at, ws.settled_at) AS settled_at,
        COALESCE(pus.created_at, CURRENT_TIMESTAMP) AS created_at,
        COALESCE(pus.updated_at, CURRENT_TIMESTAMP) AS updated_at
      FROM projects p
      JOIN project_workers pw ON pw.project_id = p.id
      JOIN users u ON u.id = pw.user_id
      LEFT JOIN project_user_status pus ON pus.project_id = p.id AND pus.user_id = u.id
      -- 关键修复：用 LATERAL + LIMIT 1 取最新一条结算单
      -- 原因：wage_settlements 的 UNIQUE 约束是 (user_id, settled_at, settlement_no)，
      --       一个 user 对一个 project 可能有多条结算单（单工程结算 + 多工程结算），
      --       直接 LEFT JOIN 会产生一对多行膨胀，导致 (project_id, user_id) 重复，
      --       唯一索引 idx_mv_puss_project_user 创建失败
      LEFT JOIN LATERAL (
        SELECT id, settled_at
        FROM wage_settlements
        WHERE user_id = u.id
          AND (project_id = p.id OR project_ids::jsonb @> to_jsonb(p.id))
        ORDER BY settled_at DESC NULLS LAST, id DESC
        LIMIT 1
      ) ws ON true
      UNION
      -- 管理员和资料员可以查看所有工程
      -- 注意1：排除已经是该工程施工人员的管理员/资料员，避免与第一部分数据重叠
      --        重叠会导致 (project_id, user_id) 重复，唯一索引创建失败
      -- 注意2：第二部分 id 加 100000 偏移，避免与第一部分 ROW_NUMBER 冲突
      --        （业务规模不会超过 10 万条施工人员记录，偏移量足够安全）
      SELECT
        ROW_NUMBER() OVER (ORDER BY p.id, u.id) + 100000 AS id,
        p.id AS project_id,
        u.id AS user_id,
        COALESCE(
          pus.settlement_status,
          CASE
            WHEN ws.id IS NOT NULL THEN 'settled'
            WHEN p.status = 'completed' THEN 'settling'
            WHEN EXISTS (
              SELECT 1 FROM subprojects sp
              WHERE sp.project_id = p.id
              AND sp.status = 'completed'
            ) THEN 'settling'
            ELSE 'unsettled'
          END
        ) AS settlement_status,
        COALESCE(pus.settlement_id, ws.id) AS settlement_id,
        COALESCE(pus.settled_at, ws.settled_at) AS settled_at,
        COALESCE(pus.created_at, CURRENT_TIMESTAMP) AS created_at,
        COALESCE(pus.updated_at, CURRENT_TIMESTAMP) AS updated_at
      FROM projects p
      CROSS JOIN users u
      LEFT JOIN project_user_status pus ON pus.project_id = p.id AND pus.user_id = u.id
      LEFT JOIN LATERAL (
        SELECT id, settled_at
        FROM wage_settlements
        WHERE user_id = u.id
          AND (project_id = p.id OR project_ids::jsonb @> to_jsonb(p.id))
        ORDER BY settled_at DESC NULLS LAST, id DESC
        LIMIT 1
      ) ws ON true
      WHERE u.role IN ('admin', 'documenter')
        AND NOT EXISTS (
          SELECT 1 FROM project_workers pw2
          WHERE pw2.project_id = p.id AND pw2.user_id = u.id
        )
      WITH DATA;

      -- 物化视图的唯一索引（REFRESH CONCURRENTLY 必须）
      CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_puss_id ON mv_project_user_settlement_status(id);
      CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_puss_project_user ON mv_project_user_settlement_status(project_id, user_id);
      CREATE INDEX IF NOT EXISTS idx_mv_puss_user_status ON mv_project_user_settlement_status(user_id, settlement_status);
      CREATE INDEX IF NOT EXISTS idx_mv_puss_project_status ON mv_project_user_settlement_status(project_id, settlement_status);

      -- 兼容层：创建普通视图指向物化视图，保持原代码中 v_project_user_settlement_status 引用不变
      CREATE OR REPLACE VIEW v_project_user_settlement_status AS
        SELECT * FROM mv_project_user_settlement_status;
    `,
    down: `
      DROP VIEW IF EXISTS v_project_user_settlement_status;
      DROP MATERIALIZED VIEW IF EXISTS mv_project_user_settlement_status;
      DROP INDEX IF EXISTS idx_wage_settlements_confirmed;
      DROP INDEX IF EXISTS idx_wage_settlements_user_confirmed;
      DROP INDEX IF EXISTS idx_wage_settlements_project_ids_gin;
      DROP INDEX IF EXISTS idx_wage_distributions_user_settled_year;
      DROP INDEX IF EXISTS idx_wage_distributions_user_settlement;
    `,
    tables: ['mv_project_user_settlement_status']
  },
  {
    version: 'V2.0',
    description: '工程备注字段规范化：projects 表添加 remark 列，将原 description 数据迁移到 remark',
    up: `
      -- 1. 添加 remark 列（projects 表原本没有 remark 列，工程备注通过 description 列存储）
      ALTER TABLE projects ADD COLUMN IF NOT EXISTS remark TEXT;

      -- 2. 数据迁移：将现有 description 列的备注内容复制到 remark 列
      --    背景：V2.0 之前 createProject 将前端传入的 remark 存入 description 列
      --    V2.0 之后 createProject 直接存入 remark 列，description 列不再用于存储备注
      UPDATE projects SET remark = description WHERE remark IS NULL AND description IS NOT NULL;
    `,
    down: `
      ALTER TABLE projects DROP COLUMN IF EXISTS remark;
    `,
    tables: ['projects']
  }
];

// ===================== 5. 检查迁移是否已应用 =====================
const isMigrationApplied = async (client, version) => {
  try {
    const result = await client.query(
      'SELECT 1 FROM db_versions WHERE version = $1',
      [version]
    );
    return result.rows.length > 0;
  } catch (error) {
    // 如果表不存在，说明是全新数据库，迁移未应用
    if (error.code === '42P01') {
      return false;
    }
    throw error;
  }
};

/**
 * 校验迁移创建的关键表是否真实存在
 * 防止"版本记录存在但表不存在"的脏数据场景（如事务提交后数据库崩溃、手动误删表等）
 *
 * @param {object} client - pg Client
 * @param {string[]} tables - 该迁移创建的表名数组
 * @returns {Promise<boolean>} true=表都存在，false=有表缺失
 */
const verifyMigrationObjects = async (client, tables) => {
  if (!tables || tables.length === 0) {
    return true;
  }
  const result = await client.query(
    `SELECT tablename FROM pg_tables WHERE tablename = ANY($1)`,
    [tables]
  );
  const existingTables = result.rows.map(r => r.tablename);
  const missingTables = tables.filter(t => !existingTables.includes(t));
  if (missingTables.length > 0) {
    log.warn(`发现缺失的表: ${missingTables.join(', ')}（版本记录存在但表不存在，将重新执行迁移）`);
    return false;
  }
  return true;
};

/**
 * 清理指定版本的迁移记录（用于脏数据修复）
 * @param {object} client - pg Client
 * @param {string} version - 迁移版本号
 */
const cleanDirtyMigrationRecord = async (client, version) => {
  await client.query('DELETE FROM db_versions WHERE version = $1', [version]);
  log.warn(`已清理版本 ${version} 的脏记录`);
};

// ===================== 6. 记录迁移版本 =====================
const recordMigration = async (client, version, description) => {
  await client.query(
    'INSERT INTO db_versions (version, description) VALUES ($1, $2)',
    [version, description]
  );
};

// ===================== 7. 执行增量迁移 =====================
const runMigrations = async () => {
  // 先进行连接预测试
  const connectionOk = await testConnection();
  if (!connectionOk) {
    process.exit(1);
  }

  let client;
  try {
    client = await pool.connect();
    if (isProduction) {
      log.info('生产环境模式：SSL已启用');
    }

    log.info('开始执行增量数据库迁移...');
    log.info(`共 ${MIGRATIONS.length} 个迁移版本需要检查`);

    let appliedCount = 0;
    let skippedCount = 0;

    for (const migration of MIGRATIONS) {
      const { version, description, up, tables } = migration;

      // 检查是否已应用
      const applied = await isMigrationApplied(client, version);

      if (applied) {
        // 防御性校验：版本记录存在，但关键表是否真的存在？
        // 防止"事务回滚但版本记录留下"或"手动误删表"导致的脏数据
        if (tables && tables.length > 0) {
          const objectsValid = await verifyMigrationObjects(client, tables);
          if (!objectsValid) {
            log.warn(`[${version}] 版本记录存在但表缺失，清理脏记录后将重新执行: ${description}`);
            await cleanDirtyMigrationRecord(client, version);
            // 不再 continue，继续往下执行迁移
          } else {
            log.info(`[${version}] 已应用，跳过: ${description}`);
            skippedCount++;
            continue;
          }
        } else {
          log.info(`[${version}] 已应用，跳过: ${description}`);
          skippedCount++;
          continue;
        }
      }

      // 执行迁移
      log.info(`[${version}] 开始应用: ${description}`);
      await client.query('BEGIN');

      try {
        await client.query(up);
        await recordMigration(client, version, description);
        await client.query('COMMIT');
        log.success(`[${version}] 应用成功: ${description}`);
        appliedCount++;
      } catch (error) {
        await client.query('ROLLBACK');
        log.error(`[${version}] 应用失败: ${error.message}`);
        throw error;
      }
    }

    // 插入默认用户（V1.5之后单独处理）
    await insertDefaultUsers(client);

    log.success(`数据库迁移完成！已应用: ${appliedCount}，已跳过: ${skippedCount}`);

    if (appliedCount > 0 && DEFAULT_USERS.length > 0) {
      log.warn(`预置用户默认密码：${DEFAULT_PASSWORD}，请登录后及时修改！`);
    }

  } catch (error) {
    log.error(`数据库迁移失败: ${error.message}`);
    throw error;
  } finally {
    if (client) {
      try {
        client.release();
        log.info('数据库连接已释放');
      } catch (err) {
        log.error(`释放数据库连接失败: ${err.message}`);
      }
    }
    try {
      await pool.end();
      log.info('数据库连接池已关闭');
    } catch (err) {
      log.error(`关闭数据库连接池失败: ${err.message}`);
    }
  }
};

// ===================== 8. 插入默认用户 =====================

// 历史 bug 记录：早期 init-db.js 使用的默认 hash 实际不对应密码 990066
// 此处维护错误 hash 列表，insertDefaultUsers 会自动检测并修复为正确 hash
const LEGACY_INVALID_PASSWORD_HASHES = [
  '$2b$10$P3wghHsPlXTX.IGPrxMmAeKNyeTHFBG6CKkpNFHnT1oPv.pFTHFpS'
];

const insertDefaultUsers = async (client) => {
  if (DEFAULT_USERS.length === 0) {
    return;
  }

  log.info('检查默认用户...');
  let insertedCount = 0;

  // 自动修复历史错误 hash：将旧默认 hash 更新为当前正确 hash
  // 这样已部署但无法登录的环境只需重新运行 init-db 即可修复，无需手动 SQL
  try {
    const fixResult = await client.query(
      `UPDATE users
       SET password = $1, updated_at = CURRENT_TIMESTAMP
       WHERE password = ANY($2::text[])
       RETURNING username`,
      [DEFAULT_PASSWORD_HASH, LEGACY_INVALID_PASSWORD_HASHES]
    );
    if (fixResult.rows.length > 0) {
      log.warn(`检测到 ${fixResult.rows.length} 个用户使用旧错误hash，已自动修复为默认密码 ${DEFAULT_PASSWORD}：`);
      fixResult.rows.forEach(row => log.warn(`  - ${row.username}`));
    }
  } catch (error) {
    log.warn(`  - 修复历史错误hash失败: ${error.message}`);
  }

  for (const user of DEFAULT_USERS) {
    try {
      const result = await client.query(
        'SELECT 1 FROM users WHERE username = $1',
        [user.username]
      );

      if (result.rows.length === 0) {
        await client.query(
          `INSERT INTO users (username, password, nickname, phone, role)
           VALUES ($1, $2, $3, $4, $5)
           ON CONFLICT (username) DO NOTHING`,
          [user.username, DEFAULT_PASSWORD_HASH, user.nickname, user.phone, user.role]
        );
        insertedCount++;
        log.info(`  - 创建用户: ${user.username} (${user.role})`);
      }
    } catch (error) {
      log.warn(`  - 用户 ${user.username} 处理失败: ${error.message}`);
    }
  }

  if (insertedCount > 0) {
    log.success(`默认用户创建完成，新增 ${insertedCount} 个用户`);
  }
};

// ===================== 9. 回滚迁移（可选） =====================
const rollbackMigration = async (targetVersion) => {
  // 先进行连接预测试
  const connectionOk = await testConnection();
  if (!connectionOk) {
    process.exit(1);
  }

  let client;
  try {
    client = await pool.connect();

    // 找到目标版本
    const targetIndex = MIGRATIONS.findIndex(m => m.version === targetVersion);
    if (targetIndex === -1) {
      log.error(`未找到版本: ${targetVersion}`);
      log.info(`可用版本: ${MIGRATIONS.map(m => m.version).join(', ')}`);
      return;
    }

    // 获取已应用的迁移版本
    const appliedMigrations = await client.query(
      'SELECT version FROM db_versions ORDER BY id'
    );
    const appliedVersions = appliedMigrations.rows.map(r => r.version);

    // 检查目标版本是否已应用
    if (!appliedVersions.includes(targetVersion)) {
      log.error(`版本 ${targetVersion} 尚未应用，无需回滚`);
      log.info(`已应用版本: ${appliedVersions.join(', ') || '无'}`);
      return;
    }

    // 检查版本顺序：确保目标版本之后的所有版本都已应用
    const versionsToRollback = [];
    for (const row of appliedMigrations.rows.reverse()) {
      if (row.version === targetVersion) {
        break;
      }
      versionsToRollback.push(row.version);
    }

    if (versionsToRollback.length === 0) {
      log.info(`版本 ${targetVersion} 是最新版本，无内容需要回滚`);
      return;
    }

    log.info(`将回滚以下版本: ${versionsToRollback.join(', ')}`);

    // 按顺序执行回滚
    for (const version of versionsToRollback) {
      const migration = MIGRATIONS.find(m => m.version === version);
      if (migration && migration.down) {
        log.info(`[${version}] 开始回滚: ${migration.description}`);
        await client.query('BEGIN');
        try {
          await client.query(migration.down);
          await client.query('DELETE FROM db_versions WHERE version = $1', [version]);
          await client.query('COMMIT');
          log.success(`[${version}] 回滚成功`);
        } catch (error) {
          await client.query('ROLLBACK');
          log.error(`[${version}] 回滚失败: ${error.message}`);
          throw error;
        }
      } else if (migration && !migration.down) {
        log.warn(`[${version}] 缺少回滚脚本，跳过`);
      } else {
        log.warn(`[${version}] 未找到迁移定义，跳过`);
      }
    }

    log.success(`回滚完成，当前版本: ${targetVersion}`);

  } catch (error) {
    log.error(`回滚失败: ${error.message}`);
    throw error;
  } finally {
    if (client) {
      client.release();
    }
    await pool.end();
  }
};

// ===================== 10. 执行迁移 =====================
const args = process.argv.slice(2);
const command = args[0];

if (command === 'rollback') {
  const targetVersion = args[1];
  if (!targetVersion) {
    log.error('请指定回滚目标版本');
    log.info('使用方法: node init-db.js rollback <版本号>');
    process.exit(1);
  }
  log.info(`开始回滚到版本: ${targetVersion}`);
  rollbackMigration(targetVersion).catch(err => {
    log.error(`回滚流程异常终止: ${err.message}`);
    process.exit(1);
  });
} else {
  log.info('开始数据库增量迁移...');
  runMigrations().catch(err => {
    log.error(`迁移流程异常终止: ${err.message}`);
    process.exit(1);
  });
}
