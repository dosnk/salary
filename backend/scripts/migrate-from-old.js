/**
 * 旧库 → 新库 数据迁移脚本（Node.js 版本 v3）
 *
 * v3 修复：
 *   - 支持 COPY ... FROM stdin 格式（pg_dump 默认格式，宝塔面板备份使用此格式）
 *   - 支持 INSERT INTO 格式（pg_dump --column-inserts 格式）
 *   - 自动检测导出文件格式
 *
 * v3.1 安全加固：
 *   - 新增 --yes 安全护栏：生产环境（NODE_ENV=production）必须显式加 --yes 才可执行
 *   - 3 秒倒计时预警（可 Ctrl+C 取消），--force 或 --yes 跳过
 *   - 金额/数字字段保留字符串精度（避免 IEEE754 浮点损失）
 *   - length/width 单位换算改用字符串移位（"3.5"→"350" 而非 3.5*100）
 *   - INSERT 解析失败直接抛错阻断（消除 SQL 注入路径 + 单位换算漏跑）
 *   - 序列重置区分空表（is_called=false）与非空表
 *   - 删除源码中的样例账号密码
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
 *   # 开发/测试环境（3 秒倒计时后自动执行）
 *   docker exec -it <容器名> node scripts/migrate-from-old.js [导出文件路径]
 *
 *   # 生产环境（必须加 --yes 显式确认）
 *   docker exec -it <容器名> node scripts/migrate-from-old.js /path/to/dump.sql --yes
 *
 *   # 跳过倒计时
 *   docker exec -it <容器名> node scripts/migrate-from-old.js /path/to/dump.sql --force
 *
 * 默认导出文件路径: /app/scripts/old_data_dump.sql
 *
 * ⚠️  执行前请务必对新库做 pg_dump 备份！
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
// 命令行参数解析：区分文件路径与 flag
const cliArgs = process.argv.slice(2);
const CLI_YES = cliArgs.includes('--yes') || cliArgs.includes('-y');
const CLI_FORCE = cliArgs.includes('--force');
const DUMP_FILE = cliArgs.find(a => !a.startsWith('-')) || '/app/scripts/old_data_dump.sql';

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

/**
 * 安全护栏：迁移会 TRUNCATE 18 张核心业务表（含 users/projects/files）
 * 生产环境必须显式加 --yes 才能执行；其他环境统一 3 秒倒计时给运维反悔机会
 *
 * 触发条件：
 *   - NODE_ENV=production 且未加 --yes/-y  → 直接退出
 *   - 其他情况：3 秒倒计时后继续（除非加 --force 跳过）
 */
async function safetyGuard() {
  const nodeEnv = process.env.NODE_ENV || 'development';
  log.warn('==========================================');
  log.warn('  ⚠️  数据迁移即将执行以下高危操作：');
  log.warn('  1. TRUNCATE 18 张核心业务表（含 users/projects/subprojects/files）');
  log.warn('  2. 全量导入 dump 文件数据');
  log.warn('  3. 重置所有表序列');
  log.warn('  4. 刷新物化视图（可能锁表）');
  log.warn('==========================================');
  log.warn(`  NODE_ENV : ${nodeEnv}`);
  log.warn(`  DB_HOST  : ${process.env.DB_HOST || 'localhost'}`);
  log.warn(`  DB_NAME  : ${process.env.DB_NAME || 'salary_system'}`);
  log.warn(`  DUMP_FILE: ${DUMP_FILE}`);
  log.warn('==========================================');

  if (nodeEnv === 'production' && !CLI_YES) {
    log.error('生产环境（NODE_ENV=production）必须显式加 --yes 参数才能执行迁移');
    log.error('示例: node scripts/migrate-from-old.js /path/to/dump.sql --yes');
    log.error('强烈建议：迁移前先备份新库！执行 pg_dump 备份新库到本地');
    process.exit(2);
  }

  if (CLI_FORCE || CLI_YES) {
    log.warn('已通过 --yes/--force 确认，跳过倒计时');
    return;
  }

  // 3 秒倒计时（非交互，给运维 Ctrl+C 反悔机会）
  for (let s = 3; s >= 1; s--) {
    log.warn(`  ${s} 秒后开始执行... (Ctrl+C 取消)`);
    await new Promise(r => setTimeout(r, 1000));
  }
}

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
 * 旧库 → 新库 字段单位/格式转换规则
 *
 * 背景：旧库 subprojects.length/width 存储单位为"米"，新库存储单位为"厘米"
 *       新库 calculation.js 计算 quantity 时会 length / 100 转回米
 *       所以迁移时需将旧库的米值乘以 100 转为厘米
 *
 * 规则：
 *   - subprojects.length 乘以 100（米 → 厘米）
 *   - subprojects.width  乘以 100（米 → 厘米）
 *   - subprojects.quantity/amount 保持不变（旧库已是基于米的正确值）
 *
 * @param {string} tableName - 表名
 * @param {string[]|null} columns - 列名数组
 * @param {Array} values - 值数组
 * @returns {Array} 转换后的值数组
 */
