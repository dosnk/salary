#!/bin/bash
# ====================================================================
# 旧库 → 新库 数据迁移脚本
#
# 使用场景：
#   旧库在腾讯云（不可直连），新库在本地/Docker
#   新库已通过 init-db.js 初始化表结构（含 V1.9 物化视图 + V2.0 remark 列）
#   新库为空库（只有 V1.5 字典数据 + 默认用户，无业务数据）
#
# 迁移范围：
#   ✅ 用户表（users）— 保留旧库 ID，用户可用旧密码登录
#   ✅ 业务数据（projects、subprojects、wage_settlements 等）
#   ✅ 附件记录（files 表）— 附件文件需用户自行按原路径迁移
#   ❌ 字典表（space_types、construction_plans 等）— 新库已有，跳过
#   ❌ 迁移版本表（db_versions）— 各自保留
#   ❌ AI 相关表 — 旧库无
#
# 结构差异处理：
#   1. projects.remark 列：新库有，旧库无。导入时 remark 自动为 NULL
#      （后续可手工用 description 填充，或让用户重新编辑）
#   2. 物化视图 mv_project_user_settlement_status：导入完成后刷新
#   3. length/width 单位：旧库新库都是厘米，无需换算
# ====================================================================

set -e  # 任何命令失败立即退出

# ====================================================================
# 配置（请根据实际情况修改）
# ====================================================================

# 旧库连接信息（腾讯云）
OLD_DB_HOST="腾讯云IP"
OLD_DB_PORT="5432"
OLD_DB_USER="postgres"
OLD_DB_NAME="salary_system"
OLD_DB_PASSWORD="你的旧库密码"

# 新库连接信息（本地/Docker）
NEW_DB_HOST="127.0.0.1"
NEW_DB_PORT="5432"
NEW_DB_USER="postgres"
NEW_DB_NAME="salary_system"
NEW_DB_PASSWORD="你的新库密码"

# 导出文件路径
DUMP_FILE="/tmp/old_data_dump.sql"

echo "=========================================="
echo "  旧库 → 新库 数据迁移"
echo "=========================================="

# ====================================================================
# 步骤 1：在腾讯云旧库导出数据（仅数据，不含表结构）
# ====================================================================
echo ""
echo "[步骤 1] 从旧库导出业务数据..."

# 导出全部数据（含 users、业务数据、files）
# 排除 db_versions（迁移版本表，各库独立维护）
# 使用 --column-inserts 生成显式列名的 INSERT，便于处理字段差异
PGPASSWORD="$OLD_DB_PASSWORD" pg_dump \
  -h "$OLD_DB_HOST" \
  -p "$OLD_DB_PORT" \
  -U "$OLD_DB_USER" \
  -d "$OLD_DB_NAME" \
  --data-only \
  --column-inserts \
  --no-owner \
  --no-privileges \
  --exclude-table=db_versions \
  > "$DUMP_FILE"

echo "  ✅ 导出完成: $DUMP_FILE"
echo "  文件大小: $(du -h $DUMP_FILE | cut -f1)"

# ====================================================================
# 步骤 2：传输到新库服务器
# ====================================================================
echo ""
echo "[步骤 2] 请将 $DUMP_FILE 传输到新库服务器"
echo "  方式1: scp $DUMP_FILE 新服务器:/tmp/"
echo "  方式2: 通过 SFTP 工具上传"
echo ""
read -p "  文件已传输到新库服务器? (y/N): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
  echo "  请先传输文件，然后重新运行此脚本的后半部分"
  exit 0
fi

# ====================================================================
# 步骤 3：在新库准备（清空旧字典和默认用户，避免主键冲突）
# ====================================================================
echo ""
echo "[步骤 3] 清空新库已有数据（字典+默认用户，避免主键冲突）..."

PGPASSWORD="$NEW_DB_PASSWORD" psql \
  -h "$NEW_DB_HOST" \
  -p "$NEW_DB_PORT" \
  -U "$NEW_DB_USER" \
  -d "$NEW_DB_NAME" \
  <<'EOF'
-- 按外键依赖反序清空所有业务表
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
  users
CASCADE;

-- 重置所有序列到初始值（导入旧数据后会重新设置）
SELECT setval(pg_get_serial_sequence('users', 'id'), 1, false) WHERE EXISTS (SELECT 1 FROM users);
-- 其他序列在步骤5统一重置

\echo '  ✅ 新库已清空'
EOF

# ====================================================================
# 步骤 4：导入旧库数据
# ====================================================================
echo ""
echo "[步骤 4] 导入旧库数据到新库..."

