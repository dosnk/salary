package com.salary.manager.feature.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.manager.feature.ai.data.AiRepository
import com.salary.manager.feature.ai.data.SseEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    companion object {
        /**
         * 对话历史上限：保留最近 N 条消息（含欢迎消息）。
         *
         * 超过上限时移除最早的非欢迎消息，避免长对话导致：
         * - 内存占用持续增长
         * - LazyColumn 渲染卡顿
         * - 后端上下文 token 超限
         */
        private const val MAX_HISTORY_SIZE = 50
    }

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

    /** 错误提示（一次性事件，使用 SharedFlow 避免配置变化后重复消费） */
    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val error: SharedFlow<String> = _error.asSharedFlow()

    /**
     * 当前SSE流式请求的Job，用于取消/防泄漏
     *
     * - 发送新消息前取消旧Job，避免并行流式响应互相覆盖
     * - stopGeneration() 手动取消
     * - clearChat() 取消进行中的流
     * - ViewModel 销毁时自动取消（viewModelScope 自动级联取消）
     */
    private var streamJob: Job? = null

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

        // 取消上一个进行中的SSE流，避免新旧流并行导致内容互相覆盖（泄漏防护）
        streamJob?.cancel()
        streamJob = null

        // 添加用户消息
        val userMsg = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            role = MessageRole.USER,
            content = message,
            isStreaming = false
        )
        _messages.update { trimHistory(it + userMsg) }

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
        _messages.update { trimHistory(it + aiMsg) }
        _isLoading.value = true

        // 发起SSE流式请求
        streamJob = viewModelScope.launch {
            try {
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
                            _error.emit(event.message)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消或新消息覆盖：将占位AI消息标记为已停止
                _messages.update { msgs ->
                    msgs.map { msg ->
                        if (msg.id == aiMsgId && msg.isStreaming) {
                            msg.copy(
                                isStreaming = false,
                                content = if (msg.content.isEmpty()) "（已停止生成）" else msg.content
                            )
                        } else msg
                    }
                }
                _isLoading.value = false
                throw e // 重新抛出以正确传播取消语义
            } catch (e: Exception) {
                _messages.update { msgs ->
                    msgs.map { msg ->
                        if (msg.id == aiMsgId) {
                            msg.copy(
                                content = if (msg.content.isEmpty()) "抱歉，发生异常" else msg.content,
                                isStreaming = false,
                                isError = true
                            )
                        } else msg
                    }
                }
                _isLoading.value = false
                _error.emit(e.message ?: "未知错误")
            } finally {
                streamJob = null
            }
        }
    }

    /**
     * 手动停止当前AI流式生成
     *
     * 适用场景：用户点击"停止生成"按钮、切换页面、清空对话
     */
    fun stopGeneration() {
        streamJob?.cancel()
        streamJob = null
        _isLoading.value = false
        // 将仍在流式的AI消息标记为已停止
        _messages.update { msgs ->
            msgs.map { msg ->
                if (msg.isStreaming) {
                    msg.copy(
                        isStreaming = false,
                        content = if (msg.content.isEmpty()) "（已停止生成）" else msg.content
                    )
                } else msg
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
        // 取消进行中的SSE流，避免新会话收到旧流的残余数据
        stopGeneration()
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

    /**
     * 裁剪对话历史，保留最近 [MAX_HISTORY_SIZE] 条消息。
     *
     * 保留策略：
     * - 欢迎消息（id="welcome"）始终保留
     * - 优先移除最早的非欢迎消息
     * - 不截断正在进行中的流式消息（避免丢失正在生成的内容）
     */
    private fun trimHistory(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.size <= MAX_HISTORY_SIZE) return messages
        // 保留欢迎消息 + 最近 N-1 条，移除中间最早的
        val welcome = messages.firstOrNull { it.id == "welcome" }
        val others = messages.filter { it.id != "welcome" }
        val keepCount = if (welcome != null) MAX_HISTORY_SIZE - 1 else MAX_HISTORY_SIZE
        val trimmedOthers = others.takeLast(keepCount)
        return if (welcome != null) listOf(welcome) + trimmedOthers else trimmedOthers
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时取消进行中的SSE流，防止泄漏
        streamJob?.cancel()
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
