# 三人行吊顶管理系统 - 开发说明书 V4.0

> 本文档记录系统的优化方案、权限设计、AI功能设计、排料引擎等关键设计决策。
> 所有开发改动以本文档为准。

---

## 一、优化总览

### 1.1 优化阶段

| 阶段 | 优先级 | 内容 | 状态 |
|------|--------|------|------|
| 阶段1 | P0 | 安全加固（SQL注入、JWT、密码、限流） | 待实施 |
| 阶段2 | P0 | 用户权限重构（RBAC） | 待实施 |
| 阶段3 | P1 | 工程管理优化（Service抽离、查询构建器） | 待实施 |
| 阶段4 | P1 | 统计结算优化（Redis缓存、异步导出） | 待实施 |
| 阶段5 | P2 | 架构优化（分层、定时任务） | 待实施 |
| 阶段6 | P2 | AI功能集成（大模型、排料引擎、知识库） | 待实施 |

### 1.2 目标架构

```
controllers → services → repositories → pool
     ↓              ↓            ↓
  请求处理      业务逻辑层    数据访问层
```

### 1.3 后端技术栈升级方案

#### 现状分析

| 类别 | 当前 | 问题 |
|------|------|------|
| 语言 | JavaScript | 无类型安全，维护困难 |
| 数据库访问 | pg 原生SQL | 字符串拼接有注入风险，重复代码多 |
| ORM/QueryBuilder | 无 | 手写SQL，代码冗余 |
| 缓存 | Redis(已配置未用) | 统计查询每次都全量实时计算 |
| 定时任务 | 无 | 缓存刷新/数据清理无法自动化 |
| 异步队列 | 无 | Excel导出同步处理可能超时 |
| 类型系统 | 无 | 大型项目维护困难 |

#### 升级路线

```
JavaScript → TypeScript 5.x        (类型安全)
pg 原生SQL → Knex.js               (Query Builder，防注入)
无缓存     → Redis 正式使用          (统计缓存 + 会话)
无队列     → BullMQ                 (异步Excel导出 + 定时任务)
无类型ORM  → Prisma(可选，后期)     (类型安全ORM)
```

#### 新增依赖

```json
{
  "devDependencies": {
    "typescript": "^5.7.0",
    "ts-node": "^10.9.0",
    "@types/node": "^22.0.0",
    "@types/koa": "^2.15.0",
    "@types/koa-router": "^7.4.0",
    "@types/koa-bodyparser": "^4.3.0",
    "@types/jsonwebtoken": "^9.0.0",
    "@types/bcryptjs": "^2.4.0"
  },
  "dependencies": {
    "knex": "^3.1.0",
    "bullmq": "^5.0.0"
  }
}
```

#### 升级路径 (渐进式)

```
阶段A (立即): TypeScript + Knex.js
  ├── tsconfig.json 配置 (allowJs: true 兼容旧JS)
  ├── 新建文件用 .ts，旧 .js 文件逐步改后缀
  ├── Knex.js 封装数据库操作，替代裸SQL
  └── 消除 statistics.js 等文件中的字符串拼接SQL

阶段B (短期): Redis缓存 + BullMQ
  ├── 统计查询结果缓存(30分钟TTL)
  ├── 字典数据永久缓存(变更时失效)
  ├── Excel导出改为异步队列任务
  └── 每日统计预计算定时任务

阶段C (中期): Prisma(可选) + Worker Threads
  ├── Prisma ORM 替代 Knex (更好的类型推导)
  └── 排料计算放入 Worker Thread 避免阻塞
```

---

## 二、权限体系设计 V2.0

### 2.1 角色定义

| 角色 | 代码 | 定位 |
|------|------|------|
| 管理员 | admin | 只读监管 + 系统配置，**不干预业务操作** |
| 施工员 | constructor | 对自己参与的工程有完全操作权 |
| 资料员 | documenter | 查看全部工程(只读) + 新建工程 |

### 2.2 权限矩阵（最终版）

| 权限 | admin | constructor | documenter |
|------|:-----:|:-----------:|:----------:|
| 用户管理 | 全部 | 仅自己 | 仅自己 |
| 工程查看 | 全部 | 自己参与的 | 全部(只读) |
| 工程创建 | ✓ | ✓ | ✓ |
| 工程修改 | ✗ | 自己参与的 | ✗ |
| 工程删除 | ✗ | 自己参与的 | ✗ |
| 子项目管理(增删改) | ✗ | 自己参与的 | ✗ |
| 结算操作 | ✗ | 工程施工人员(自确认) | ✗ |
| 预支管理(增删改) | ✗(只能查看) | 自己 | ✗ |
| 统计查看 | ✓ | ✓(自己的) | ✗ |
| 字典管理 | ✓ | ✗ | ✗ |
| 数据迁移 | ✓ | ✗ | ✗ |
| AI助手 | ✓(全部数据) | ✓(自己数据) | ✓(查看数据) |

