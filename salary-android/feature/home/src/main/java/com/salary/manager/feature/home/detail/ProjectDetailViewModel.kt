package com.salary.manager.feature.home.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.AppLog
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.api.UpdateSubprojectRequest
import com.salary.core.network.api.UserApi
import com.salary.core.network.api.UserDto
import com.salary.core.network.dto.ConstructorItem
import com.salary.core.network.dto.UpdateProjectRequest
import com.salary.core.network.dto.WorkerWorkdayItem
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * workdays为Double类型，因后端NUMERIC(6,2)返回带小数
 */
data class WorkerUiModel(
    val userId: Int,
    val nickname: String,
    val workdays: Double?
)

/**
 * 子项目UI模型
 *
 * amount为Double类型，由UI层调用AmountFormatter.format()格式化显示
 * 避免预先格式化为字符串导致UI层再次格式化时解析失败（双重格式化问题）
 */
data class SubprojectUiModel(
    val id: Int,
    val spaceTypeName: String,
    val constructionPlanName: String,
    val length: Double,
    val width: Double,
    val quantity: Double,
    val amount: Double,
    /** 子项目备注（null表示无备注，编辑弹窗初始化时使用） */
    val remark: String? = null,
    /** 实测数量（异形空间现场实测值，非null时覆盖按长宽计算的quantity） */
    val measuredQuantity: Double? = null,
    /** 实测备注（记录实测方式或现场说明） */
    val measuredNote: String? = null,
    /** 空间形状（rectangle/right_triangle/trapezoid/circle，决定编辑弹窗参数组） */
    val spaceTypeShape: String = "rectangle",
    /** 高度（米，仅梯形等需要三维参数的形状使用，null表示未设置） */
    val height: Double? = null
)

/**
 * 附件UI模型
 * type字段用于判断媒体类型（图片/视频可直接预览，其他类型用系统应用打开）
 */
