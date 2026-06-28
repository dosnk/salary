package com.salary.manager.feature.ai.layout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import com.salary.core.network.api.MaterialOptionsDto
import com.salary.core.network.api.LayoutResponse
import com.salary.manager.feature.ai.data.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 排料计算ViewModel
 *
 * 管理材料选择和排料计算流程
 */
@HiltViewModel
class MaterialLayoutViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    /** 房间长度(cm) */
    private val _roomLength = MutableStateFlow("")
    val roomLength: StateFlow<String> = _roomLength.asStateFlow()

    /** 房间宽度(cm) */
    private val _roomWidth = MutableStateFlow("")
    val roomWidth: StateFlow<String> = _roomWidth.asStateFlow()

    /** 材料分类列表 */
    private val _categories = MutableStateFlow<List<MaterialCategoryDto>>(emptyList())
    val categories: StateFlow<List<MaterialCategoryDto>> = _categories.asStateFlow()

    /** 所有材料列表 */
    private val _materials = MutableStateFlow<List<MaterialDto>>(emptyList())
    val materials: StateFlow<List<MaterialDto>> = _materials.asStateFlow()

    /** 选中的面材ID */
    private val _selectedPanelId = MutableStateFlow<Int?>(null)
    val selectedPanelId: StateFlow<Int?> = _selectedPanelId.asStateFlow()

    /** 选中的主龙骨ID */
    private val _selectedMainKeelId = MutableStateFlow<Int?>(null)
    val selectedMainKeelId: StateFlow<Int?> = _selectedMainKeelId.asStateFlow()

    /** 选中的副龙骨ID */
    private val _selectedSubKeelId = MutableStateFlow<Int?>(null)
    val selectedSubKeelId: StateFlow<Int?> = _selectedSubKeelId.asStateFlow()

    /** 选中的收边条ID */
    private val _selectedTrimId = MutableStateFlow<Int?>(null)
    val selectedTrimId: StateFlow<Int?> = _selectedTrimId.asStateFlow()

    /** 排料计算结果 */
    private val _layoutResult = MutableStateFlow<LayoutResponse?>(null)
    val layoutResult: StateFlow<LayoutResponse?> = _layoutResult.asStateFlow()

    /** 是否正在计算 */
    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    /** 错误信息 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadMaterials()
    }

    /** 加载材料数据 */
    private fun loadMaterials() {
        viewModelScope.launch {
            aiRepository.getMaterialCategories()
                .onSuccess { _categories.value = it }

            aiRepository.getAllMaterials()
                .onSuccess { materials ->
                    _materials.value = materials
                    // 默认选中第一个材料
                    if (materials.isNotEmpty()) {
                        val panels = materials.filter { it.categoryName == "面材" }
                        if (panels.isNotEmpty()) _selectedPanelId.value = panels[0].id

                        val mainKeels = materials.filter { it.categoryName == "主龙骨" }
                        if (mainKeels.isNotEmpty()) _selectedMainKeelId.value = mainKeels[0].id

                        val subKeels = materials.filter { it.categoryName == "副龙骨" }
                        if (subKeels.isNotEmpty()) _selectedSubKeelId.value = subKeels[0].id

                        val trims = materials.filter { it.categoryName == "收边条" }
                        if (trims.isNotEmpty()) _selectedTrimId.value = trims[0].id
                    }
                }
        }
    }

    /** 更新房间长度 */
    fun updateRoomLength(value: String) { _roomLength.value = value }

    /** 更新房间宽度 */
    fun updateRoomWidth(value: String) { _roomWidth.value = value }

    /** 选择面材 */
    fun selectPanel(id: Int) { _selectedPanelId.value = id }

    /** 选择主龙骨 */
    fun selectMainKeel(id: Int) { _selectedMainKeelId.value = id }

    /** 选择副龙骨 */
    fun selectSubKeel(id: Int) { _selectedSubKeelId.value = id }

    /** 选择收边条 */
    fun selectTrim(id: Int) { _selectedTrimId.value = id }

    /** 执行排料计算 */
    fun calculateLayout() {
        val length = _roomLength.value.toDoubleOrNull()
        val width = _roomWidth.value.toDoubleOrNull()

        if (length == null || width == null || length <= 0 || width <= 0) {
            _error.value = "请输入有效的房间尺寸"
            return
        }

        _isCalculating.value = true
        _error.value = null
        _layoutResult.value = null

        // 构建材料选项，传递用户选择的材料ID到后端
        val materialOptions = MaterialOptionsDto(
            panelId = _selectedPanelId.value,
            mainKeelId = _selectedMainKeelId.value,
            subKeelId = _selectedSubKeelId.value,
            trimId = _selectedTrimId.value
        )

        viewModelScope.launch {
            aiRepository.calculateLayout(length, width, materialOptions)
                .onSuccess { result ->
                    _layoutResult.value = result
                }
                .onFailure { e ->
                    _error.value = e.message ?: "排料计算失败"
                }
            _isCalculating.value = false
        }
    }

    /** 清除计算结果 */
    fun clearResult() {
        _layoutResult.value = null
    }

    /** 获取指定分类的材料列表 */
    fun getMaterialsByCategory(categoryName: String): List<MaterialDto> {
        return _materials.value.filter { it.categoryName == categoryName }
    }
}
