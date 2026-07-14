# 三人行吊顶管理系统 - 数据库设计文档

## 1. 数据库概述

- 数据库类型：PostgreSQL
- 当前版本：V3.0
- 字符编码：UTF-8
- 时区：Asia/Shanghai

## 2. 核心表结构

### 2.1 用户表 (users)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| username | VARCHAR(50) | 用户名（唯一） |
| password | VARCHAR(255) | 密码（加密） |
| nickname | VARCHAR(50) | 昵称 |
| role | VARCHAR(20) | 角色：admin/documenter/constructor |
| phone | VARCHAR(20) | 手机号 |
| is_active | BOOLEAN | 是否激活 |

### 2.2 工程表 (projects)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| name | VARCHAR(100) | 工程名称（唯一） |
| description | TEXT | 工程描述 |
| status | VARCHAR(20) | 状态：preparing/constructing/completed/canceled |
| total_amount | NUMERIC(14,4) | 工程总额 |
| salary_distribution | VARCHAR(20) | 分配方式：average/work_days |
| created_by | INTEGER | 创建人ID |

**重要约束：**
- `total_amount` 必须在创建子项目时累加计算
- `status` 变为 completed 时，必须同步更新子项目状态

### 2.3 子项目表 (subprojects)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| project_id | INTEGER | 工程ID（外键） |
| space_type_id | INTEGER | 空间类型ID |
| construction_plan_id | INTEGER | 施工方案ID |
| length | NUMERIC(10,2) | 长度（米） |
| width | NUMERIC(10,2) | 宽度（米） |
| quantity | NUMERIC(14,4) | 数量（根据单位计算） |
| amount | NUMERIC(14,4) | 金额（数量 × 单价） |
| status | VARCHAR(20) | 状态：pending/completed |

**计算规则：**
- 单位为 m² 或 平方米：quantity = length × width
- 单位为 m 或 米：quantity = length
- amount = quantity × 施工方案单价

### 2.4 施工人员表 (project_workers)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| project_id | INTEGER | 工程ID（外键） |
| user_id | INTEGER | 用户ID（外键） |

### 2.5 工资结算表 (wage_settlements)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| user_id | INTEGER | 用户ID |
| project_id | INTEGER | 主工程ID |
| project_ids | JSONB | 关联工程ID数组 |
| total_amount | NUMERIC(14,4) | 结算总额 |
| start_month | VARCHAR(7) | 开始月份 |
| end_month | VARCHAR(7) | 结束月份 |
| settled_by | INTEGER | 结算人ID |
| settled_at | TIMESTAMPTZ | 结算时间 |

### 2.6 工资分配表 (wage_distributions)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| project_id | INTEGER | 工程ID |
| subproject_id | INTEGER | 子项目ID |
| user_id | INTEGER | 用户ID |
| amount | NUMERIC(14,4) | 分配金额 |
| workdays | INTEGER | 工日数 |
| quantity | NUMERIC(14,4) | 分配工程量 |
| settlement_id | INTEGER | 结算单ID |

### 2.7 预支表 (wage_advances)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | SERIAL | 主键 |
| user_id | INTEGER | 用户ID |
| amount | NUMERIC(14,4) | 预支金额 |
| reason | TEXT | 预支原因 |
| advance_date | DATE | 预支日期 |
| settled | BOOLEAN | 是否已结算 |
| settlement_id | INTEGER | 结算单ID |

## 3. 数据库视图

### 3.1 结算状态视图 (v_project_user_settlement_status)

**用途：** 动态计算每个用户在每个项目中的结算状态

```sql
CREATE OR REPLACE VIEW v_project_user_settlement_status AS
SELECT 
    ROW_NUMBER() OVER (ORDER BY p.id, u.id) AS id,
    p.id AS project_id,
    u.id AS user_id,
    CASE 
        WHEN ws.id IS NOT NULL THEN 'settled'
        WHEN EXISTS (
            SELECT 1 FROM subprojects sp 
            WHERE sp.project_id = p.id 
            AND sp.status = 'completed'
        ) THEN 'settling'
        ELSE 'unsettled'
    END AS settlement_status,
    ws.id AS settlement_id,
    ws.settled_at
FROM projects p
CROSS JOIN users u
LEFT JOIN wage_settlements ws ON 
    ws.user_id = u.id 
    AND (ws.project_id = p.id OR ws.project_ids::jsonb ? p.id::text)
WHERE 
    EXISTS (
        SELECT 1 FROM project_workers pw 
        WHERE pw.project_id = p.id 
        AND pw.user_id = u.id
    )
    OR u.role = 'admin'
    OR u.role = 'documenter';
```

