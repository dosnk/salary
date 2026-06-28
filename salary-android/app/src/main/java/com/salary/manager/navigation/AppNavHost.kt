package com.salary.manager.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.design.component.ApiLatencyChip
import com.salary.core.design.theme.AppColors
import com.salary.core.network.interceptor.LatencyTracker
import com.salary.manager.feature.ai.chat.AiChatScreen
import com.salary.manager.feature.ai.knowledge.KnowledgeScreen
import com.salary.manager.feature.ai.layout.LayoutPreviewScreen
import com.salary.manager.feature.ai.layout.MaterialLayoutScreen
import com.salary.manager.feature.auth.login.LoginScreen
import com.salary.manager.feature.auth.setup.ServerSetupScreen
import com.salary.manager.feature.home.dashboard.DashboardScreen
import com.salary.manager.feature.home.detail.ProjectDetailScreen
import com.salary.manager.feature.home.list.ProjectListScreen
import com.salary.manager.feature.profile.AboutScreen
import com.salary.manager.feature.profile.AiConfigScreen
import com.salary.manager.feature.profile.ChangePasswordScreen
import com.salary.manager.feature.profile.DictionaryScreen
import com.salary.manager.feature.profile.ProfileScreen
import com.salary.manager.feature.profile.UserManagementScreen
import com.salary.manager.feature.messages.MessageScreen
import com.salary.manager.feature.statistics.dashboard.StatisticsDashboardScreen

/**
 * 底部导航Tab项
 */
data class TabItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

/**
 * 主导航入口
 *
 * 启动流程：检查服务器配置 → 检查登录状态 → 进入主页
 * 后端离线时自动退出登录，回到登录页显示离线信息
 */
@Composable
fun AppNavHost() {
    val appViewModel: AppViewModel = hiltViewModel()
    val serverConfig = appViewModel.serverConfig
    val tokenStorage = appViewModel.tokenStorage
    val latencyTracker: LatencyTracker = appViewModel.latencyTracker
    val scope = rememberCoroutineScope()

    var isServerConfigured by rememberSaveable { mutableStateOf(false) }
    var isChecking by rememberSaveable { mutableStateOf(true) }

    // 监听后端离线状态
    val isOnline by latencyTracker.isOnline.collectAsState()

    // 监听401认证过期（AuthInterceptor检测到401后自动清除token并更新此状态）
    val isAuthenticated by tokenStorage.isAuthenticated.collectAsState()

    LaunchedEffect(Unit) {
        isServerConfigured = serverConfig.isConfigured()
        tokenStorage.initAuthState()
        isChecking = false
    }

    // 后端离线时自动退出登录
    LaunchedEffect(isOnline) {
        if (!isOnline && isAuthenticated) {
            tokenStorage.clearTokens()
        }
    }

    if (isChecking) {
        // 加载中
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Green400)
        }
    } else if (!isServerConfigured) {
        // 首次启动，配置服务器地址
        ServerSetupScreen(serverConfig = serverConfig) {
            isServerConfigured = true
        }
    } else if (!isAuthenticated) {
        // 未登录或token已失效，显示登录页（含API状态栏和离线信息）
        LoginScreen(
            onLoginSuccess = {
                // 登录成功后重置追踪器状态
                // token由LoginViewModel.saveTokens()自动更新isAuthenticated
                latencyTracker.reset()
            },
            latencyTracker = latencyTracker
        )
    } else {
        // 已登录，进入主页
        MainScaffold(
            onLogout = { scope.launch { tokenStorage.clearTokens() } },
            latencyTracker = latencyTracker
        )
    }
}

/**
 * 主页面Scaffold（含底部导航）
 * 5个Tab: 主页 / 工程管理 / 统计 / AI助手 / 我的
 * 底部统一显示API状态栏
 */
