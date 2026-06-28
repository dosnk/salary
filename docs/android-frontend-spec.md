# 三人行吊顶管理系统 - Android 前端开发文档 V1.0

> 本文档定义 Android 原生 APK 前端的技术栈、架构设计、模块划分、UI/UX规范及开发指南。

---

## 一、技术栈

### 1.1 核心技术

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Kotlin | 2.0+ | 100% Kotlin |
| UI框架 | Jetpack Compose | 1.7+ | 声明式UI |
| 架构 | MVVM + Clean Architecture | - | 分层解耦 |
| 导航 | Navigation Compose | 2.8+ | 类型安全导航 |
| 网络 | Retrofit2 + OkHttp4 | 2.11+/4.12+ | REST API |
| 序列化 | Kotlinx Serialization | 1.7+ | JSON序列化 |
| 图片 | Coil3 Compose | 3.0+ | 图片加载 |
| 依赖注入 | Hilt | 2.52+ | DI |
| 协程 | Kotlin Coroutines + Flow | 1.9+ | 异步/响应式 |
| 本地存储 | Room Database | 2.7+ | 离线缓存 |
| 键值存储 | DataStore | 1.1+ | 偏好设置/Token |
| SVG渲染 | AndroidSVG / Compose Canvas | - | 排料可视化 |

### 1.2 开发工具

| 工具 | 说明 |
|------|------|
| Android Studio | Ladybug (2024.2+) |
| Gradle + Kotlin DSL | 构建系统 |
| AGP (Android Gradle Plugin) | 8.7+ |
| Target SDK | 35 (Android 15) |
| Min SDK | 26 (Android 8.0) |

### 1.3 Gradle 版本目录 (libs.versions.toml)

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
compose-bom = "2025.01.00"
navigation = "2.8.5"
hilt = "2.52"
retrofit = "2.11.0"
okhttp = "4.12.0"
room = "2.7.0"
datastore = "1.1.2"
coil = "3.0.4"
coroutines = "1.9.0"
lifecycle = "2.8.7"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Networking
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# Data
datastore = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.7.3" }

# Image
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

# Lifecycle
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
```

---

## 二、架构设计

### 2.1 分层架构 (Clean Architecture × MVVM)

```
┌──────────────────────────────────────────────────────────────┐
│                        Presentation Layer                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐│
│  │ Composable│ │ ViewModel│ │ NavGraph │ │ Theme/Style      ││
│  │ Screens   │ │ State    │ │ Routes   │ │ DesignSystem     ││
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘│
├──────────────────────────────────────────────────────────────┤
│                         Domain Layer                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐│
│  │ UseCase   │ │ Repository│ │ Model    │ │ Validator        ││
│  │ 业务逻辑   │ │ Interface │ │ 领域模型   │ │ 校验器           ││
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘│
├──────────────────────────────────────────────────────────────┤
│                          Data Layer                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐│
│  │ Repository│ │ RemoteSrc │ │ LocalSrc  │ │ DTO / Entity    ││
│  │ Impl      │ │ (Retrofit)│ │ (Room/DS) │ │ Mapper          ││
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘│
└──────────────────────────────────────────────────────────────┘
```

### 2.2 数据流 (单向数据流 UDF)

```
Composable Screen
      │  ↑ 触发事件
      │  ↓ 观察状态
   ┌──────┐
   │ VM   │ ←── UseCase ←── Repository ←── DataSource(Remote/Local)
   └──────┘
      ↑
   StateFlow<UiState>
      ↓
   Composable (自动重组)
