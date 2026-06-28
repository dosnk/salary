package com.salary.core.ui.state

/**
 * UI状态基类 - 单向数据流
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val code: Int = -1) : UiState<Nothing>
}

/**
 * 列表专用UI状态
 */
sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>
    data class Success<T>(
        val items: List<T>,
        val hasMore: Boolean = false,
        val page: Int = 1
    ) : ListUiState<T>
    data class Error(val message: String, val code: Int = -1) : ListUiState<Nothing>
}