@Composable
fun MainScaffold(
    onLogout: () -> Unit = {},
    latencyTracker: LatencyTracker
) {
    val tabs = listOf(
        TabItem("主页", Icons.Default.Home, Route.Dashboard.route),
        TabItem("工程管理", Icons.Default.Work, Route.Home.route),
        TabItem("统计", Icons.Default.BarChart, Route.Statistics.route),
        TabItem("AI助手", Icons.Default.SmartToy, Route.AiChat.route),
        TabItem("我的", Icons.Default.Person, Route.Profile.route)
    )

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 主页子页面导航: 0=工作台, 1=工程详情
    var homeSubPage by rememberSaveable { mutableIntStateOf(0) }
    var homeProjectId by rememberSaveable { mutableStateOf(0) }

    // 工程管理子页面导航: 0=列表, 1=工程详情
    var projectSubPage by rememberSaveable { mutableIntStateOf(0) }
    var projectProjectId by rememberSaveable { mutableStateOf(0) }

    // AI子页面导航: 0=对话, 1=排料输入, 2=排料预览, 3=知识库
    var aiSubPage by rememberSaveable { mutableIntStateOf(0) }

    // 个人中心子页面导航: 0=主页, 1=修改密码, 2=字典管理, 3=用户管理, 4=关于, 5=消息, 6=AI配置
    var profileSubPage by rememberSaveable { mutableIntStateOf(0) }

    // 是否显示底部导航（子页面时隐藏）
    val showBottomBar = homeSubPage == 0 &&
            projectSubPage == 0 &&
            aiSubPage == 0 &&
            profileSubPage == 0

    // 个人中心API ViewModel
    val profileApiViewModel: ProfileApiViewModel = hiltViewModel()
    val users by profileApiViewModel.users.collectAsState()
    val spaceTypes by profileApiViewModel.spaceTypes.collectAsState()
    val constructionPlans by profileApiViewModel.constructionPlans.collectAsState()
    val wageDistributionTypes by profileApiViewModel.wageDistributionTypes.collectAsState()
    val messages by profileApiViewModel.messages.collectAsState()
    val unreadCount by profileApiViewModel.unreadCount.collectAsState()

    // API状态
    val isOnline by latencyTracker.isOnline.collectAsState()
    val latencyMs by latencyTracker.latencyMs.collectAsState()
    val lastError by latencyTracker.lastError.collectAsState()

    // 进入子页面时加载对应数据
    LaunchedEffect(profileSubPage) {
        when (profileSubPage) {
            2 -> profileApiViewModel.loadDictionaries()
            3 -> profileApiViewModel.loadUsers()
            5 -> profileApiViewModel.loadMessages()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                // 切换Tab时重置子页面
                                homeSubPage = 0
                                projectSubPage = 0
                                aiSubPage = 0
                                profileSubPage = 0
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.Green400,
                                selectedTextColor = AppColors.Green400,
                                indicatorColor = AppColors.Green50,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        // 只应用底部导航栏的padding，顶部由各页面自行处理（GreenTopNavBar含statusBarsPadding）
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding())) {

            // 页面内容区域（占据上方空间，可滚动）
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> {
                    // 主页/工作台 - 支持子页面导航
                    when (homeSubPage) {
                        0 -> DashboardScreen(
                            onNavigateToProject = { projectId ->
                                homeProjectId = projectId
                                homeSubPage = 1
                            }
                        )
                        1 -> ProjectDetailScreen(
                            projectId = homeProjectId,
                            onBack = { homeSubPage = 0 }
                        )
                    }
                }
                1 -> {
                    // 工程管理 - 支持子页面导航
                    when (projectSubPage) {
                        0 -> ProjectListScreen(
                            onNavigateToProject = { projectId ->
                                projectProjectId = projectId
                                projectSubPage = 1
                            }
                        )
                        1 -> ProjectDetailScreen(
                            projectId = projectProjectId,
                            onBack = { projectSubPage = 0 }
                        )
                    }
                }
                2 -> {
                    // 统计
                    StatisticsDashboardScreen()
                }
                3 -> {
                    // AI模块 - 支持子页面导航
                    when (aiSubPage) {
                        0 -> AiChatScreen(
                            onNavigateToLayout = { aiSubPage = 1 },
                            onNavigateToKnowledge = { aiSubPage = 3 }
                        )
                        1 -> MaterialLayoutScreen(
                            onNavigateToPreview = { aiSubPage = 2 },
                            onBack = { aiSubPage = 0 }
                        )
                        2 -> LayoutPreviewScreen(
                            onBack = { aiSubPage = 1 }
                        )
                        3 -> KnowledgeScreen(
                            onBack = { aiSubPage = 0 }
                        )
                    }
                }
                4 -> {
                    // 个人中心 - 支持子页面导航
                    when (profileSubPage) {
                        0 -> ProfileScreen(
                            onChangePassword = { profileSubPage = 1 },
                            onDictionaryManage = { profileSubPage = 2 },
                            onUserManage = { profileSubPage = 3 },
                            onAiConfig = { profileSubPage = 6 },
                            onAbout = { profileSubPage = 4 },
                            onMessages = { profileSubPage = 5 },
                            onLogout = onLogout
                        )
                        1 -> ChangePasswordScreen(
                            onBack = { profileSubPage = 0 },
                            onSuccess = { profileSubPage = 0 },
                            onSubmit = { oldPwd, newPwd, callback ->
                                profileApiViewModel.changePassword(oldPwd, newPwd, callback)
                            }
                        )
                        2 -> DictionaryScreen(
                            onBack = { profileSubPage = 0 },
                            spaceTypes = spaceTypes,
                            constructionPlans = constructionPlans,
                            wageDistributionTypes = wageDistributionTypes,
                            onAddSpaceType = { name, desc, callback ->
                                profileApiViewModel.addSpaceType(name, desc, callback)
                            },
                            onDeleteSpaceType = { id, callback ->
                                profileApiViewModel.deleteSpaceType(id, callback)
                            },
                            onAddConstructionPlan = { name, desc, callback ->
                                profileApiViewModel.addConstructionPlan(name, desc, callback)
                            },
                            onDeleteConstructionPlan = { id, callback ->
                                profileApiViewModel.deleteConstructionPlan(id, callback)
                            }
                        )
                        3 -> UserManagementScreen(
                            onBack = { profileSubPage = 0 },
                            users = users,
                            onCreateUser = { request, callback ->
                                profileApiViewModel.createUser(request, callback)
                            },
                            onResetPassword = { userId, newPassword, callback ->
                                profileApiViewModel.resetPassword(userId, newPassword, callback)
                            },
                            onDeleteUser = { userId, callback ->
                                profileApiViewModel.deleteUser(userId, callback)
                            }
                        )
                        4 -> AboutScreen(
                            onBack = { profileSubPage = 0 },
                            versionName = com.salary.manager.BuildConfig.VERSION_NAME
                        )
                        5 -> MessageScreen(
                            onBack = { profileSubPage = 0 },
                            messages = messages,
                            unreadCount = unreadCount,
                            onMarkRead = { id -> profileApiViewModel.markMessageRead(id) },
                            onMarkAllRead = { profileApiViewModel.markAllMessagesRead() },
                            onDeleteMessage = { id -> profileApiViewModel.deleteMessage(id) }
                        )
                        6 -> AiConfigScreen(
                            onBack = { profileSubPage = 0 }
                        )
                    }
                }
            }
            // 关闭内部 Box (页面内容区域)
        }

        // ===== 底部版权信息 + API时延信息（同一行，左边版权，右边时延）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "©微信群：三人行必有我师",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
            // API时延信息显示在右边
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 状态指示点
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (isOnline) Color(0xFF4CAF50) else AppColors.Error,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                // 状态文字
                if (isOnline) {
                    Text(
                        text = if (latencyMs > 0) "服务器在线：${latencyMs}ms" else "服务器在线",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            latencyMs <= 0 -> Color(0xFF4CAF50)
                            latencyMs < 200 -> Color(0xFF4CAF50)
                            latencyMs < 500 -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5722)
                        }
                    )
                } else {
                    val errorText = when {
                        lastError != null -> lastError
                        else -> "连接失败"
                    }
                    Text(
                        text = "服务器离线：$errorText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Error,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