```

### 2.3 模块化设计

```
salary-android/
├── app/                          # 主模块 (壳工程 + DI + 导航)
├── core/
│   ├── common/                   # 公共工具
│   │   ├── extensions/           # Kotlin扩展
│   │   ├── constants/            # 常量
│   │   └── util/                 # 工具类 (日期/金额格式化)
│   ├── network/                  # 网络层
│   │   ├── api/                  # API接口定义
│   │   ├── interceptor/          # Token拦截器/日志
│   │   ├── dto/                  # 通用DTO (分页/响应)
│   │   └── di/                   # 网络模块DI
│   ├── data/                     # 数据层通用
│   │   ├── local/                # Room DAO / DataStore
│   │   ├── model/                # 领域模型
│   │   └── mapper/               # DTO ↔ Entity ↔ Model 映射
│   ├── design/                   # 设计系统
│   │   ├── theme/                # 主题/颜色/排版
│   │   ├── component/            # 公共组件
│   │   └── icon/                 # 图标资源
│   └── ui/                       # UI通用工具
│       ├── state/                 # UiState基类
│       ├── navigation/           # 导航帮助类
│       └── preview/              # 预览参数
│
├── feature/
│   ├── auth/                     # 登录/注册
│   │   ├── login/
│   │   ├── register/
│   │   └── di/
│   ├── home/                     # 首页(工程列表)
│   │   ├── list/
│   │   ├── create/
│   │   ├── detail/
│   │   ├── edit/
│   │   └── di/
│   ├── statistics/               # 统计结算
│   │   ├── dashboard/
│   │   ├── settlement/
│   │   ├── advance/
│   │   └── di/
│   ├── ai/                       # AI助手
│   │   ├── chat/
│   │   ├── layout/               # 排料可视化
│   │   ├── knowledge/            # 知识库浏览
│   │   └── di/
│   └── profile/                  # 个人中心
│       ├── info/
│       ├── settings/
│       └── di/
```

---

## 三、模块详细设计

### 3.1 主模块 (app)

**职责**: 壳工程、Hilt入口、导航图组装、主题配置

```
app/src/main/java/com/salary/
├── SalaryApp.kt              # Application入口 (@HiltAndroidApp)
├── MainActivity.kt           # 唯一Activity
├── navigation/
│   ├── AppNavHost.kt         # 主导航图
│   ├── AuthNavGraph.kt       # 认证导航
│   ├── HomeNavGraph.kt       # 首页导航
│   ├── StatisticsNavGraph.kt# 统计导航
│   ├── AiNavGraph.kt         # AI导航
│   └── ProfileNavGraph.kt    # 个人中心导航
└── di/
    └── AppModule.kt          # 全局依赖
```

**导航路由设计**:

```kotlin
sealed class Route(val route: String) {
    // 认证
    data object Login : Route("login")
    data object Register : Route("register")
    
    // 首页 (底部Tab)
    data object Home : Route("home")
    data object ProjectCreate : Route("project/create")
    data object ProjectDetail : Route("project/{projectId}")
    data object ProjectEdit : Route("project/{projectId}/edit")
    
    // 统计结算 (底部Tab)
    data object Statistics : Route("statistics")
    data object Settlement : Route("settlement")
    data object AdvanceList : Route("advance")
    
    // AI助手 (底部Tab)
    data object AiChat : Route("ai/chat")
    data object MaterialLayout : Route("ai/layout")
    data object KnowledgeBase : Route("ai/knowledge")
    
    // 个人 (底部Tab)
    data object Profile : Route("profile")
    data object UserSettings : Route("profile/settings")
    data object Dictionary : Route("admin/dictionary")
    data object UserManagement : Route("admin/users")
}
```

**底部导航**:

| Tab | 图标 | 标题 | 路由 |
|-----|------|------|------|
| 首页 | Home | 工程 | home |
| 统计 | BarChart | 统计结算 | statistics |
| AI | SmartToy | AI助手 | ai/chat |
| 我的 | Person | 我的 | profile |

---

### 3.2 Core - 网络层 (core:network)

**API服务划分**:

```kotlin
// 基础响应模型
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val data: T? = null,
    val msg: String = ""
)

// 分页响应
@Serializable
data class PageResponse<T>(
    val records: List<T>,
    val total: Long,
    val page: Int,
    val size: Int
)

// API接口
interface AuthApi {
    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>
    
    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<Unit>
}

interface ProjectApi {
    @GET("v1/projects")
    suspend fun getProjects(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("keyword") keyword: String? = null,
        @Query("status") status: String? = null,
        @Query("settlementStatus") settlementStatus: String? = null
    ): ApiResponse<PageResponse<ProjectDto>>
    
