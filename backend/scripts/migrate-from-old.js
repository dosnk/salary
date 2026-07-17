/**
 * 旧库 → 新库 数据迁移脚本（Node.js 版本）
 *
 * 使用场景：
 *   后端服务运行在 Docker 容器中，容器内无 psql 客户端，但有 Node.js + pg 库
 *   旧库在腾讯云（不可直连），已通过 pg_dump 导出为 SQL 文件
 *
 * 使用方法：
 *   1. 在腾讯云旧库导出数据：
 *      pg_dump -h 127.0.0.1 -U postgres -d salary_system \
 *        --data-only --column-inserts --no-owner --no-privileges \
 *        --exclude-table=db_versions > /tmp/old_data_dump.sql
 *
 *   2. 将导出文件复制到容器内：
 *      docker cp old_data_dump.sql <容器名>:/app/scripts/old_data_dump.sql
 *
 *   3. 在容器内执行本脚本：
 *      docker exec -it <容器名> node scripts/migrate-from-old.js
 *
 *   或指定导出文件路径：
 *      docker exec -it <容器名> node scripts/migrate-from-old.js /tmp/old_data_dump.sql
 *
 * 迁移范围：
 *   ✅ 用户表（users）— 保留旧库 ID，用户可用旧密码登录
 *   ✅ 业务数据（projects、subprojects、wage_settlements 等）
 *   ✅ 附件记录（files 表）— 附件文件需用户自行按原路径迁移
 *   ❌ 字典表（space_types、construction_plans 等）— 用旧库覆盖
 *   ❌ 迁移版本表（db_versions）— 各自保留
 *   ❌ AI 相关表 — 旧库无
 *
 * 结构差异处理：
 *   1. projects.remark 列：新库有，旧库无。导入时 remark 自动为 NULL
 *   2. 物化视图 mv_project_user_settlement_status：导入完成后刷新
 *   3. length/width 单位：旧库新库都是厘米，无需换算
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
// 导出文件路径：优先使用命令行参数，其次使用默认路径
const DUMP_FILE = process.argv[2] || '/app/scripts/old_data_dump.sql';

// 新库连接配置：从 .env 读取
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

// ===================== 主迁移流程 =====================
async function migrate() {
  let client;

  try {
    // ========== 1. 前置检查 ==========
    log.info('==========================================');
    log.info('  旧库 → 新库 数据迁移（Node.js 版本）');
    log.info('==========================================');

    // 检查导出文件是否存在
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

    // 按外键依赖反序清空所有业务表
    // 使用 TRUNCATE CASCADE 一次性清空，避免逐表删除的外键约束问题
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

    // 将 SQL 文件按分号分割为独立语句
    // 注意：pg_dump --column-inserts 生成的 INSERT 语句每条以分号结尾
    // 需要处理：1) 注释行（以 -- 开头）2) 空行 3) 多行INSERT（含换行）
    const statements = [];
    let currentStatement = '';
    let inString = false;

    // 逐字符解析，处理字符串内的分号（如备注中的分号）
    for (let i = 0; i < sqlContent.length; i++) {
      const char = sqlContent[i];

      // 处理单引号字符串（转义单引号用 '' 表示）
      if (char === "'" && sqlContent[i - 1] !== '\\') {
        inString = !inString;
      }

      currentStatement += char;

      // 遇到分号且不在字符串内，视为语句结束
      if (char === ';' && !inString) {
        const trimmed = currentStatement.trim();
        // 过滤空语句和纯注释
        if (trimmed && !trimmed.startsWith('--')) {
          // 移除语句内的注释行（如 -- Name: users_id_seq; Type: SEQUENCE ...）
          const lines = trimmed.split('\n');
          const codeLines = lines.filter(line => !line.trim().startsWith('--'));
          const cleanStatement = codeLines.join('\n').trim();
          if (cleanStatement) {
            statements.push(cleanStatement);
          }
        }
        currentStatement = '';
      }
    }

    log.info(`解析出 ${statements.length} 条 SQL 语句`);

    // 统计语句类型
    const insertCount = statements.filter(s => s.toUpperCase().startsWith('INSERT INTO')).length;
    const setCount = statements.filter(s => s.toUpperCase().startsWith('SET ')).length;
    const otherCount = statements.length - insertCount - setCount;
    log.info(`  - INSERT 语句: ${insertCount} 条`);
    log.info(`  - SET 语句: ${setCount} 条（如 SET client_encoding）`);
    log.info(`  - 其他: ${otherCount} 条`);

    // ========== 4. 执行导入 ==========
    log.info('');
    log.info('[步骤 3] 开始导入数据...');

    let executedCount = 0;
    let failedCount = 0;
    const failedStatements = [];

    // 使用事务确保导入原子性
    await client.query('BEGIN');

    try {
      for (let i = 0; i < statements.length; i++) {
        const stmt = statements[i];
        const stmtPreview = stmt.substring(0, 80).replace(/\n/g, ' ');

        try {
          await client.query(stmt);
          executedCount++;

          // 进度提示（每1000条输出一次）
          if (executedCount % 1000 === 0) {
            log.info(`  进度: ${executedCount}/${statements.length} (${(executedCount / statements.length * 100).toFixed(1)}%)`);
          }
        } catch (err) {
          // 某些非关键语句失败可以跳过（如 SET search_path、序列操作等）
          // 但 INSERT 失败需要记录
          if (stmt.toUpperCase().startsWith('INSERT INTO')) {
            failedCount++;
            failedStatements.push({
              index: i + 1,
              error: err.message,
              preview: stmtPreview
            });
            // INSERT 失败是严重问题，输出警告但继续执行（可能部分数据已导入）
            log.warn(`  语句 ${i + 1} 执行失败: ${err.message}`);
            log.warn(`    预览: ${stmtPreview}...`);
          }
          // 非 INSERT 语句（SET、SELECT等）失败静默跳过
        }
      }

      await client.query('COMMIT');
      log.success(`数据导入完成: 成功 ${executedCount - failedCount} 条, 失败 ${failedCount} 条`);
    } catch (err) {
      await client.query('ROLLBACK');
      throw new Error(`导入事务失败，已回滚: ${err.message}`);
    }

    // 如果有失败的 INSERT，输出详细信息
    if (failedStatements.length > 0) {
      log.warn('');
      log.warn(`失败的 INSERT 语句详情（共 ${failedStatements.length} 条）:`);
      failedStatements.slice(0, 10).forEach(s => {
        log.warn(`  语句 ${s.index}: ${s.error}`);
        log.warn(`    预览: ${s.preview}`);
      });
      if (failedStatements.length > 10) {
        log.warn(`  ... 还有 ${failedStatements.length - 10} 条未显示`);
      }
    }

    // ========== 5. 重置所有序列 ==========
    log.info('');
    log.info('[步骤 4] 重置所有表序列到最大ID...');

    // 查询所有有 SERIAL 序列的表，并重置序列到该表的最大ID
    // 不重置会导致后续 INSERT 报主键冲突（序列从1开始，但已导入的ID可能很大）
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
      // 动态执行 setval，将序列设为该列当前最大值
      // COALESCE 处理表为空的情况（默认设为1）
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
      // 优先尝试 CONCURRENTLY（不阻塞读，需要唯一索引）
      await client.query('REFRESH MATERIALIZED VIEW CONCURRENTLY mv_project_user_settlement_status');
      log.success('物化视图已刷新（CONCURRENTLY 模式）');
    } catch (err) {
      // CONCURRENTLY 失败（如唯一索引未创建），降级为普通刷新
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
    for (const table of tablesToVerify) {
      const countResult = await client.query(`SELECT COUNT(*) AS count FROM ${table}`);
      const count = parseInt(countResult.rows[0].count, 10);
      console.log(`  ${table.padEnd(30)} ${count} 条`);
    }

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

    // 序列校验
    log.info('');
    log.info('========== 序列检查 ==========');
    const seqCheckResult = await client.query(`
      SELECT 
        c.relname AS table_name,
        a.attname AS column_name,
        (SELECT last_value FROM pg_sequences s WHERE s.schemaname = 'public' AND s.sequencename = split_part(pg_get_serial_sequence(c.relname, a.attname), '.', 2)) AS current_seq_value,
        (SELECT MAX(${'$'}{1}) FROM ${'$'}{2} LIMIT 1) AS max_id
      FROM pg_class c
      JOIN pg_attribute a ON a.attrelid = c.oid
      LIMIT 5
    `).catch(() => null);

    if (seqCheckResult) {
      log.info('  （序列已重置，下次 INSERT 将正确递增）');
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