function transformValues(tableName, columns, values) {
  // 仅 subprojects 表需要转换 length/width
  if (tableName !== 'subprojects' || !columns) {
    return values;
  }

  return values.map((value, index) => {
    const column = columns[index];
    // length 和 width 乘以 100（米 → 厘米），NULL 保持 NULL
    // 使用字符串移位而非浮点乘法，避免精度损失（如 3.5 → "350"，不会变成 349.9999...）
    if ((column === 'length' || column === 'width') && value !== null && value !== '') {
      return multiplyBy100AsString(String(value));
    }
    return value;
  });
}

/**
 * 字符串数字乘以 100（等价于小数点右移 2 位），避免浮点精度损失
 *
 * 示例：
 *   "3.5"   -> "350"
 *   "1.234" -> "123.4"
 *   "0.05"  -> "5"
 *   "10"    -> "1000"
 *   "-3.5"  -> "-350"
 *
 * @param {string} s - 字符串形式的数字
 * @returns {string} 乘以 100 后的字符串
 */
function multiplyBy100AsString(s) {
  const trimmed = s.trim();
  if (!/^-?\d+(\.\d+)?$/.test(trimmed)) {
    // 非标准数字格式，退化为浮点乘法（罕见分支）
    const num = parseFloat(trimmed);
    return isNaN(num) ? trimmed : String(num * 100);
  }
  const negative = trimmed.startsWith('-');
  const abs = negative ? trimmed.slice(1) : trimmed;
  const dotIndex = abs.indexOf('.');
  let integerPart, fractionPart;
  if (dotIndex === -1) {
    integerPart = abs;
    fractionPart = '';
  } else {
    integerPart = abs.slice(0, dotIndex);
    fractionPart = abs.slice(dotIndex + 1);
  }
  // 从小数部分取 2 位向左合并，剩余留作新小数
  let result;
  if (fractionPart.length >= 2) {
    result = integerPart + fractionPart.slice(0, 2);
    const rest = fractionPart.slice(2);
    if (rest.length > 0) result += '.' + rest;
  } else {
    result = integerPart + fractionPart.padEnd(2, '0');
  }
  // 去除前导 0（保留至少 1 位整数），去除小数尾部 0
  result = result.replace(/^0+(?=\d)/, '');
  if (result.includes('.')) {
    result = result.replace(/\.?0+$/, '');
  }
  if (result === '' || result === '-') result = '0';
  return negative && result !== '0' ? '-' + result : result;
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
  // 应用字段转换规则（如 subprojects.length/width 单位换算）
  const transformedValues = transformValues(tableName, columns, values);

  // 处理列名
  const columnList = columns ? `(${columns.join(', ')})` : '';

  // 处理值（参数化）
  const placeholders = transformedValues.map((_, i) => `$${i + 1}`).join(', ');

  return {
    text: `INSERT INTO ${tableName} ${columnList} VALUES (${placeholders})`.replace(/\s+/g, ' ').trim(),
    values: transformedValues
  };
}

/**
 * 解析 INSERT INTO 语句，提取表名、列名和值
 *
 * 支持格式：
 *   INSERT INTO table (col1, col2) VALUES (val1, val2);
 *   INSERT INTO table VALUES (val1, val2);
 *   INSERT INTO table (col1, col2) VALUES (val1, val2), (val3, val4);
 *
 * 值解析支持：数字、单引号字符串（含 '' 转义）、NULL、布尔值
 *
 * @param {string} sql - 完整的 INSERT 语句
 * @returns {{tableName: string|null, columns: string[]|null, valueGroups: Array<Array>|null}}
 *          解析失败返回 {tableName, columns: null, valueGroups: null}
 */
