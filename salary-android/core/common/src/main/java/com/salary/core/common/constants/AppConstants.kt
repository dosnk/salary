package com.salary.core.common.constants

/**
 * 应用常量
 */
object AppConstants {
    const val PAGE_SIZE = 10

    // 工程状态
    const val STATUS_PREPARING = "preparing"
    const val STATUS_CONSTRUCTING = "constructing"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_CANCELED = "canceled"

    // 结算状态
    const val SETTLEMENT_UNSETTLED = "unsettled"
    const val SETTLEMENT_SETTLING = "settling"
    const val SETTLEMENT_SETTLED = "settled"

    // 用户角色
    const val ROLE_ADMIN = "admin"
    const val ROLE_CONSTRUCTOR = "constructor"
    const val ROLE_DOCUMENTER = "documenter"
}
