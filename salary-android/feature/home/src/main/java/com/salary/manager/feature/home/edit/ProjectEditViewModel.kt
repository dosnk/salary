package com.salary.manager.feature.home.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.dto.UpdateProjectRequest
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 子项目UI模型
 */
data class SubprojectEditUiModel(
    val id: Int,
    val spaceTypeName: String,
    val constructionPlanName: String,
    val length: Double,
    val width: Double,
    val amount: String
)

/**
 * 编辑工程UI模型
 */
data class ProjectEditUiModel(
    val id: Int,
    val name: String,
    val status: String,
    val remark: String?,
    val subprojects: List<SubprojectEditUiModel>
)

/**
 * 编辑工程ViewModel
 */
@HiltViewModel
class ProjectEditViewModel @Inject constructor(
    private val projectApi: ProjectApi
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProjectEditUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<ProjectEditUiModel>> = _state.asStateFlow()

    private var currentProjectId: Int = 0

    /**
     * 加载工程详情
     */
    fun loadProject(projectId: Int) {
        currentProjectId = projectId
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val response = projectApi.getProjectDetail(projectId)
                if (response.code == 200) {
                    val dto = response.data ?: run {
                        _state.value = UiState.Error("工程数据不存在")
                        return@launch
                    }
                    _state.value = UiState.Success(
                        ProjectEditUiModel(
                            id = dto.id,
                            name = dto.name,
                            status = dto.status,
                            remark = dto.remark,
                            subprojects = dto.subprojects.map {
                                SubprojectEditUiModel(
                                    it.id, it.spaceTypeName, it.constructionPlanName,
                                    it.length ?: 0.0, it.width ?: 0.0, String.format("%.2f", it.amount ?: 0.0)
                                )
                            }
                        )
                    )
                } else {
                    _state.value = UiState.Error(NetworkErrorHandler.translateServerError(response.msg, "加载工程信息失败"))
                }
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载工程信息失败"))
            }
        }
    }

    /**
     * 保存工程修改
     */
    fun saveProject(name: String, status: String, remark: String?) {
        viewModelScope.launch {
            try {
                val response = projectApi.updateProject(
                    currentProjectId,
                    UpdateProjectRequest(name = name, status = status, remark = remark)
                )
                if (response.code == 200) {
                    // 重新加载
                    loadProject(currentProjectId)
                }
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }

    /**
     * 删除子项目
     */
    fun deleteSubproject(subprojectId: Int) {
        viewModelScope.launch {
            try {
                // TODO: 调用删除子项目API
                loadProject(currentProjectId)
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }
}
