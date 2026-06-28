package com.salary.manager.feature.home.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.common.util.AmountFormatter
import com.salary.core.network.api.ProjectApi
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 工程详情UI模型
 */
data class ProjectDetailUiModel(
    val id: Int,
    val name: String,
    val status: String,
    val totalAmount: String,
    val remark: String?,
    val settlementStatus: String?,
    val salaryDistribution: String?,
    val createdAt: String,
    val updatedAt: String,
    val workers: List<WorkerUiModel>,
    val subprojects: List<SubprojectUiModel>,
    val files: List<FileUiModel>,
    val history: List<HistoryUiModel>
)

/**
 * 施工人员UI模型
 */
data class WorkerUiModel(
    val userId: Int,
    val nickname: String,
    val workdays: Int?
)

/**
 * 子项目UI模型
 */
data class SubprojectUiModel(
    val id: Int,
    val spaceTypeName: String,
    val constructionPlanName: String,
    val length: Double,
    val width: Double,
    val quantity: Double,
    val amount: String
)

/**
 * 附件UI模型
 */
data class FileUiModel(
    val id: Int,
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val uploadedAt: String
)

/**
 * 修改历史UI模型
 */
data class HistoryUiModel(
    val id: Int,
    val action: String,
    val actionName: String,
    val description: String,
    val nickname: String,
    val username: String,
    val createdAt: String
)

/**
 * 工程详情ViewModel
 */
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectApi: ProjectApi
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProjectDetailUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<ProjectDetailUiModel>> = _state.asStateFlow()

    /**
     * 加载工程详情
     */
    fun loadProject(projectId: Int) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val response = projectApi.getProjectDetail(projectId)
                if (response.code == 200) {
                    val dto = response.data ?: run {
                        _state.value = UiState.Error("工程数据不存在")
                        return@launch
                    }
                    // 加载修改历史
                    val historyList = loadHistory(projectId)

                    _state.value = UiState.Success(
                        ProjectDetailUiModel(
                            id = dto.id,
                            name = dto.name,
                            status = dto.status,
                            totalAmount = String.format("%.2f", dto.totalAmount),
                            remark = dto.remark,
                            settlementStatus = dto.settlementStatus,
                            salaryDistribution = dto.salaryDistribution,
                            createdAt = dto.createdAt,
                            updatedAt = dto.updatedAt,
                            workers = dto.workers.map { WorkerUiModel(it.id, it.nickname, it.workdays) },
                            subprojects = dto.subprojects.map {
                                SubprojectUiModel(
                                    it.id, it.spaceTypeName, it.constructionPlanName,
                                    it.length ?: 0.0, it.width ?: 0.0, it.quantity ?: 0.0,
                                    AmountFormatter.format(it.amount)
                                )
                            },
                            files = dto.files.map {
                                FileUiModel(it.id, it.fileName, it.fileUrl, it.fileSize, it.uploadedAt)
                            },
                            history = historyList
                        )
                    )
                } else {
                    _state.value = UiState.Error(NetworkErrorHandler.translateServerError(response.msg, "加载工程详情失败"))
                }
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载工程详情失败"))
            }
        }
    }

    /**
     * 加载工程修改历史
     */
    private suspend fun loadHistory(projectId: Int): List<HistoryUiModel> {
        return try {
            val response = projectApi.getProjectHistory(projectId)
            if (response.code == 200) {
                response.data?.map {
                    HistoryUiModel(
                        id = it.id,
                        action = it.action,
                        actionName = it.actionName,
                        description = it.description,
                        nickname = it.nickname,
                        username = it.username,
                        createdAt = it.createdAt
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