function parseInsertStatement(sql) {
  // 提取表名
  const tableMatch = sql.match(/INSERT\s+INTO\s+(?:public\.)?(\w+)/i);
  const tableName = tableMatch ? tableMatch[1] : null;

  // 提取列名（可选，在 VALUES 关键字之前的括号内）
  let columns = null;
  const columnsMatch = sql.match(/INSERT\s+INTO\s+(?:public\.)?\w+\s*\(([^)]+)\)\s*VALUES/i);
  if (columnsMatch) {
    columns = columnsMatch[1].split(',').map(c => c.trim());
  }

  // 提取 VALUES 部分（可能有多个值组，用逗号分隔）
  const valuesMatch = sql.match(/VALUES\s+(.+?);?\s*$/i);
  if (!valuesMatch) {
    return { tableName, columns, valueGroups: null };
  }

  const valuesPart = valuesMatch[1].trim();
  const valueGroups = [];
  let currentGroup = [];
  let current = '';
  let inString = false;
  let i = 0;

  // 状态机解析 VALUES 内容
  while (i < valuesPart.length) {
    const char = valuesPart[i];

    if (!inString) {
      if (char === "'") {
        // 进入字符串
        inString = true;
        current += char;
        i++;
      } else if (char === '(') {
        // 值组开始，清空当前值
        current = '';
        i++;
      } else if (char === ')') {
        // 值组结束，先保存最后一个值
        currentGroup.push(parseSqlValue(current.trim()));
        current = '';
        valueGroups.push(currentGroup);
        currentGroup = [];
        i++;
      } else if (char === ',') {
        // 值分隔符（在值组内）或值组分隔符（在值组外，但值组外应该是 ),）
        // 由于 ) 已经清空 currentGroup，这里的 , 是值组之间的分隔符
        if (currentGroup.length > 0 || current.trim() !== '') {
          // 值组内的逗号
          currentGroup.push(parseSqlValue(current.trim()));
          current = '';
        }
        i++;
      } else {
        current += char;
        i++;
      }
    } else {
      // 字符串内
      if (char === "'" && valuesPart[i + 1] === "'") {
        // 转义的单引号
        current += "''";
        i += 2;
      } else if (char === "'") {
        // 字符串结束
        inString = false;
        current += char;
        i++;
      } else {
        current += char;
        i++;
      }
    }
  }

  return { tableName, columns, valueGroups };
}

/**
 * 解析单个 SQL 值字面量
 *
 * 注意：数字保留字符串形式，避免 IEEE754 浮点精度损失
 *   - PostgreSQL 端 numeric/decimal 列接收字符串会自动转换为精确类型
 *   - 若走 parseFloat，金额如 1432698.5 可能变为 1432698.4999999...
 *
 * @param {string} raw - 原始字符串（已 trim）
 * @returns {string|null|boolean} 解析后的值（数字保留字符串）
 */
