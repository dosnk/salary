/**
 * 数据库完整备份脚本
 * 备份所有业务数据，包括工程、结算、预支、消息等
 * 
 * 使用方法: node scripts/backup-projects.js
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432'),
  database: process.env.DB_NAME || 'salary',
  user: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres'
});

const backup = async () => {
  const client = await pool.connect();
  
  try {
    console.log('开始完整备份数据...');
    
    const backupData = {
      backupTime: new Date().toISOString(),
      version: 'V2.0',
      tables: {}
    };

    // ===================== 1. 基础字典数据 =====================
    console.log('\n--- 备份基础字典数据 ---');
    
    console.log('备份用户数据...');
    const usersResult = await client.query('SELECT * FROM users ORDER BY id');
    backupData.tables.users = usersResult.rows;
    console.log(`  - 用户: ${usersResult.rows.length} 条`);

    console.log('备份空间类型...');
    const spaceTypesResult = await client.query('SELECT * FROM space_types ORDER BY id');
    backupData.tables.space_types = spaceTypesResult.rows;
    console.log(`  - 空间类型: ${spaceTypesResult.rows.length} 条`);

    console.log('备份施工方案...');
    const constructionPlansResult = await client.query('SELECT * FROM construction_plans ORDER BY id');
    backupData.tables.construction_plans = constructionPlansResult.rows;
    console.log(`  - 施工方案: ${constructionPlansResult.rows.length} 条`);

    console.log('备份工资分配方式...');
    const wageDistributionTypesResult = await client.query('SELECT * FROM wage_distribution_types ORDER BY id');
    backupData.tables.wage_distribution_types = wageDistributionTypesResult.rows;
    console.log(`  - 工资分配方式: ${wageDistributionTypesResult.rows.length} 条`);

    console.log('备份操作类型...');
    const actionTypesResult = await client.query('SELECT * FROM action_types ORDER BY code');
    backupData.tables.action_types = actionTypesResult.rows;
    console.log(`  - 操作类型: ${actionTypesResult.rows.length} 条`);

    // ===================== 2. 工程数据 =====================
    console.log('\n--- 备份工程数据 ---');
    
    console.log('备份工程数据...');
    const projectsResult = await client.query('SELECT * FROM projects ORDER BY id');
    backupData.tables.projects = projectsResult.rows;
    console.log(`  - 工程: ${projectsResult.rows.length} 条`);

    console.log('备份工程施工人员关联...');
    const projectWorkersResult = await client.query('SELECT * FROM project_workers ORDER BY id');
    backupData.tables.project_workers = projectWorkersResult.rows;
    console.log(`  - 工程施工人员: ${projectWorkersResult.rows.length} 条`);

    console.log('备份子项目数据...');
    const subprojectsResult = await client.query('SELECT * FROM subprojects ORDER BY id');
    backupData.tables.subprojects = subprojectsResult.rows;
    console.log(`  - 子项目: ${subprojectsResult.rows.length} 条`);

    console.log('备份工程历史记录...');
    const projectHistoryResult = await client.query('SELECT * FROM project_history ORDER BY id');
    backupData.tables.project_history = projectHistoryResult.rows;
    console.log(`  - 工程历史: ${projectHistoryResult.rows.length} 条`);

    console.log('备份附件数据...');
    const filesResult = await client.query('SELECT * FROM files ORDER BY id');
    backupData.tables.files = filesResult.rows;
    console.log(`  - 附件: ${filesResult.rows.length} 条`);

    // ===================== 3. 结算数据 =====================
    console.log('\n--- 备份结算数据 ---');
    
    console.log('备份工资结算记录...');
    const wageSettlementsResult = await client.query('SELECT * FROM wage_settlements ORDER BY id');
    backupData.tables.wage_settlements = wageSettlementsResult.rows;
    console.log(`  - 工资结算: ${wageSettlementsResult.rows.length} 条`);

    console.log('备份结算历史快照...');
    const wageSettlementSnapshotsResult = await client.query('SELECT * FROM wage_settlement_snapshots ORDER BY id');
    backupData.tables.wage_settlement_snapshots = wageSettlementSnapshotsResult.rows;
    console.log(`  - 结算快照: ${wageSettlementSnapshotsResult.rows.length} 条`);

    console.log('备份工资分配明细...');
    const wageDistributionsResult = await client.query('SELECT * FROM wage_distributions ORDER BY id');
    backupData.tables.wage_distributions = wageDistributionsResult.rows;
    console.log(`  - 工资分配: ${wageDistributionsResult.rows.length} 条`);

    console.log('备份用户结算状态...');
    const projectUserStatusResult = await client.query('SELECT * FROM project_user_status ORDER BY id');
    backupData.tables.project_user_status = projectUserStatusResult.rows;
    console.log(`  - 用户结算状态: ${projectUserStatusResult.rows.length} 条`);

    // ===================== 4. 预支数据 =====================
    console.log('\n--- 备份预支数据 ---');
    
    console.log('备份预支工资记录...');
    const wageAdvancesResult = await client.query('SELECT * FROM wage_advances ORDER BY id');
    backupData.tables.wage_advances = wageAdvancesResult.rows;
    console.log(`  - 预支记录: ${wageAdvancesResult.rows.length} 条`);

    // ===================== 5. 转交记录 =====================
    console.log('\n--- 备份转交记录 ---');
    
    console.log('备份子项目转交记录...');
    const subprojectTransfersResult = await client.query('SELECT * FROM subproject_transfers ORDER BY id');
    backupData.tables.subproject_transfers = subprojectTransfersResult.rows;
    console.log(`  - 转交记录: ${subprojectTransfersResult.rows.length} 条`);

    // ===================== 6. 消息数据 =====================
    console.log('\n--- 备份消息数据 ---');
    
    console.log('备份站内消息...');
    const messagesResult = await client.query('SELECT * FROM messages ORDER BY id');
    backupData.tables.messages = messagesResult.rows;
    console.log(`  - 站内消息: ${messagesResult.rows.length} 条`);

    // ===================== 7. 写入备份文件 =====================
    const backupDir = path.join(__dirname, '..', 'backups');
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(backupDir, { recursive: true });
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const backupFile = path.join(backupDir, `backup-full-${timestamp}.json`);
    
    fs.writeFileSync(backupFile, JSON.stringify(backupData, null, 2), 'utf8');
    
    // ===================== 8. 输出汇总 =====================
    console.log('\n========================================');
    console.log('备份完成！');
    console.log(`备份文件: ${backupFile}`);
    console.log('========================================');
    console.log('\n备份内容汇总:');
    console.log('\n基础数据:');
    console.log(`  - 用户: ${backupData.tables.users.length} 条`);
    console.log(`  - 空间类型: ${backupData.tables.space_types.length} 条`);
    console.log(`  - 施工方案: ${backupData.tables.construction_plans.length} 条`);
    console.log(`  - 工资分配方式: ${backupData.tables.wage_distribution_types.length} 条`);
    console.log(`  - 操作类型: ${backupData.tables.action_types.length} 条`);
    console.log('\n工程数据:');
    console.log(`  - 工程: ${backupData.tables.projects.length} 条`);
    console.log(`  - 工程施工人员: ${backupData.tables.project_workers.length} 条`);
    console.log(`  - 子项目: ${backupData.tables.subprojects.length} 条`);
    console.log(`  - 工程历史: ${backupData.tables.project_history.length} 条`);
    console.log(`  - 附件: ${backupData.tables.files.length} 条`);
    console.log('\n结算数据:');
    console.log(`  - 工资结算: ${backupData.tables.wage_settlements.length} 条`);
    console.log(`  - 结算快照: ${backupData.tables.wage_settlement_snapshots.length} 条`);
    console.log(`  - 工资分配: ${backupData.tables.wage_distributions.length} 条`);
    console.log(`  - 用户结算状态: ${backupData.tables.project_user_status.length} 条`);
    console.log('\n其他数据:');
    console.log(`  - 预支记录: ${backupData.tables.wage_advances.length} 条`);
    console.log(`  - 转交记录: ${backupData.tables.subproject_transfers.length} 条`);
    console.log(`  - 站内消息: ${backupData.tables.messages.length} 条`);
    
  } catch (error) {
    console.error('备份失败:', error);
    throw error;
  } finally {
    client.release();
    await pool.end();
  }
};

backup().catch(err => {
  console.error('备份脚本执行失败:', err);
  process.exit(1);
});
