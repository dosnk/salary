#!/usr/bin/env node
/**
 * 物化视图刷新脚本
 *
 * 用途：刷新 mv_project_user_settlement_status 物化视图，使结算状态数据保持最新
 *
 * 使用场景：
 *   1. 结算操作完成后（settlementService 自动调用）
 *   2. 工程状态变更后（projectService 自动调用）
 *   3. 定时任务（每5分钟刷新一次）
 *   4. 手动执行：docker compose exec app node scripts/refresh-mv.js
 *
 * 刷新方式：REFRESH CONCURRENTLY（不阻塞读取，但需要唯一索引）
 *
 * 注意：CONCURRENTLY 刷新需要物化视图有唯一索引（已在 V1.9 迁移中创建）
 */

const pool = require('../config/database');

/**
 * 刷新物化视图（并发模式，不阻塞读取）
 * @param {string} viewName - 物化视图名
 * @returns {Promise<boolean>} 是否成功
 */
const refreshMaterializedView = async (viewName = 'mv_project_user_settlement_status') => {
  const client = await pool.connect();
  try {
    const start = Date.now();
    // CONCURRENTLY 模式不阻塞读取，但需要在事务外执行
    await client.query(`REFRESH MATERIALIZED VIEW CONCURRENTLY ${viewName}`);
    const elapsed = ((Date.now() - start) / 1000).toFixed(2);
    console.log(`[refresh-mv] 物化视图 ${viewName} 刷新完成，耗时 ${elapsed}s`);
    return true;
  } catch (error) {
    // 如果 CONCURRENTLY 失败（如无唯一索引），降级为普通刷新
    if (error.message.includes('CONCURRENTLY')) {
      console.warn(`[refresh-mv] CONCURRENTLY 刷新失败，降级为普通刷新: ${error.message}`);
      try {
        await client.query(`REFRESH MATERIALIZED VIEW ${viewName}`);
        console.log(`[refresh-mv] 物化视图 ${viewName} 普通刷新完成`);
        return true;
      } catch (e) {
        console.error(`[refresh-mv] 普通刷新也失败: ${e.message}`);
        return false;
      }
    }
    console.error(`[refresh-mv] 刷新物化视图失败: ${error.message}`);
    return false;
  } finally {
    client.release();
  }
};

// 直接运行时执行
if (require.main === module) {
  refreshMaterializedView()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}

module.exports = { refreshMaterializedView };