PGPASSWORD="$NEW_DB_PASSWORD" psql \
  -h "$NEW_DB_HOST" \
  -p "$NEW_DB_PORT" \
  -U "$NEW_DB_USER" \
  -d "$NEW_DB_NAME" \
  -f "$DUMP_FILE"

echo "  ✅ 数据导入完成"

# ====================================================================
# 步骤 5：重置所有序列（关键！导入数据后序列需同步到最大ID）
# ====================================================================
echo ""
echo "[步骤 5] 重置所有表序列到最大ID..."

PGPASSWORD="$NEW_DB_PASSWORD" psql \
  -h "$NEW_DB_HOST" \
  -p "$NEW_DB_PORT" \
  -U "$NEW_DB_USER" \
  -d "$NEW_DB_NAME" \
  <<'EOF'
-- 重置所有 SERIAL 序列到对应表的最大 ID
-- 不重置会导致后续 INSERT 报主键冲突（序列从1开始，但已导入的ID可能很大）

DO $$
DECLARE
  r RECORD;
BEGIN
  -- 查询所有有 SERIAL/IDENTITY 序列的表
  FOR r IN
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
  LOOP
    -- 动态执行 setval，将序列设为该列当前最大值
    EXECUTE format(
      'SELECT setval(%L, COALESCE((SELECT MAX(%I) FROM %I), 1), true)',
      r.sequence_name,
      r.column_name,
      r.table_name
    );
    RAISE NOTICE '已重置序列: %.%', r.table_name, r.column_name;
  END LOOP;
END $$;

\echo '  ✅ 所有序列已重置'
EOF

# ====================================================================
# 步骤 6：刷新物化视图
# ====================================================================
echo ""
echo "[步骤 6] 刷新物化视图..."

PGPASSWORD="$NEW_DB_PASSWORD" psql \
  -h "$NEW_DB_HOST" \
  -p "$NEW_DB_PORT" \
  -U "$NEW_DB_USER" \
  -d "$NEW_DB_NAME" \
  -c "REFRESH MATERIALIZED VIEW mv_project_user_settlement_status;"

echo "  ✅ 物化视图已刷新"

# ====================================================================
# 步骤 7：数据校验
# ====================================================================
echo ""
echo "[步骤 7] 数据校验..."

PGPASSWORD="$NEW_DB_PASSWORD" psql \
  -h "$NEW_DB_HOST" \
  -p "$NEW_DB_PORT" \
  -U "$NEW_DB_USER" \
  -d "$NEW_DB_NAME" \
  <<'EOF'
\echo ''
\echo '========== 数据量统计 =========='
SELECT 'users' AS 表名, COUNT(*) AS 记录数 FROM users
UNION ALL SELECT 'projects', COUNT(*) FROM projects
UNION ALL SELECT 'subprojects', COUNT(*) FROM subprojects
UNION ALL SELECT 'project_workers', COUNT(*) FROM project_workers
UNION ALL SELECT 'wage_settlements', COUNT(*) FROM wage_settlements
UNION ALL SELECT 'wage_distributions', COUNT(*) FROM wage_distributions
UNION ALL SELECT 'wage_advances', COUNT(*) FROM wage_advances
UNION ALL SELECT 'files', COUNT(*) FROM files
UNION ALL SELECT 'project_history', COUNT(*) FROM project_history
UNION ALL SELECT 'project_user_status', COUNT(*) FROM project_user_status
UNION ALL SELECT 'wage_settlement_snapshots', COUNT(*) FROM wage_settlement_snapshots
UNION ALL SELECT 'space_types', COUNT(*) FROM space_types
UNION ALL SELECT 'construction_plans', COUNT(*) FROM construction_plans
ORDER BY 表名;

\echo ''
\echo '========== 序列检查 =========='
\echo '下次 users.id 将使用:'
SELECT nextval('users_id_seq');
\echo '（已重置，请忽略上面的数字，实际下次INSERT会正确递增）'

\echo ''
\echo '========== 金额校验 =========='
SELECT 
  '工程总额合计' AS 项目, 
  SUM(total_amount) AS 金额 
FROM projects
UNION ALL
SELECT '子项目金额合计', SUM(amount) FROM subprojects
UNION ALL
SELECT '结算单总额合计', SUM(total_amount) FROM wage_settlements
UNION ALL
SELECT '预支总额合计', SUM(advance_amount) FROM wage_advances;
EOF

echo ""
echo "=========================================="
echo "  ✅ 迁移完成！"
echo "=========================================="
echo ""
echo "后续操作："
echo "  1. 迁移附件文件到原路径（files.path 保持旧库路径）"
echo "  2. 重启后端服务"
echo "  3. 用旧库用户账号登录验证"
echo "  4. 检查工程列表、结算记录、附件显示是否正常"