**状态判断逻辑：**
1. `settled`：存在结算记录
2. `settling`：存在已完成的子项目（但未结算）
3. `unsettled`：其他情况

## 4. 字典表

### 4.1 空间类型表 (space_types)
- 客厅、卧室、厨房、卫生间等

### 4.2 施工方案表 (construction_plans)
- 名称、单位、单价

## 5. 索引优化

已创建的索引详见 `backend/scripts/init-db.js` 中的索引部分。

## 6. 数据迁移注意事项

1. **版本升级**：通过 `db_versions` 表管理
2. **增量更新**：在 `init-db.js` 的 `runIncrementalUpdates` 函数中处理
3. **数据备份**：升级前务必备份数据

## 7. 数据库维护脚本

项目提供多个维护脚本，位于 `backend/scripts/` 目录下。**所有脚本均应在后端容器内执行**：

```bash
# 进入容器执行（推荐）
docker compose exec app node scripts/<脚本名>.js
```

### 7.1 数据库初始化（`init-db.js`）

**用途**：首次部署或重建数据库时执行。增量迁移所有表结构，并插入默认用户和字典数据。

```bash
docker compose exec app node scripts/init-db.js
```

**说明**：
- 通过 `db_versions` 表记录已应用的迁移版本，仅执行新增量
- V1.7 版本已包含 AI 相关表（`material_categories`、`material_params`、`ai_chat_history`、`ai_knowledge_chunks`）的创建和默认材料分类数据
- 执行后会插入 11 个默认用户（喜临门/莫量波/梁祖霞等）和默认字典数据
- 默认密码为 `admin123`，登录后请及时修改

### 7.2 清空业务数据（`clear-business-data.js`）⚠️ 推荐

**用途**：彻底清空所有工程、子项目、结算、预支、消息、文件等业务数据，但**保留**用户账号、施工方案、空间类型、字典数据、AI 相关表。

```bash
# 容器内推荐用法（跳过3秒确认期）
docker compose exec app node scripts/clear-business-data.js --yes

# 如需同时清空 AI 对话历史和知识库分块
docker compose exec app node scripts/clear-business-data.js --yes --clear-ai
```

**参数说明**：

| 参数 | 说明 |
|------|------|
| `--yes` / `-y` | 跳过3秒确认期，立即执行（容器内推荐使用） |
| `--clear-ai` | 额外清空 `ai_chat_history` 和 `ai_knowledge_chunks` 表 |

**清空的表（12张，TRUNCATE + 重置自增ID）**：

| 表名 | 说明 |
|------|------|
| `projects` | 工程主表 |
| `subprojects` | 子项目 |
| `project_workers` | 工程施工员关联 |
| `project_history` | 工程操作历史 |
| `wage_settlements` | 结算单主表 |
| `wage_settlement_snapshots` | 结算快照（历史结算单） |
| `wage_distributions` | 工资分配（结算生成的用户分摊明细） |
| `wage_advances` | 预支记录 |
| `project_user_status` | 用户在工程中的结算状态 |
| `files` | 工程附件文件记录 |
| `subproject_transfers` | 子项目转交记录 |
| `messages` | 站内消息 |

**保留的表**：

| 表名 | 说明 |
|------|------|
| `users` | 用户账号（11个默认用户） |
| `construction_plans` | 施工方案字典 |
| `space_types` | 空间类型字典 |
| `wage_distribution_types` | 工资分配类型字典 |
| `action_types` | 动作类型字典 |
| `material_categories` | 材料分类（含默认4个分类） |
| `material_params` | 材料参数（保留用户手动添加的物料） |
| `ai_chat_history` | AI对话历史（除非加 `--clear-ai`） |
| `ai_knowledge_chunks` | 知识库分块（除非加 `--clear-ai`） |
| `db_versions` | 迁移版本记录（保留以避免重复迁移） |

**注意事项**：
- ⚠️ 此操作**不可逆**，执行前建议先备份数据库：
  ```bash
  docker compose exec db pg_dump -U salary -d salary > backup.sql
  ```
- 数据库清空后，宿主机 `backend/upload/` 目录中的工程附件文件不会自动删除，如需彻底清理：
  ```bash
  # Linux NAS
  rm -rf ./backend/upload/*
  # Windows
  Remove-Item -Path .\backend\upload\* -Recurse -Force
  ```