### 2.3 施工员权限判断逻辑

```
判断条件:
├── 工程创建者是自己 (projects.created_by = currentUserId)
└── 或者 自己是工程的施工人员 (project_workers.user_id = currentUserId)

SQL示例:
WHERE project_id IN (
  SELECT id FROM projects WHERE created_by = $1
  UNION
  SELECT project_id FROM project_workers WHERE user_id = $1
)
```

### 2.4 RBAC实现方案

- 新建 `middleware/rbac.js` 统一权限入口
- 将controller中的 `isAdmin()` / `isConstructor()` / `canModifyProject()` 检查收敛到中间件
- 新增资源级数据过滤：施工员自动只查自己参与的工程数据

---

## 三、结算流程 V2.0（简化版）

```
旧流程(废弃): 选择工程 → 预览计算 → 提交结算 → 管理员审批 → 确认完成
新流程:       选择工程 → 预览计算 → 提交结算 → 施工员确认完成
```

- **不再需要管理员审批**，施工员对自己的工程自主完成结算确认
- 施工员只能结算自己参与且已完工(completed)的工程

### 3.1 结算维度设计决策

> **决策：结算按工程维度触发，而非按月份维度。**

- 前端交互流程：选择工程 → 预览计算（按工程） → 提交结算 → 施工员确认
- 当前后端 `POST /settlements` 接口接收 `startMonth/endMonth` 参数，**需调整为支持按工程ID结算**
- 后端 `POST /calculate` 已支持按 projectIds 预览，与前端设计一致
- 后端待改造项：`POST /settlements` 增加 `projectIds` 参数，支持按工程维度创建结算单

---

## 四、AI功能设计

### 4.1 AI核心能力

| 能力 | 类型 | 说明 | 权限控制 |
|------|------|------|---------|
| 排料计算 | 本地引擎 | 根据空间+材料参数计算面材/龙骨/配件需求 | 按用户角色过滤 |
| 工程查询 | FunctionCall | 查询用户有权限的工程 | 按用户角色过滤 |
| 统计查询 | FunctionCall | 查询收入、工程数量等统计 | 施工员只看自己 |
| 结算查询 | FunctionCall | 查询结算单详情 | 只能看自己的 |
| 智能分析 | LLM | 收入趋势分析、建议 | 只能分析自己数据 |
| 知识问答 | LLM+RAG | 施工规范、材料知识 | 全局知识库 |
| 材料推荐 | LLM+KB | 根据空间类型推荐材料 | 全局知识库 |
| 采购清单 | 本地+LLM | 生成未完工工程的材料采购清单 | 按用户角色过滤 |

### 4.2 AI架构

```
Android前端(AI聊天界面)
        │ HTTP/SSE
┌───────▼────────────────────────────────────┐
│              后端 AI 模块                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ 权限过滤层│  │ 意图路由  │  │ 大模型调用│ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘ │
│       │              │              │       │
│  ┌────▼──────────────▼──────────────▼────┐ │
│  │         Function Calling Tools        │ │
│  │  工程查询 │ 统计查询 │ 结算查询        │ │
│  └──────────────────┬───────────────────┘ │
│                     │                      │
│  ┌──────────────────▼───────────────────┐ │
│  │          排料引擎 (本地计算)          │ │
│  │  面材排料 │ 龙骨计算 │ 配件计算       │ │
│  └──────────────────┬───────────────────┘ │
│                     │                      │
│  ┌──────────────────▼───────────────────┐ │
│  │        材料知识库 (RAG检索)           │ │
│  │  material_params + ai_knowledge_chunks│ │
│  └──────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

### 4.3 AI数据权限

```javascript
// AI查询时自动注入权限过滤
const getProjectDataForAI = async (userId) => {
  const user = await getUser(userId);
  if (isAdmin(user))    return queryAllProjects();    // 管理员看全部
  if (isDocumenter(user)) return queryAllProjects();  // 资料员看全部(只读)
  return queryUserProjects(userId);  // 施工员只看自己参与的
};
```

### 4.4 知识库分类

| 目录 | 内容 | 示例 |
|------|------|------|
| construction/ | 施工规范 | 吊顶施工标准、安全操作规程 |
| materials/ | 材料知识 | 蜂窝板规格、铝扣板对比 |
| safety/ | 安全标准 | 高空作业规范、用电安全 |
| business/ | 业务规则 | 薪资计算规则、结算流程说明 |

---

## 五、排料引擎设计

### 5.1 核心原则

- 材料参数**只存在知识库**（`material_params`表），引擎不硬编码任何材料数据
- 排料计算**纯本地引擎**（<50ms），不依赖网络和大模型
- 大模型**仅做分析和建议**（对比优化、材料推荐），不影响核心计算结果
- **SVG数据在后端生成**，前端接收即渲染，不关心排料算法细节

### 5.2 协作链路

```
知识库(material_params) → 排料引擎(本地计算) → LLM(优化建议) → SVG数据 → Android渲染
```

### 5.3 面材排料算法

```
场景: 客厅 400cm × 500cm，使用 60cm × 60cm 铝扣板