data class FileUiModel(
    val id: Int,
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val uploadedAt: String,
    /** 文件MIME类型，如 image/jpeg、application/pdf */
    val type: String? = null
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
    private val projectApi: ProjectApi,
    /** 用于拼接附件完整访问URL（后端path字段为相对路径） */
    private val serverConfig: ServerConfig,
    /** 用于加载施工人员列表（编辑工程弹窗中使用） */
    private val userApi: UserApi,
    /** 用于获取当前用户角色（控制编辑/删除等操作按钮的显示） */
    private val userStorage: UserStorage
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ProjectDetailUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<ProjectDetailUiModel>> = _state.asStateFlow()

    /** 当前用户角色（用于UI层按角色控制编辑/删除等操作按钮的显示） */
    val userRole: StateFlow<String> = userStorage.roleFlow

    /** 错误消息（一次性事件，使用 SharedFlow 避免配置变化后重复消费） */
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    /** 成功消息（一次性事件，使用 SharedFlow 避免配置变化后重复消费） */
    private val _successMessage = MutableSharedFlow<String>()
    val successMessage: SharedFlow<String> = _successMessage.asSharedFlow()

    /** 子项目保存中状态 */
    private val _savingSubproject = MutableStateFlow(false)
    val savingSubproject: StateFlow<Boolean> = _savingSubproject.asStateFlow()

    /** 可选施工人员列表（编辑工程时加载） */
    private val _constructors = MutableStateFlow<List<UserDto>>(emptyList())
    val constructors: StateFlow<List<UserDto>> = _constructors.asStateFlow()

    /** 工程更新中状态 */
    private val _savingProject = MutableStateFlow(false)
    val savingProject: StateFlow<Boolean> = _savingProject.asStateFlow()

    private companion object {
        const val TAG = "ProjectDetailViewModel"
    }

    /**
     * 加载工程详情
     * @param silent 静默模式：不显示Loading状态，用于保存成功后的刷新，避免页面闪烁
     */
    fun loadProject(projectId: Int, silent: Boolean = false) {
        viewModelScope.launch {
            // 静默模式不覆盖已有Success状态，仅后台刷新数据，避免保存后页面闪烁
            if (!silent) {
                _state.value = UiState.Loading
            }
            try {
                val response = projectApi.getProjectDetail(projectId)
                if (response.code == 200) {
                    val dto = response.data ?: run {
                        _state.value = UiState.Error("工程数据不存在")
                        return@launch
                    }
                    // 加载修改历史
                    val historyList = loadHistory(projectId)
                    // 预构建附件完整URL（后端path为相对路径，需拼接服务器地址）
                    val mappedFiles = dto.files.map {
                        FileUiModel(
                            id = it.id,
                            fileName = it.fileName,
                            fileUrl = serverConfig.buildFileUrl(it.fileUrl),
                            fileSize = it.fileSize,
                            uploadedAt = it.uploadedAt,
                            type = it.type
                        )
                    }

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
                                    it.amount ?: 0.0,
                                    it.remark,
                                    it.measuredQuantity,
                                    it.measuredNote,
                                    it.spaceTypeShape ?: "rectangle",
                                    // 后端 height 单位厘米，UI 层统一使用米
                                    it.height?.let { h -> h / 100.0 }
                                )
                            },
                            files = mappedFiles,
                            history = historyList
                        )
                    )
                } else {
                    AppLog.w(TAG, "加载工程详情失败: projectId=$projectId, code=${response.code}, msg=${response.msg}")
                    _state.value = UiState.Error(NetworkErrorHandler.translateServerError(response.msg, "加载工程详情失败"))
                }
            } catch (e: Exception) {
                // 记录详细异常信息便于诊断（如反序列化失败、网络超时等）
                AppLog.e(TAG, "加载工程详情异常: projectId=$projectId, ${e.javaClass.simpleName}: ${e.message}", e)
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

    /**
     * 更新子项目
     * 前端输入为米，需转换为厘米（×100）后传给后端
     * @param projectId 工程ID
     * @param subprojectId 子项目ID
     * @param lengthMeter 长度（米）
     * @param widthMeter 宽度（米）
     * @param heightMeter 高度（米，仅梯形使用，null 表示清除高度）
     * @param remark 备注（空字符串表示删除备注，后端会转为 null 存储）
     * @param measuredQuantity 实测数量（异形空间现场实测值，null表示清除实测回退到按长宽计算）
     * @param measuredNote 实测备注（空字符串表示清除）
     */
    fun updateSubproject(
        projectId: Int,
        subprojectId: Int,
        lengthMeter: Double,
        widthMeter: Double,
        heightMeter: Double?,
        remark: String,
        measuredQuantity: Double? = null,
        measuredNote: String = ""
    ) {
        viewModelScope.launch {
            _savingSubproject.value = true
            // SharedFlow 不保留值，无需清除上一次消息
            try {
                // 米转厘米
                val lengthCm = lengthMeter * 100
                val widthCm = widthMeter * 100
                val heightCm = heightMeter?.let { it * 100 }
                val response = projectApi.updateSubproject(
                    projectId,
                    subprojectId,
                    UpdateSubprojectRequest(
                        length = lengthCm,
                        width = widthCm,
                        height = heightCm,
                        remark = remark,
                        // 实测数量：null 表示清除实测回退到按长宽计算
                        measuredQuantity = measuredQuantity,
                        measuredNote = measuredNote.ifBlank { null }
                    )
                )
                if (response.code == 200) {
                    _successMessage.emit("子项目保存成功")
                    // 保存成功后静默刷新工程详情，避免页面闪烁（保留当前显示，仅更新数据）
                    loadProject(projectId, silent = true)
                } else {
                    _errorMessage.emit(NetworkErrorHandler.translateServerError(response.msg, "保存子项目失败"))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "更新子项目异常: subprojectId=$subprojectId, ${e.javaClass.simpleName}: ${e.message}", e)
                _errorMessage.emit(NetworkErrorHandler.translate(e, "保存子项目失败"))
            } finally {
                _savingSubproject.value = false
            }
        }
    }

    /**
     * 删除工程附件
     * 成功后从当前state的files列表中移除该项，避免全量刷新
     * @param projectId 工程ID
     * @param fileId 文件ID
     */
    fun deleteFile(projectId: Int, fileId: Int) {
        viewModelScope.launch {
            try {
                val response = projectApi.deleteFile(projectId, fileId)
                if (response.code == 200) {
                    // 从当前files列表中移除已删除项，避免全量刷新工程详情
                    val currentState = _state.value
                    if (currentState is UiState.Success) {
                        val updatedFiles = currentState.data.files.filterNot { it.id == fileId }
                        _state.value = UiState.Success(currentState.data.copy(files = updatedFiles))
                    }
                    _successMessage.emit("附件已删除")
                } else {
                    _errorMessage.emit(NetworkErrorHandler.translateServerError(response.msg, "删除附件失败"))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "删除附件异常: fileId=$fileId, ${e.javaClass.simpleName}: ${e.message}", e)
                _errorMessage.emit(NetworkErrorHandler.translate(e, "删除附件失败"))
            }
        }
    }

    /**
     * 加载可选施工人员列表（编辑工程弹窗使用）
     */
    fun loadConstructors() {
        viewModelScope.launch {
            try {
                val response = userApi.getConstructors()
                if (response.code == 200) {
                    _constructors.value = response.data ?: emptyList()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "加载施工人员列表异常: ${e.message}", e)
            }
        }
    }

    /**
     * 更新工程信息
     * 支持更新名称、备注、状态、施工人员、工资分配方式（及工日）
     * 成功后重新加载工程详情刷新数据
     *
     * @param projectId 工程ID
     * @param name 工程名称（null表示不更新）
     * @param remark 工程备注（空字符串表示删除备注，后端会转为 null 存储）
     * @param status 工程状态（null表示不更新）
     * @param salaryDistribution 工资分配方式（null表示不更新）
     * @param constructorIds 选中的施工人员ID列表
     * @param workerWorkdays 按工日分配模式下的工日映射（userId → 工日）
     */
    fun updateProject(
        projectId: Int,
        name: String?,
        remark: String,
        status: String?,
        salaryDistribution: String?,
        constructorIds: List<Int>,
        workerWorkdays: Map<Int, String>
    ) {
        viewModelScope.launch {
            _savingProject.value = true
            // SharedFlow 不保留值，无需清除上一次消息
            try {
                // 构造施工人员列表
                val constructors = constructorIds.map { ConstructorItem(it) }
                // 按工日分配时附加工日列表
                val workDaysList = if (salaryDistribution == "work_days") {
                    constructorIds.mapNotNull { uid ->
                        val wd = workerWorkdays[uid]?.toDoubleOrNull()
                        if (wd != null && wd > 0) WorkerWorkdayItem(uid, wd) else null
                    }
                } else null

                val request = UpdateProjectRequest(
                    name = name,
                    status = status,
                    remark = remark,
                    salaryDistribution = salaryDistribution,
                    constructors = constructors,
                    workerWorkDays = workDaysList
                )

                val response = projectApi.updateProject(projectId, request)
                if (response.code == 200) {
                    _successMessage.emit("工程信息已保存")
                    // 保存成功后静默刷新工程详情，避免页面闪烁（保留当前显示，仅更新数据）
                    loadProject(projectId, silent = true)
                } else {
                    _errorMessage.emit(NetworkErrorHandler.translateServerError(response.msg, "保存工程失败"))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "更新工程异常: projectId=$projectId, ${e.javaClass.simpleName}: ${e.message}", e)
                _errorMessage.emit(NetworkErrorHandler.translate(e, "保存工程失败"))
            } finally {
                _savingProject.value = false
            }
        }
    }

    // 注：原 clearErrorMessage/clearSuccessMessage 已移除，SharedFlow 不保留值无需清除
}
