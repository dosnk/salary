/**
 * 数据库完整恢复脚本（pg_restore）
 * 从 pg_dump custom 格式备份文件（backupService.js 生成的 .dump）恢复整个数据库，
 * 包含表结构、数据、索引、约束、序列（setval 值）与物化视图定义。
 *
 * 使用方法: node scripts/restore-projects.js <备份文件路径>
 *
 * 注意：
 * - 使用 pg_restore --clean --if-exists，恢复前自动删除同名对象，可重复恢复
 * - 恢复会覆盖目标库中的同名数据，请确认目标库与备份文件一致后操作
 * - 恢复完成后建议执行数据一致性校验（管理端"数据一致性校验"功能）
 */

const { execFile } = require('child_process');
const { promisify } = require('util');
const fs = require('fs');
const path = require('path');

require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const execFileAsync = promisify(execFile);

/** pg_restore 执行超时（30分钟，大库可通过环境变量 RESTORE_TIMEOUT_MS 覆盖） */
const RESTORE_TIMEOUT_MS = parseInt(process.env.RESTORE_TIMEOUT_MS || String(30 * 60 * 1000), 10);

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

  const host = process.env.DB_HOST || 'localhost';
  const port = parseInt(process.env.DB_PORT || '5432', 10);
  const database = process.env.DB_NAME || 'salary';
  const user = process.env.DB_USER || 'postgres';
  const password = process.env.DB_PASSWORD || 'postgres';

  console.log(`开始恢复数据库 ${database} <- ${backupFile} ...`);

  // 密码通过 PGPASSWORD 环境变量传入，避免出现在进程命令行
  await execFileAsync('pg_restore', [
    '-h', host,
    '-p', String(port),
    '-U', user,
    '-d', database,
    '--clean',          // 恢复前删除已存在的同名对象（表/序列/视图等）
    '--if-exists',      // 删除时忽略不存在的对象错误
    '--no-owner',       // 不恢复对象属主
    '--no-privileges',  // 不恢复对象 ACL 权限
    backupFile,
  ], {
    env: { ...process.env, PGPASSWORD: password },
    timeout: RESTORE_TIMEOUT_MS,
    maxBuffer: 16 * 1024 * 1024,
  });

  console.log('\n========================================');
  console.log('恢复完成！');
  console.log('========================================');
};

restore().catch(err => {
  console.error('恢复脚本执行失败:', err.message);
  process.exit(1);
});