    @GET("v1/projects/{id}")
    suspend fun getProjectDetail(@Path("id") id: Int): ApiResponse<ProjectDetailDto>
    
    @POST("v1/projects")
    suspend fun createProject(@Body body: CreateProjectRequest): ApiResponse<CreateResponse>
    
    @PUT("v1/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: Int,
        @Body body: UpdateProjectRequest
    ): ApiResponse<Unit>
    
    @DELETE("v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Int): ApiResponse<Unit>
}

interface SettlementApi {
    @GET("v1/settlements/preview")
    suspend fun previewSettlement(
        @Query("projectIds") projectIds: List<Int>
    ): ApiResponse<SettlementPreviewDto>
    
    @POST("v1/settlements")
    suspend fun createSettlement(@Body body: SettlementRequest): ApiResponse<SettlementResponse>
    
    @GET("v1/settlements/history")
    suspend fun getSettlementHistory(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<PageResponse<SettlementHistoryDto>>
}

interface StatisticsApi {
    @GET("v1/statistics/monthly")
    suspend fun getMonthlyStats(
        @Query("year") year: Int,
        @Query("month") month: Int? = null
    ): ApiResponse<MonthlyStatsDto>
    
    @GET("v1/statistics/personal")
    suspend fun getPersonalStats(): ApiResponse<PersonalStatsDto>
}

interface AiApi {
    @POST("v1/ai/chat")
    suspend fun chat(@Body body: ChatRequest): ApiResponse<ChatResponse>
    
    @POST("v1/ai/material-layout")
    suspend fun materialLayout(@Body body: LayoutRequest): ApiResponse<LayoutResponse>
    
    @GET("v1/ai/knowledge/search")
    suspend fun searchKnowledge(@Query("q") query: String): ApiResponse<List<KnowledgeItem>>
    
    @GET("v1/ai/knowledge/materials")
    suspend fun getMaterials(): ApiResponse<List<MaterialDto>>
}
```

**OkHttp拦截器链**:

```
AuthInterceptor (自动添加Token)
   → LoggingInterceptor (请求/响应日志)
   → ErrorInterceptor (统一错误处理)
```

---

### 3.3 Core - 设计系统 (core:design)

#### 3.3.1 颜色体系 (继承Web端黄绿色系)

```kotlin
// Color.kt - Material3 颜色方案
val Green50 = Color(0xFFF5FBE8)
val Green100 = Color(0xFFE0F0C0)
val Green200 = Color(0xFFC4E098)
val Green300 = Color(0xFF96CC4C)
val Green400 = Color(0xFF8CC63F)   // 主色
val Green500 = Color(0xFF7CB034)   // 主色深
val Green600 = Color(0xFF6B9727)
val Green700 = Color(0xFF567A1F)
val Green800 = Color(0xFF415E18)
val Green900 = Color(0xFF2C4110)

val Background = Color(0xFFF9FDF7)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF5FBE8)

val TextPrimary = Color(0xFF333333)
val TextSecondary = Color(0xFF666666)
val TextTertiary = Color(0xFF999999)
val TextPlaceholder = Color(0xFFCCCCCC)

val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)

