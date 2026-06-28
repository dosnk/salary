# 三人行吊顶管理系统 - 综合开发排期

> 本文档按依赖关系排列后端+前端的所有开发任务，形成唯一开发顺序。
> 每阶段任务完成后方可进入下一阶段。

---

## 依赖关系图

```
后端                                前端
────────────────────────────────────────────
阶段1: TS+Knex+安全 ──┐
阶段2: RBAC权限 ──────┤
                       ├──→ 阶段1: 项目搭建+Core模块
                       │        (可与后端并行)
                       │
阶段3: Service+Repository │
       +Redis+BullMQ ──┼──→ 阶段2: Auth模块
                       │         (需RBAC完成)
阶段4: ────────────────┼──→ 阶段3: 工程管理模块
                       │         (需后端API)
                       │
                       ├──→ 阶段4: 统计结算模块
                       │         (需Redis缓存)
                       │
阶段5: AI模块 ─────────┼──→ 阶段5: AI模块
                       │         (需AI后端)
                       │
                       └──→ 阶段6: 收尾完善
```

---

## 阶段1: 基础设施搭建 (后端+前端并行)

### 1.1 后端 - TypeScript迁移 + Knex.js + 安全加固

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1.1.1 | 配置TypeScript | tsconfig.json | allowJs:true 兼容旧JS |
| 1.1.2 | 安装TS依赖 | package.json | typescript, ts-node, @types/* |
| 1.1.3 | 封装Knex连接 | backend/config/database.ts | 替代pg原生驱动 |
| 1.1.4 | 迁移连接池代码 | backend/config/database.ts → .ts | 保持兼容 |
| 1.1.5 | 修复SQL注入 | backend/controllers/statistics.ts | 多月份查询参数化 |
| 1.1.6 | JWT增强 | backend/middleware/auth.ts | access_token(2h)+refresh_token(7d) |
| 1.1.7 | 密码策略强化 | backend/controllers/auth.ts | 最小6位，字母数字组合 |
| 1.1.8 | 登录保护 | backend/controllers/auth.ts | 5次失败锁定30分钟 |
| 1.1.9 | API限流 | backend/middleware/rateLimiter.ts | Redis按用户/IP频率限制 |
| 1.1.10 | 路由重构(TS化) | backend/routes/*.ts | 逐步迁移路由文件 |
| 1.1.11 | 入口文件TS化 | backend/index.ts | Koa启动改为TS |

### 1.2 前端 - 项目搭建 + Core模块

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1.2.1 | Android项目创建 | salary-android/ | AGP 8.7 + Kotlin 2.1 + Compose BOM |
| 1.2.2 | Gradle配置 | libs.versions.toml, build.gradle.kts | 版本目录统一管理 |
| 1.2.3 | Hilt配置 | SalaryApp.kt, AppModule.kt | DI框架搭建 |
| 1.2.4 | 主题系统 | core/design/theme/ | Color/Type/Shape + 黄绿色系 |
| 1.2.5 | 公共组件 | core/design/component/ | TopBar/Button/TextField/Card/Tag等 |
| 1.2.6 | 网络层 | core/network/ | Retrofit + OkHttp + 拦截器 + API接口定义 |
| 1.2.7 | 数据层 | core/data/ | Room数据库(空表) + DataStore(Token) |
| 1.2.8 | 导航框架 | app/navigation/AppNavHost.kt | 主导航图 + 4Tab底部导航 |
| 1.2.9 | UiState基类 | core/ui/state/ | Loading/Success/Error/LoadingMore |

---

## 阶段2: 权限 + 认证

### 2.1 后端 - RBAC权限重构

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 2.1.1 | 新建RBAC中间件 | backend/middleware/rbac.ts | 统一权限入口 |
| 2.1.2 | 收敛权限检查 | backend/controllers/*.ts | 移除散落的isAdmin()检查 |
| 2.1.3 | 资源级数据过滤 | backend/controllers/projects.ts | 施工员自动只查自己工程 |
| 2.1.4 | 赋能施工员 | backend/controllers/settlements.ts | 施工员自结算+自确认 |
| 2.1.5 | 限制admin权限 | backend/controllers/*.ts | admin只读监管，不修改业务数据 |
| 2.1.6 | 更新预支权限 | backend/controllers/advances.ts | admin只查看不修改 |
| 2.1.7 | 权限单元测试 | backend/tests/rbac.test.ts | 覆盖所有权限矩阵用例 |

### 2.2 前端 - Auth模块

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 2.2.1 | 登录页 | feature/auth/LoginScreen.kt | 用户名+密码+记住我 |
| 2.2.2 | 注册页 | feature/auth/RegisterScreen.kt | 注册表单+角色选择 |
| 2.2.3 | Token管理 | core/data/TokenStorage.kt | DataStore存储+自动刷新 |
| 2.2.4 | Auth拦截器 | core/network/AuthInterceptor.kt | 请求头自动附带Token |
| 2.2.5 | 登录状态管理 | feature/auth/AuthViewModel.kt | 登录/登出/Token刷新 |

---

## 阶段3: 后端核心业务层重构

### 3.1 后端 - Service + Repository层

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 3.1.1 | ProjectService | backend/services/projectService.ts | 工程CRUD业务逻辑 |
| 3.1.2 | SettlementService | backend/services/settlementService.ts | 结算业务逻辑 |
| 3.1.3 | StatisticsService | backend/services/statisticsService.ts | 统计业务逻辑 |
| 3.1.4 | UserService | backend/services/userService.ts | 用户业务逻辑 |
| 3.1.5 | ProjectRepository | backend/repositories/projectRepo.ts | 工程数据访问(Knex) |
| 3.1.6 | SettlementRepository | backend/repositories/settlementRepo.ts | 结算数据访问(Knex) |
| 3.1.7 | 查询构建器 | backend/utils/queryBuilder.ts | 分页/筛选/排序统一封装 |
| 3.1.8 | 计算逻辑统一 | backend/services/calculation.ts | 消除createProject/updateSubproject重复 |

### 3.2 后端 - Redis + BullMQ

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 3.2.1 | Redis缓存服务 | backend/services/cacheService.ts | 连接管理+通用缓存方法 |
| 3.2.2 | 统计缓存 | backend/services/statisticsService.ts | 月度统计缓存30分钟 |
| 3.2.3 | 字典缓存 | backend/controllers/dictionary.ts | 空间类型/施工方案永久缓存 |
| 3.2.4 | 工程列表缓存 | backend/controllers/projects.ts | 按用户缓存10分钟 |
| 3.2.5 | BullMQ队列 | backend/jobs/queue.ts | 初始化队列 |
| 3.2.6 | 异步Excel导出 | backend/jobs/excelExport.ts | 大结算单异步生成 |
| 3.2.7 | 统计预计算 | backend/jobs/statsPrecompute.ts | 每日凌晨定时任务 |

---

## 阶段4: 前端业务功能

### 4.1 前端 - 工程管理模块

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 4.1.1 | 工程列表页 | feature/home/ProjectListScreen.kt | 搜索+筛选+下拉刷新+上拉加载 |
| 4.1.2 | 工程卡片组件 | feature/home/ProjectCard.kt | 状态标签+金额+施工员 |
| 4.1.3 | 筛选组件 | feature/home/FilterBar.kt | 状态+结算状态+月份级联筛选 |
| 4.1.4 | 创建工程页 | feature/home/ProjectCreateScreen.kt | 表单+施工方案选择+人员选择 |
| 4.1.5 | 工程详情页 | feature/home/ProjectDetailScreen.kt | 子项目列表+文件+历史 |
| 4.1.6 | 编辑工程页 | feature/home/ProjectEditScreen.kt | 编辑工程+子项目增删改 |
| 4.1.7 | 施工人员选择器 | feature/home/WorkerSelectDialog.kt | 多选施工人员弹窗 |
| 4.1.8 | 工程ViewModel | feature/home/ProjectListViewModel.kt | 状态管理+筛选+分页 |

### 4.2 前端 - 统计结算模块

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 4.2.1 | 统计面板页 | feature/statistics/StatisticsDashboardScreen.kt | 统计卡片+图表 |
| 4.2.2 | 图表组件 | feature/statistics/charts/ | 柱状图+折线图(Compose Canvas) |
| 4.2.3 | 月度统计页 | feature/statistics/MonthlyStatsScreen.kt | 按月份筛选统计 |
| 4.2.4 | 结算单列表 | feature/statistics/SettlementListScreen.kt | 待结算工程列表 |
| 4.2.5 | 结算预览页 | feature/statistics/SettlementPreviewScreen.kt | 预览计算+扣除预支 |
| 4.2.6 | 结算确认 | feature/statistics/SettlementConfirmDialog.kt | 确认结算弹窗 |
| 4.2.7 | 结算历史页 | feature/statistics/SettlementHistoryScreen.kt | 已结算记录+快照 |
| 4.2.8 | 预支管理页 | feature/statistics/AdvanceScreen.kt | 预支列表+创建预支 |

---

## 阶段5: AI功能

### 5.1 后端 - AI模块

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 5.1.1 | AI入口+配置 | backend/ai/index.ts, config.ts | 模块注册+提供商配置 |
| 5.1.2 | 通义千问Provider | backend/ai/providers/tongyi.ts | OpenAI兼容接口 |
| 5.1.3 | 文心一言Provider | backend/ai/providers/wenxin.ts | 百度API |
| 5.1.4 | DeepSeek Provider | backend/ai/providers/deepseek.ts | OpenAI兼容接口 |
| 5.1.5 | ChatGLM Provider | backend/ai/providers/glm.ts | 智谱API |
| 5.1.6 | 豆包 Provider | backend/ai/providers/doubao.ts | 字节API |
| 5.1.7 | 意图路由 | backend/ai/intentRouter.ts | 排料/查询/问答意图分发 |
| 5.1.8 | 对话管理器 | backend/ai/chatManager.ts | 多轮对话+SSE流式 |
| 5.1.9 | 数据库表创建 | backend/scripts/init-db.js | material_categories, material_params, ai_chat_history, ai_knowledge_chunks |
| 5.1.10 | 材料参数预设数据 | backend/scripts/seed-materials.sql | 面材/龙骨/收边/配件预设 |
| 5.1.11 | 材料参数API | backend/controllers/materials.ts | CRUD |
| 5.1.12 | 排料引擎-面板 | backend/ai/engine/panelLayout.ts | 面材排料+SVG生成 |
| 5.1.13 | 排料引擎-龙骨 | backend/ai/engine/keelCalculator.ts | 龙骨计算 |
| 5.1.14 | 排料引擎-收边 | backend/ai/engine/trimCalculator.ts | 收边条计算 |
| 5.1.15 | 排料引擎-配件 | backend/ai/engine/accessoriesCalc.ts | 配件计算 |
| 5.1.16 | 排料引擎-材料加载 | backend/ai/engine/materialLoader.ts | 知识库→引擎参数 |
| 5.1.17 | FunctionCall-工程查询 | backend/ai/tools/queryProjects.ts | 带权限过滤 |
| 5.1.18 | FunctionCall-统计查询 | backend/ai/tools/queryStatistics.ts | 带权限过滤 |
| 5.1.19 | FunctionCall-结算查询 | backend/ai/tools/querySettlements.ts | 带权限过滤 |
| 5.1.20 | FunctionCall-预支查询 | backend/ai/tools/queryAdvances.ts | 带权限过滤 |
| 5.1.21 | RAG知识库-分块 | backend/ai/knowledge/chunker.ts | 文档分块 |
| 5.1.22 | RAG知识库-嵌入 | backend/ai/knowledge/embedder.ts | 向量嵌入 |
| 5.1.23 | RAG知识库-检索 | backend/ai/knowledge/retriever.ts | pgvector相似搜索 |
| 5.1.24 | AI数据权限过滤 | backend/ai/utils/permissionFilter.ts | 自动注入权限 |
| 5.1.25 | AI路由注册 | backend/routes/ai.ts | /v1/ai/* 路由 |

### 5.2 前端 - AI模块

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 5.2.1 | AI对话页 | feature/ai/AiChatScreen.kt | 对话气泡+输入栏+快捷提问 |
| 5.2.2 | SSE流式接收 | feature/ai/AiRepository.kt | OkHttp SSE事件流解析 |
| 5.2.3 | 对话ViewModel | feature/ai/AiChatViewModel.kt | 消息列表+流式状态管理 |
| 5.2.4 | 排料输入页 | feature/ai/MaterialLayoutScreen.kt | 空间尺寸+材料选择 |
| 5.2.5 | 排料可视化 | feature/ai/LayoutPreviewScreen.kt | Compose Canvas渲染SVG |
| 5.2.6 | Canvas渲染器 | feature/ai/LayoutCanvas.kt | 面板/裁切线/尺寸标注绘制 |
| 5.2.7 | 缩放拖动手势 | feature/ai/LayoutCanvas.kt | 双指缩放+平移 |
| 5.2.8 | 知识库浏览 | feature/ai/KnowledgeScreen.kt | 分类列表+搜索 |
| 5.2.9 | 知识详情 | feature/ai/KnowledgeDetailScreen.kt | Markdown渲染 |

---

## 阶段6: 收尾完善

### 6.1 后端 - 收尾

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 6.1.1 | Worker Threads | backend/ai/engine/worker.ts | 排料计算移入Worker |
| 6.1.2 | Prisma评估 | - | 评估是否迁移到Prisma(可选) |
| 6.1.3 | 全量集成测试 | backend/tests/integration/ | 端到端测试 |
| 6.1.4 | 性能测试 | - | 结算/统计查询压力测试 |

### 6.2 前端 - 收尾

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 6.2.1 | 个人中心页 | feature/profile/ProfileScreen.kt | 头像+信息+菜单 |
| 6.2.2 | 修改密码 | feature/profile/ChangePasswordScreen.kt | 密码修改表单 |
| 6.2.3 | 字典管理(admin) | feature/profile/DictionaryScreen.kt | 空间类型/施工方案管理 |
| 6.2.4 | 用户管理(admin) | feature/profile/UserManagementScreen.kt | 用户列表+创建/禁用 |
| 6.2.5 | 关于页 | feature/profile/AboutScreen.kt | 版本信息 |
| 6.2.6 | Room离线缓存 | core/data/local/ | 工程列表+字典缓存 |
| 6.2.7 | 离线策略 | core/data/repository/ | Memory→Room→Remote三级 |
| 6.2.8 | 消息通知(可选) | feature/messages/ | 站内消息推送 |

---

## 执行总结

```
阶段 1: 基础设施    ██████████ 后端TS+Knex+安全  │ 前端项目搭建+Core
阶段 2: 权限认证    ██████████ 后端RBAC重构       │ 前端Auth模块
阶段 3: 业务层重构  ██████████ 后端Service+Redis  │ (前端等待)
阶段 4: 前端业务    ██████████ (后端完成)         │ 前端工程+统计模块
阶段 5: AI功能      ██████████ 后端AI全模块       │ 前端AI模块
阶段 6: 收尾完善    ██████████ 后端优化+测试      │ 前端个人中心+缓存+消息
                    └─ 后端 ─┘ └──── 前端 ────┘
```

| 阶段 | 后端工时 | 前端工时 | 可并行 | 依赖 |
|------|:------:|:------:|:------:|------|
| 阶段1 | 1.5天 | 1天 | ✅ | - |
| 阶段2 | 1天 | 0.5天 | ❌ | 后端阶段1 |
| 阶段3 | 2天 | - | - | 阶段2 |
| 阶段4 | - | 1.5天 | - | 阶段3 |
| 阶段5 | 2.5天 | 1.5天 | ❌ | 后端AI先于前端 |
| 阶段6 | 0.5天 | 0.5天 | ✅ | 阶段5 |

---

> **文档版本**: V2.0 | **日期**: 2026-06-10 | **状态**: 6阶段全部完成

---

## 测试指南

> 集成测试和性能测试已编写完成，待日后执行验证。

### 前置准备

1. 安装依赖: `cd backend && npm install`（自动安装 jest + supertest）
2. 创建测试数据库: `CREATE DATABASE salary_test;`
3. 配置 `.env.test` 中的 DB_PASSWORD
4. 初始化测试表: `set DB_NAME=salary_test && node scripts/init-db.js`

### 集成测试

```bash
npm test                    # 运行全部集成测试
npm run test:integration    # 同上
npx jest tests/integration/rbac.test.js --forceExit  # 单模块
```

### 性能测试（需先启动后端服务）

```bash
npm run test:perf           # 一键运行全部性能测试
node tests/performance/statistics.bench.js   # 统计查询
node tests/performance/settlement.bench.js   # 结算操作
```