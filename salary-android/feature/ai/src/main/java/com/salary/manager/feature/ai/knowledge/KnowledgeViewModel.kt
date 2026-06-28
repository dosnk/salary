package com.salary.manager.feature.ai.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import com.salary.manager.feature.ai.data.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 知识库ViewModel
 *
 * 管理材料分类和材料列表数据
 */
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    /** 材料分类列表 */
    private val _categories = MutableStateFlow<List<MaterialCategoryDto>>(emptyList())
    val categories: StateFlow<List<MaterialCategoryDto>> = _categories.asStateFlow()

    /** 所有材料列表 */
    private val _materials = MutableStateFlow<List<MaterialDto>>(emptyList())
    val materials: StateFlow<List<MaterialDto>> = _materials.asStateFlow()

    /** 当前选中的分类ID */
    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    val selectedCategoryId: StateFlow<Int?> = _selectedCategoryId.asStateFlow()

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 选中的材料详情 */
    private val _selectedMaterial = MutableStateFlow<MaterialDto?>(null)
    val selectedMaterial: StateFlow<MaterialDto?> = _selectedMaterial.asStateFlow()

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 筛选后的材料列表 */
    val filteredMaterials: StateFlow<List<MaterialDto>> = MutableStateFlow(emptyList())

    init {
        loadData()
    }

    /** 加载分类和材料数据 */
    private fun loadData() {
        _isLoading.value = true
        viewModelScope.launch {
            aiRepository.getMaterialCategories()
                .onSuccess { _categories.value = it }

            aiRepository.getAllMaterials()
                .onSuccess { allMaterials ->
                    _materials.value = allMaterials
                    updateFilteredMaterials()
                }
            _isLoading.value = false
        }
    }

    /** 选择分类 */
    fun selectCategory(categoryId: Int?) {
        _selectedCategoryId.value = categoryId
        updateFilteredMaterials()
    }

    /** 更新搜索关键词 */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        updateFilteredMaterials()
    }

    /** 查看材料详情 */
    fun selectMaterial(material: MaterialDto) {
        _selectedMaterial.value = material
    }

    /** 关闭材料详情 */
    fun clearSelectedMaterial() {
        _selectedMaterial.value = null
    }

    /** 更新筛选后的材料列表 */
    private fun updateFilteredMaterials() {
        val query = _searchQuery.value.lowercase()
        val categoryId = _selectedCategoryId.value

        var result = _materials.value

        // 按分类筛选
        if (categoryId != null) {
            result = result.filter { it.categoryId == categoryId }
        }

        // 按关键词搜索
        if (query.isNotEmpty()) {
            result = result.filter {
                it.name.lowercase().contains(query) ||
                it.brand?.lowercase()?.contains(query) == true ||
                it.specification?.lowercase()?.contains(query) == true
            }
        }

        (filteredMaterials as MutableStateFlow).value = result
    }
}
