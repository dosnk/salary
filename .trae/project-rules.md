# 三人行吊顶管理系统 - 项目核心规则

> **本文件由 Trae AI 在每次对话前自动读取。**
> 所有代码修改必须遵循以下规则。请严格遵守。

---

## 📌 项目基本信息

| 项目 | 内容 |
|-----|-----|
| **项目名称** | 三人行吊顶管理系统 |
| **项目根目录** | `f:\AIPoject\salary\` |
| **架构模式** | 后端 (Node.js) + Web 前端 (Vue 3) + Android 端 (Kotlin/Compose) |
| **数据库** | PostgreSQL（支持 NULL 字段） |
| **缓存/Token** | Redis |
| **AI 能力** | 排料引擎（本地计算） + 大模型对话（通义千问/DeepSeek/豆包/文心/ChatGLM） |
| **最新 APK 版本** | salary-1.0.0-53-debug.apk（每次构建 BUILD 自增） |

---

## 📁 目录结构速览

```
salary/
├── backend/              Node.js + Koa 后端
│   ├── controllers/      API 路由层（参数校验 + 调用 Service）
│   ├── services/         业务逻辑层（核心计算）
│   ├── repositories/     数据访问层（SQL 参数化查询）⚠️ 修改需谨慎
│   ├── middleware/       认证/权限/限流中间件
│   ├── routes/           路由定义
│   ├── ai/               AI 模块（engine/知识库/providers/tools）
│   ├── scripts/          数据库初始化脚本
│   └── index.js          应用入口
│
├── frontend/             Vue 3 + Vite Web 前端
│   └── src/views/        页面组件（Dashboard, Projects, Statistics 等）
│
├── salary-android/       Android 客户端
│   └── core/network/src/main/java/...  网络层（DTO + API + AuthInterceptor）
│   └── feature/home/                    主页/工程管理模块
│
├── docs/                 开发文档
├── .memory/              补充记忆文件（由 AI 维护，对话中按需读取）
└── .trae/                本文件所在目录（Trae 自动读取）
```

---

## 👥 角色与权限体系

| 角色 | 能力 | 关键限制 |
|-----|-----|---------|
| **admin** | 用户管理 + 全部工程查看 + AI 配置 + 字典管理 + 数据迁移 | ❌ 不能创建/修改/删除工程，不能操作结算 |
| **constructor** | 创建/修改自己的工程 + 子项目管理 + 结算 + 预支 + 自己的统计 | 只能看和操作自己参与的工程 |
| **documenter** | 全部工程只读 | 无修改能力 |

**关键约束**：结算状态按"工程×用户"维度存储（`project_user_status` 表），工程状态统一在 `projects.status`（preparing/constructing/completed/canceled）。

---

## 🔒 硬编码规则（修改代码前必须读）

### 1. 金额计算（绝对禁止前端计算）

| 规则 | 实现方式 | 位置 |
|-----|---------|-----|
| 所有金额由后端计算 | `POST /v1/settlements/calculate` 唯一入口 | [controllers/settlements.js](file:///f:/AIPoject/salary/backend/controllers/settlements.js) |
| 金额字段类型 | `NUMERIC(14,4)` → Kotlin DTO 中用 `Double?`（可空） | [ProjectDtos.kt](file:///f:/AIPoject/salary/salary-android/core/network/src/main/java/com/salary/core/network/dto/ProjectDtos.kt) |
| 子项目金额必须在事务内计算 | 先 `calculation.calculateSubprojectAmount(...)` 再 INSERT | [services/projectService.js](file:///f:/AIPoject/salary/backend/services/projectService.js) |
| 工程总额重算 | 子项目增删改后必须调用 `recalculateProjectTotal(projectId)` | 同上 |

### 2. 时区处理（Asia/Shanghai 东八区）

所有 `WHERE TO_CHAR(p.created_at, 'YYYY-MM')` 的查询**必须**改为：
```sql
TO_CHAR(p.created_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM')
```
位置：[repositories/projectRepo.js](file:///f:/AIPoject/salary/backend/repositories/projectRepo.js) 的 `countQuery` 和 `listQuery`。
否则当天 00:00-08:00 创建的工程会被计入"上月"。

### 3. 可空字段处理（Android 端）

数据库允许 NULL 的字段在 Kotlin DTO 中**必须**用 `?` 标记为可空：
- `SubprojectDto` 中：`length`, `width`, `quantity`, `amount` → 全部 `Double? = null`
- 映射到 UI Model 时用 `it.field ?: 0.0` 提供默认值
- 金额字段用 `AmountFormatter.format(it.amount)`

### 4. Token 与认证

- access_token 有效期：2 小时
- refresh_token 有效期：**30 天**（2026-06-18 从 7d 延长）
- Android 端收到 401 时：**先自动调用 `/v1/auth/refresh` 刷新**，失败再清除 token
- Token 管理：[AuthInterceptor.kt](file:///f:/AIPoject/salary/salary-android/core/network/src/main/java/com/salary/core/network/interceptor/AuthInterceptor.kt) 用独立 OkHttpClient + 双重检查锁防并发刷新

### 5. SQL 安全

- **禁止字符串拼接**：所有查询必须使用参数化 (`$1, $2, ...`)
- `?` 占位符在 pg-native 中使用，`$N` 在原生 `node-postgres` 中使用

### 6. API 响应格式

统一格式：
```json
{ "code": 200, "message": "success", "data": { ... } }
```
Android 端 `ApiResponse<T>` 对应。错误消息由 `NetworkErrorHandler.translateServerError()` 翻译为中文。

---

## 📱 Android 端构建规则

修改 Kotlin 代码后**必须**执行：
```powershell
cd f:\AIPoject\salary\salary-android
./gradlew assembleDebug
```
- 版本号由 `app/build.gradle.kts` 管理（每次构建 BUILD 自增）
- 产物输出：`salary-android/app/build/outputs/apk/debug/`
- 构建失败时需先执行 `./gradlew clean` 后重试

---

## 🎨 UI/交互 规范摘要

| 项目 | 规范 |
|-----|-----|
| 主色调（Web） | `#10b981`（绿色）+ `#e6f7e6`（浅绿背景） |
| 主色调（Android） | `#8CC63F` 绿色系 |
| 工程状态标签 | preparing(黄) / constructing(绿) / completed(绿) / canceled(灰) |
| API 时延显示 | 页面底部右侧 + 圆点指示 + <200ms绿 / 200-500ms橙 / >500ms红 |
| 错误提示 | "网络连接失败，请检查网络后重试"等中文提示 |

---

## 💬 对话与回复规范

| 项目 | 规则 |
|-----|-----|
| 回复语言 | **全中文** |
| 代码注释 | **中文**注释 |
| 文件引用格式 | `[文件名](file:///f:/AIPoject/salary/绝对路径#LLine)` |
| 代码行号 | 提供行号便于定位 |
| 修复不改原功能 | bug 修复不引入新逻辑 |

---

## 🗂️ 补充记忆文件（`.memory/` 目录）

以下文件包含更详细信息，涉及对应主题时请读取：

| 文件名 | 何时读取 |
|--------|---------|
| `.memory/01-项目核心记忆.md` | 理解业务流程、功能清单、实体关系 |
| `.memory/02-技术栈与架构.md` | 理解架构设计、数据库表结构、代码分层 |
| `.memory/03-开发规范与约束.md` | 编写/修改代码前必读（完整规范） |
| `.memory/04-关键文件与快速定位.md` | 需要快速定位某个文件/功能的位置 |
| `.memory/05-历史会话摘要.md` | 了解最近完成的工作、避免重复开发 |

---

## 🔍 快速查找索引（常见需求→文件路径）

| 需求 | 文件路径 |
|-----|---------|
| 创建工程的 API | `backend/controllers/projects.js` |
| 金额计算逻辑 | `backend/services/calculation.js` |
| 工程列表月份筛选 | `backend/repositories/projectRepo.js`（⚠️ AT TIME ZONE 'Asia/Shanghai'） |
| 结算 API | `backend/controllers/settlements.js` |
| Token 生成/刷新 | `backend/controllers/auth.js` |
| 权限检查中间件 | `backend/middleware/rbac.js` + `permission.js` |
| Android DTO 可空字段 | `salary-android/core/network/.../dto/ProjectDtos.kt` |
| Android Token 刷新拦截器 | `salary-android/core/network/.../interceptor/AuthInterceptor.kt` |
| Android 工程列表 | `salary-android/feature/home/.../dashboard/DashboardViewModel.kt` |
| AI 配置管理 | `backend/ai/config.js` + `backend/controllers/ai.js` |
| 数据库初始化 | `backend/scripts/init-db.js` |

---

## ⚠️ 已知陷阱（踩过的坑，再次遇到请绕行）

| 场景 | 问题 | 解决方案 |
|-----|-----|---------|
| Subproject JSON 解析失败 | amount/length 等字段数据库 NULL，DTO 非空 Double | 将 DTO 字段改为 `Double?`，映射时用 `it.field ?: 0.0` |
| 当月工程不显示 | UTC 时区差 8 小时 | 查询时加 `AT TIME ZONE 'Asia/Shanghai'` |
| 工程创建后子项目金额空 | 金额计算在事务外执行 | 先计算 amount/quantity，再在同一事务 INSERT |
| 401 后用户被迫重登 | AuthInterceptor 直接清 token | 先尝试 `/v1/auth/refresh`，失败再清 |
| Docker 重建后 Token 失效 | JWT_SECRET 重置 | 用户需要重新登录，不属 bug |

---

> **最后更新**: 2026-06-18
> 修改规则或新增约束时请同步更新本文件
