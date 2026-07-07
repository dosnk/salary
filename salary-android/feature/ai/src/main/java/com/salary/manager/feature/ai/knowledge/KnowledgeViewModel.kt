package com.salary.manager.feature.ai.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.CreateMaterialRequest
import com.salary.core.network.api.KnowledgeDetailResponse
import com.salary.core.network.api.KnowledgeItemDto
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import com.salary.core.network.api.UpdateMaterialRequest
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
 * 管理两个Tab的数据：
 * 1. 材料库 - 材料分类和材料列表（所有角色可查看，admin可增删改）
 * 2. 知识文档 - 知识文档的增删查（仅admin可用）
 */
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val userStorage: UserStorage
) : ViewModel() {

    /** Tab类型：0=材料库，1=知识文档 */
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // ========== 材料库相关状态 ==========

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

    /** 筛选后的材料列表 */
    private val _filteredMaterials = MutableStateFlow<List<MaterialDto>>(emptyList())
    val filteredMaterials: StateFlow<List<MaterialDto>> = _filteredMaterials.asStateFlow()

    /** 是否显示材料录入弹窗 */
    private val _showMaterialCreateDialog = MutableStateFlow(false)
    val showMaterialCreateDialog: StateFlow<Boolean> = _showMaterialCreateDialog.asStateFlow()

    /** 正在编辑的材料（null表示新建） */
    private val _editingMaterial = MutableStateFlow<MaterialDto?>(null)
    val editingMaterial: StateFlow<MaterialDto?> = _editingMaterial.asStateFlow()

    /** 待删除的材料ID（用于确认弹窗） */
    private val _pendingDeleteMaterialId = MutableStateFlow<Int?>(null)
    val pendingDeleteMaterialId: StateFlow<Int?> = _pendingDeleteMaterialId.asStateFlow()

    // ========== 材料录入表单状态 ==========

    private val _formCategoryId = MutableStateFlow<Int?>(null)
    val formCategoryId: StateFlow<Int?> = _formCategoryId.asStateFlow()

    private val _formName = MutableStateFlow("")
    val formName: StateFlow<String> = _formName.asStateFlow()

    private val _formBrand = MutableStateFlow("")
    val formBrand: StateFlow<String> = _formBrand.asStateFlow()

    private val _formSpec = MutableStateFlow("")
    val formSpec: StateFlow<String> = _formSpec.asStateFlow()

    private val _formUnit = MutableStateFlow("张")
    val formUnit: StateFlow<String> = _formUnit.asStateFlow()

    private val _formPrice = MutableStateFlow("")
    val formPrice: StateFlow<String> = _formPrice.asStateFlow()

    private val _formWidthCm = MutableStateFlow("")
    val formWidthCm: StateFlow<String> = _formWidthCm.asStateFlow()

    private val _formLengthCm = MutableStateFlow("")
    val formLengthCm: StateFlow<String> = _formLengthCm.asStateFlow()

    private val _formThicknessCm = MutableStateFlow("")
    val formThicknessCm: StateFlow<String> = _formThicknessCm.asStateFlow()

    private val _formCoverageArea = MutableStateFlow("")
    val formCoverageArea: StateFlow<String> = _formCoverageArea.asStateFlow()

    private val _formKeelSpacingCm = MutableStateFlow("")
    val formKeelSpacingCm: StateFlow<String> = _formKeelSpacingCm.asStateFlow()

    // ========== 知识文档相关状态 ==========

    /** 知识文档列表 */
    private val _knowledgeList = MutableStateFlow<List<KnowledgeItemDto>>(emptyList())
    val knowledgeList: StateFlow<List<KnowledgeItemDto>> = _knowledgeList.asStateFlow()

    /** 知识文档搜索关键词 */
    private val _knowledgeSearchQuery = MutableStateFlow("")
    val knowledgeSearchQuery: StateFlow<String> = _knowledgeSearchQuery.asStateFlow()

    /** 筛选后的知识文档列表 */
    private val _filteredKnowledgeList = MutableStateFlow<List<KnowledgeItemDto>>(emptyList())
    val filteredKnowledgeList: StateFlow<List<KnowledgeItemDto>> = _filteredKnowledgeList.asStateFlow()

    /** 选中的知识文档详情 */
    private val _selectedKnowledge = MutableStateFlow<KnowledgeDetailResponse?>(null)
    val selectedKnowledge: StateFlow<KnowledgeDetailResponse?> = _selectedKnowledge.asStateFlow()

    /** 是否显示录入弹窗 */
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    /** 录入弹窗 - 标题 */
    private val _inputTitle = MutableStateFlow("")
    val inputTitle: StateFlow<String> = _inputTitle.asStateFlow()

    /** 录入弹窗 - 内容 */
    private val _inputContent = MutableStateFlow("")
    val inputContent: StateFlow<String> = _inputContent.asStateFlow()

    /** 是否正在提交录入 */
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    /** 待删除的知识文档标题（用于确认弹窗） */
    private val _pendingDeleteTitle = MutableStateFlow<String?>(null)
    val pendingDeleteTitle: StateFlow<String?> = _pendingDeleteTitle.asStateFlow()

    // ========== 公共状态 ==========

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误提示 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 成功提示 */
    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    /** 当前用户角色 */
    val roleFlow: StateFlow<String> = userStorage.roleFlow

    init {
        loadMaterials()
    }

    /** 切换Tab */
    fun selectTab(tab: Int) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
        if (tab == 1 && _knowledgeList.value.isEmpty()) {
            loadKnowledgeList()
        }
    }

    // ========== 材料库方法 ==========

    /** 加载分类和材料数据 */
    private fun loadMaterials() {
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

        _filteredMaterials.value = result
    }

    /** 打开材料录入弹窗（新建模式） */
    fun openMaterialCreateDialog() {
        _editingMaterial.value = null
        _formCategoryId.value = _categories.value.firstOrNull()?.id
        _formName.value = ""
        _formBrand.value = ""
        _formSpec.value = ""
        _formUnit.value = "张"
        _formPrice.value = ""
        _formWidthCm.value = ""
        _formLengthCm.value = ""
        _formThicknessCm.value = ""
        _formCoverageArea.value = ""
        _formKeelSpacingCm.value = ""
        _showMaterialCreateDialog.value = true
    }

    /** 打开材料编辑弹窗（编辑模式） */
    fun openMaterialEditDialog(material: MaterialDto) {
        _editingMaterial.value = material
        _formCategoryId.value = material.categoryId
        _formName.value = material.name
        _formBrand.value = material.brand ?: ""
        _formSpec.value = material.specification ?: ""
        _formUnit.value = material.unit
        _formPrice.value = material.unitPrice.toString()
        _formWidthCm.value = material.widthCm?.toString() ?: ""
        _formLengthCm.value = material.lengthCm?.toString() ?: ""
        _formThicknessCm.value = material.thicknessCm?.toString() ?: ""
        _formCoverageArea.value = material.coverageArea?.toString() ?: ""
        _formKeelSpacingCm.value = material.keelSpacingCm?.toString() ?: ""
        _showMaterialCreateDialog.value = true
    }

    /** 关闭材料录入/编辑弹窗 */
    fun closeMaterialDialog() {
        _showMaterialCreateDialog.value = false
        _editingMaterial.value = null
    }

    /** 表单字段更新方法 */
    fun updateFormCategoryId(id: Int) { _formCategoryId.value = id }
    fun updateFormName(v: String) { _formName.value = v }
    fun updateFormBrand(v: String) { _formBrand.value = v }
    fun updateFormSpec(v: String) { _formSpec.value = v }
    fun updateFormUnit(v: String) { _formUnit.value = v }
    fun updateFormPrice(v: String) { _formPrice.value = v }
    fun updateFormWidthCm(v: String) { _formWidthCm.value = v }
    fun updateFormLengthCm(v: String) { _formLengthCm.value = v }
    fun updateFormThicknessCm(v: String) { _formThicknessCm.value = v }
    fun updateFormCoverageArea(v: String) { _formCoverageArea.value = v }
    fun updateFormKeelSpacingCm(v: String) { _formKeelSpacingCm.value = v }

    /** 提交材料创建/更新 */
    fun submitMaterial() {
        val categoryId = _formCategoryId.value ?: run {
            _error.value = "请选择材料分类"
            return
        }
        val name = _formName.value.trim()
        if (name.isEmpty()) {
            _error.value = "请输入材料名称"
            return
        }
        val price = _formPrice.value.trim().toDoubleOrNull()
        if (price == null || price <= 0) {
            _error.value = "请输入有效的单价"
            return
        }

        _isSubmitting.value = true
        _error.value = null
        viewModelScope.launch {
            val editing = _editingMaterial.value
            if (editing != null) {
                // 更新模式
                val request = UpdateMaterialRequest(
                    name = name,
                    brand = _formBrand.value.trim().ifEmpty { null },
                    specification = _formSpec.value.trim().ifEmpty { null },
                    unit = _formUnit.value,
                    unitPrice = price,
                    widthCm = _formWidthCm.value.trim().toDoubleOrNull(),
                    lengthCm = _formLengthCm.value.trim().toDoubleOrNull(),
                    thicknessCm = _formThicknessCm.value.trim().toDoubleOrNull(),
                    coverageArea = _formCoverageArea.value.trim().toDoubleOrNull(),
                    keelSpacingCm = _formKeelSpacingCm.value.trim().toDoubleOrNull()
                )
                aiRepository.updateMaterial(editing.id, request)
                    .onSuccess {
                        _showMaterialCreateDialog.value = false
                        _editingMaterial.value = null
                        _success.value = "材料更新成功"
                        // 重新加载材料列表
                        reloadMaterials()
                    }
                    .onFailure { e ->
                        _error.value = e.message ?: "更新材料失败"
                    }
            } else {
                // 创建模式
                val request = CreateMaterialRequest(
                    categoryId = categoryId,
                    name = name,
                    brand = _formBrand.value.trim().ifEmpty { null },
                    specification = _formSpec.value.trim().ifEmpty { null },
                    unit = _formUnit.value,
                    unitPrice = price,
                    widthCm = _formWidthCm.value.trim().toDoubleOrNull(),
                    lengthCm = _formLengthCm.value.trim().toDoubleOrNull(),
                    thicknessCm = _formThicknessCm.value.trim().toDoubleOrNull(),
                    coverageArea = _formCoverageArea.value.trim().toDoubleOrNull(),
                    keelSpacingCm = _formKeelSpacingCm.value.trim().toDoubleOrNull()
                )
                aiRepository.createMaterial(request)
                    .onSuccess {
                        _showMaterialCreateDialog.value = false
                        _success.value = "材料添加成功"
                        // 重新加载材料列表
                        reloadMaterials()
                    }
                    .onFailure { e ->
                        _error.value = e.message ?: "添加材料失败"
                    }
            }
            _isSubmitting.value = false
        }
    }

    /** 请求删除材料（弹出确认弹窗） */
    fun requestDeleteMaterial(id: Int) {
        _pendingDeleteMaterialId.value = id
    }

    /** 取消删除材料 */
    fun cancelDeleteMaterial() {
        _pendingDeleteMaterialId.value = null
    }

    /** 确认删除材料 */
    fun confirmDeleteMaterial() {
        val id = _pendingDeleteMaterialId.value ?: return
        _isSubmitting.value = true
        _error.value = null
        viewModelScope.launch {
            aiRepository.deleteMaterial(id)
                .onSuccess {
                    _pendingDeleteMaterialId.value = null
                    _selectedMaterial.value = null
                    _success.value = "材料删除成功"
                    // 重新加载材料列表
                    reloadMaterials()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "删除材料失败"
                }
            _isSubmitting.value = false
        }
    }

    /** 重新加载材料列表（不显示加载动画） */
    private fun reloadMaterials() {
        viewModelScope.launch {
            aiRepository.getAllMaterials()
                .onSuccess { allMaterials ->
                    _materials.value = allMaterials
                    updateFilteredMaterials()
                }
        }
    }

    // ========== 知识文档方法 ==========

    /** 加载知识库文档列表 */
    fun loadKnowledgeList() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            aiRepository.listKnowledge(page = 1, pageSize = 50)
                .onSuccess { response ->
                    _knowledgeList.value = response.items
                    updateFilteredKnowledgeList()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "加载知识库列表失败"
                }
            _isLoading.value = false
        }
    }

    /** 更新知识文档搜索关键词 */
    fun updateKnowledgeSearchQuery(query: String) {
        _knowledgeSearchQuery.value = query
        updateFilteredKnowledgeList()
    }

    /** 更新筛选后的知识文档列表（前端过滤，按标题搜索） */
    private fun updateFilteredKnowledgeList() {
        val query = _knowledgeSearchQuery.value.trim().lowercase()
        if (query.isEmpty()) {
            _filteredKnowledgeList.value = _knowledgeList.value
        } else {
            _filteredKnowledgeList.value = _knowledgeList.value.filter {
                it.title.lowercase().contains(query)
            }
        }
    }

    /** 查看知识文档详情 */
    fun viewKnowledgeDetail(title: String) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            aiRepository.getKnowledgeDetail(title)
                .onSuccess { detail ->
                    _selectedKnowledge.value = detail
                }
                .onFailure { e ->
                    _error.value = e.message ?: "加载知识文档详情失败"
                }
            _isLoading.value = false
        }
    }

    /** 关闭知识文档详情 */
    fun clearSelectedKnowledge() {
        _selectedKnowledge.value = null
    }

    /** 打开录入弹窗 */
    fun openCreateDialog() {
        _inputTitle.value = ""
        _inputContent.value = ""
        _showCreateDialog.value = true
    }

    /** 关闭录入弹窗 */
    fun closeCreateDialog() {
        _showCreateDialog.value = false
    }

    /** 更新录入标题 */
    fun updateInputTitle(text: String) {
        _inputTitle.value = text
    }

    /** 更新录入内容 */
    fun updateInputContent(text: String) {
        _inputContent.value = text
    }

    /** 提交录入知识文档 */
    fun submitCreate() {
        val title = _inputTitle.value.trim()
        val content = _inputContent.value.trim()

        // 客户端基础校验（与后端Joi规则一致）
        if (title.isEmpty()) {
            _error.value = "请输入文档标题"
            return
        }
        if (title.length > 200) {
            _error.value = "标题不能超过200个字符"
            return
        }
        if (content.length < 10) {
            _error.value = "内容至少需要10个字符"
            return
        }
        if (content.length > 50000) {
            _error.value = "内容不能超过50000个字符"
            return
        }

        _isSubmitting.value = true
        _error.value = null
        viewModelScope.launch {
            aiRepository.createKnowledge(title, content)
                .onSuccess {
                    _showCreateDialog.value = false
                    _success.value = "知识文档添加成功"
                    // 刷新列表
                    loadKnowledgeList()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "添加知识文档失败"
                }
            _isSubmitting.value = false
        }
    }

    /** 请求删除知识文档（弹出确认弹窗） */
    fun requestDelete(title: String) {
        _pendingDeleteTitle.value = title
    }

    /** 取消删除 */
    fun cancelDelete() {
        _pendingDeleteTitle.value = null
    }

    /** 确认删除知识文档 */
    fun confirmDelete() {
        val title = _pendingDeleteTitle.value ?: return
        _isSubmitting.value = true
        _error.value = null
        viewModelScope.launch {
            aiRepository.deleteKnowledge(title)
                .onSuccess {
                    _pendingDeleteTitle.value = null
                    _selectedKnowledge.value = null
                    _success.value = "知识文档删除成功"
                    // 刷新列表
                    loadKnowledgeList()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "删除知识文档失败"
                }
            _isSubmitting.value = false
        }
    }

    /** 清除错误提示 */
    fun clearError() {
        _error.value = null
    }

    /** 清除成功提示 */
    fun clearSuccess() {
        _success.value = null
    }
}
