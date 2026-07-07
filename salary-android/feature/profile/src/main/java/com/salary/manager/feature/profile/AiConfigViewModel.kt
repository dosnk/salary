package com.salary.manager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.AiApi
import com.salary.core.network.api.AiConfigResponse
import com.salary.core.network.api.AiConfigUpdateRequest
import com.salary.core.network.api.AiProviderConfigDto
import com.salary.core.network.api.AiProviderConfigUpdate
import com.salary.core.network.api.AiTestRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI配置ViewModel
 *
 * 管理AI大模型配置的加载和更新
 */
@HiltViewModel
class AiConfigViewModel @Inject constructor(
    private val aiApi: AiApi
) : ViewModel() {

    private val _config = MutableStateFlow<AiConfigResponse?>(null)
    val config: StateFlow<AiConfigResponse?> = _config.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // 各提供商的编辑状态
    private val _editedProviders = MutableStateFlow<Map<String, EditedProvider>>(emptyMap())
    val editedProviders: StateFlow<Map<String, EditedProvider>> = _editedProviders.asStateFlow()

    // 当前选中的默认提供商
    private val _selectedProvider = MutableStateFlow("")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    /** 加载AI配置 */
    fun loadConfig() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = aiApi.getAiConfig()
                if (response.code == 200) {
                    _config.value = response.data
                    _selectedProvider.value = response.data?.defaultProvider ?: ""
                    // 初始化编辑状态
                    val edited = mutableMapOf<String, EditedProvider>()
                    response.data?.providers?.forEach { (key, dto) ->
                        edited[key] = EditedProvider(
                            apiKey = dto.apiKey,
                            secretKey = dto.secretKey,
                            model = dto.model
                        )
                    }
                    _editedProviders.value = edited
                } else {
                    _error.value = NetworkErrorHandler.translateServerError(response.msg, "加载AI配置失败")
                }
            } catch (e: Exception) {
                _error.value = NetworkErrorHandler.translate(e, "加载AI配置失败")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 更新提供商的API Key */
    fun updateApiKey(provider: String, apiKey: String) {
        val current = _editedProviders.value.toMutableMap()
        current[provider] = current[provider]?.copy(apiKey = apiKey) ?: EditedProvider(apiKey = apiKey)
        _editedProviders.value = current
    }

    /** 更新提供商的Secret Key */
    fun updateSecretKey(provider: String, secretKey: String) {
        val current = _editedProviders.value.toMutableMap()
        current[provider] = current[provider]?.copy(secretKey = secretKey) ?: EditedProvider(secretKey = secretKey)
        _editedProviders.value = current
    }

    /** 更新提供商的模型 */
    fun updateModel(provider: String, model: String) {
        val current = _editedProviders.value.toMutableMap()
        current[provider] = current[provider]?.copy(model = model) ?: EditedProvider(model = model)
        _editedProviders.value = current
    }

    /** 选择默认提供商 */
    fun selectProvider(provider: String) {
        _selectedProvider.value = provider
    }

    /** 保存配置 */
    fun saveConfig() {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _saveSuccess.value = false
            try {
                val providerConfigs = mutableMapOf<String, AiProviderConfigUpdate>()
                _editedProviders.value.forEach { (key, edited) ->
                    val original = _config.value?.providers?.get(key)
                    providerConfigs[key] = AiProviderConfigUpdate(
                        apiKey = if (edited.apiKey != original?.apiKey) edited.apiKey else null,
                        secretKey = if (edited.secretKey != original?.secretKey) edited.secretKey else null,
                        model = if (edited.model != original?.model) edited.model else null,
                    )
                }

                val request = AiConfigUpdateRequest(
                    defaultProvider = _selectedProvider.value,
                    providerConfigs = providerConfigs
                )

                val response = aiApi.updateAiConfig(request)
                if (response.code == 200) {
                    _saveSuccess.value = true
                    // 重新加载配置
                    loadConfig()
                } else {
                    _error.value = NetworkErrorHandler.translateServerError(response.msg, "保存AI配置失败")
                }
            } catch (e: Exception) {
                _error.value = NetworkErrorHandler.translate(e, "保存AI配置失败")
            } finally {
                _isSaving.value = false
            }
        }
    }

    /** API连接测试 */
    fun testConnection() {
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null
            _error.value = null
            try {
                val response = aiApi.testConnection(AiTestRequest(_selectedProvider.value))
                if (response.code == 200) {
                    _testResult.value = response.data?.providerName + " 连接测试成功"
                } else {
                    _testResult.value = NetworkErrorHandler.translateServerError(response.msg, "连接测试失败")
                }
            } catch (e: Exception) {
                _testResult.value = NetworkErrorHandler.translate(e, "连接测试失败")
            } finally {
                _isTesting.value = false
            }
        }
    }

    /** 清除测试结果 */
    fun clearTestResult() {
        _testResult.value = null
    }

    /** 清除保存成功标记 */
    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}

/**
 * 编辑中的提供商配置
 */
data class EditedProvider(
    val apiKey: String = "",
    val secretKey: String = "",
    val model: String = ""
)