// 暗色主题色 (规划)
val GreenDark = Color(0xFFA0D468)
```

#### 3.3.2 排版系统

```kotlin
// Type.kt
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
)
```

#### 3.3.3 形状系统

```kotlin
// Shape.kt
val AppShapes = Shapes(
    extraLarge = RoundedCornerShape(24.dp),  // 弹窗/大卡片
    large = RoundedCornerShape(16.dp),        // 按钮/卡片
    medium = RoundedCornerShape(12.dp),       // 通用组件
    small = RoundedCornerShape(8.dp),         // 输入框/标签
    extraSmall = RoundedCornerShape(4.dp)     // 小徽章
)
```

#### 3.3.4 公共组件

| 组件 | 文件 | 说明 |
|------|------|------|
| SalaryTopBar | component/TopBar.kt | 统一顶栏 |
| SalaryButton | component/Button.kt | 主/次/禁用按钮样式 |
| SalaryTextField | component/TextField.kt | 输入框统一样式 |
| SalaryCard | component/Card.kt | 卡片容器 |
| SalaryTag | component/Tag.kt | 状态标签 |
| SalaryEmpty | component/EmptyState.kt | 空状态占位 |
| SalaryLoading | component/Loading.kt | 加载动画 |
| SalaryDialog | component/Dialog.kt | 确认弹窗 |
| SalaryDropdown | component/Dropdown.kt | 下拉选择器 |
| SalaryDivider | component/Divider.kt | 分割线 |

---

### 3.4 Feature - 工程管理 (feature:home)

#### 3.4.1 页面列表

| 页面 | 路由 | 组件 |
|------|------|------|
| 工程列表 | home | ProjectListScreen |
| 创建工程 | project/create | ProjectCreateScreen |
| 工程详情 | project/{id} | ProjectDetailScreen |
| 编辑工程 | project/{id}/edit | ProjectEditScreen |
| 添加子项目 | project/{id}/subproject/add | SubProjectAddScreen |
| 选择施工人员 | select-workers | WorkerSelectScreen |

#### 3.4.2 工程列表页设计

```
┌──────────────────────────────────┐
│  搜索框 (🔍 搜索工程名称)         │
│  ┌─────┐ ┌─────┐ ┌─────┐       │
│  │全部  │ │施工中│ │已完工│ 筛选  │ → 级联筛选: 状态 + 结算状态
│  └─────┘ └─────┘ └─────┘       │
├──────────────────────────────────┤
│  ┌──────────────────────────┐   │
│  │ 🏠 张宅吊顶工程            │   │  → 工程卡片
│  │ 状态: 🟢施工中 | 总额:¥5800│   │
│  │ 施工员: 张三, 李四         │   │
│  │ 结算: 统计中               │   │
│  └──────────────────────────┘   │
│  ┌──────────────────────────┐   │
│  │ 🏢 万达办公楼吊顶          │   │
│  │ 状态: ✅已完工 | 总额:¥12000│  │
│  │ 施工员: 王五, 赵六         │   │
│  │ 结算: ⚠️未结算            │   │
│  └──────────────────────────┘   │
├──────────────────────────────────┤
│         🔽 上拉加载更多           │
│                                 │
│                  ┌──────────┐   │
│                  │ ➕ 创建工程│   │  → FAB浮动按钮
│                  └──────────┘   │
└──────────────────────────────────┘
```

#### 3.4.3 状态标签设计

```kotlin
@Composable
fun ProjectStatusTag(status: String) {
    val (text, color) = when (status) {
        "preparing" -> "备料中" to Warning
        "constructing" -> "施工中" to Green400
        "completed" -> "已完工" to Success
        "canceled" -> "已取消" to TextTertiary
        else -> status to TextSecondary
    }
    SalaryTag(text = text, backgroundColor = color.copy(alpha = 0.12f), textColor = color)
}

