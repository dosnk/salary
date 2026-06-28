package com.salary.manager.feature.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.manager.feature.ai.data.AiRepository
import com.salary.manager.feature.ai.data.SseEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * AI对话ViewModel
 *
 * 管理对话消息列表和流式响应状态
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    /** 当前会话ID */
    private val sessionId = MutableStateFlow(UUID.randomUUID().toString())

    /** 对话消息列表 */
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** 是否正在等待AI响应 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 输入框文本 */
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /** 错误提示 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 快捷提问建议 */
    val quickQuestions = listOf(
        "帮我查看最近的工程",
        "这个月收入多少",
        "帮我算一下排料",
        "石膏板和铝扣板区别"
    )

    init {
        // 添加欢迎消息
        _messages.value = listOf(
            ChatMessage(
                id = "welcome",
                role = MessageRole.ASSISTANT,
                content = "你好！我是三人行吊顶管理系统的AI助手，可以帮你：\n\n" +
                        "📊 查询工程、统计、结算数据\n" +
                        "📐 排料计算，生成材料清单\n" +
                        "💡 回答吊顶施工相关问题\n\n" +
                        "请问有什么可以帮你的？",
                isStreaming = false
            )
        )
    }

    /** 更新输入框文本 */
    fun updateInputText(text: String) {
        _inputText.value = text
    }

    /** 发送消息 */
    fun sendMessage(text: String? = null) {
        val message = (text ?: _inputText.value).trim()
        if (message.isEmpty() || _isLoading.value) return

        // 清除错误
        _error.value = null

        // 添加用户消息
        val userMsg = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            role = MessageRole.USER,
            content = message,
            isStreaming = false
        )
        _messages.update { it + userMsg }

        // 清空输入框
        _inputText.value = ""

        // 添加AI占位消息（流式填充）
        val aiMsgId = "ai_${System.currentTimeMillis()}"
        val aiMsg = ChatMessage(
            id = aiMsgId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _messages.update { it + aiMsg }
        _isLoading.value = true

        // 发起SSE流式请求
        viewModelScope.launch {
            aiRepository.sendMessageStream(message, sessionId.value).collect { event ->
                when (event) {
                    is SseEvent.Content -> {
                        // 追加流式文本
                        _messages.update { msgs ->
                            msgs.map { msg ->
                                if (msg.id == aiMsgId) {
                                    msg.copy(content = msg.content + event.text)
                                } else msg
                            }
                        }
                    }
                    is SseEvent.Done -> {
                        // 流式结束
                        _messages.update { msgs ->
                            msgs.map { msg ->
                                if (msg.id == aiMsgId) {
                                    msg.copy(isStreaming = false)
                                } else msg
                            }
                        }
                        _isLoading.value = false
                    }
                    is SseEvent.Error -> {
                        // 错误处理
                        _messages.update { msgs ->
                            msgs.map { msg ->
                                if (msg.id == aiMsgId) {
                                    msg.copy(
                                        content = if (msg.content.isEmpty()) "抱歉，${event.message}" else msg.content,
                                        isStreaming = false,
                                        isError = true
                                    )
                                } else msg
                            }
                        }
                        _isLoading.value = false
                        _error.value = event.message
                    }
                }
            }
        }
    }

    /** 重新发送最后一条失败的消息 */
    fun retryLastMessage() {
        val lastUserMsg = _messages.value.lastOrNull { it.role == MessageRole.USER }
        if (lastUserMsg != null) {
            // 移除最后一条AI消息（失败的）
            _messages.update { msgs ->
                val lastAi = msgs.lastOrNull { it.role == MessageRole.ASSISTANT && it.isError }
                if (lastAi != null) msgs - lastAi else msgs
            }
            sendMessage(lastUserMsg.content)
        }
    }

    /** 清空对话（开始新会话） */
    fun clearChat() {
        sessionId.value = UUID.randomUUID().toString()
        _messages.value = listOf(
            ChatMessage(
                id = "welcome",
                role = MessageRole.ASSISTANT,
                content = "对话已清空，有什么可以帮你的？",
                isStreaming = false
            )
        )
    }
}

/**
 * 对话消息数据类
 */
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

/**
 * 消息角色
 */
enum class MessageRole {
    USER,
    ASSISTANT
}
