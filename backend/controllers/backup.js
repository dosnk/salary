/**
 * 备份管理API控制器
 *
 * 触发数据库全量备份（保存到 upload/backdata，保留最近5份）
 */

const { createDatabaseBackup } = require('../services/backupService');
const logger = require('../config/logger');

/**
 * 触发数据库全量备份
 * POST /v1/backup/database
 *
 * 返回备份汇总信息（文件名/表数/记录数/保留份数），不暴露业务数据
 */
const backupDatabase = async (ctx) => {
  try {
    const result = await createDatabaseBackup();

    // 备份进行中时返回提示，仍按成功处理（避免前端误判为失败）
    if (result.inProgress) {
      ctx.success({
        message: '数据库备份正在进行中，请稍后再试',
        fileName: null,
        tableCount: 0,
        totalRows: 0,
        keptBackups: result.keptBackups
      });
      return;
    }

    ctx.success({
      message: '数据库备份完成',
      fileName: result.fileName,
      tableCount: result.tableCount,
      totalRows: result.totalRows,
      keptBackups: result.keptBackups,
      deletedOld: result.deletedOld
    });
  } catch (error) {
    logger.error('触发数据库备份失败:', error);
    ctx.fail(5001, `数据库备份失败: ${error.message}`);
  }
};

module.exports = { backupDatabase };