@Composable
fun SettlementStatusTag(status: String) {
    val (text, color) = when (status) {
        "unsettled" -> "未结算" to Warning
        "settling" -> "统计中" to Green400
        "settled" -> "已结算" to Success
        else -> status to TextSecondary
    }
    SalaryTag(text = text, backgroundColor = color.copy(alpha = 0.12f), textColor = color)
}
```

---

### 3.5 Feature - 统计结算 (feature:statistics)

#### 3.5.1 页面列表

| 页面 | 路由 | 组件 |
|------|------|------|
| 统计面板 | statistics | StatisticsDashboardScreen |
| 月度统计 | statistics/monthly | MonthlyStatsScreen |
| 结算单列表 | statistics/settlements | SettlementListScreen |
| 结算预览 | settlement/preview/{projectIds} | SettlementPreviewScreen |
| 结算历史 | settlement/history | SettlementHistoryScreen |
| 预支管理 | advance | AdvanceScreen |
| 创建预支 | advance/create | AdvanceCreateScreen |

#### 3.5.2 统计面板设计

```
┌──────────────────────────────────┐
│          2026年6月  ▼             │
├──────────────────────────────────┤
│  ┌─────────────┐ ┌─────────────┐ │
│  │ 📊 本月工程  │ │ 💰 本月收入  │ │  → 统计卡片
│  │    12 个     │ │  ¥28,500    │ │
│  └─────────────┘ └─────────────┘ │
│  ┌─────────────┐ ┌─────────────┐ │
│  │ 📋 待结算    │ │ 💳 已预支    │ │
│  │    3 个      │ │  ¥5,000     │ │
│  └─────────────┘ └─────────────┘ │
├──────────────────────────────────┤
│  收入趋势                         │
│  📈 ┌──────────────────────┐   │  → 折线图/柱状图
│     │   /\    /\           │   │
│     │  /  \  /  \    /\    │   │
│     │ /    \/    \  /  \   │   │
│     │/              \/    \ │   │
│     └──────────────────────┘   │
│     1月 2月 3月 4月 5月 6月     │
├──────────────────────────────────┤
│  施工方案分布                     │
│  🟩 蜂窝平面 ████████ 45%        │  → 环形图
│  🟦 铝扣平面 ██████   30%        │
│  🟨 半吊     ███      15%        │
│  ⬜ 其他     ██       10%        │
└──────────────────────────────────┘
```

---

### 3.6 Feature - AI助手 (feature:ai)

#### 3.6.1 页面列表

| 页面 | 路由 | 组件 |
|------|------|------|
| AI对话 | ai/chat | AiChatScreen |
| 排料计算 | ai/layout | MaterialLayoutScreen |
| 排料可视化 | ai/layout/preview | LayoutPreviewScreen (SVG) |
| 知识库 | ai/knowledge | KnowledgeScreen |
| 知识详情 | ai/knowledge/{id} | KnowledgeDetailScreen |

#### 3.6.2 AI对话页设计

```
┌──────────────────────────────────┐
│  🤖 AI助手                        │
├──────────────────────────────────┤
│                                  │
│   👤 我完成了多少工程？            │
│                                  │
│   🤖 您本月共完成3个工程：         │  → 对话气泡
│      - 张宅吊顶 (¥5,800)          │
│      - 万达办公楼 (¥12,000)       │
│      - 丽都酒店 (¥8,200)          │
│      📊 总收入: ¥26,000           │
│      📊 待结算: ¥8,200            │
│                                  │
│   👤 丽都酒店用600铝扣板帮我算材料  │
│                                  │
│   🤖 正在计算排料...               │
│      [查看排料可视化 →]           │
│                                  │
├──────────────────────────────────┤
│  ┌────────────────────┐  📎 ✈️  │  → 输入栏
│  │ 输入问题...          │         │  → 📎快捷: 排料计算/工程查询/结算查询
│  └────────────────────┘         │
│  💡 排料计算  📊 工程查询  📋 结算查询 │  → 快捷提问
└──────────────────────────────────┘
```

#### 3.6.3 排料可视化页 (SVG渲染)

```
┌──────────────────────────────────┐
│  排料可视化       客厅 400×500cm  │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │                        △   │  │  → Canvas/SVG渲染区
│  │  ┌──┬──┬──┬──┬──┬──┬────┐│  │
│  │  │  │  │  │  │  │  ││    ││  │     完整面板(蓝)
│  │  ├──┼──┼──┼──┼──┼──┤│    ││  │     裁切面板(橙)
│  │  │  │  │  │  │  │  ││    ││  │     裁切线(红虚线)
│  │  ├──┼──┼──┼──┼──┼──┤│    ││  │
│  │  │  │  │  │  │  │  ││    ││  │
│  │  ...                      │  │
│  │  └──┴──┴──┴──┴──┴──────────┘│  │
│  │   ◁────────── 400cm ──────▷ │  │  → 尺寸标注
│  └────────────────────────────┘  │
├──────────────────────────────────┤
│  📐 面板统计                      │
│  ┌──────────────┬──────────────┐ │
│  │ 完整面板      │ 裁切面板      │ │
│  │    48 片      │    15 片     │ │
│  └──────────────┴──────────────┘ │
│  含损耗总计: 64 片 | 损耗率 5%     │
├──────────────────────────────────┤
│  🤖 AI建议:                       │
│  • 右侧40cm建议从完整板切割        │
│  • 改用300板损耗可降至3.2%         │
│  [查看详细清单 →]                 │
└──────────────────────────────────┘
```

#### 3.6.4 SVG渲染实现方案

```kotlin
// MaterialLayoutCanvas.kt
@Composable
fun MaterialLayoutCanvas(svgData: LayoutSvgData) {
    val scale = remember { mutableFloatStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(svgData.canvas.width / svgData.canvas.height)
            .pointerInput(Unit) { /* 双指缩放 + 拖动 */ }
    ) {
        // 1. 绘制房间背景
        val room = svgData.room
        drawRect(
            color = Color(parseHex(room.fill)),
            topLeft = Offset(room.x * scale.floatValue, room.y * scale.floatValue),
            size = Size(room.w * scale.floatValue, room.h * scale.floatValue)
        )
        
        // 2. 绘制面板(网格)
        svgData.panels.forEach { panel ->
            drawRect(
                color = Color(parseHex(panel.fill)),
                topLeft = Offset(panel.x * scale.floatValue, panel.y * scale.floatValue),
                size = Size(panel.w * scale.floatValue, panel.h * scale.floatValue),
                style = Stroke(width = 1.5f)
            )
            // 裁切面板标注
            if (panel.type == "cut" && panel.label != null) {
                drawContext.canvas.nativeCanvas.drawText(
                    panel.label,
                    panel.x * scale.floatValue + 4,
                    panel.y * scale.floatValue + 16,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#FF5722")
                        textSize = 12f * scale.floatValue
                    }
                )
            }
        }
        
        // 3. 绘制裁切线 (虚线)
        svgData.cuts.forEach { cut ->
            drawDashedLine(
                start = Offset(cut.x1 * scale.floatValue, cut.y1 * scale.floatValue),
                end = Offset(cut.x2 * scale.floatValue, cut.y2 * scale.floatValue),
                color = Color(parseHex(cut.stroke)),
                strokeWidth = 2f
            )
        }
        
        // 4. 绘制尺寸标注
        svgData.dimensions.forEach { dim ->
            drawLine(
                start = Offset(dim.x1 * scale.floatValue, dim.y * scale.floatValue),
                end = Offset(dim.x2 * scale.floatValue, dim.y * scale.floatValue),
                color = TextPrimary,
                strokeWidth = 1f
            )
            // 标注文字 ...
        }
        
        // 5. 绘制龙骨线 (可选视图切换)
        // ...
    }
}
```

#### 3.6.5 SSE流式接收 (AI对话)

```kotlin
// AiRepository.kt
fun chatStream(message: String, history: List<ChatMessage>): Flow<AiEvent> = flow {
    val request = ChatRequest(message, history)
    
    // 使用OkHttp进行SSE流式请求
    okHttpClient.newCall(Request.Builder()
        .url("${baseUrl}/v1/ai/chat")
        .post(request.toJsonBody())
        .header("Authorization", "Bearer ${tokenStorage.getToken()}")
        .build()
    ).execute().let { response ->
        response.body?.source()?.let { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event: thinking") -> emit(AiEvent.Thinking)
                    line.startsWith("data: ") -> {
                        val data = line.removePrefix("data: ")
                        emit(AiEvent.Data(data))
                    }
                    line.startsWith("event: answer") -> emit(AiEvent.AnswerStart)
                }
            }
        }
    }
}