步骤:
1. 尝试两种方向(标准/旋转90°)，选最优
2. 计算完整面板: floor(400/60) × floor(500/60) = 6×8 = 48片
3. 计算裁切面板:
   右侧余40cm: 8片(60×40)
   上方余20cm: 6片(20×60)
   右上角: 1片(40×20)
   共15片裁切面板
4. 加损耗量: ceil(63 × 1.05) = 64片
5. 生成SVG渲染数据
```

### 5.4 SVG渲染数据结构

```json
{
  "svg": {
    "canvas": { "width": 560, "height": 660 },
    "room": { "x": 40, "y": 40, "w": 480, "h": 600 },
    "panels": [
      { "type": "full", "x": 40, "y": 40, "w": 72, "h": 72,
        "fill": "#E3F2FD", "stroke": "#1976D2" },
      { "type": "cut",  "x": 472, "y": 40, "w": 48, "h": 72,
        "fill": "#FFF3E0", "stroke": "#FF9800", "label": "裁" }
    ],
    "cuts": [
      { "x1": 472, "y1": 40, "x2": 472, "y2": 520,
        "dash": "5,3", "stroke": "#FF5722", "label": "40cm" }
    ],
    "dimensions": [
      { "x1": 40, "x2": 520, "y": 620, "label": "400cm" }
    ],
    "legend": [
      { "color": "#E3F2FD", "label": "完整面板" },
      { "color": "#FFF3E0", "label": "裁切面板" }
    ]
  }
}
```

### 5.5 颜色规范(前端SVG渲染)

| 元素 | 填充色 | 描边色 | 说明 |
|------|--------|--------|------|
| 房间区域 | #FAFAFA | #333333 | 灰色背景 |
| 完整面板 | #E3F2FD | #1976D2 | 浅蓝 |
| 裁切面板 | #FFF3E0 | #FF9800 | 浅橙 |
| 裁切线 | - | #FF5722 | 红色虚线 |
| 龙骨 | - | #9E9E9E | 灰色虚线 |

### 5.6 单位规范

- 排料计算统一使用**厘米(cm)**
- 与现有子项目 `length/width`（存储单位：米）换算关系：`1m = 100cm`
- 引擎内部全cm计算，输出时标注清楚单位

---

## 六、AI模块文件结构

```
backend/ai/
├── index.js                 # 模块入口 + 路由注册
├── config.js                # AI提供商配置
├── intentRouter.js          # 意图路由(排料/查询/问答)
├── chatManager.js           # 对话管理
│
├── engine/                  # 排料引擎 ⭐核心
│   ├── materialLoader.js    # 知识库→引擎参数加载
│   ├── panelLayout.js       # 面材排料算法 + SVG生成
│   ├── keelCalculator.js    # 龙骨计算
│   ├── trimCalculator.js    # 收边条计算
│   └── accessoriesCalc.js   # 配件计算
│
├── providers/               # 大模型提供商
│   ├── index.js             # 提供商标路由
│   ├── tongyi.js            # 通义千问
│   ├── wenxin.js            # 文心一言
│   ├── glm.js               # 智谱ChatGLM
│   ├── deepseek.js          # DeepSeek
│   └── doubao.js            # 豆包
│
├── tools/                   # Function Calling工具
│   ├── index.js
│   ├── queryProjects.js     # 带权限过滤
│   ├── queryStatistics.js
│   ├── querySettlements.js
│   ├── queryAdvances.js
│   └── queryProfile.js
│
├── knowledge/               # 知识库
│   ├── index.js
│   ├── chunker.js           # 文档分块
│   ├── embedder.js          # 向量嵌入
│   ├── retriever.js         # RAG检索
│   └── docs/
│       ├── construction/    # 施工规范
│       ├── materials/       # 材料知识
│       ├── safety/          # 安全标准
│       └── business/        # 业务规则
│
└── utils/
    ├── permissionFilter.js  # AI数据权限过滤
    └── tokenCounter.js      # Token计数
