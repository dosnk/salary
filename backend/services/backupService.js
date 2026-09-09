/**
 * 数据库全量备份服务
 *
 * 将公共 schema 下所有业务表（含 AI 相关表）动态枚举并导出为 JSON 文件，
 * 保存到 upload/backdata 目录（容器 /app/upload/backdata，宿主机 ./upload/backdata）。
 *
 * 备份策略：
 * - 文件名带本地时间戳（秒+毫秒）：backup-full-YYYYMMDD-HHmmss-SSS.json，
 *   不覆盖历史文件；毫秒级粒度避免同一秒内连续触发互相覆盖（2026-09-10 加固）
 * - 轮转保留：最多保留 BACKUP_KEEP_COUNT（默认5）份，超出时从最旧开始删除
 * - 流式写入：逐表分页查询（ORDER BY ctid + LIMIT/OFFSET）边查边写临时文件，
 *   内存只驻留一页数据，避免大表全量载入内存导致 OOM 或阻塞事件循环（2026-09-10 加固）
 * - 并发防护：备份进行中重复触发直接返回 inProgress=true，避免并发写文件
 *
 * 恢复方式：由 init-db.js 重建表结构后，用本服务导出的 JSON 逐表导回数据。
 */

const fs = require('fs');
const path = require('path');
const moment = require('moment');
const pool = require('../config/database');
const logger = require('../config/logger');

/** 备份文件保留份数（可用环境变量 BACKUP_KEEP_COUNT 覆盖） */
const KEEP_COUNT = parseInt(process.env.BACKUP_KEEP_COUNT || '5', 10);

/** 备份文件命名前缀与匹配正则（兼容旧 6 位秒格式与新 9 位 秒+毫秒格式） */
const BACKUP_PREFIX = 'backup-full-';
const BACKUP_PATTERN = new RegExp(`^${BACKUP_PREFIX}\\d{8}-\\d{6}(-\\d{3})?\\.json$`);

/** 分页导出每页行数（控制内存峰值，避免大表整表驻留内存） */
const PAGE_SIZE = 2000;

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
 * 动态枚举公共 schema 下的所有业务表
 * 仅查普通表（pg_tables 不含视图/物化视图），排除迁移版本表 db_versions
 * @returns {Promise<string[]>} 表名数组
 */
const listBusinessTables = async () => {
  const result = await pool.query(
    `SELECT tablename
     FROM pg_tables
     WHERE schemaname = 'public'
       AND tablename <> 'db_versions'
     ORDER BY tablename`
  );
  return result.rows.map(r => r.tablename);
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
 * 分页导出单表所有行，逐批写入流（不整表驻留内存）
 *
 * 使用 ORDER BY ctid 保证分页顺序稳定：ctid 是 PostgreSQL 物理行标识，
 * 不存在业务列重复值导致 OFFSET 分页跳行/重复的问题。
 *
 * @param {import('stream').Writable} ws - 输出流
 * @param {string} table - 表名（来自 pg_tables 白名单，双引号转义防注入）
 * @param {boolean} firstRow - 是否为当前表第一行（控制 JSON 逗号）
 * @returns {Promise<number>} 该表导出行数
 */
const exportTableToStream = async (ws, table, firstRow) => {
  let offset = 0;
  let rowsWritten = 0;
  while (true) {
    // 表名来自 pg_tables 白名单；ORDER BY ctid 稳定分页，LIMIT/OFFSET 控制每页内存
    const result = await pool.query(
      `SELECT * FROM "${table}" ORDER BY ctid LIMIT $1 OFFSET $2`,
      [PAGE_SIZE, offset]
    );
    if (result.rows.length === 0) break;

    for (const row of result.rows) {
      if (!firstRow) ws.write(',');
      ws.write('\n      ' + JSON.stringify(row));
      firstRow = false;
    }
    rowsWritten += result.rows.length;
    if (result.rows.length < PAGE_SIZE) break;
    offset += PAGE_SIZE;
  }
  return rowsWritten;
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
    // 1. 动态枚举业务表（含 AI 相关表，未来新增表自动纳入）
    const tables = await listBusinessTables();
    logger.info(`开始数据库全量备份，共 ${tables.length} 张表`);

    // 2. 确保备份目录存在
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }

    // 3. 文件名：本地时间戳（秒+毫秒），不覆盖历史
    const timestamp = moment().format('YYYYMMDD-HHmmss-SSS');
    const fileName = `${BACKUP_PREFIX}${timestamp}.json`;
    const backupPath = path.join(backupDir, fileName);
    tmpPath = path.join(backupDir, `.tmp-${process.pid}-${Date.now()}.json`);

    // 4. 流式写入：逐表分页查询边查边写，内存峰值仅为一页数据
    const ws = fs.createWriteStream(tmpPath, { encoding: 'utf8' });
    await new Promise((resolve, reject) => {
      ws.once('error', reject);
      ws.once('open', resolve);
    });

    let totalRows = 0;
    try {
      ws.write('{\n');
      ws.write(`  "backupTime": ${JSON.stringify(new Date().toISOString())},\n`);
      ws.write('  "version": "V2.10",\n');
      ws.write('  "tables": {\n');

      let firstTable = true;
      for (const table of tables) {
        if (!firstTable) ws.write(',\n');
        ws.write(`    ${JSON.stringify(table)}: [`);
        const tableRows = await exportTableToStream(ws, table, true);
        totalRows += tableRows;
        ws.write('\n    ]');
        firstTable = false;
      }

      ws.write('\n  }\n}');
    } finally {
      // 确保流关闭并刷新到磁盘
      await new Promise((resolve, reject) => {
        ws.end((err) => (err ? reject(err) : resolve()));
        ws.once('error', reject);
      });
    }

    // 5. 原子写入：临时文件就绪后重命名到最终文件名
    fs.renameSync(tmpPath, backupPath);
    tmpPath = null; // 已成功改名，无需清理

    // 6. 轮转：仅保留最新 KEEP_COUNT 份
    const deletedOld = rotateBackups(backupDir);

    // 7. 统计保留的备份数
    const keptBackups = fs.readdirSync(backupDir).filter(name => BACKUP_PATTERN.test(name)).length;

    logger.info(`数据库备份完成: ${fileName}，${tables.length}张表 ${totalRows}条记录，保留${keptBackups}份，轮转删除${deletedOld}份`);

    return {
      inProgress: false,
      fileName,
      tableCount: tables.length,
      totalRows,
      keptBackups,
      deletedOld
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
