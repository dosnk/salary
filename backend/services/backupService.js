/**
 * 数据库全量备份服务（pg_dump 实现）
 *
 * 使用 PostgreSQL 官方客户端工具 pg_dump 将整个 salary 数据库导出为
 * custom 格式（-Fc）备份文件，保存到 upload/backdata 目录
 * （容器 /app/upload/backdata，宿主机 ./upload/backdata）。
 *
 * 相比早期 JSON 逐表导出方案的改进：
 * - 原生 pg_dump：类型保真（NUMERIC/JSONB/时间戳等），包含表结构、索引、
 *   约束、序列（含 setval 当前值）、物化视图等全部元数据
 * - custom 格式（-Fc）：内部压缩，文件体积小；恢复用 pg_restore，
 *   支持只恢复单表、并行恢复等灵活操作
 * - 无内存风险：pg_dump 由 PostgreSQL 服务端流式导出，Node 进程不驻留数据
 *
 * 备份策略：
 * - 文件名带本地时间戳（秒+毫秒）：backup-full-YYYYMMDD-HHmmss-SSS.dump，
 *   不覆盖历史文件；毫秒级粒度避免同一秒内连续触发互相覆盖
 * - 轮转保留：最多保留 BACKUP_KEEP_COUNT（默认5）份，超出时从最旧开始删除
 * - 原子写入：pg_dump 写入临时文件，成功后 rename 到最终文件名
 * - 并发防护：备份进行中重复触发直接返回 inProgress=true，避免并发写文件
 *
 * 恢复方式：
 *   pg_restore -h <host> -p <port> -U <user> -d <db> \
 *     --clean --if-exists --no-owner --no-privileges <备份文件>
 *   （也可使用 scripts/restore-projects.js 一键恢复）
 */

const fs = require('fs');
const path = require('path');
const moment = require('moment');
const { execFile } = require('child_process');
const { promisify } = require('util');
const pool = require('../config/database');
const logger = require('../config/logger');

const execFileAsync = promisify(execFile);

/** 备份文件保留份数（可用环境变量 BACKUP_KEEP_COUNT 覆盖） */
const KEEP_COUNT = parseInt(process.env.BACKUP_KEEP_COUNT || '5', 10);

/** 备份文件命名前缀与匹配正则（pg_dump custom 格式，扩展名 .dump） */
const BACKUP_PREFIX = 'backup-full-';
const BACKUP_PATTERN = /^backup-full-\d{8}-\d{6}(-\d{3})?\.dump$/;

/** pg_dump 执行超时（10分钟，超大库可通过环境变量 DUMP_TIMEOUT_MS 覆盖） */
const DUMP_TIMEOUT_MS = parseInt(process.env.DUMP_TIMEOUT_MS || String(10 * 60 * 1000), 10);

/** 备份进行中标志（防止并发触发重复备份） */
let isBackingUp = false;

/**
 * 计算备份目录绝对路径
 * upload 目录基准与 controllers/upload.js 一致：backend/upload
 * @returns {string} 备份目录绝对路径
 */
const getBackupDir = () => {
  return path.join(__dirname, '..', 'upload', 'backdata');
};

/**
 * 统计当前表数与总行数（仅用于接口返回与日志，不参与备份本身）
 * 行数取 pg_stat_user_tables.n_live_tup 近似值，避免大表 COUNT(*) 全表扫描
 * @returns {Promise<{tableCount: number, totalRows: number}>}
 */
const collectDbStats = async () => {
  try {
    const tableResult = await pool.query(
      `SELECT COUNT(*)::int AS count
       FROM pg_tables
       WHERE schemaname = 'public' AND tablename <> 'db_versions'`
    );
    const rowResult = await pool.query(
      `SELECT COALESCE(SUM(n_live_tup), 0)::bigint AS rows FROM pg_stat_user_tables`
    );
    return {
      tableCount: tableResult.rows[0]?.count || 0,
      totalRows: Number(rowResult.rows[0]?.rows || 0),
    };
  } catch (error) {
    logger.warn('统计数据库规模失败（不影响备份）:', error.message);
    return { tableCount: 0, totalRows: 0 };
  }
};

/**
 * 轮转清理：仅保留最新 KEEP_COUNT 个备份文件，从最旧开始删除
 * @param {string} backupDir - 备份目录
 * @returns {number} 删除的文件数
 */
