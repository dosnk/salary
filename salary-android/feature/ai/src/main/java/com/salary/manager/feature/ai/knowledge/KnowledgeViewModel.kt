package com.salary.manager.feature.ai.knowledge

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.CreateMaterialRequest
import com.salary.core.network.api.KnowledgeCategoryDto
import com.salary.core.network.api.KnowledgeDetailResponse
import com.salary.core.network.api.KnowledgeItemDto
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import com.salary.core.network.api.UpdateMaterialRequest
import com.salary.manager.feature.ai.data.AiRepository
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
 * 知识库ViewModel
 *
 * 管理两个Tab的数据：
 * 1. 材料库 - 材料分类和材料列表（所有角色可查看，admin可增删改）
 * 2. 知识文档 - 知识文档的增删查（仅admin可用）
 */
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val userStorage: UserStorage,
    private val serverConfig: ServerConfig
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

    /** 知识文档列表（服务端筛选后） */
    private val _knowledgeList = MutableStateFlow<List<KnowledgeItemDto>>(emptyList())
    val knowledgeList: StateFlow<List<KnowledgeItemDto>> = _knowledgeList.asStateFlow()

    /** 知识文档搜索关键词（标题/内容，服务端筛选） */
    private val _knowledgeSearchQuery = MutableStateFlow("")
    val knowledgeSearchQuery: StateFlow<String> = _knowledgeSearchQuery.asStateFlow()

    /** 知识分类列表（含文档数） */
    private val _knowledgeCategories = MutableStateFlow<List<KnowledgeCategoryDto>>(emptyList())
    val knowledgeCategories: StateFlow<List<KnowledgeCategoryDto>> = _knowledgeCategories.asStateFlow()

    /** 当前选中的知识分类（null=全部） */
    private val _selectedKnowledgeCategory = MutableStateFlow<String?>(null)
    val selectedKnowledgeCategory: StateFlow<String?> = _selectedKnowledgeCategory.asStateFlow()

    /** 选中的知识文档详情 */
    private val _selectedKnowledge = MutableStateFlow<KnowledgeDetailResponse?>(null)
    val selectedKnowledge: StateFlow<KnowledgeDetailResponse?> = _selectedKnowledge.asStateFlow()

    /** 详情页媒体文件完整URL（媒体类型文档预览用） */
    private val _detailMediaUrl = MutableStateFlow<String?>(null)
    val detailMediaUrl: StateFlow<String?> = _detailMediaUrl.asStateFlow()

    /** 是否显示录入方式菜单（手动录入/导入文档/导入媒体） */
    private val _showImportMenu = MutableStateFlow(false)
    val showImportMenu: StateFlow<Boolean> = _showImportMenu.asStateFlow()

    /** 是否显示录入/编辑弹窗 */
    private val _showKnowledgeFormDialog = MutableStateFlow(false)
    val showKnowledgeFormDialog: StateFlow<Boolean> = _showKnowledgeFormDialog.asStateFlow()

    /** 正在编辑的文档ID（null=新建模式） */
    private val _editingKnowledgeId = MutableStateFlow<Int?>(null)
    val editingKnowledgeId: StateFlow<Int?> = _editingKnowledgeId.asStateFlow()

    /** 录入/编辑弹窗 - 标题 */
    private val _inputTitle = MutableStateFlow("")
    val inputTitle: StateFlow<String> = _inputTitle.asStateFlow()

    /** 录入/编辑弹窗 - 内容 */
    private val _inputContent = MutableStateFlow("")
    val inputContent: StateFlow<String> = _inputContent.asStateFlow()

    /** 录入/编辑弹窗 - 分类 */
    private val _inputCategory = MutableStateFlow("")
    val inputCategory: StateFlow<String> = _inputCategory.asStateFlow()

    /** 是否正在提交录入/编辑 */
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    // ========== 导入（文件/媒体）相关状态 ==========

    /** 待导入的文件Uri（选好文件后暂存） */
    private val _pendingImportUri = MutableStateFlow<Uri?>(null)
    val pendingImportUri: StateFlow<Uri?> = _pendingImportUri.asStateFlow()

    /** 导入类型: file=文档(txt/md/pdf/docx), media=媒体(图片/视频/音频) */
    private val _importKind = MutableStateFlow("file")
    val importKind: StateFlow<String> = _importKind.asStateFlow()

    /** 待导入文件的原始文件名 */
    private val _importFileName = MutableStateFlow("")
    val importFileName: StateFlow<String> = _importFileName.asStateFlow()

    /** 是否显示导入确认弹窗 */
    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog: StateFlow<Boolean> = _showImportDialog.asStateFlow()

    /** 导入弹窗 - 标题 */
    private val _importTitle = MutableStateFlow("")
    val importTitle: StateFlow<String> = _importTitle.asStateFlow()

    /** 导入弹窗 - 分类 */
    private val _importCategory = MutableStateFlow("")
    val importCategory: StateFlow<String> = _importCategory.asStateFlow()

    /** 导入弹窗 - 描述（媒体用，补充检索关键词） */
    private val _importDescription = MutableStateFlow("")
    val importDescription: StateFlow<String> = _importDescription.asStateFlow()

    /** 上传进度（0-100，仅在导入中有效） */
    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    /** 是否正在导入（上传中） */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /** 待删除的知识文档（用于确认弹窗） */
    private val _pendingDeleteKnowledge = MutableStateFlow<KnowledgeItemDto?>(null)
    val pendingDeleteKnowledge: StateFlow<KnowledgeItemDto?> = _pendingDeleteKnowledge.asStateFlow()

    /** 正在重新识别的图片文档ID */
    private val _redescribingId = MutableStateFlow<Int?>(null)
    val redescribingId: StateFlow<Int?> = _redescribingId.asStateFlow()

    // ========== 公共状态 ==========

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误提示（一次性事件，使用 SharedFlow 避免配置变化后重复消费） */
    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val error: SharedFlow<String> = _error.asSharedFlow()

    /** 成功提示（一次性事件，使用 SharedFlow 避免配置变化后重复消费） */
    private val _success = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val success: SharedFlow<String> = _success.asSharedFlow()

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
            loadKnowledgeCategories()
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
            _error.tryEmit("请选择材料分类")
            return
        }
        val name = _formName.value.trim()
        if (name.isEmpty()) {
            _error.tryEmit("请输入材料名称")
            return
        }
        val price = _formPrice.value.trim().toDoubleOrNull()
        if (price == null || price <= 0) {
            _error.tryEmit("请输入有效的单价")
            return
        }

        _isSubmitting.value = true
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
                        _success.emit("材料更新成功")
                        // 重新加载材料列表
                        reloadMaterials()
                    }
                    .onFailure { e ->
                        _error.emit(e.message ?: "更新材料失败")
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
                        _success.emit("材料添加成功")
                        // 重新加载材料列表
                        reloadMaterials()
                    }
                    .onFailure { e ->
                        _error.emit(e.message ?: "添加材料失败")
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
        viewModelScope.launch {
            aiRepository.deleteMaterial(id)
                .onSuccess {
                    _pendingDeleteMaterialId.value = null
                    _selectedMaterial.value = null
                    _success.emit("材料删除成功")
                    // 重新加载材料列表
                    reloadMaterials()
                }
                .onFailure { e ->
                    _error.emit(e.message ?: "删除材料失败")
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

    /**
     * 加载知识库文档列表（服务端筛选：分类+关键词）
     * @param force 是否强制刷新
     */
    fun loadKnowledgeList(force: Boolean = false) {
        if (!force && _knowledgeList.value.isNotEmpty()) return
        _isLoading.value = true
        viewModelScope.launch {
            aiRepository.listKnowledge(
                page = 1,
                pageSize = 50,
                category = _selectedKnowledgeCategory.value,
                keyword = _knowledgeSearchQuery.value.takeIf { it.isNotBlank() }
            )
                .onSuccess { response ->
                    _knowledgeList.value = response.items
                }
                .onFailure { e ->
                    _error.emit(e.message ?: "加载知识库列表失败")
                }
            _isLoading.value = false
        }
    }

    /** 加载知识分类列表（含文档数） */
    private fun loadKnowledgeCategories() {
        viewModelScope.launch {
            aiRepository.getKnowledgeCategories()
                .onSuccess { response ->
                    _knowledgeCategories.value = response.categories
                }
        }
    }

    /** 选择知识分类（null=全部），并刷新列表 */
    fun selectKnowledgeCategory(category: String?) {
        if (_selectedKnowledgeCategory.value == category) return
        _selectedKnowledgeCategory.value = category
        loadKnowledgeList(force = true)
    }

    /** 更新知识文档搜索关键词（服务端筛选，输入即刷新） */
    fun updateKnowledgeSearchQuery(query: String) {
        _knowledgeSearchQuery.value = query
        loadKnowledgeList(force = true)
    }

    /** 查看知识文档详情 */
    fun viewKnowledgeDetail(id: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            aiRepository.getKnowledgeDetail(id)
                .onSuccess { detail ->
                    _selectedKnowledge.value = detail
                    // 计算媒体文件完整URL（供图片/音视频预览）
                    _detailMediaUrl.value = detail.mediaUrl?.let { serverConfig.buildFileUrl(it) }
                }
                .onFailure { e ->
                    _error.emit(e.message ?: "加载知识文档详情失败")
                }
            _isLoading.value = false
        }
    }

    /** 关闭知识文档详情 */
    fun clearSelectedKnowledge() {
        _selectedKnowledge.value = null
        _detailMediaUrl.value = null
    }

    /** 打开录入方式菜单（手动录入/导入文档/导入媒体） */
    fun openImportMenu() {
        _showImportMenu.value = true
    }

    /** 关闭录入方式菜单 */
    fun closeImportMenu() {
        _showImportMenu.value = false
    }

    /** 打开手动录入弹窗（新建模式） */
    fun openCreateDialog() {
        _showImportMenu.value = false
        _editingKnowledgeId.value = null
        _inputTitle.value = ""
        _inputContent.value = ""
        _inputCategory.value = _selectedKnowledgeCategory.value ?: ""
        _showKnowledgeFormDialog.value = true
    }

    /** 从详情打开编辑弹窗（编辑模式，预填文档内容） */
    fun openEditDialog() {
        val detail = _selectedKnowledge.value ?: return
        _showImportMenu.value = false
        _editingKnowledgeId.value = detail.id
        _inputTitle.value = detail.title
        _inputContent.value = detail.content
        _inputCategory.value = detail.category
        _selectedKnowledge.value = null
        _showKnowledgeFormDialog.value = true
    }

    /** 关闭录入/编辑弹窗 */
    fun closeKnowledgeFormDialog() {
        if (_isSubmitting.value) return
        _showKnowledgeFormDialog.value = false
        _editingKnowledgeId.value = null
    }

    /** 更新录入标题 */
    fun updateInputTitle(text: String) {
        _inputTitle.value = text
    }

    /** 更新录入内容 */
    fun updateInputContent(text: String) {
        _inputContent.value = text
    }

    /** 更新录入分类 */
    fun updateInputCategory(text: String) {
        _inputCategory.value = text
    }

    /** 提交录入/编辑知识文档（内容变更后端自动重新分块） */
    fun submitKnowledgeForm() {
        val title = _inputTitle.value.trim()
        val content = _inputContent.value.trim()
        val category = _inputCategory.value.trim().ifEmpty { null }

        // 客户端基础校验（与后端Joi规则一致）
        if (title.isEmpty()) {
            _error.tryEmit("请输入文档标题")
            return
        }
        if (title.length > 200) {
            _error.tryEmit("标题不能超过200个字符")
            return
        }
        if (content.length < 10) {
            _error.tryEmit("内容至少需要10个字符")
            return
        }
        if (content.length > 50000) {
            _error.tryEmit("内容不能超过50000个字符")
            return
        }

        _isSubmitting.value = true
        viewModelScope.launch {
            val editingId = _editingKnowledgeId.value
            if (editingId != null) {
                // 编辑模式
                aiRepository.updateKnowledge(editingId, title = title, content = content, category = category)
                    .onSuccess {
                        _showKnowledgeFormDialog.value = false
                        _editingKnowledgeId.value = null
                        _success.emit("知识文档更新成功")
                        loadKnowledgeList(force = true)
                        loadKnowledgeCategories()
                    }
                    .onFailure { e ->
                        _error.emit(e.message ?: "更新知识文档失败")
                    }
            } else {
                // 新建模式
                aiRepository.createKnowledge(title, content, category)
                    .onSuccess {
                        _showKnowledgeFormDialog.value = false
                        _success.emit("知识文档添加成功")
                        loadKnowledgeList(force = true)
                        loadKnowledgeCategories()
                    }
                    .onFailure { e ->
                        _error.emit(e.message ?: "添加知识文档失败")
                    }
            }
            _isSubmitting.value = false
        }
    }

    // ========== 导入（文件/媒体）方法 ==========

    /**
     * 文件选择完成回调（进入导入确认弹窗）
     * @param uri 选中的文件Uri
     * @param kind 导入类型: file/media
     * @param fileName 原始文件名（由Screen层查询）
     */
    fun onFileSelected(uri: Uri, kind: String, fileName: String) {
        _showImportMenu.value = false
        _pendingImportUri.value = uri
        _importKind.value = kind
        _importFileName.value = fileName
        // 标题默认用文件名去扩展名
        _importTitle.value = fileName.substringBeforeLast('.').ifEmpty { fileName }
        _importCategory.value = _selectedKnowledgeCategory.value ?: ""
        _importDescription.value = ""
        _uploadProgress.value = 0
        _showImportDialog.value = true
    }

    /** 关闭导入确认弹窗 */
    fun closeImportDialog() {
        if (_isImporting.value) return
        _showImportDialog.value = false
        _pendingImportUri.value = null
    }

    /** 更新导入标题 */
    fun updateImportTitle(text: String) {
        _importTitle.value = text
    }

    /** 更新导入分类 */
    fun updateImportCategory(text: String) {
        _importCategory.value = text
    }

    /** 更新导入描述 */
    fun updateImportDescription(text: String) {
        _importDescription.value = text
    }

    /** 确认导入（执行上传，带进度） */
    fun confirmImport() {
        val uri = _pendingImportUri.value ?: return
        val title = _importTitle.value.trim()
        if (_importKind.value == "media" && title.isEmpty()) {
            _error.tryEmit("请填写媒体文件的标题")
            return
        }

        _isImporting.value = true
        _uploadProgress.value = 0
        viewModelScope.launch {
            val onProgress: (Int) -> Unit = { percent ->
                _uploadProgress.value = percent
            }
            if (_importKind.value == "file") {
                // 文档导入（txt/md/pdf/docx，后端自动提取文本）
                aiRepository.importKnowledgeFile(uri, title, _importCategory.value.trim().ifEmpty { null }, onProgress)
                    .onSuccess {
                        _showImportDialog.value = false
                        _pendingImportUri.value = null
                        _success.emit("文档导入成功")
                        loadKnowledgeList(force = true)
                        loadKnowledgeCategories()
                    }
                    .onFailure { e ->
                        _error.emit(e.message ?: "文件导入失败")
                    }
            } else {
                // 媒体导入（图片自动识别/视频音频靠标题+描述检索）
                aiRepository.importKnowledgeMedia(
                    uri, title,
                    _importCategory.value.trim().ifEmpty { null },
                    _importDescription.value.trim(),
                    onProgress
                )
                    .onSuccess { response ->
                        _showImportDialog.value = false
                        _pendingImportUri.value = null
                        _success.emit(response.message.ifEmpty { "媒体导入成功" })
                        loadKnowledgeList(force = true)
                        loadKnowledgeCategories()
                    }
                    .onFailure { e ->
                        _error.emit(e.message ?: "媒体导入失败")
                    }
            }
            _isImporting.value = false
        }
    }

    // ========== 删除/重新识别方法 ==========

    /** 请求删除知识文档（弹出确认弹窗） */
    fun requestDelete(item: KnowledgeItemDto) {
        _pendingDeleteKnowledge.value = item
    }

    /** 取消删除 */
    fun cancelDelete() {
        _pendingDeleteKnowledge.value = null
    }

    /** 确认删除知识文档 */
    fun confirmDelete() {
        val item = _pendingDeleteKnowledge.value ?: return
        _isSubmitting.value = true
        viewModelScope.launch {
            aiRepository.deleteKnowledge(item.id)
                .onSuccess {
                    _pendingDeleteKnowledge.value = null
                    _selectedKnowledge.value = null
                    _success.emit("知识文档删除成功")
                    loadKnowledgeList(force = true)
                    loadKnowledgeCategories()
                }
                .onFailure { e ->
                    _error.emit(e.message ?: "删除知识文档失败")
                }
            _isSubmitting.value = false
        }
    }

    /** 图片重新识别（视觉模型配置好后补识别） */
    fun redescribeImage() {
        val detail = _selectedKnowledge.value ?: return
        if (detail.docType != "image") return
        _redescribingId.value = detail.id
        viewModelScope.launch {
            aiRepository.redescribeKnowledge(detail.id)
                .onSuccess {
                    _redescribingId.value = null
                    _success.emit("图片重新识别完成")
                    // 刷新详情与列表
                    viewKnowledgeDetail(detail.id)
                    loadKnowledgeList(force = true)
                }
                .onFailure { e ->
                    _redescribingId.value = null
                    _error.emit(e.message ?: "图片重新识别失败")
                }
        }
    }
}