function parseSqlValue(raw) {
  if (raw === '' || raw.toUpperCase() === 'NULL') {
    return null;
  }
  // 单引号字符串
  if (raw.startsWith("'") && raw.endsWith("'")) {
    // 去掉首尾单引号，还原 '' 为 '
    return raw.slice(1, -1).replace(/''/g, "'");
  }
  // 布尔值
  if (raw.toUpperCase() === 'TRUE') return true;
  if (raw.toUpperCase() === 'FALSE') return false;
  // 数字：保留字符串形式传给 PostgreSQL，避免浮点精度损失
  if (/^-?\d*\.?\d+([eE][+-]?\d+)?$/.test(raw)) {
    return raw;
  }
  // 其他（如数组、表达式）原样返回
  return raw;
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

      // 解析 INSERT 语句并应用字段转换（如 subprojects.length/width 单位换算）
      // 这样 pg_dump --column-inserts 格式的导出文件也会做单位换算
      const parsed = parseInsertStatement(fullStatement);
      if (parsed.tableName && parsed.valueGroups && parsed.valueGroups.length > 0) {
        // 成功解析，按值组生成参数化 INSERT（应用 transformValues）
        let addedRows = 0;
        for (const values of parsed.valueGroups) {
          const insert = buildInsertStatement(parsed.tableName, parsed.columns, values);
          inserts.push({ ...insert, table: parsed.tableName });
          addedRows++;
        }
        stats.insertStatements += addedRows;
        stats.tables[parsed.tableName] = (stats.tables[parsed.tableName] || 0) + addedRows;
      } else {
        // 解析失败：早期直接抛错阻断迁移，避免：
        //   1. 单位换算被跳过（length/width 保持米单位与新库厘米不一致）
        //   2. SQL 注入风险（原始文本直接执行）
        //   3. 静默数据损坏
        // 建议改用 pg_dump 默认 COPY 格式，或用 --column-inserts 且不含复杂表达式
        throw new Error(
          `INSERT 语句解析失败（表: ${tableName}），无法应用单位换算与安全参数化。\n` +
          `建议改用 pg_dump 默认 COPY 格式导出。\n` +
          `失败语句预览: ${fullStatement.substring(0, 200)}...`
        );
      }
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

    // ========== 0. 安全护栏 ==========
    await safetyGuard();

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

    // 临时禁用外键约束和触发器（PostgreSQL 标准数据迁移方式）
    // pg_dump 按字母顺序导出表，不考虑外键依赖关系
    // 例如 files 表在 projects 表之前导出，但 files.project_id 引用 projects.id
    // 设置 session_replication_role = 'replica' 会禁用所有触发器和外键检查
    // 导入完成后恢复为 'origin' 重新启用
    log.info('临时禁用外键约束（session_replication_role = replica）...');
    await client.query("SET session_replication_role = 'replica'");

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

    // 恢复外键约束和触发器
    log.info('恢复外键约束（session_replication_role = origin）...');
    await client.query("SET session_replication_role = 'origin'");

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
      // COALESCE 处理空表：MAX(id) 为 NULL 时返回 NULL，与非空表区分
      const maxQuery = `SELECT MAX(${row.column_name}) AS max_id FROM ${row.table_name}`;
      const maxResult = await client.query(maxQuery);
      const maxId = maxResult.rows[0].max_id;

      if (maxId === null || maxId === undefined) {
        // 空表：将序列重置为 1 但标记 is_called=false，下一个 nextval 返回 1
        await client.query(`SELECT setval($1, 1, false)`, [row.sequence_name]);
        log.info(`  ✅ ${row.table_name}.${row.column_name} → 1 (空表)`);
      } else {
        // 非空表：is_called=true，下一个 nextval 返回 maxId + 1
        await client.query(`SELECT setval($1, $2, true)`, [row.sequence_name, maxId]);
        log.info(`  ✅ ${row.table_name}.${row.column_name} → ${maxId}`);
      }
    }

    log.success('所有序列已重置');

    // ========== 6. 刷新物化视图 ==========
    log.info('');
    log.info('[步骤 5] 刷新物化视图...');

    try {
      await client.query('REFRESH MATERIALIZED VIEW CONCURRENTLY mv_project_user_settlement_status');
      log.success('物化视图已刷新（CONCURRENTLY 模式）');
    } catch (err) {
      log.warn(`CONCURRENTLY 刷新失败: ${err.message}`);
      log.warn('⚠️  即将降级为非并发刷新，会短暂锁表 mv_project_user_settlement_status');
      log.warn('⚠️  期间统计页会阻塞，通常几秒内完成');
      await client.query('REFRESH MATERIALIZED VIEW mv_project_user_settlement_status');
      log.success('物化视图已刷新（普通模式，锁表已释放）');
    }

    // ========== 7. 数据校验 ==========
    log.info('');
    log.info('[步骤 6] 数据校验...');

    // 7.1 失败记录完整输出（如有）
    if (failedCount > 0) {
      log.warn('');
      log.warn(`========== 导入失败记录（共 ${failedCount} 条）==========`);
      failedStatements.forEach((fail, idx) => {
        log.warn(`  [${idx + 1}] 表: ${fail.table} | 错误: ${fail.error}`);
        log.warn(`      预览: ${fail.preview}`);
      });
      log.warn('');
      log.warn('⚠️  存在导入失败记录，请检查上方日志确认是否影响财务数据');
    }

    // 7.2 数据量统计
    const tablesToVerify = [
      'users', 'projects', 'subprojects', 'project_workers',
      'wage_settlements', 'wage_distributions', 'wage_advances',
      'files', 'project_history', 'project_user_status',
      'wage_settlement_snapshots', 'space_types', 'construction_plans'
    ];

    log.info('');
    log.info('========== 数据量统计 ==========');
    let totalRecords = 0;
    let hasCountAnomaly = false;
    for (const table of tablesToVerify) {
      const countResult = await client.query(`SELECT COUNT(*) AS count FROM ${table}`);
      const count = parseInt(countResult.rows[0].count, 10);
      totalRecords += count;
      // 对比导入时的统计：如果导入统计中有该表但行数不一致，标记异常
      const imported = importedTableCount[table] || 0;
      const marker = (imported > 0 && count !== imported) ? ' ⚠️异常' : '';
      if (marker) hasCountAnomaly = true;
      console.log(`  ${table.padEnd(30)} ${count} 条${marker}`);
    }
    log.info(`  ${'总计'.padEnd(30)} ${totalRecords} 条`);
    if (hasCountAnomaly) {
      log.warn('⚠️  检测到行数异常：导入成功数与新库实际行数不一致，可能存在数据丢失');
    }

    // 7.3 金额汇总校验
    log.info('');
    log.info('========== 金额汇总校验 ==========');
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

    // 7.4 交叉金额校验（财务数据一致性）
    // 修复：原实现仅汇总金额，无法发现明细与汇总不一致的问题
    log.info('');
    log.info('========== 交叉金额校验（财务一致性）==========');

    // 校验1：工程总额 vs 子项目金额之和
    // 若不一致，说明 projects.total_amount 与 subprojects.amount 脱节（旧库冗余字段未及时更新）
    log.info('');
    log.info('校验1: 工程总额 vs 子项目金额之和（按工程维度）');
    const projectAmountCheck = await client.query(`
      SELECT p.id, p.name, p.total_amount,
             COALESCE(SUM(sp.amount), 0) AS subproject_total,
             p.total_amount - COALESCE(SUM(sp.amount), 0) AS diff
      FROM projects p
      LEFT JOIN subprojects sp ON sp.project_id = p.id
      GROUP BY p.id, p.name, p.total_amount
      HAVING p.total_amount != COALESCE(SUM(sp.amount), 0)
         AND p.total_amount IS NOT NULL
      ORDER BY ABS(p.total_amount - COALESCE(SUM(sp.amount), 0)) DESC
      LIMIT 20
    `);
    if (projectAmountCheck.rows.length === 0) {
      log.success('  ✅ 所有工程总额与子项目金额之和一致');
    } else {
      log.warn(`  ⚠️  发现 ${projectAmountCheck.rows.length} 个工程总额与子项目金额不一致（最多显示20条）:`);
      console.log(`  ${'工程ID'.padEnd(8)} ${'工程名'.padEnd(30)} ${'工程总额'.padEnd(15)} ${'子项目之和'.padEnd(15)} ${'差额'.padEnd(15)}`);
      projectAmountCheck.rows.forEach(row => {
        console.log(`  ${String(row.id).padEnd(8)} ${String(row.name).substring(0, 28).padEnd(30)} ¥${parseFloat(row.total_amount).toFixed(2).padEnd(13)} ¥${parseFloat(row.subproject_total).toFixed(2).padEnd(13)} ¥${parseFloat(row.diff).toFixed(2)}`);
      });
      log.warn('  说明：旧库 projects.total_amount 为冗余字段，可能未及时更新。新系统统计以 subprojects.amount 为准，不影响实际计算');
    }

    // 校验2：结算单总额 vs 工费分配明细之和
    // 若不一致，说明 wage_distributions 有记录丢失或金额错误，直接影响财务数据
    log.info('');
    log.info('校验2: 结算单总额 vs 工费分配明细之和（按结算单维度）');
    const settlementAmountCheck = await client.query(`
      SELECT ws.id, ws.settlement_no, ws.total_amount,
             COALESCE(SUM(wd.amount), 0) AS distribution_total,
             ws.total_amount - COALESCE(SUM(wd.amount), 0) AS diff
      FROM wage_settlements ws
      LEFT JOIN wage_distributions wd ON wd.settlement_id = ws.id
      GROUP BY ws.id, ws.settlement_no, ws.total_amount
      HAVING ws.total_amount != COALESCE(SUM(wd.amount), 0)
      ORDER BY ABS(ws.total_amount - COALESCE(SUM(wd.amount), 0)) DESC
      LIMIT 20
    `);
    if (settlementAmountCheck.rows.length === 0) {
      log.success('  ✅ 所有结算单总额与工费分配明细之和一致');
    } else {
      log.warn(`  ⚠️  发现 ${settlementAmountCheck.rows.length} 个结算单总额与工费分配明细不一致（最多显示20条）:`);
      console.log(`  ${'结算单ID'.padEnd(10)} ${'结算单号'.padEnd(25)} ${'结算单总额'.padEnd(15)} ${'分配明细之和'.padEnd(15)} ${'差额'.padEnd(15)}`);
      settlementAmountCheck.rows.forEach(row => {
        console.log(`  ${String(row.id).padEnd(10)} ${String(row.settlement_no).substring(0, 23).padEnd(25)} ¥${parseFloat(row.total_amount).toFixed(2).padEnd(13)} ¥${parseFloat(row.distribution_total).toFixed(2).padEnd(13)} ¥${parseFloat(row.diff).toFixed(2)}`);
      });
      log.warn('  ⚠️  此异常表示工费分配记录丢失或金额错误，需人工核查原始数据');
    }

    // 校验3：结算单预支金额 vs 预支明细之和
    // 若不一致，说明 wage_advances 有记录丢失，影响结算单的实付金额
    log.info('');
    log.info('校验3: 结算单预支金额 vs 预支明细之和（按结算单维度）');
    const advanceAmountCheck = await client.query(`
      SELECT ws.id, ws.settlement_no, ws.advance_amount,
             COALESCE(SUM(wa.advance_amount), 0) AS advance_total,
             ws.advance_amount - COALESCE(SUM(wa.advance_amount), 0) AS diff
      FROM wage_settlements ws
      LEFT JOIN wage_advances wa ON wa.settlement_id = ws.id
      GROUP BY ws.id, ws.settlement_no, ws.advance_amount
      HAVING ws.advance_amount != COALESCE(SUM(wa.advance_amount), 0)
      ORDER BY ABS(ws.advance_amount - COALESCE(SUM(wa.advance_amount), 0)) DESC
      LIMIT 20
    `);
    if (advanceAmountCheck.rows.length === 0) {
      log.success('  ✅ 所有结算单预支金额与预支明细之和一致');
    } else {
      log.warn(`  ⚠️  发现 ${advanceAmountCheck.rows.length} 个结算单预支金额与预支明细不一致（最多显示20条）:`);
      console.log(`  ${'结算单ID'.padEnd(10)} ${'结算单号'.padEnd(25)} ${'结算单预支'.padEnd(15)} ${'预支明细之和'.padEnd(15)} ${'差额'.padEnd(15)}`);
      advanceAmountCheck.rows.forEach(row => {
        console.log(`  ${String(row.id).padEnd(10)} ${String(row.settlement_no).substring(0, 23).padEnd(25)} ¥${parseFloat(row.advance_amount).toFixed(2).padEnd(13)} ¥${parseFloat(row.advance_total).toFixed(2).padEnd(13)} ¥${parseFloat(row.diff).toFixed(2)}`);
      });
      log.warn('  ⚠️  此异常表示预支记录丢失或金额错误，需人工核查原始数据');
    }

    // 校验4：已结算工程状态一致性
    // 检查已结算工程（有结算单）的 project_user_status 是否正确标记为 settled
    log.info('');
    log.info('校验4: 已结算工程状态一致性');
    const statusCheck = await client.query(`
      SELECT COUNT(*) AS inconsistent_count
      FROM wage_settlements ws
      JOIN project_user_status pus ON pus.user_id = ws.user_id
      WHERE pus.settlement_id = ws.id
        AND pus.settlement_status != 'settled'
    `);
    const inconsistentCount = parseInt(statusCheck.rows[0].inconsistent_count, 10);
    if (inconsistentCount === 0) {
      log.success('  ✅ 所有已结算工程的状态标记一致');
    } else {
      log.warn(`  ⚠️  发现 ${inconsistentCount} 条结算状态不一致记录（project_user_status 未正确标记为 settled）`);
      log.warn('  可执行: UPDATE project_user_status SET settlement_status = \'settled\' WHERE settlement_id IS NOT NULL AND settlement_status != \'settled\'');
    }

    // 校验5：子项目单位长度合理性（避免迁移时单位换算错误）
    // 新库 length/width 单位为厘米，正常范围 1-10000（0.01m - 100m）
    // 若出现 >10000 或 <1 的值，可能是单位换算异常
    log.info('');
    log.info('校验5: 子项目尺寸单位合理性（厘米）');
    const dimensionCheck = await client.query(`
      SELECT id, project_id, length, width
      FROM subprojects
      WHERE (length IS NOT NULL AND (length > 10000 OR length < 1))
         OR (width IS NOT NULL AND (width > 10000 OR width < 1))
      LIMIT 20
    `);
    if (dimensionCheck.rows.length === 0) {
      log.success('  ✅ 所有子项目尺寸值在合理范围内（1-10000厘米）');
    } else {
      log.warn(`  ⚠️  发现 ${dimensionCheck.rows.length} 条子项目尺寸异常（最多显示20条）:`);
      console.log(`  ${'子项目ID'.padEnd(10)} ${'工程ID'.padEnd(10)} ${'长度(cm)'.padEnd(15)} ${'宽度(cm)'.padEnd(15)}`);
      dimensionCheck.rows.forEach(row => {
        console.log(`  ${String(row.id).padEnd(10)} ${String(row.project_id).padEnd(10)} ${String(row.length).padEnd(15)} ${String(row.width).padEnd(15)}`);
      });
      log.warn('  说明：正常范围 1-10000 厘米（0.01m-100m）。异常值可能是迁移时单位换算错误');
    }

    // 7.5 校验结果汇总
    log.info('');
    log.info('========== 校验结果汇总 ==========');
    const hasFailedImports = failedCount > 0;
    const hasProjectAmountIssue = projectAmountCheck.rows.length > 0;
    const hasSettlementAmountIssue = settlementAmountCheck.rows.length > 0;
    const hasAdvanceAmountIssue = advanceAmountCheck.rows.length > 0;
    const hasStatusIssue = inconsistentCount > 0;
    const hasDimensionIssue = dimensionCheck.rows.length > 0;

    if (!hasFailedImports && !hasCountAnomaly && !hasProjectAmountIssue &&
        !hasSettlementAmountIssue && !hasAdvanceAmountIssue &&
        !hasStatusIssue && !hasDimensionIssue) {
      log.success('  ✅ 所有校验通过，财务数据一致性正常');
    } else {
      log.warn('  ⚠️  存在异常项，请逐项检查上方日志：');
      if (hasFailedImports) log.warn(`     - ${failedCount} 条导入失败记录`);
      if (hasCountAnomaly) log.warn('     - 行数统计异常');
      if (hasProjectAmountIssue) log.warn(`     - ${projectAmountCheck.rows.length} 个工程总额与子项目金额不一致`);
      if (hasSettlementAmountIssue) log.warn(`     - ${settlementAmountCheck.rows.length} 个结算单总额与分配明细不一致`);
      if (hasAdvanceAmountIssue) log.warn(`     - ${advanceAmountCheck.rows.length} 个结算单预支与预支明细不一致`);
      if (hasStatusIssue) log.warn(`     - ${inconsistentCount} 条结算状态不一致`);
      if (hasDimensionIssue) log.warn(`     - ${dimensionCheck.rows.length} 条子项目尺寸异常`);
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
    log.info('  3. 用旧库用户账号登录验证（账号密码请向管理员索取，请勿在源码中留存）');
    log.info('  4. 检查工程列表、结算记录、附件显示是否正常');
    if (hasFailedImports || hasSettlementAmountIssue || hasAdvanceAmountIssue || hasStatusIssue) {
      log.info('  5. ⚠️  存在财务数据异常，请按校验结果人工核查并修复后再投入使用');
    }

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