- 清空后无需重新初始化数据库，用户和字典数据完整保留，可立即重新录入工程数据

### 7.3 完全清空数据库（`clear-db.js`）⚠️ 慎用

**用途**：DROP 所有表和视图（包括用户和字典），返回到"空数据库"状态。执行后必须重新初始化。

```bash
# 1. 清空所有表
docker compose exec app node scripts/clear-db.js

# 2. 重新初始化（重建表 + 插入默认用户 + 字典数据 + AI表）
docker compose exec app node scripts/init-db.js
```

**与 `clear-business-data.js` 的区别**：

| 项目 | `clear-business-data.js`（方案二） | `clear-db.js`（完全清空） |
|------|----------------------------------|------------------------|
| 用户账号 | ✅ 保留 | ❌ 删除 |
| 字典数据 | ✅ 保留 | ❌ 删除 |
| AI 表 | ✅ 保留（可选清空） | ❌ 删除 |
| 业务数据 | ❌ 清空 | ❌ 清空 |
| 自增ID | 重置 | 重置 |
| 是否需要重新初始化 | 否 | 是（必须执行 `init-db.js`） |
| 推荐场景 | 日常清理业务数据 | 彻底重建数据库 |

### 7.4 备份与恢复工程数据（`backup-projects.js` / `restore-projects.js`）

**用途**：备份所有工程数据到 JSON 文件，或从备份文件恢复。

```bash
# 备份所有工程数据到 backend/temp/projects-backup-YYYYMMDD.json
docker compose exec app node scripts/backup-projects.js

# 从备份文件恢复工程数据
docker compose exec app node scripts/restore-projects.js
```

## 8. 常见问题

### Q1: 为什么结算状态用视图而不是表？
**A:** 避免数据冗余和不一致。视图动态计算，始终与实际数据同步。

### Q2: 工程完工后为什么子项目状态要同步更新？
**A:** 结算状态视图检查的是子项目状态，不是工程状态。工程完工时必须同步子项目状态。

### Q3: 金额为什么要在后端计算？
**A:** 确保数据一致性和安全性，避免前端篡改。

## 9. 测试与验证脚本

项目提供完整的测试数据生成、数据一致性校验和性能压测工具，用于验证程序在大量数据下的稳定性和准确性。

### 9.1 大数据量生成（`seed-test-data.js`）

**用途**：模拟 1~3 年的真实业务数据规模，生成工程、子项目、施工人员、历史、预支记录，用于压力测试和准确性验证。

```bash
# 默认中量级（300工程/2000子项目/100预支），跨2年
docker compose exec app node scripts/seed-test-data.js --yes

# 小量级（快速验证）
docker compose exec app node scripts/seed-test-data.js --yes --scale=small

# 大量级（深度压力测试，1000工程/8000子项目/500预支）
docker compose exec app node scripts/seed-test-data.js --yes --scale=large

# 中量级跨3年
docker compose exec app node scripts/seed-test-data.js --yes --scale=medium --years=3
```

| 参数 | 说明 |
|------|------|
| `--scale <small\|medium\|large>` | 数据量级（默认 medium） |
| `--years <1-5>` | 跨度年数（默认 2） |
| `--yes` / `-y` | 跳过3秒确认期（容器内推荐） |

**量级说明**：

| 量级 | 工程数 | 子项目数 | 预支数 | 用途 |
|------|:-----:|:-------:|:-----:|------|
| small | 50 | ~250 | 10 | 快速验证 |
| medium | 300 | ~2000 | 100 | 常规压测（推荐） |
| large | 1000 | ~8000 | 500 | 深度压力测试 |

**注意事项**：
- 执行前确保已运行 `init-db.js`，字典数据已就绪
- 建议先执行 `clear-business-data.js --yes` 清空旧业务数据
- 生成的数据 `description` 字段含"自动生成"标识，便于区分
- 不生成结算单（结算单需通过 HTTP 接口产生，保证业务流程完整）

### 9.2 数据一致性校验（`verify-data-consistency.js`）⭐ 推荐

**用途**：验证金额、统计、结算三个口径的一致性，自动化捕获金额计算误差。**只读不写**，可在任意时刻运行。

