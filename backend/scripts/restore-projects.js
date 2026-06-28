/**
 * 数据库完整恢复脚本
 * 从备份文件恢复所有业务数据
 * 
 * 使用方法: node scripts/restore-projects.js <备份文件路径>
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

const restore = async () => {
  const backupFile = process.argv[2];
  
  if (!backupFile) {
    console.error('请指定备份文件路径');
    console.log('使用方法: node scripts/restore-projects.js <备份文件路径>');
    process.exit(1);
  }

  if (!fs.existsSync(backupFile)) {
    console.error(`备份文件不存在: ${backupFile}`);
    process.exit(1);
  }

  const client = await pool.connect();
  
  try {
    console.log('读取备份文件...');
    const backupData = JSON.parse(fs.readFileSync(backupFile, 'utf8'));
    console.log(`备份时间: ${backupData.backupTime}`);
    console.log(`备份版本: ${backupData.version || 'V1.0'}`);

    await client.query('BEGIN');

    // ===================== 1. 恢复基础字典数据 =====================
    console.log('\n--- 恢复基础字典数据 ---');
    
    // 1. 恢复用户数据
    console.log('恢复用户数据...');
    for (const user of backupData.tables.users || []) {
      await client.query(`
        INSERT INTO users (id, username, password, nickname, phone, role, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        ON CONFLICT (id) DO UPDATE SET
          username = EXCLUDED.username,
          password = EXCLUDED.password,
          nickname = EXCLUDED.nickname,
          phone = EXCLUDED.phone,
          role = EXCLUDED.role,
          updated_at = EXCLUDED.updated_at
      `, [user.id, user.username, user.password, user.nickname, user.phone, user.role, user.created_at, user.updated_at]);
    }
    console.log(`  - 用户: ${backupData.tables.users?.length || 0} 条`);

    // 2. 恢复空间类型
    console.log('恢复空间类型...');
    for (const item of backupData.tables.space_types || []) {
      await client.query(`
        INSERT INTO space_types (id, name, created_at, updated_at)
        VALUES ($1, $2, $3, $4)
        ON CONFLICT (id) DO UPDATE SET
          name = EXCLUDED.name,
          updated_at = EXCLUDED.updated_at
      `, [item.id, item.name, item.created_at, item.updated_at]);
    }
    console.log(`  - 空间类型: ${backupData.tables.space_types?.length || 0} 条`);

    // 3. 恢复施工方案
    console.log('恢复施工方案...');
    for (const item of backupData.tables.construction_plans || []) {
      await client.query(`
        INSERT INTO construction_plans (id, name, unit, price, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (id) DO UPDATE SET
          name = EXCLUDED.name,
          unit = EXCLUDED.unit,
          price = EXCLUDED.price,
          updated_at = EXCLUDED.updated_at
      `, [item.id, item.name, item.unit, item.price, item.created_at, item.updated_at]);
    }
    console.log(`  - 施工方案: ${backupData.tables.construction_plans?.length || 0} 条`);

    // 4. 恢复工资分配方式
    console.log('恢复工资分配方式...');
    for (const item of backupData.tables.wage_distribution_types || []) {
      await client.query(`
        INSERT INTO wage_distribution_types (id, name, code, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5)
        ON CONFLICT (id) DO UPDATE SET
          name = EXCLUDED.name,
          code = EXCLUDED.code,
          updated_at = EXCLUDED.updated_at
      `, [item.id, item.name, item.code, item.created_at, item.updated_at]);
    }
    console.log(`  - 工资分配方式: ${backupData.tables.wage_distribution_types?.length || 0} 条`);

    // 5. 恢复操作类型
    console.log('恢复操作类型...');
    for (const item of backupData.tables.action_types || []) {
      await client.query(`
        INSERT INTO action_types (code, name, created_at)
        VALUES ($1, $2, $3)
        ON CONFLICT (code) DO UPDATE SET
          name = EXCLUDED.name
      `, [item.code, item.name, item.created_at]);
    }
    console.log(`  - 操作类型: ${backupData.tables.action_types?.length || 0} 条`);

    // ===================== 2. 恢复工程数据 =====================
    console.log('\n--- 恢复工程数据 ---');
    
    // 6. 恢复工程数据
    console.log('恢复工程数据...');
    for (const item of backupData.tables.projects || []) {
      await client.query(`
        INSERT INTO projects (id, name, description, status, total_amount, salary_distribution, total_work_days, settled_by, created_by, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
        ON CONFLICT (id) DO UPDATE SET
          name = EXCLUDED.name,
          description = EXCLUDED.description,
          status = EXCLUDED.status,
          total_amount = EXCLUDED.total_amount,
          salary_distribution = EXCLUDED.salary_distribution,
          total_work_days = EXCLUDED.total_work_days,
          settled_by = EXCLUDED.settled_by,
          created_by = EXCLUDED.created_by,
          updated_at = EXCLUDED.updated_at
      `, [item.id, item.name, item.description, item.status, item.total_amount, item.salary_distribution, item.total_work_days, item.settled_by, item.created_by, item.created_at, item.updated_at]);
    }
    console.log(`  - 工程: ${backupData.tables.projects?.length || 0} 条`);

    // 7. 恢复工程施工人员关联
    console.log('恢复工程施工人员关联...');
    for (const item of backupData.tables.project_workers || []) {
      await client.query(`
        INSERT INTO project_workers (id, project_id, user_id, workdays, created_at)
        VALUES ($1, $2, $3, $4, $5)
        ON CONFLICT (project_id, user_id) DO UPDATE SET
          workdays = EXCLUDED.workdays
      `, [item.id, item.project_id, item.user_id, item.workdays, item.created_at]);
    }
    console.log(`  - 工程施工人员: ${backupData.tables.project_workers?.length || 0} 条`);

    // 8. 恢复子项目数据
    console.log('恢复子项目数据...');
    for (const item of backupData.tables.subprojects || []) {
      await client.query(`
        INSERT INTO subprojects (id, project_id, space_type_id, construction_plan_id, length, width, quantity, amount, status, remark, created_by, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
        ON CONFLICT (id) DO UPDATE SET
          project_id = EXCLUDED.project_id,
          space_type_id = EXCLUDED.space_type_id,
          construction_plan_id = EXCLUDED.construction_plan_id,
          length = EXCLUDED.length,
          width = EXCLUDED.width,
          quantity = EXCLUDED.quantity,
          amount = EXCLUDED.amount,
          status = EXCLUDED.status,
          remark = EXCLUDED.remark,
          updated_at = EXCLUDED.updated_at
      `, [item.id, item.project_id, item.space_type_id, item.construction_plan_id, item.length, item.width, item.quantity, item.amount, item.status, item.remark, item.created_by, item.created_at, item.updated_at]);
    }
    console.log(`  - 子项目: ${backupData.tables.subprojects?.length || 0} 条`);

    // 9. 恢复工程历史记录
    console.log('恢复工程历史记录...');
    for (const item of backupData.tables.project_history || []) {
      await client.query(`
        INSERT INTO project_history (id, project_id, action, description, performed_by, created_at)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (id) DO UPDATE SET
          project_id = EXCLUDED.project_id,
          action = EXCLUDED.action,
          description = EXCLUDED.description,
          performed_by = EXCLUDED.performed_by
      `, [item.id, item.project_id, item.action, item.description, item.performed_by, item.created_at]);
    }
    console.log(`  - 工程历史: ${backupData.tables.project_history?.length || 0} 条`);

    // 10. 恢复附件数据
    console.log('恢复附件数据...');
    for (const item of backupData.tables.files || []) {
      await client.query(`
        INSERT INTO files (id, project_id, filename, original_name, path, size, type, uploaded_by, created_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
        ON CONFLICT (id) DO UPDATE SET
          project_id = EXCLUDED.project_id,
          filename = EXCLUDED.filename,
          original_name = EXCLUDED.original_name,
          path = EXCLUDED.path,
          size = EXCLUDED.size,
          type = EXCLUDED.type,
          uploaded_by = EXCLUDED.uploaded_by
      `, [item.id, item.project_id, item.filename, item.original_name, item.path, item.size, item.type, item.uploaded_by, item.created_at]);
    }
    console.log(`  - 附件: ${backupData.tables.files?.length || 0} 条`);

    // ===================== 3. 恢复结算数据 =====================
    console.log('\n--- 恢复结算数据 ---');
    
    // 11. 恢复工资结算记录
    console.log('恢复工资结算记录...');
    for (const item of backupData.tables.wage_settlements || []) {
      await client.query(`
        INSERT INTO wage_settlements (id, settlement_no, project_id, project_ids, user_id, start_month, end_month, total_amount, advance_amount, actual_amount, confirmed, confirmed_at, paid, paid_at, settled_by, settled_at, remark)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17)
        ON CONFLICT (id) DO UPDATE SET
          settlement_no = EXCLUDED.settlement_no,
          project_id = EXCLUDED.project_id,
          project_ids = EXCLUDED.project_ids,
          user_id = EXCLUDED.user_id,
          start_month = EXCLUDED.start_month,
          end_month = EXCLUDED.end_month,
          total_amount = EXCLUDED.total_amount,
          advance_amount = EXCLUDED.advance_amount,
          actual_amount = EXCLUDED.actual_amount,
          confirmed = EXCLUDED.confirmed,
          confirmed_at = EXCLUDED.confirmed_at,
          paid = EXCLUDED.paid,
          paid_at = EXCLUDED.paid_at,
          settled_by = EXCLUDED.settled_by,
          settled_at = EXCLUDED.settled_at,
          remark = EXCLUDED.remark
      `, [item.id, item.settlement_no, item.project_id, JSON.stringify(item.project_ids), item.user_id, item.start_month, item.end_month, item.total_amount, item.advance_amount, item.actual_amount, item.confirmed, item.confirmed_at, item.paid, item.paid_at, item.settled_by, item.settled_at, item.remark]);
    }
    console.log(`  - 工资结算: ${backupData.tables.wage_settlements?.length || 0} 条`);

    // 12. 恢复结算历史快照
    console.log('恢复结算历史快照...');
    for (const item of backupData.tables.wage_settlement_snapshots || []) {
      await client.query(`
        INSERT INTO wage_settlement_snapshots (id, settlement_id, settlement_no, project_id, project_name, user_id, username, nickname, start_month, end_month, total_amount, advance_amount, actual_amount, confirmed, confirmed_at, settled_by, settled_by_username, settled_by_nickname, settled_at, remark, projects_snapshot, plans_snapshot, advances_snapshot, calculation_snapshot, created_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23, $24, $25)
        ON CONFLICT (id) DO UPDATE SET
          settlement_id = EXCLUDED.settlement_id,
          settlement_no = EXCLUDED.settlement_no,
          project_id = EXCLUDED.project_id,
          project_name = EXCLUDED.project_name,
          user_id = EXCLUDED.user_id,
          username = EXCLUDED.username,
          nickname = EXCLUDED.nickname,
          start_month = EXCLUDED.start_month,
          end_month = EXCLUDED.end_month,
          total_amount = EXCLUDED.total_amount,
          advance_amount = EXCLUDED.advance_amount,
          actual_amount = EXCLUDED.actual_amount,
          confirmed = EXCLUDED.confirmed,
          confirmed_at = EXCLUDED.confirmed_at,
          settled_by = EXCLUDED.settled_by,
          settled_by_username = EXCLUDED.settled_by_username,
          settled_by_nickname = EXCLUDED.settled_by_nickname,
          settled_at = EXCLUDED.settled_at,
          remark = EXCLUDED.remark,
          projects_snapshot = EXCLUDED.projects_snapshot,
          plans_snapshot = EXCLUDED.plans_snapshot,
          advances_snapshot = EXCLUDED.advances_snapshot,
          calculation_snapshot = EXCLUDED.calculation_snapshot
      `, [item.id, item.settlement_id, item.settlement_no, item.project_id, item.project_name, item.user_id, item.username, item.nickname, item.start_month, item.end_month, item.total_amount, item.advance_amount, item.actual_amount, item.confirmed, item.confirmed_at, item.settled_by, item.settled_by_username, item.settled_by_nickname, item.settled_at, item.remark, JSON.stringify(item.projects_snapshot), JSON.stringify(item.plans_snapshot), JSON.stringify(item.advances_snapshot), JSON.stringify(item.calculation_snapshot), item.created_at]);
    }
    console.log(`  - 结算快照: ${backupData.tables.wage_settlement_snapshots?.length || 0} 条`);

    // 13. 恢复工资分配明细
    console.log('恢复工资分配明细...');
    for (const item of backupData.tables.wage_distributions || []) {
      await client.query(`
        INSERT INTO wage_distributions (id, subproject_id, user_id, workdays, quantity, amount, settlement_id, created_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        ON CONFLICT (id) DO UPDATE SET
          subproject_id = EXCLUDED.subproject_id,
          user_id = EXCLUDED.user_id,
          workdays = EXCLUDED.workdays,
          quantity = EXCLUDED.quantity,
          amount = EXCLUDED.amount,
          settlement_id = EXCLUDED.settlement_id
      `, [item.id, item.subproject_id, item.user_id, item.workdays, item.quantity, item.amount, item.settlement_id, item.created_at]);
    }
    console.log(`  - 工资分配: ${backupData.tables.wage_distributions?.length || 0} 条`);

    // 14. 恢复用户结算状态
    console.log('恢复用户结算状态...');
    for (const item of backupData.tables.project_user_status || []) {
      await client.query(`
        INSERT INTO project_user_status (id, project_id, user_id, settlement_status, settlement_id, settled_at, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        ON CONFLICT (project_id, user_id) DO UPDATE SET
          settlement_status = EXCLUDED.settlement_status,
          settlement_id = EXCLUDED.settlement_id,
          settled_at = EXCLUDED.settled_at,
          updated_at = EXCLUDED.updated_at
      `, [item.id, item.project_id, item.user_id, item.settlement_status, item.settlement_id, item.settled_at, item.created_at, item.updated_at]);
    }
    console.log(`  - 用户结算状态: ${backupData.tables.project_user_status?.length || 0} 条`);

    // ===================== 4. 恢复预支数据 =====================
    console.log('\n--- 恢复预支数据 ---');
    
    // 15. 恢复预支工资记录
    console.log('恢复预支工资记录...');
    for (const item of backupData.tables.wage_advances || []) {
      await client.query(`
        INSERT INTO wage_advances (id, user_id, advance_amount, advance_date, settled, settlement_id, created_by, created_at, remark)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
        ON CONFLICT (id) DO UPDATE SET
          user_id = EXCLUDED.user_id,
          advance_amount = EXCLUDED.advance_amount,
          advance_date = EXCLUDED.advance_date,
          settled = EXCLUDED.settled,
          settlement_id = EXCLUDED.settlement_id,
          created_by = EXCLUDED.created_by,
          remark = EXCLUDED.remark
      `, [item.id, item.user_id, item.advance_amount, item.advance_date, item.settled, item.settlement_id, item.created_by, item.created_at, item.remark]);
    }
    console.log(`  - 预支记录: ${backupData.tables.wage_advances?.length || 0} 条`);

    // ===================== 5. 恢复转交记录 =====================
    console.log('\n--- 恢复转交记录 ---');
    
    // 16. 恢复子项目转交记录
    console.log('恢复子项目转交记录...');
    for (const item of backupData.tables.subproject_transfers || []) {
      await client.query(`
        INSERT INTO subproject_transfers (id, subproject_id, from_user_id, to_user_id, transfer_reason, transferred_at, transferred_by)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
        ON CONFLICT (id) DO UPDATE SET
          subproject_id = EXCLUDED.subproject_id,
          from_user_id = EXCLUDED.from_user_id,
          to_user_id = EXCLUDED.to_user_id,
          transfer_reason = EXCLUDED.transfer_reason,
          transferred_at = EXCLUDED.transferred_at,
          transferred_by = EXCLUDED.transferred_by
      `, [item.id, item.subproject_id, item.from_user_id, item.to_user_id, item.transfer_reason, item.transferred_at, item.transferred_by]);
    }
    console.log(`  - 转交记录: ${backupData.tables.subproject_transfers?.length || 0} 条`);

    // ===================== 6. 恢复消息数据 =====================
    console.log('\n--- 恢复消息数据 ---');
    
    // 17. 恢复站内消息
    console.log('恢复站内消息...');
    for (const item of backupData.tables.messages || []) {
      await client.query(`
        INSERT INTO messages (id, user_id, title, content, type, is_read, related_type, related_id, created_at, created_by)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        ON CONFLICT (id) DO UPDATE SET
          user_id = EXCLUDED.user_id,
          title = EXCLUDED.title,
          content = EXCLUDED.content,
          type = EXCLUDED.type,
          is_read = EXCLUDED.is_read,
          related_type = EXCLUDED.related_type,
          related_id = EXCLUDED.related_id,
          created_by = EXCLUDED.created_by
      `, [item.id, item.user_id, item.title, item.content, item.type, item.is_read, item.related_type, item.related_id, item.created_at, item.created_by]);
    }
    console.log(`  - 站内消息: ${backupData.tables.messages?.length || 0} 条`);

    // ===================== 7. 重置序列 =====================
    console.log('\n--- 重置序列 ---');
    await client.query(`SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users))`);
    await client.query(`SELECT setval('projects_id_seq', (SELECT COALESCE(MAX(id), 1) FROM projects))`);
    await client.query(`SELECT setval('project_workers_id_seq', (SELECT COALESCE(MAX(id), 1) FROM project_workers))`);
    await client.query(`SELECT setval('subprojects_id_seq', (SELECT COALESCE(MAX(id), 1) FROM subprojects))`);
    await client.query(`SELECT setval('files_id_seq', (SELECT COALESCE(MAX(id), 1) FROM files))`);
    await client.query(`SELECT setval('space_types_id_seq', (SELECT COALESCE(MAX(id), 1) FROM space_types))`);
    await client.query(`SELECT setval('construction_plans_id_seq', (SELECT COALESCE(MAX(id), 1) FROM construction_plans))`);
    await client.query(`SELECT setval('wage_distribution_types_id_seq', (SELECT COALESCE(MAX(id), 1) FROM wage_distribution_types))`);
    await client.query(`SELECT setval('wage_settlements_id_seq', (SELECT COALESCE(MAX(id), 1) FROM wage_settlements))`);
    await client.query(`SELECT setval('wage_settlement_snapshots_id_seq', (SELECT COALESCE(MAX(id), 1) FROM wage_settlement_snapshots))`);
    await client.query(`SELECT setval('wage_distributions_id_seq', (SELECT COALESCE(MAX(id), 1) FROM wage_distributions))`);
    await client.query(`SELECT setval('wage_advances_id_seq', (SELECT COALESCE(MAX(id), 1) FROM wage_advances))`);
    await client.query(`SELECT setval('project_user_status_id_seq', (SELECT COALESCE(MAX(id), 1) FROM project_user_status))`);
    await client.query(`SELECT setval('project_history_id_seq', (SELECT COALESCE(MAX(id), 1) FROM project_history))`);
    await client.query(`SELECT setval('subproject_transfers_id_seq', (SELECT COALESCE(MAX(id), 1) FROM subproject_transfers))`);
    await client.query(`SELECT setval('messages_id_seq', (SELECT COALESCE(MAX(id), 1) FROM messages))`);
    console.log('序列重置完成');

    await client.query('COMMIT');

    console.log('\n========================================');
    console.log('恢复完成！');
    console.log('========================================');

  } catch (error) {
    await client.query('ROLLBACK');
    console.error('恢复失败:', error);
    throw error;
  } finally {
    client.release();
    await pool.end();
  }
};

restore().catch(err => {
  console.error('恢复脚本执行失败:', err);
  process.exit(1);
});
