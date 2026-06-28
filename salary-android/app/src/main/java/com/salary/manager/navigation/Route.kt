package com.salary.manager.navigation

/**
 * 应用导航路由定义
 */
sealed class Route(val route: String) {
    // 认证
    data object Login : Route("login")
    data object Register : Route("register")

    // 主页/工作台 (底部Tab)
    data object Dashboard : Route("dashboard")
    data object Home : Route("home")
    data object ProjectDetail : Route("project/{projectId}") {
        fun createRoute(projectId: Int) = "project/$projectId"
    }
    data object ProjectEdit : Route("project/{projectId}/edit") {
        fun createRoute(projectId: Int) = "project/$projectId/edit"
    }

    // 统计结算 (底部Tab)
    data object Statistics : Route("statistics")
    data object MonthlyStats : Route("statistics/monthly")
    data object SettlementList : Route("settlement/list")
    data object SettlementPreview : Route("settlement/preview")
    data object SettlementHistory : Route("settlement/history")
    data object AdvanceList : Route("advance")

    // AI助手 (底部Tab)
    data object AiChat : Route("ai/chat")
    data object MaterialLayout : Route("ai/layout")
    data object LayoutPreview : Route("ai/layout/preview")
    data object KnowledgeBase : Route("ai/knowledge")

    // 个人 (底部Tab)
    data object Profile : Route("profile")
    data object ChangePassword : Route("profile/password")
    data object Dictionary : Route("admin/dictionary")
    data object UserManagement : Route("admin/users")
}
