package com.salary.manager.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
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
import com.salary.core.design.theme.AppColors
import com.salary.core.design.component.SwipeBackLayout
import com.salary.core.network.interceptor.HealthMonitor
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
    val userStorage = appViewModel.userStorage
    val latencyTracker: LatencyTracker = appViewModel.latencyTracker
    val healthMonitor: HealthMonitor = appViewModel.healthMonitor
    val scope = rememberCoroutineScope()

    var isServerConfigured by rememberSaveable { mutableStateOf(false) }
    var isChecking by rememberSaveable { mutableStateOf(true) }

    // 登录成功欢迎提示：登录成功后设为true，传给MainScaffold触发Snackbar
    var showWelcomeMessage by remember { mutableStateOf(false) }

    // 监听后端离线状态
    val isOnline by latencyTracker.isOnline.collectAsState()

    // 监听401认证过期（AuthInterceptor检测到401后自动清除token并更新此状态）
    val isAuthenticated by tokenStorage.isAuthenticated.collectAsState()

    // 监听用户昵称状态（响应式，登录/登出后自动更新）
    val userNickname by userStorage.nicknameFlow.collectAsState()

    LaunchedEffect(Unit) {
        // 初始化ServerConfig缓存（供Retrofit构建同步读取）
        serverConfig.initConfig()
        isServerConfigured = serverConfig.isConfigured()
        // 初始化TokenStorage缓存（供AuthInterceptor同步读取）
        tokenStorage.initAuthState()
        userStorage.initUserState()
        isChecking = false
        // 启动后端健康监控（定时主动探测，不依赖业务请求）
        // 登录页等无业务请求场景也能实时显示后端在线状态
        healthMonitor.start()
    }

    // 后端离线时自动退出登录
    LaunchedEffect(isOnline) {
        if (!isOnline && isAuthenticated) {
            tokenStorage.clearTokens()
            userStorage.clearUserInfo()
        }
    }

    if (isChecking) {
        // 加载中
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Green400)
        }
    } else if (!isServerConfigured) {
        // 首次启动，配置服务器地址
        // 注意：配置保存后，用户需重启应用以应用新地址（Retrofit单例需要重建）
        ServerSetupScreen(onConfigured = { isServerConfigured = true })
    } else if (!isAuthenticated) {
        // 未登录或token已失效，显示登录页（含API状态栏和离线信息）
        LoginScreen(
            onLoginSuccess = {
                // 登录成功后重置追踪器状态
                // token由LoginViewModel.saveTokens()自动更新isAuthenticated
                latencyTracker.reset()
                // 标记需要显示欢迎提示
                showWelcomeMessage = true
            },
            latencyTracker = latencyTracker
        )
    } else {
        // 已登录，进入主页
        MainScaffold(
            onLogout = { scope.launch { tokenStorage.clearTokens(); userStorage.clearUserInfo() } },
            latencyTracker = latencyTracker,
            userNickname = userNickname,
            showWelcomeMessage = showWelcomeMessage,
            onWelcomeShown = { showWelcomeMessage = false }
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
    latencyTracker: LatencyTracker,
    userNickname: String = "",
    showWelcomeMessage: Boolean = false,
    onWelcomeShown: () -> Unit = {}
) {
    val tabs = listOf(
        TabItem("主页", Icons.Default.Home, Route.Dashboard.route),
        TabItem("工程管理", Icons.Default.Work, Route.Home.route),
        TabItem("统计", Icons.Default.BarChart, Route.Statistics.route),
        TabItem("AI助手", Icons.Default.SmartToy, Route.AiChat.route),
        TabItem("我的", Icons.Default.Person, Route.Profile.route)
    )

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 欢迎提示Snackbar
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val welcomeScope = rememberCoroutineScope()

    // 登录成功后显示欢迎提示
    LaunchedEffect(showWelcomeMessage) {
        if (showWelcomeMessage) {
            val displayName = userNickname.ifBlank { "用户" }
            welcomeScope.launch {
                snackbarHostState.showSnackbar(
                    message = "欢迎回来，$displayName！",
                    withDismissAction = true
                )
            }
            onWelcomeShown()
        }
    }

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

    // 记录点击消息图标时所在的Tab，用于消息页返回时恢复到原页面
    // -1 表示未从其他Tab跳转过来（即从"我的"Tab直接进入消息页）
    var messageOriginTab by rememberSaveable { mutableIntStateOf(-1) }

    // 数据刷新触发器：切换到工程管理/统计Tab时递增，触发对应页面静默刷新
    // 解决：主页新建工程后切换Tab数据不更新的问题
    var projectListRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }
    var statisticsRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }

    // 检测键盘是否弹出（IME可见时隐藏底部导航栏，避免输入栏与键盘间产生间距）
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    // 是否显示底部导航（子页面时隐藏；键盘弹出时也隐藏，避免底部导航栏占据空间导致输入栏与键盘间产生间距）
    val showBottomBar = !isImeVisible &&
            homeSubPage == 0 &&
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

    // 全局消息点击回调：跳转到消息列表页（profileSubPage=5）
    // 所有页面顶部导航栏的消息图标共用此回调，确保点击后统一进入消息中心
    // 记录原Tab，消息页返回时恢复到原页面（而非停留在"我的"Tab）
    val onMessageClick: () -> Unit = {
        if (selectedTab != 4) {
            messageOriginTab = selectedTab  // 记录原Tab（非"我的"Tab时才记录）
        }
        selectedTab = 4  // 切换到"我的"Tab
        profileSubPage = 5  // 进入消息列表子页面
    }

    // 进入子页面时加载对应数据
    LaunchedEffect(profileSubPage) {
        when (profileSubPage) {
            2 -> profileApiViewModel.loadDictionaries()
            3 -> profileApiViewModel.loadUsers()
            5 -> profileApiViewModel.loadMessages()
        }
    }

    // Tab切换时触发对应页面数据刷新
    // 切换到工程管理/统计Tab时递增刷新触发器，触发静默刷新
    // 切换到个人中心Tab时加载未读消息数
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            1 -> projectListRefreshTrigger++  // 工程管理：静默刷新工程列表
            2 -> statisticsRefreshTrigger++   // 统计：静默刷新统计数据
            4 -> profileApiViewModel.loadUnreadCount()
        }
    }

    // 进入主页时主动加载一次未读消息数，使所有页面顶部导航栏消息Badge一致
    // 不依赖Tab切换，确保登录后首个页面（主页）就能看到未读数
    LaunchedEffect(Unit) {
        profileApiViewModel.loadUnreadCount()
    }

    Scaffold(
        // 不让Scaffold消耗IME（键盘）insets，避免键盘高度通过padding传递给内容
        // 键盘适配由各页面内部自行处理（如AI对话页面的InputBar使用windowInsetsPadding）
        // 否则键盘高度会被应用两次：Scaffold的padding + 页面内部的imePadding/windowInsetsPadding
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 底部导航栏：键盘收起时拉幕式展开，键盘弹出时拉幕式收起
            // 使用 expandVertically + fadeIn 实现优雅的拉幕过渡效果
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar,
                enter = androidx.compose.animation.expandVertically(
                    expandFrom = androidx.compose.ui.Alignment.Bottom,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                ),
                exit = androidx.compose.animation.shrinkVertically(
                    shrinkTowards = androidx.compose.ui.Alignment.Bottom,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
                )
            ) {
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
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding())) {
            when (selectedTab) {
                0 -> {
                    // 主页/工作台 - 支持子页面导航
                    when (homeSubPage) {
                        0 -> DashboardScreen(
                            onNavigateToProject = { projectId ->
                                homeProjectId = projectId
                                homeSubPage = 1
                            },
                            latencyTracker = latencyTracker,
                            userNickname = userNickname,
                            onMessageClick = onMessageClick,
                            unreadCount = unreadCount
                        )
                        1 -> {
                            // 工程详情页：滑动返回 + 系统返回拦截，避免误退出应用
                            BackHandler { homeSubPage = 0 }
                            SwipeBackLayout(onBack = { homeSubPage = 0 }) {
                                ProjectDetailScreen(
                                    projectId = homeProjectId,
                                    onBack = { homeSubPage = 0 },
                                    onDataChanged = {
                                        // 工程数据变更时，递增工程管理和统计的刷新触发器
                                        projectListRefreshTrigger++
                                        statisticsRefreshTrigger++
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // 工程管理 - 支持子页面导航
                    when (projectSubPage) {
                        0 -> ProjectListScreen(
                            onNavigateToProject = { projectId ->
                                projectProjectId = projectId
                                projectSubPage = 1
                            },
                            userNickname = userNickname,
                            refreshTrigger = projectListRefreshTrigger,
                            onMessageClick = onMessageClick,
                            unreadCount = unreadCount
                        )
                        1 -> {
                            // 工程详情页：滑动返回 + 系统返回拦截，避免误退出应用
                            BackHandler { projectSubPage = 0 }
                            SwipeBackLayout(onBack = { projectSubPage = 0 }) {
                                ProjectDetailScreen(
                                    projectId = projectProjectId,
                                    onBack = { projectSubPage = 0 },
                                    onDataChanged = {
                                        // 工程数据变更时，递增工程管理和统计的刷新触发器
                                        projectListRefreshTrigger++
                                        statisticsRefreshTrigger++
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // 统计（含预支Tab）
                    StatisticsDashboardScreen(
                        userNickname = userNickname,
                        refreshTrigger = statisticsRefreshTrigger,
                        onMessageClick = onMessageClick,
                        unreadCount = unreadCount
                    )
                }
                3 -> {
                    // AI模块 - 支持子页面导航
                    when (aiSubPage) {
                        0 -> AiChatScreen(
                            onNavigateToLayout = { aiSubPage = 1 },
                            onNavigateToKnowledge = { aiSubPage = 3 },
                            userNickname = userNickname,
                            onMessageClick = onMessageClick
                        )
                        1 -> {
                            BackHandler { aiSubPage = 0 }
                            SwipeBackLayout(onBack = { aiSubPage = 0 }) {
                                MaterialLayoutScreen(
                                    onNavigateToPreview = { aiSubPage = 2 },
                                    onBack = { aiSubPage = 0 }
                                )
                            }
                        }
                        2 -> {
                            BackHandler { aiSubPage = 1 }
                            SwipeBackLayout(onBack = { aiSubPage = 1 }) {
                                LayoutPreviewScreen(
                                    onBack = { aiSubPage = 1 }
                                )
                            }
                        }
                        3 -> {
                            BackHandler { aiSubPage = 0 }
                            SwipeBackLayout(onBack = { aiSubPage = 0 }) {
                                KnowledgeScreen(
                                    onBack = { aiSubPage = 0 }
                                )
                            }
                        }
                    }
                }
                4 -> {
                    // 个人中心 - 支持子页面导航，所有子页面均支持滑动返回
                    when (profileSubPage) {
                        0 -> ProfileScreen(
                            onChangePassword = { profileSubPage = 1 },
                            onDictionaryManage = { profileSubPage = 2 },
                            onUserManage = { profileSubPage = 3 },
                            onAiConfig = { profileSubPage = 6 },
                            onAbout = { profileSubPage = 4 },
                            onMessages = { profileSubPage = 5 },
                            onLogout = onLogout,
                            userNickname = userNickname,
                            unreadCount = unreadCount,
                            onMessageClick = onMessageClick
                        )
                        1 -> {
                            BackHandler { profileSubPage = 0 }
                            SwipeBackLayout(onBack = { profileSubPage = 0 }) {
                                ChangePasswordScreen(
                                    onBack = { profileSubPage = 0 },
                                    onSuccess = { profileSubPage = 0 },
                                    onSubmit = { oldPwd, newPwd, callback ->
                                        profileApiViewModel.changePassword(oldPwd, newPwd, callback)
                                    }
                                )
                            }
                        }
                        2 -> {
                            BackHandler { profileSubPage = 0 }
                            SwipeBackLayout(onBack = { profileSubPage = 0 }) {
                                DictionaryScreen(
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
                            }
                        }
                        3 -> {
                            BackHandler { profileSubPage = 0 }
                            SwipeBackLayout(onBack = { profileSubPage = 0 }) {
                                UserManagementScreen(
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
                            }
                        }
                        4 -> {
                            BackHandler { profileSubPage = 0 }
                            SwipeBackLayout(onBack = { profileSubPage = 0 }) {
                                AboutScreen(
                                    onBack = { profileSubPage = 0 },
                                    versionName = com.salary.manager.BuildConfig.VERSION_NAME
                                )
                            }
                        }
                        5 -> {
                            // 消息页返回逻辑：若从其他Tab点击消息图标进入，返回时恢复原Tab
                            val onMessageBack: () -> Unit = {
                                profileSubPage = 0
                                if (messageOriginTab in 0..3) {
                                    selectedTab = messageOriginTab
                                    messageOriginTab = -1  // 重置，避免下次误恢复
                                }
                            }
                            BackHandler { onMessageBack() }
                            SwipeBackLayout(onBack = onMessageBack) {
                                MessageScreen(
                                    onBack = onMessageBack,
                                    messages = messages,
                                    unreadCount = unreadCount,
                                    onMarkRead = { id -> profileApiViewModel.markMessageRead(id) },
                                    onMarkAllRead = { profileApiViewModel.markAllMessagesRead() },
                                    onDeleteMessage = { id -> profileApiViewModel.deleteMessage(id) }
                                )
                            }
                        }
                        6 -> {
                            BackHandler { profileSubPage = 0 }
                            SwipeBackLayout(onBack = { profileSubPage = 0 }) {
                                AiConfigScreen(
                                    onBack = { profileSubPage = 0 }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
