/**
 * 旧库 → 新库 数据迁移脚本（Node.js 版本 v2）
 *
 * v2 修复：
 *   - 修复多行 INSERT 语句解析问题（pg_dump --column-inserts 生成的 INSERT 跨多行）
 *   - 修复语句类型识别（trim 后再判断前缀）
 *   - 增加详细的导入计数（按表名统计）
 *   - 失败时输出完整错误语句
 *
 * 使用方法：
 *   docker exec -it <容器名> node scripts/migrate-from-old.js [导出文件路径]
 *
 * 默认导出文件路径: /app/scripts/old_data_dump.sql
 */

const path = require('path');
const fs = require('fs');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

const { Pool } = require('pg');

// ===================== 日志工具 =====================
const log = {
  info: (msg) => console.log(`[${new Date().toISOString()}] [INFO] ${msg}`),
  success: (msg) => console.log(`[${new Date().toISOString()}] [SUCCESS] ${msg}`),
  warn: (msg) => console.warn(`[${new Date().toISOString()}] [WARN] ${msg}`),
  error: (msg) => console.error(`[${new Date().toISOString()}] [ERROR] ${msg}`)
};

// ===================== 配置 =====================
const DUMP_FILE = process.argv[2] || '/app/scripts/old_data_dump.sql';

const pool = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'salary_system',
  password: process.env.DB_PASSWORD,
  port: parseInt(process.env.DB_PORT, 10) || 5432,
  max: 5,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000
});

// ===================== SQL 语句解析 =====================
/**
 * 解析 SQL 导出文件为独立语句数组
 *
 * pg_dump --column-inserts 输出格式：
 *   -- 注释行
 *   INSERT INTO table_name (col1, col2, ...) VALUES
 *     (val1, val2, ...);
 *   INSERT INTO table_name (col1, col2, ...) VALUES
 *     (val1, val2, ...);
 *
 * 解析要点：
 *   1. 按分号分割语句，但需跳过字符串内的分号（如备注中的分号）
 *   2. 多行 INSERT 的 VALUES 在下一行，需合并到同一条语句
 *   3. 过滤注释行（以 -- 开头）和空行
 *   4. trim 后再判断语句类型
 *
 * @param {string} content - SQL 文件内容
 * @returns {string[]} 独立 SQL 语句数组
 */
function parseSqlStatements(content) {
  const statements = [];
  let currentStatement = '';
  let inString = false;

  for (let i = 0; i < content.length; i++) {
    const char = content[i];

    // 处理单引号字符串（PostgreSQL 用 '' 转义单引号）
    if (char === "'" && content[i - 1] !== '\\') {
      inString = !inString;
    }

    currentStatement += char;

    // 遇到分号且不在字符串内，视为语句结束
    if (char === ';' && !inString) {
      // 移除语句内的注释行（以 -- 开头的整行）
      const lines = currentStatement.split('\n');
      const codeLines = lines.filter(line => {
        const trimmed = line.trim();
        // 保留有内容的代码行，过滤纯注释行（以--开头）和空行
        return trimmed && !trimmed.startsWith('--');
      });
      const cleanStatement = codeLines.join(' ').trim();

      if (cleanStatement) {
        statements.push(cleanStatement);
      }
      currentStatement = '';
    }
  }

  return statements;
}

/**
 * 识别 SQL 语句类型
 * @param {string} stmt - SQL 语句
 * @returns {string} 语句类型: INSERT/SET/SELECT/OTHER
 */
function getStatementType(stmt) {
  const upper = stmt.toUpperCase().trim();
  if (upper.startsWith('INSERT INTO')) return 'INSERT';
  if (upper.startsWith('SET ')) return 'SET';
  if (upper.startsWith('SELECT ')) return 'SELECT';
  return 'OTHER';
}

/**
 * 从 INSERT 语句中提取表名
 * 用于按表统计导入数量
 * @param {string} stmt - INSERT 语句
 * @returns {string|null} 表名
 */
function getTableNameFromInsert(stmt) {
  const match = stmt.match(/^INSERT\s+INTO\s+(\w+)/i);
  return match ? match[1] : null;
}