sealed class AiEvent {
    data object Thinking : AiEvent()
    data class Data(val content: String) : AiEvent()
    data object AnswerStart : AiEvent()
    data class AnswerChunk(val text: String) : AiEvent()
    data object Done : AiEvent()
    data class Error(val message: String) : AiEvent()
}
```

---

### 3.7 Feature - 个人中心 (feature:profile)

#### 3.7.1 页面列表

| 页面 | 路由 | 组件 |
|------|------|------|
| 个人中心 | profile | ProfileScreen |
| 设置 | profile/settings | SettingsScreen |
| 修改密码 | profile/password | ChangePasswordScreen |
| 字典管理 | admin/dictionary | DictionaryScreen (仅admin) |
| 用户管理 | admin/users | UserManagementScreen (仅admin) |
| 数据迁移 | admin/migration | MigrationScreen (仅admin) |
| 关于 | profile/about | AboutScreen |

#### 3.7.2 个人中心页设计

```
┌──────────────────────────────────┐
│  ┌────────────────────────────┐  │
│  │  👤 昵称                    │  │  → 渐变卡片背景
│  │      施工员 | 138xxxx8888   │  │
│  └────────────────────────────┘  │
├──────────────────────────────────┤
│  📊 本月收入: ¥28,500            │
│  📋 本月工程: 12个               │
│  💳 待结算: ¥8,200               │
├──────────────────────────────────┤
│  ┌──────────────────────────┐   │
│  │ 👤 个人信息             > │   │  → 菜单列表
│  ├──────────────────────────┤   │
│  │ 🔒 修改密码             > │   │
│  ├──────────────────────────┤   │
│  │ 📖 字典管理 (仅admin)   > │   │  → 条件显示
│  ├──────────────────────────┤   │
│  │ 👥 用户管理 (仅admin)   > │   │
│  ├──────────────────────────┤   │
│  │ 📦 数据迁移 (仅admin)   > │   │
│  ├──────────────────────────┤   │
│  │ ℹ️ 关于                > │   │
│  └──────────────────────────┘   │
├──────────────────────────────────┤
│          🚪 退出登录              │
└──────────────────────────────────┘
```

---

## 四、状态管理

### 4.1 UiState 基类

```kotlin
// core/ui/state/UiState.kt
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val code: Int = -1) : UiState<Nothing>
}

