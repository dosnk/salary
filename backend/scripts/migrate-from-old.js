/**
 * 旧库 → 新库 数据迁移脚本（Node.js 版本 v3）
 *
 * v3 修复：
 *   - 支持 COPY ... FROM stdin 格式（pg_dump 默认格式，宝塔面板备份使用此格式）
 *   - 支持 INSERT INTO 格式（pg_dump --column-inserts 格式）
 *   - 自动检测导出文件格式
 *
 * COPY 格式说明：
 *   pg_dump 默认用 COPY 语句批量导出数据，格式如下：
 *
 *     COPY public.table_name (col1, col2, col3, ...) FROM stdin;
 *     1\tvalue1\tvalue2\tvalue3\n
 *     2\tvalue1\tvalue2\tvalue3\n
 *     \.
 *
 *   特殊标记：
 *     - \N  表示 NULL
 *     - \t  表示制表符（列分隔符）
 *     - \n  表示换行符（数据内的换行）
 *     - \r  表示回车
 *     - \\  表示反斜杠本身
 *     - \.  单独一行表示数据结束
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

// ===================== COPY 格式数据解析 =====================
/**
 * 解析 COPY 数据行（tab 分隔，含转义字符）
 *
 * PostgreSQL COPY 格式转义规则：
 *   \N  → NULL
 *   \t  → 制表符
 *   \n  → 换行符
 *   \r  → 回车
 *   \\  → 反斜杠
 *   其他 \x → x（保留原字符）
 *
 * @param {string} line - COPY 数据行（不含换行符）
 * @returns {Array} 解析后的值数组，NULL 用 null 表示
 */
function parseCopyDataLine(line) {
  // 按 tab 分隔列，但需处理转义的 \t
  // PostgreSQL COPY 格式中 \t 表示数据内的 tab，\N 表示 NULL
  const values = [];
  let current = '';
  let i = 0;

  while (i < line.length) {
    const char = line[i];

    if (char === '\t') {
      // 列分隔符
      values.push(parseCopyValue(current));
      current = '';
      i++;
    } else if (char === '\\' && i + 1 < line.length) {
      // 转义字符
      const next = line[i + 1];
      if (next === 'N') {
        current += '\u0000'; // 临时标记 NULL，稍后处理
        i += 2;
      } else if (next === 't') {
        current += '\t';
        i += 2;
      } else if (next === 'n') {
        current += '\n';
        i += 2;
      } else if (next === 'r') {
        current += '\r';
        i += 2;
      } else if (next === '\\') {
        current += '\\';
        i += 2;
      } else {
        // 其他转义保留原字符
        current += next;
        i += 2;
      }
    } else {
      current += char;
      i++;
    }
  }
  // 最后一列
  values.push(parseCopyValue(current));

  return values;
}

/**
 * 解析单个 COPY 值
 * @param {string} value - 原始值（已处理转义，但可能含 NULL 标记）
 * @returns {string|null} 解析后的值，NULL 返回 null
 */
function parseCopyValue(value) {
  if (value === '\u0000') {
    return null;
  }
  return value;
}

/**
 * 从 COPY 语句中提取表名和列名
 *
 * COPY 语句格式：
 *   COPY public.table_name (col1, col2, ...) FROM stdin;
 *   COPY public.table_name FROM stdin;  (无列名，表示所有列)
 *
 * @param {string} copyStmt - COPY 语句
 * @returns {{tableName: string, columns: string[]|null}}
 */
function parseCopyStatement(copyStmt) {
  // 提取表名：COPY public.table_name 或 COPY table_name
  const tableMatch = copyStmt.match(/COPY\s+(?:public\.)?(\w+)/i);
  const tableName = tableMatch ? tableMatch[1] : null;

  // 提取列名：(col1, col2, ...) 可选
  const columnsMatch = copyStmt.match(/\(([^)]+)\)/);
  let columns = null;
  if (columnsMatch) {
    columns = columnsMatch[1].split(',').map(c => c.trim());
  }

  return { tableName, columns };
}

/**
 * 将值数组转换为 SQL INSERT 语句
 *
 * @param {string} tableName - 表名
 * @param {string[]|null} columns - 列名数组（null 表示所有列）
 * @param {Array} values - 值数组
 * @returns {string} INSERT 语句
 */
