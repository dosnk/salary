package com.salary.manager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心API桥接ViewModel
 *
 * 为个人中心子页面提供API调用和数据管理
 */
@HiltViewModel
class ProfileApiViewModel @Inject constructor(
    val userApi: UserApi,
    val dictionaryApi: DictionaryApi,
    val messageApi: MessageApi
) : ViewModel() {

    // 用户列表
    private val _users = MutableStateFlow<List<UserDto>>(emptyList())
    val users: StateFlow<List<UserDto>> = _users.asStateFlow()

    // 字典数据
    private val _spaceTypes = MutableStateFlow<List<DictionaryItemDto>>(emptyList())
    val spaceTypes: StateFlow<List<DictionaryItemDto>> = _spaceTypes.asStateFlow()

    private val _constructionPlans = MutableStateFlow<List<DictionaryItemDto>>(emptyList())
    val constructionPlans: StateFlow<List<DictionaryItemDto>> = _constructionPlans.asStateFlow()

    private val _wageDistributionTypes = MutableStateFlow<List<DictionaryItemDto>>(emptyList())
    val wageDistributionTypes: StateFlow<List<DictionaryItemDto>> = _wageDistributionTypes.asStateFlow()

    // 消息数据
    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /** 加载用户列表 */
    fun loadUsers() {
        viewModelScope.launch {
            try {
                val response = userApi.getUsers()
                if (response.code == 200) {
                    val data = response.data ?: return@launch
                    _users.value = data.list
                }
            } catch (_: Exception) { }
        }
    }

    /** 加载字典数据 */
    fun loadDictionaries() {
        viewModelScope.launch {
            try {
                val spaceTypesResp = dictionaryApi.getSpaceTypes()
                if (spaceTypesResp.code == 200) {
                    val data = spaceTypesResp.data ?: return@launch
                    _spaceTypes.value = data
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                val plansResp = dictionaryApi.getConstructionPlans()
                if (plansResp.code == 200) {
                    val data = plansResp.data ?: return@launch
                    _constructionPlans.value = data
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                val wageResp = dictionaryApi.getWageDistributionTypes()
                if (wageResp.code == 200) {
                    val data = wageResp.data ?: return@launch
                    _wageDistributionTypes.value = data
                }
            } catch (_: Exception) { }
        }
    }

    /** 加载消息 */
    fun loadMessages() {
        viewModelScope.launch {
            try {
                val response = messageApi.getMessages()
                if (response.code == 200) {
                    val data = response.data
                    if (data != null) {
                        _messages.value = data.list
                    }
                }
                val countResp = messageApi.getUnreadCount()
                if (countResp.code == 200) {
                    val data = countResp.data
                    if (data != null) {
                        _unreadCount.value = data.count
                    }
                }
            } catch (_: Exception) { }
        }
    }

    /** 修改密码 */
    fun changePassword(oldPassword: String, newPassword: String, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = userApi.changePassword(ChangePasswordRequest(oldPassword, newPassword))
                if (response.code == 200) callback(null)
                else callback(response.msg)
            } catch (e: Exception) {
                callback(e.message ?: "修改失败")
            }
        }
    }

    /** 创建用户 */
    fun createUser(request: CreateUserRequest, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = userApi.createUser(request)
                if (response.code == 200) { loadUsers(); callback(null) }
                else callback(response.msg)
            } catch (e: Exception) {
                callback(e.message ?: "创建失败")
            }
        }
    }

    /** 重置密码 */
    fun resetPassword(userId: Int, newPassword: String, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = userApi.resetPassword(userId, ResetPasswordRequest(newPassword))
                if (response.code == 200) callback(null)
                else callback(response.msg)
            } catch (e: Exception) {
                callback(e.message ?: "重置失败")
            }
        }
    }

    /** 删除用户 */
    fun deleteUser(userId: Int, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = userApi.deleteUser(userId)
                if (response.code == 200) { loadUsers(); callback(null) }
                else callback(response.msg)
            } catch (e: Exception) {
                callback(NetworkErrorHandler.translate(e, "删除失败"))
            }
        }
    }

    /** 添加空间类型 */
    fun addSpaceType(name: String, description: String?, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = dictionaryApi.createSpaceType(CreateDictionaryRequest(name, description))
                if (response.code == 200) { loadDictionaries(); callback(null) }
                else callback(response.msg)
            } catch (e: Exception) { callback(NetworkErrorHandler.translate(e, "操作失败")) }
        }
    }

    /** 删除空间类型 */
    fun deleteSpaceType(id: Int, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = dictionaryApi.deleteSpaceType(id)
                if (response.code == 200) { loadDictionaries(); callback(null) }
                else callback(response.msg)
            } catch (e: Exception) { callback(NetworkErrorHandler.translate(e, "操作失败")) }
        }
    }

    /** 添加施工方案 */
    fun addConstructionPlan(name: String, description: String?, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = dictionaryApi.createConstructionPlan(CreateDictionaryRequest(name, description))
                if (response.code == 200) { loadDictionaries(); callback(null) }
                else callback(response.msg)
            } catch (e: Exception) { callback(NetworkErrorHandler.translate(e, "操作失败")) }
        }
    }

    /** 删除施工方案 */
    fun deleteConstructionPlan(id: Int, callback: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = dictionaryApi.deleteConstructionPlan(id)
                if (response.code == 200) { loadDictionaries(); callback(null) }
                else callback(response.msg)
            } catch (e: Exception) { callback(NetworkErrorHandler.translate(e, "操作失败")) }
        }
    }

    /** 标记消息已读 */
    fun markMessageRead(id: Int) {
        viewModelScope.launch {
            try { messageApi.markAsRead(id); loadMessages() } catch (_: Exception) { }
        }
    }

    /** 全部标记已读 */
    fun markAllMessagesRead() {
        viewModelScope.launch {
            try { messageApi.markAllAsRead(); loadMessages() } catch (_: Exception) { }
        }
    }

    /** 删除消息 */
    fun deleteMessage(id: Int) {
        viewModelScope.launch {
            try { messageApi.deleteMessage(id); loadMessages() } catch (_: Exception) { }
        }
    }
}