// 列表专用
sealed interface ListUiState<out T> : UiState<List<T>> {
    data class ListSuccess<T>(
        val items: List<T>,
        val hasMore: Boolean = false,
        val page: Int = 1
    ) : ListUiState<T>
}
```

### 4.2 ViewModel 示例

```kotlin
// 工程列表 ViewModel
@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val getProjectsUseCase: GetProjectsUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow<ListUiState<ProjectModel>>(ListUiState.Loading)
    val state: StateFlow<ListUiState<ProjectModel>> = _state.asStateFlow()
    
    private var currentPage = 1
    private var filters = ProjectFilters()
    
    init { loadProjects() }
    
    fun loadProjects(isLoadMore: Boolean = false) {
        viewModelScope.launch {
            if (!isLoadMore) {
                _state.value = UiState.Loading
                currentPage = 1
            }
            
            getProjectsUseCase(currentPage, filters)
                .onSuccess { page ->
                    val currentItems = if (isLoadMore) {
                        (_state.value as? ListUiState.ListSuccess)?.items.orEmpty()
                    } else emptyList()
                    
                    _state.value = ListUiState.ListSuccess(
                        items = currentItems + page.records,
                        hasMore = page.records.size >= page.size,
                        page = currentPage
                    )
                }
                .onFailure { e ->
                    _state.value = UiState.Error(e.message ?: "加载失败")
                }
        }
    }
    
    fun updateFilter(newFilters: ProjectFilters) {
        filters = newFilters
        loadProjects()
    }
    
    fun loadMore() {
        if (_state.value is ListUiState.ListSuccess) {
            val success = _state.value as ListUiState.ListSuccess
            if (success.hasMore) {
                currentPage++
                loadProjects(isLoadMore = true)
            }
        }
    }
}
```

---

## 五、路由导航设计

### 5.1 AppNavHost

```kotlin
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Route.Home.route else Route.Login.route
    ) {
        // 认证流程
        composable(Route.Login.route) { LoginScreen(navController) }
        composable(Route.Register.route) { RegisterScreen(navController) }
        
        // 主页面 (含底部导航)
        composable(Route.Home.route) { MainScaffold(navController) }
    }
}