```

---

## 七、AI接口设计

### 7.1 AI对话接口

```
POST /v1/ai/chat
Request:
{
  "message": "我本月完成了多少工程？收入多少？",
  "history": [],
  "context": { "project_id": 123 }
}

Response (SSE流式):
event: thinking → AI思考状态
event: data     → 工具调用结果
event: answer   → AI回复文本
```

### 7.2 排料计算接口

```
POST /v1/ai/material-layout
Request:
{
  "room": { "name": "客厅", "length": 400, "width": 500 },
  "material_id": 2,
  "with_ai": true
}

Response: { panel_layout, keel, trim, accessories, cost, svg, ai_suggestions }
```

### 7.3 知识库管理接口

```
POST /v1/ai/knowledge/upload          - 上传知识文档
GET  /v1/ai/knowledge/search?q=xxx    - RAG检索
POST /v1/ai/knowledge/import-material - 批量导入材料参数
GET  /v1/ai/knowledge/materials       - 查询材料参数列表
```

---

## 八、AI配置

```env
# 默认AI提供商
AI_PROVIDER=tongyi                # tongyi | wenxin | glm | deepseek | doubao
AI_API_KEY=sk-xxxx
AI_MODEL=qwen-plus

# 备选提供商
AI_PROVIDER_2=deepseek
AI_API_KEY_2=sk-yyyy

# 知识库
AI_KB_PATH=./ai/knowledge/docs

# 向量模型(用于RAG)
AI_EMBEDDING_MODEL=text-embedding-v3
```

---

## 九、新增数据库表

### 9.1 材料分类表

```sql
CREATE TABLE material_categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 9.2 材料参数表

```sql
CREATE TABLE material_params (
    id SERIAL PRIMARY KEY,
    category_id INT REFERENCES material_categories(id),
    name VARCHAR(100) NOT NULL,
    specification VARCHAR(200),
    panel_length NUMERIC(8,2),
    panel_width NUMERIC(8,2),
    unit VARCHAR(20) NOT NULL,
    coverage_per_unit NUMERIC(8,4),
    waste_rate NUMERIC(5,4) DEFAULT 0.05,
    price NUMERIC(10,2),
    keel_main_spacing NUMERIC(8,2),
    keel_sub_spacing NUMERIC(8,2),
    standard_length NUMERIC(8,2),
    accessory_per_sqm NUMERIC(5,1),
    description TEXT,
    tags VARCHAR(200),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 9.3 AI对话历史表

```sql
CREATE TABLE ai_chat_history (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id),
    session_id VARCHAR(50),
    role VARCHAR(20),           -- user / assistant / system / tool
    content TEXT,
    tokens INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 9.4 知识库分块表

```sql
CREATE TABLE ai_knowledge_chunks (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50),
    tags VARCHAR(200),
    embedding vector(1536),     -- pgvector扩展
    source_file VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 十、安全加固清单

| 项目 | 当前问题 | 改后方案 |
|------|---------|---------|
| SQL注入 | statistics.js 多月份查询使用字符串拼接 | 改为参数化查询 |
| JWT | 无刷新机制 | access_token(2h) + refresh_token(7d) |
| 密码策略 | 3-8位无复杂度要求 | 最小6位，支持字母数字组合 |
| 登录保护 | 无失败限制 | 5次失败锁定30分钟 |
| API限流 | 无 | Redis实现按用户/IP频率限制 |

---

## 十一、工程项目规则

### 11.1 代码规范
- 所有回复用中文，代码注释用中文
- 修复不改原程序功能
- controller只做请求处理，业务逻辑在service层，数据访问在repository层

### 11.2 金额计算规则
- 所有金额计算必须由后端完成，前端不参与计算
- 金额字段精度 NUMERIC(14,4)
- 计算服务统一使用 `services/calculation.js`

### 11.3 数据一致性
- 工程完工(completed)时，必须同步更新所有子项目状态
- 结算操作必须在事务中完成
- 删除子项目后必须重新计算工程总额

### 11.4 前端替换
- 前端从 Vue 3 + Vant 改为 Android原生APK (后续实施)
- 排料可视化使用SVG渲染 (后续实施)

---

> **文档版本**: V4.0 | **更新日期**: 2026-06-09 | **作者**: 系统优化