function buildInsertStatement(tableName, columns, values) {
  // 处理列名
  const columnList = columns ? `(${columns.join(', ')})` : '';

  // 处理值（参数化）
  const placeholders = values.map((_, i) => `$${i + 1}`).join(', ');

  return {
    text: `INSERT INTO ${tableName} ${columnList} VALUES (${placeholders})`.replace(/\s+/g, ' ').trim(),
    values: values
  };
}

// ===================== SQL 文件解析 =====================
/**
 * 解析 SQL 导出文件，提取所有数据操作
 *
 * 支持两种格式：
 *   1. COPY ... FROM stdin 格式（pg_dump 默认，宝塔备份使用）
 *   2. INSERT INTO 格式（pg_dump --column-inserts）
 *
 * @param {string} content - SQL 文件内容
 * @returns {{inserts: Array, stats: object}} INSERT 语句数组和统计信息
 */
function parseSqlFile(content) {
  const inserts = []; // {text, values, table}
  const stats = {
    copyBlocks: 0,
    copyRows: 0,
    insertStatements: 0,
    setStatements: 0,
    otherStatements: 0,
    tables: {} // 按表统计行数
  };

  const lines = content.split('\n');
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // 跳过空行和注释
    if (!line.trim() || line.trim().startsWith('--')) {
      i++;
      continue;
    }

    // 检测 COPY 语句
    if (/^COPY\s+/i.test(line.trim())) {
      const { tableName, columns } = parseCopyStatement(line);

      if (!tableName) {
        log.warn(`无法解析 COPY 语句: ${line.substring(0, 100)}`);
        i++;
        continue;
      }

      stats.copyBlocks++;
      i++; // 移动到数据行

      // 读取 COPY 数据，直到遇到 \. 结束标记
      let rowCount = 0;
      while (i < lines.length && lines[i] !== '\\.') {
        const dataLine = lines[i];

        // 跳过空行（COPY 数据中的空行可能是数据，需根据列数判断）
        // 但通常 \. 之前的空行是数据行，需保留
        if (dataLine !== '\\.') {
          const values = parseCopyDataLine(dataLine);
          const insert = buildInsertStatement(tableName, columns, values);
          inserts.push({ ...insert, table: tableName });
          rowCount++;
          stats.copyRows++;
        }
        i++;
      }

      // 跳过 \. 结束标记
      if (i < lines.length && lines[i] === '\\.') {
        i++;
      }

      stats.tables[tableName] = (stats.tables[tableName] || 0) + rowCount;
      continue;
    }

    // 检测 INSERT 语句（可能跨多行，以分号结束）
    if (/^INSERT\s+INTO/i.test(line.trim())) {
      let fullStatement = line;
      // 如果不以分号结束，继续读取下一行
      while (i + 1 < lines.length && !fullStatement.trim().endsWith(';')) {
        i++;
        fullStatement += '\n' + lines[i];
      }

      // 提取表名
      const tableMatch = fullStatement.match(/INSERT\s+INTO\s+(?:public\.)?(\w+)/i);
      const tableName = tableMatch ? tableMatch[1] : 'unknown';

      inserts.push({
        text: fullStatement.replace(/\s+/g, ' ').trim(),
        values: null, // INSERT 语句无需参数化（已含字面值）
        table: tableName
      });
      stats.insertStatements++;
      stats.tables[tableName] = (stats.tables[tableName] || 0) + 1;
      i++;
      continue;
    }

    // 检测 SET 语句
    if (/^SET\s+/i.test(line.trim())) {
      stats.setStatements++;
      i++;
      continue;
    }

    // 其他语句（CREATE、ALTER、COMMENT等，数据迁移时跳过）
    stats.otherStatements++;
    i++;
  }

  return { inserts, stats };
}