const rotateBackups = (backupDir) => {
  let files = [];
  try {
    files = fs.readdirSync(backupDir).filter(name => BACKUP_PATTERN.test(name)).sort();
  } catch (error) {
    logger.warn('读取备份目录失败:', error.message);
    return 0;
  }

  // 超出保留上限时删除最旧的文件（第KEEP_COUNT+1个开始向前覆盖）
  const excess = files.length - KEEP_COUNT;
  let deleted = 0;
  for (let i = 0; i < excess; i++) {
    try {
      fs.unlinkSync(path.join(backupDir, files[i]));
      logger.info(`轮转删除旧备份: ${files[i]}`);
      deleted++;
    } catch (error) {
      logger.warn(`删除旧备份失败 ${files[i]}:`, error.message);
    }
  }
  return deleted;
};

/**
 * 执行 pg_dump 导出数据库为 custom 格式
 * 密码通过 PGPASSWORD 环境变量传入，避免出现在进程命令行（防泄露）
 * @param {string} outputPath - 输出文件路径（临时文件）
 * @returns {Promise<void>}
 */
const runPgDump = async (outputPath) => {
  const host = process.env.DB_HOST || 'localhost';
  const port = process.env.DB_PORT || '5432';
  const user = process.env.DB_USER || 'postgres';
  const database = process.env.DB_NAME || 'salary';
  const password = process.env.DB_PASSWORD;

  // 使用 execFile 数组参数，避免 shell 拼接注入
  await execFileAsync('pg_dump', [
    '-h', host,
    '-p', String(port),
    '-U', user,
    '-d', database,
    '-Fc',                 // custom 格式：内部压缩 + pg_restore 可选/并行恢复
    '--no-owner',          // 不导出对象属主，避免恢复时权限不匹配
    '--no-privileges',     // 不导出对象 ACL 权限
    '-f', outputPath,
  ], {
    env: { ...process.env, PGPASSWORD: password || '' },
    timeout: DUMP_TIMEOUT_MS,
    // pg_dump 的 stderr 会输出进度/警告信息，放宽上限避免大库截断
    maxBuffer: 16 * 1024 * 1024,
  });
};

/**
 * 创建数据库全量备份
 *
 * @returns {Promise<{inProgress: boolean, fileName: string|null, tableCount: number,
 *                    totalRows: number, keptBackups: number, deletedOld: number}>}
 */
const createDatabaseBackup = async () => {
  // 并发防护：上一次备份尚未完成时直接返回，避免重复写文件
  if (isBackingUp) {
    logger.warn('数据库备份进行中，跳过本次触发');
    return { inProgress: true, fileName: null, tableCount: 0, totalRows: 0, keptBackups: 0, deletedOld: 0 };
  }

  isBackingUp = true;
  const backupDir = getBackupDir();
  // 临时文件路径（写入完成后 rename 到最终文件名，保证原子性）
  let tmpPath = null;
  try {
    // 1. 确保备份目录存在
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }

    // 2. 文件名：本地时间戳（秒+毫秒），不覆盖历史
    const timestamp = moment().format('YYYYMMDD-HHmmss-SSS');
    const fileName = `${BACKUP_PREFIX}${timestamp}.dump`;
    const backupPath = path.join(backupDir, fileName);
    tmpPath = path.join(backupDir, `.tmp-${process.pid}-${Date.now()}.dump`);

    // 3. pg_dump 导出到临时文件（服务端流式读取，Node 进程无内存压力）
    await runPgDump(tmpPath);

    // 4. 原子写入：临时文件就绪后重命名到最终文件名
    fs.renameSync(tmpPath, backupPath);
    tmpPath = null; // 已成功改名，无需清理

    // 5. 统计规模（表数/行数近似值，供接口返回与日志）
    const stats = await collectDbStats();

    // 6. 轮转：仅保留最新 KEEP_COUNT 份
    const deletedOld = rotateBackups(backupDir);

    // 7. 统计保留的备份数
    const keptBackups = fs.readdirSync(backupDir).filter(name => BACKUP_PATTERN.test(name)).length;

    logger.info(`数据库备份完成: ${fileName}，${stats.tableCount}张表约${stats.totalRows}条记录，保留${keptBackups}份，轮转删除${deletedOld}份`);

    return {
      inProgress: false,
      fileName,
      tableCount: stats.tableCount,
      totalRows: stats.totalRows,
      keptBackups,
      deletedOld,
    };
  } catch (error) {
    // 清理未完成的临时文件，避免残留脏数据
    if (tmpPath && fs.existsSync(tmpPath)) {
      try {
        fs.unlinkSync(tmpPath);
      } catch (cleanErr) {
        logger.warn('清理临时备份文件失败:', cleanErr.message);
      }
    }
    logger.error('数据库全量备份失败: 类型=%s, 消息=%s, 堆栈=%s',
      error.constructor.name, error.message, error.stack);
    throw error;
  } finally {
    isBackingUp = false;
  }
};

module.exports = { createDatabaseBackup, getBackupDir };
