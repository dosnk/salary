/**
 * 数据库全量备份服务
 *
 * 将公共 schema 下所有业务表（含 AI 相关表）动态枚举并导出为 JSON 文件，
 * 保存到 upload/backdata 目录（容器 /app/upload/backdata，宿主机 ./upload/backdata）。
 *
 * 备份策略：
 * - 文件名带本地时间戳：backup-full-YYYYMMDD-HHmmss.json，不覆盖历史文件
 * - 轮转保留：最多保留 BACKUP_KEEP_COUNT（默认5）份，超出时从最旧开始删除
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

/** 备份文件命名前缀与匹配正则 */
const BACKUP_PREFIX = 'backup-full-';
const BACKUP_PATTERN = new RegExp(`^${BACKUP_PREFIX}\\d{8}-\\d{6}\\.json$`);

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
  try {
    // 1. 动态枚举业务表（含 AI 相关表，未来新增表自动纳入）
    const tables = await listBusinessTables();
    logger.info(`开始数据库全量备份，共 ${tables.length} 张表`);

    // 2. 逐表导出数据
    const backupTables = {};
    let totalRows = 0;
    for (const table of tables) {
      // 表名来自 pg_tables 白名单，双引号转义防注入
      const result = await pool.query(`SELECT * FROM "${table}"`);
      backupTables[table] = result.rows;
      totalRows += result.rows.length;
    }

    // 3. 组装备份内容（结构与 scripts/backup-projects.js 兼容，便于复用恢复脚本）
    const backupData = {
      backupTime: new Date().toISOString(),
      version: 'V2.10',
      tables: backupTables
    };

    // 4. 写入文件（本地时间戳，不覆盖历史）
    const backupDir = getBackupDir();
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }
    const timestamp = moment().format('YYYYMMDD-HHmmss');
    const fileName = `${BACKUP_PREFIX}${timestamp}.json`;
    const backupPath = path.join(backupDir, fileName);

    // 原子写入：先写临时文件再重命名，避免写入中断产生损坏文件
    const tmpPath = path.join(backupDir, `.tmp-${process.pid}-${Date.now()}.json`);
    fs.writeFileSync(tmpPath, JSON.stringify(backupData, null, 2), 'utf8');
    fs.renameSync(tmpPath, backupPath);

    // 5. 轮转：仅保留最新 KEEP_COUNT 份
    const deletedOld = rotateBackups(backupDir);

    // 6. 统计保留的备份数
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
    logger.error('数据库全量备份失败: 类型=%s, 消息=%s, 堆栈=%s',
      error.constructor.name, error.message, error.stack);
    throw error;
  } finally {
    isBackingUp = false;
  }
};

module.exports = { createDatabaseBackup, getBackupDir };