// ===================== 主迁移流程 =====================
async function migrate() {
  let client;

  try {
    log.info('==========================================');
    log.info('  旧库 → 新库 数据迁移（Node.js 版本 v3）');
    log.info('  支持 COPY 和 INSERT 两种格式');
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
    log.info('[步骤 1] 清空新库已有数据（字典+默认用户+迁移版本表，避免主键冲突）...');

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
        users,
        db_versions
      CASCADE
    `);
    log.success('新库已清空');

    // ========== 3. 读取并解析导出文件 ==========
    log.info('');
    log.info('[步骤 2] 读取并解析导出文件...');

    const sqlContent = fs.readFileSync(DUMP_FILE, 'utf8');
    log.info(`文件内容长度: ${sqlContent.length} 字符`);

    const { inserts, stats } = parseSqlFile(sqlContent);

    log.info(`解析完成:`);
    log.info(`  - COPY 数据块: ${stats.copyBlocks} 个`);
    log.info(`  - COPY 数据行: ${stats.copyRows} 行`);
    log.info(`  - INSERT 语句: ${stats.insertStatements} 条`);
    log.info(`  - SET 语句: ${stats.setStatements} 条（已跳过）`);
    log.info(`  - 其他语句: ${stats.otherStatements} 条（已跳过）`);
    log.info(`  - 待导入数据: ${inserts.length} 条`);

    if (inserts.length === 0) {
      log.error('未解析到任何数据！请检查导出文件格式');
      throw new Error('导出文件无有效数据');
    }

    log.info('');
    log.info('按表统计待导入数量:');
    Object.entries(stats.tables).forEach(([table, count]) => {
      log.info(`  ${table.padEnd(30)} ${count} 条`);
    });

    // ========== 4. 执行导入 ==========
    log.info('');
    log.info('[步骤 3] 开始导入数据...');

    let executedCount = 0;
    let failedCount = 0;
    const failedStatements = [];
    const importedTableCount = {};

    // 每条 INSERT 独立事务，避免单条失败导致整个事务中止
    // （PostgreSQL 事务中一旦出错，后续所有语句都会被拒绝，必须 ROLLBACK 后重新 BEGIN）
    for (let i = 0; i < inserts.length; i++) {
      const { text, values, table } = inserts[i];

      try {
        await client.query('BEGIN');
        if (values) {
          // COPY 格式（参数化查询）
          await client.query(text, values);
        } else {
          // INSERT 格式（直接执行）
          await client.query(text);
        }
        await client.query('COMMIT');
        executedCount++;

        // 统计成功导入（按表）
        importedTableCount[table] = (importedTableCount[table] || 0) + 1;

        // 进度提示（每1000条输出一次）
        if (executedCount % 1000 === 0) {
          log.info(`  进度: ${executedCount}/${inserts.length} (${(executedCount / inserts.length * 100).toFixed(1)}%)`);
        }
      } catch (err) {
        // 单条失败：回滚当前事务，开下一条新事务
        try { await client.query('ROLLBACK'); } catch (e) { /* 忽略回滚错误 */ }
        failedCount++;
        failedStatements.push({
          index: i + 1,
          table: table,
          error: err.message,
          preview: text.substring(0, 150)
        });
        // 输出前10条失败的详细信息
        if (failedCount <= 10) {
          log.warn(`  语句 ${i + 1} [${table}] 失败: ${err.message}`);
          log.warn(`    预览: ${text.substring(0, 100)}...`);
          if (values) {
            log.warn(`    参数: ${JSON.stringify(values).substring(0, 200)}`);
          }
        }
      }
    }

    log.success(`数据导入完成: 成功 ${executedCount} 条, 失败 ${failedCount} 条`);

    // 输出按表导入统计
    if (Object.keys(importedTableCount).length > 0) {
      log.info('');
      log.info('按表导入成功统计:');
      Object.entries(importedTableCount).forEach(([table, count]) => {
        log.info(`  ${table.padEnd(30)} ${count} 条`);
      });
    }

    // 输出失败详情汇总
    if (failedStatements.length > 0) {
      log.warn('');
      log.warn(`失败的语句汇总（共 ${failedStatements.length} 条）:`);
      failedStatements.slice(0, 20).forEach(s => {
        log.warn(`  语句 ${s.index} [${s.table}]: ${s.error}`);
      });
      if (failedStatements.length > 20) {
        log.warn(`  ... 还有 ${failedStatements.length - 20} 条未显示`);
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
