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

## 7. 常见问题

### Q1: 为什么结算状态用视图而不是表？
**A:** 避免数据冗余和不一致。视图动态计算，始终与实际数据同步。

### Q2: 工程完工后为什么子项目状态要同步更新？
**A:** 结算状态视图检查的是子项目状态，不是工程状态。工程完工时必须同步子项目状态。

### Q3: 金额为什么要在后端计算？
**A:** 确保数据一致性和安全性，避免前端篡改。
