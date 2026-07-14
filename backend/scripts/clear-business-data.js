/**
 * 业务数据清空脚本（方案二：只清业务数据，保留用户和字典）
 *
 * 用途：
 *   彻底清空所有工程/子项目/结算/预支/消息/文件等业务数据，
 *   但保留用户账号（users）、施工方案（construction_plans）、
 *   空间类型（space_types）、工资分配类型（wage_distribution_types）、
 *   动作类型（action_types）等字典数据。
 *
 * 特点：
 *   - 使用 TRUNCATE ... RESTART IDENTITY CASCADE，比 DELETE 更彻底
 *     且会重置自增ID（SERIAL），下一个新工程从id=1开始
 *   - 事务包裹，失败自动回滚
 *   - 保留物料库分类和用户添加的物料参数（不动 material_* 表）
 *   - 保留AI对话历史相关表结构（可选择性清空，见下方说明）
 *
 * 使用方法：
 *   node scripts/clear-business-data.js
 *   或 docker compose exec app node scripts/clear-business-data.js
 *
 * 警告：
 *   此操作不可逆！执行前建议先备份数据库：
 *   pg_dump -U salary -d salary > backup.sql
 */

const pool = require('../config/database');
const logger = require('../config/logger');

// ===================== 需要清空的业务表（按外键依赖顺序不重要，CASCADE会处理） =====================
// 说明：
//   - messages: 站内消息
//   - subproject_transfers: 子项目转交记录
//   - wage_advances: 预支记录
//   - files: 工程附件文件记录
//   - wage_distributions: 工资分配（结算生成的用户分摊明细）
//   - wage_settlement_snapshots: 结算快照（历史结算单）
//   - project_user_status: 用户在工程中的结算状态
//   - wage_settlements: 结算单主表
//   - project_history: 工程操作历史
//   - subprojects: 子项目
//   - project_workers: 工程施工员关联
//   - projects: 工程主表
const BUSINESS_TABLES = [
  'messages',
  'subproject_transfers',
  'wage_advances',
  'files',
  'wage_distributions',
  'wage_settlement_snapshots',
  'project_user_status',
  'wage_settlements',
  'project_history',
  'subprojects',
  'project_workers',
  'projects'
];

// ===================== AI相关业务表（可选清空，默认不清） =====================
// 若需要连同AI对话历史一起清空，将下面数组内容合并到 BUSINESS_TABLES
// 或通过命令行参数 --clear-ai 触发
const AI_BUSINESS_TABLES = [
  'ai_chat_history',        // AI对话历史（用户会话）
  'ai_knowledge_chunks'     // 知识库文档分块（若使用文档上传功能）
];

/**
 * 检查表是否存在
 * @param {import('pg').PoolClient} client
 * @param {string} tableName
 * @returns {Promise<boolean>}
 */
const tableExists = async (client, tableName) => {
  const result = await client.query(
    `SELECT EXISTS (
       SELECT 1 FROM information_schema.tables
       WHERE table_schema = 'public' AND table_name = $1
     ) AS exists`,
    [tableName]
  );
  return result.rows[0].exists;
};

/**
 * 清空业务数据（TRUNCATE + RESTART IDENTITY + CASCADE）
 * @param {boolean} clearAi - 是否同时清空AI对话历史
 */
const clearBusinessData = async (clearAi = false) => {
  const client = await pool.connect();
  try {
    console.log('========================================');
    console.log('⚠️  警告：即将清空所有业务数据！');
    console.log('保留：用户账号、施工方案、空间类型、字典数据');
    if (clearAi) {
      console.log('额外清空：AI对话历史、知识库分块');
    }
    console.log('========================================');

    await client.query('BEGIN');

    // 筛选实际存在的表（防止某些环境未创建AI表）
    const tablesToClear = [...BUSINESS_TABLES];
    if (clearAi) {
      tablesToClear.push(...AI_BUSINESS_TABLES);
    }

    const existingTables = [];
    for (const tbl of tablesToClear) {
      if (await tableExists(client, tbl)) {
        existingTables.push(tbl);
      } else {
        console.warn(`表 ${tbl} 不存在，跳过`);
      }
    }

    if (existingTables.length === 0) {
      console.warn('没有找到任何需要清空的业务表，操作已跳过');
      await client.query('ROLLBACK');
      return;
    }

    // 一次性 TRUNCATE 所有业务表，CASCADE 会自动处理外键依赖
    // RESTART IDENTITY 重置SERIAL自增ID
    const truncateSql = `TRUNCATE TABLE ${existingTables.join(', ')} RESTART IDENTITY CASCADE`;
    console.log(`执行: ${truncateSql}`);
    await client.query(truncateSql);

    await client.query('COMMIT');

    console.log('========================================');
    console.log(`✅ 业务数据清空完成！共清空 ${existingTables.length} 张表：`);
    existingTables.forEach(t => console.log(`   - ${t}`));
    console.log('保留数据：users, space_types, construction_plans,');
    console.log('          wage_distribution_types, action_types,');
    console.log('          material_categories, material_params');
    console.log('========================================');

    logger.info('业务数据清空完成', {
      clearedTables: existingTables,
      clearAi
    });
  } catch (error) {
    await client.query('ROLLBACK');
    console.error(`❌ 清空失败: ${error.message}`);
    logger.error('业务数据清空失败', { error: error.message });
    throw error;
  } finally {
    client.release();
  }
};

// ===================== 命令行入口 =====================
if (require.main === module) {
  const args = process.argv.slice(2);
  const clearAi = args.includes('--clear-ai');
  const skipConfirm = args.includes('--yes') || args.includes('-y');

  const run = async () => {
    // 非确认模式下需要交互确认（Docker容器中运行请加 --yes 跳过）
    if (!skipConfirm) {
      console.log('');
      console.log('此操作将清空所有工程、子项目、结算、预支、消息、文件等业务数据。');
      console.log('保留用户账号、施工方案、空间类型等字典数据。');
      console.log('');
      console.log('如需跳过确认，请使用: node scripts/clear-business-data.js --yes');
      console.log('如需同时清空AI对话历史，追加: --clear-ai');
      console.log('');
      console.log('3秒后开始执行，按 Ctrl+C 取消...');
      await new Promise(resolve => setTimeout(resolve, 3000));
    }

    try {
      await clearBusinessData(clearAi);
      await pool.end();
      process.exit(0);
    } catch (err) {
      await pool.end().catch(() => {});
      process.exit(1);
    }
  };

  run();
}

module.exports = { clearBusinessData };