// ===================== 主迁移流程 =====================
async function migrate() {
  let client;

  try {
    log.info('==========================================');
    log.info('  旧库 → 新库 数据迁移（Node.js 版本 v2）');
    log.info('==========================================');

    // ========== 1. 前置检查 ==========
    if (!fs.existsSync(DUMP_FILE)) {
      throw new Error(`导出文件不存在: ${DUMP_FILE}\n请先在腾讯云执行 pg_dump 导出数据，并复制到容器内`);
    }

    const fileSize = fs.statSync(DUMP_FILE).size;
    log.info(`导出文件: ${DUMP_FILE} (${(fileSize / 1024 / 1024).toFixed(2)} MB)`);

    // 测试数据库连接
    log.info('测试新库连接...');
    client = await pool.connect();
    const versionResult = await client.query('SELECT version()');
    log.success(`新库连接成功: ${versionResult.rows[0].version.split(' ').slice(0, 2).join(' ')}`);

    // ========== 2. 清空新库已有数据 ==========
    log.info('');
    log.info('[步骤 1] 清空新库已有数据（字典+默认用户，避免主键冲突）...');

    await client.query(`
      TRUNCATE TABLE 
        messages,
        subproject_transfers,
        wage_advances,
        files,
        wage_distributions,
        wage_settlement_snapshots,
        project_user_status,
        wage_settlements,
        project_history,
        subprojects,
        project_workers,
        projects,
        wage_distribution_types,
        action_types,
        construction_plans,
        space_types,
        users
      CASCADE
    `);
    log.success('新库已清空');

    // ========== 3. 读取并解析导出文件 ==========
    log.info('');
    log.info('[步骤 2] 读取导出文件...');

    const sqlContent = fs.readFileSync(DUMP_FILE, 'utf8');
    log.info(`文件内容长度: ${sqlContent.length} 字符`);

    const statements = parseSqlStatements(sqlContent);
    log.info(`解析出 ${statements.length} 条 SQL 语句`);

    // 按类型统计
    const typeCount = { INSERT: 0, SET: 0, SELECT: 0, OTHER: 0 };
    const tableCount = {}; // 按表名统计 INSERT 数量
    statements.forEach(stmt => {
      const type = getStatementType(stmt);
      typeCount[type]++;

      if (type === 'INSERT') {
        const tableName = getTableNameFromInsert(stmt);
        if (tableName) {
          tableCount[tableName] = (tableCount[tableName] || 0) + 1;
        }
      }
    });

    log.info(`  - INSERT 语句: ${typeCount.INSERT} 条`);
    log.info(`  - SET 语句: ${typeCount.SET} 条`);
    log.info(`  - SELECT 语句: ${typeCount.SELECT} 条`);
    log.info(`  - 其他: ${typeCount.OTHER} 条`);

    if (typeCount.INSERT === 0) {
      log.error('未解析到任何 INSERT 语句！请检查导出文件格式');
      log.error('提示：导出时必须使用 --column-inserts 参数');
      log.error('示例: pg_dump --data-only --column-inserts --no-owner --exclude-table=db_versions > dump.sql');
      throw new Error('导出文件无有效 INSERT 语句');
    }

    log.info('');
    log.info('按表统计 INSERT 数量:');
    Object.entries(tableCount).forEach(([table, count]) => {
      log.info(`  ${table.padEnd(30)} ${count} 条`);
    });

    // ========== 4. 执行导入 ==========
    log.info('');
    log.info('[步骤 3] 开始导入数据...');

    let executedCount = 0;
    let failedCount = 0;
    const failedStatements = [];
    const importedTableCount = {}; // 实际导入成功的数量（按表）

    // 使用事务确保导入原子性
    await client.query('BEGIN');

    try {
      for (let i = 0; i < statements.length; i++) {
        const stmt = statements[i];
        const type = getStatementType(stmt);

        try {
          await client.query(stmt);
          executedCount++;

          // 统计成功导入的 INSERT（按表）
          if (type === 'INSERT') {
            const tableName = getTableNameFromInsert(stmt);
            if (tableName) {
              importedTableCount[tableName] = (importedTableCount[tableName] || 0) + 1;
            }
          }

          // 进度提示（每1000条输出一次）
          if (executedCount % 1000 === 0) {
            log.info(`  进度: ${executedCount}/${statements.length} (${(executedCount / statements.length * 100).toFixed(1)}%)`);
          }
        } catch (err) {
          // INSERT 失败是严重问题，需要记录
          if (type === 'INSERT') {
            failedCount++;
            failedStatements.push({
              index: i + 1,
              error: err.message,
              statement: stmt.substring(0, 200),
              table: getTableNameFromInsert(stmt)
            });
            log.warn(`  语句 ${i + 1} 执行失败: ${err.message}`);
            log.warn(`    预览: ${stmt.substring(0, 100)}...`);
          }
          // SET/SELECT 等辅助语句失败静默跳过
        }
      }

      await client.query('COMMIT');
      log.success(`数据导入完成: 成功 ${executedCount - failedCount} 条, 失败 ${failedCount} 条`);

      // 输出按表导入统计
      if (Object.keys(importedTableCount).length > 0) {
        log.info('');
        log.info('按表导入成功统计:');
        Object.entries(importedTableCount).forEach(([table, count]) => {
          log.info(`  ${table.padEnd(30)} ${count} 条`);
        });
      }
    } catch (err) {
      await client.query('ROLLBACK');
      throw new Error(`导入事务失败，已回滚: ${err.message}`);
    }

    // 输出失败详情
    if (failedStatements.length > 0) {
      log.warn('');
      log.warn(`失败的 INSERT 语句详情（共 ${failedStatements.length} 条）:`);
      failedStatements.slice(0, 10).forEach(s => {
        log.warn(`  语句 ${s.index} [${s.table}]: ${s.error}`);
      });
      if (failedStatements.length > 10) {
        log.warn(`  ... 还有 ${failedStatements.length - 10} 条未显示`);
      }
    }

    // ========== 5. 重置所有序列 ==========
    log.info('');
    log.info('[步骤 4] 重置所有表序列到最大ID...');

    const sequenceQuery = `
      SELECT 
        c.relname AS table_name,
        a.attname AS column_name,
        pg_get_serial_sequence(c.relname, a.attname) AS sequence_name
      FROM pg_class c
      JOIN pg_attribute a ON a.attrelid = c.oid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relkind = 'r'
        AND pg_get_serial_sequence(c.relname, a.attname) IS NOT NULL
      ORDER BY c.relname
    `;
    const sequenceResult = await client.query(sequenceQuery);
    log.info(`找到 ${sequenceResult.rows.length} 个序列需要重置`);

    for (const row of sequenceResult.rows) {
      const maxQuery = `SELECT COALESCE(MAX(${row.column_name}), 1) AS max_id FROM ${row.table_name}`;
      const maxResult = await client.query(maxQuery);
      const maxId = maxResult.rows[0].max_id;

      await client.query(`SELECT setval($1, $2, true)`, [row.sequence_name, maxId]);
      log.info(`  ✅ ${row.table_name}.${row.column_name} → ${maxId}`);
    }

    log.success('所有序列已重置');

    // ========== 6. 刷新物化视图 ==========
    log.info('');
    log.info('[步骤 5] 刷新物化视图...');

    try {
      await client.query('REFRESH MATERIALIZED VIEW CONCURRENTLY mv_project_user_settlement_status');
      log.success('物化视图已刷新（CONCURRENTLY 模式）');
    } catch (err) {
      log.warn(`CONCURRENTLY 刷新失败，降级为普通刷新: ${err.message}`);
      await client.query('REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
      log.success('物化视图已刷新（普通模式）');
    }

    // ========== 7. 数据校验 ==========
    log.info('');
    log.info('[步骤 6] 数据校验...');

    const tablesToVerify = [
      'users', 'projects', 'subprojects', 'project_workers',
      'wage_settlements', 'wage_distributions', 'wage_advances',
      'files', 'project_history', 'project_user_status',
      'wage_settlement_snapshots', 'space_types', 'construction_plans'
    ];

    log.info('');
    log.info('========== 数据量统计 ==========');
    let totalRecords = 0;
    for (const table of tablesToVerify) {
      const countResult = await client.query(`SELECT COUNT(*) AS count FROM ${table}`);
      const count = parseInt(countResult.rows[0].count, 10);
      totalRecords += count;
      console.log(`  ${table.padEnd(30)} ${count} 条`);
    }
    log.info(`  ${'总计'.padEnd(30)} ${totalRecords} 条`);

    // 金额校验
    log.info('');
    log.info('========== 金额校验 ==========');
    const amountChecks = [
      { name: '工程总额合计', query: 'SELECT COALESCE(SUM(total_amount), 0) AS total FROM projects' },
      { name: '子项目金额合计', query: 'SELECT COALESCE(SUM(amount), 0) AS total FROM subprojects' },
      { name: '结算单总额合计', query: 'SELECT COALESCE(SUM(total_amount), 0) AS total FROM wage_settlements' },
      { name: '预支总额合计', query: 'SELECT COALESCE(SUM(advance_amount), 0) AS total FROM wage_advances' }
    ];

    for (const check of amountChecks) {
      const result = await client.query(check.query);
      const total = parseFloat(result.rows[0].total).toFixed(2);
      console.log(`  ${check.name.padEnd(20)} ¥${total}`);
    }

    // ========== 完成 ==========
    log.info('');
    log.info('==========================================');
    log.success('  ✅ 迁移完成！');
    log.info('==========================================');
    log.info('');
    log.info('后续操作：');
    log.info('  1. 迁移附件文件到原路径（files.path 保持旧库路径）');
    log.info('  2. 重启后端服务');
    log.info('  3. 用旧库用户账号登录验证（如 喜临门 / 990066）');
    log.info('  4. 检查工程列表、结算记录、附件显示是否正常');

  } catch (err) {
    log.error(`迁移失败: ${err.message}`);
    if (err.stack) {
      log.error(err.stack);
    }
    process.exit(1);
  } finally {
    if (client) {
      client.release();
      log.info('数据库连接已释放');
    }
    await pool.end();
    log.info('数据库连接池已关闭');
  }
}

// 执行迁移
migrate();