```bash
# 校验全部用户
docker compose exec app node scripts/verify-data-consistency.js

# 只校验指定用户
docker compose exec app node scripts/verify-data-consistency.js --user=5

# 自定义容差（默认0.01元）
docker compose exec app node scripts/verify-data-consistency.js --tolerance=0.001
```

| 参数 | 说明 |
|------|------|
| `--user=<id>` | 只校验指定用户（默认全部） |
| `--tolerance=<amount>` | 金额容差，默认 0.01 元 |

**8项校验内容**：

| 序号 | 校验项 | 说明 |
|:---:|--------|------|
| 1 | 工程总额一致性 | `projects.total_amount` = `SUM(subprojects.amount)` |
| 2 | 结算单总额一致性 | `wage_settlements.total_amount` = `SUM(wage_distributions.amount)` |
| 3 | 结算快照总额一致性 | 快照总额 = 工资分配明细之和（历史27.52元Bug根因） |
| 4 | 卡片1待结算工程一致性 | SQL重算 vs 手动重算 `calculateUserWage` 逻辑 |
| 5 | 卡片4月均收入一致性 | 按结算时间过滤重算 vs SQL重算 |
| 6 | 工资分配无孤儿记录 | `settlement_id` 非空但结算单已删除的异常记录 |
| 7 | 视图结算状态正确性 | `settled` 有结算单，`settling` 有完工子项目 |
| 8 | 工日数据完整性 | `work_days` 模式下所有施工人员有有效工日 |

**退出码**：`0` 全部通过 / `1` 存在不一致 / `2` 执行异常

### 9.3 性能压测

性能测试脚本位于 `backend/testfile/test/performance/`，通过 HTTP 接口黑盒压测。

#### 9.3.1 基础性能测试

```bash
# 统计查询性能（月度统计、工程列表、结算历史、并发查询）
node testfile/test/performance/statistics.bench.js

# 结算操作性能（结算预览计算、分页查询、预支查询）
node testfile/test/performance/settlement.bench.js

# 一键运行全部基础测试
bash testfile/test/performance/run-all.sh http://localhost:3000
```

#### 9.3.2 扩展压测（深页翻页+并发结算+内存泄漏检测）

```bash
# 默认配置（10并发，100轮连续请求）
node testfile/test/performance/stress.bench.js

# 自定义配置
CONCURRENT=20 ROUNDS=500 node testfile/test/performance/stress.bench.js

# 启用GC采样（需 --expose-gc 参数）
node --expose-gc testfile/test/performance/stress.bench.js

# 指定目标服务器
BASE_URL=http://192.168.1.100:3000 node testfile/test/performance/stress.bench.js
```

| 环境变量 | 默认值 | 说明 |
|---------|:-----:|------|
| `BASE_URL` | `http://localhost:3000` | 目标服务器地址 |
| `CONCURRENT` | 10 | 并发请求数 |
| `ROUNDS` | 100 | 连续请求轮数 |
| `TEST_USERNAME` | admin | 管理员账号 |
| `TEST_PASSWORD` | admin123 | 管理员密码 |
| `CTOR_USERNAME` | 杨耀贵 | 施工员账号（并发结算测试用） |
| `CTOR_PASSWORD` | admin123 | 施工员密码 |

**测试场景**：
1. **深页翻页**：翻到第10/50/100页，验证深页查询性能
2. **并发结算预览**：多请求同时预览，校验返回金额一致性（并发安全）
3. **连续请求稳定性**：连续 ROUNDS 次请求，采样内存增长，检测内存泄漏
4. **健康检查**：验证基础接口可用性

**内存泄漏判定**：每1000次请求堆内存增长 > 50MB 视为疑似泄漏。

#### 9.3.3 npm 脚本

```bash
# 基础性能测试
npm run test:perf

# 集成测试（需要测试库就绪）
npm run test:integration

# 全部jest测试
npm test
```

### 9.4 推荐测试流程

```bash
# 1. 清空旧业务数据
docker compose exec app node scripts/clear-business-data.js --yes

# 2. 生成大量测试数据
docker compose exec app node scripts/seed-test-data.js --yes --scale=large

# 3. 校验生成数据的一致性
docker compose exec app node scripts/verify-data-consistency.js

# 4. 启动服务后执行性能压测
bash testfile/test/performance/run-all.sh http://localhost:3000

# 5. 通过Android端执行结算操作后，再次校验一致性
docker compose exec app node scripts/verify-data-consistency.js

# 6. 清理测试数据
docker compose exec app node scripts/clear-business-data.js --yes
```