@Composable
fun MainScaffold(rootNavController: NavHostController) {
    val tabs = listOf(
        TabItem("工程", Icons.Default.Home, "home"),
        TabItem("统计结算", Icons.Default.BarChart, "statistics"),
        TabItem("AI助手", Icons.Default.SmartToy, "ai/chat"),
        TabItem("我的", Icons.Default.Person, "profile")
    )
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green400,
                            selectedTextColor = Green400,
                            indicatorColor = Green50
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ProjectListScreen(rootNavController)     // 工程
                1 -> StatisticsDashboardScreen(rootNavController) // 统计
                2 -> AiChatScreen(rootNavController)          // AI
                3 -> ProfileScreen(rootNavController)         // 我的
            }
        }
    }
}
```

---

## 六、离线与缓存策略

### 6.1 缓存层级

| 层级 | 存储 | 数据 | 过期时间 |
|------|------|------|---------|
| Memory | StateFlow | 当前页面状态 | 进程生命周期 |
| Local | Room Database | 工程列表/字典/用户信息 | 10分钟 |
| Local | DataStore | Token/用户偏好 | 永久 |
| Remote | Retrofit | 实时数据 | - |

### 6.2 离线策略

```
Read:
  1. 返回Memory缓存(StateFlow)
  2. 返回Local缓存(Room) 
  3. 请求Remote(Retrofit) → 更新Local → 更新Memory

Write:
  1. 先写Remote → 成功
  2. 更新Local → 更新Memory
  3. 失败 → 提示用户/本地队列暂存
```

### 6.3 Room数据库表 (离线缓存)

```kotlin
@Entity(tableName = "cached_projects")
data class CachedProject(
    @PrimaryKey val id: Int,
    val name: String,
    val status: String,
    val totalAmount: Double,
    val settlementStatus: String?,
    val workersJson: String,       // JSON序列化的施工人员列表
    val cachedAt: Long
)

@Entity(tableName = "cached_dictionary")
data class CachedDictionary(
    @PrimaryKey val type: String,   // space_types / construction_plans
    val dataJson: String,
    val cachedAt: Long
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_projects WHERE cachedAt > :threshold ORDER BY id")
    suspend fun getProjects(threshold: Long): List<CachedProject>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<CachedProject>)
    
    @Query("DELETE FROM cached_projects WHERE cachedAt < :threshold")
    suspend fun cleanExpired(threshold: Long)
}
```

---

## 七、构建与发布

### 7.1 Gradle 构建类型

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "BASE_URL", "\"http://192.168.1.100:3000\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(...)
            buildConfigField("String", "BASE_URL", "\"https://api.salary.com\"")
        }
    }
}
```

### 7.2 发布配置

| 项目 | 配置 |
|------|------|
| applicationId | com.salary.manager |
| versionCode | 1 (自增) |
| versionName | 1.0.0 |
| 签名 | release.keystore |
| Target SDK | 35 |
| Min SDK | 26 |

---

## 八、开发实施顺序

| 阶段 | 内容 | 依赖 |
|------|------|------|
| 1 | 项目搭建 (Gradle配置/模块划分/Hilt) | - |
| 2 | Core模块 (网络/数据/设计系统/公共组件) | 阶段1 |
| 3 | 认证模块 (登录/注册/Token管理) | 阶段2 |
| 4 | 工程管理模块 (列表/创建/详情/编辑) | 阶段2 |
| 5 | 统计结算模块 (面板/月度/结算/预支) | 阶段2 |
| 6 | AI助手模块 (对话/排料/SVG渲染/知识库) | 后端AI接口完成 |
| 7 | 个人中心 (信息/密码/管理功能) | 阶段2 |
| 8 | 离线缓存 (Room/DataStore) | 阶段3+ |

---

> **文档版本**: V1.0 | **日期**: 2026-06-09