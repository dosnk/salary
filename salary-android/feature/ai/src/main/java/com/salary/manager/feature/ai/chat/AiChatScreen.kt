package com.salary.manager.feature.ai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.component.TopBarActionIcon
import com.salary.core.design.theme.AppColors
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * AI对话页面
 *
 * 功能:
 * - 对话气泡列表（用户/AI）
 * - SSE流式实时显示
 * - 输入栏 + 发送按钮
 * - 快捷提问建议
 * - 清空对话
 * - 失败重试
 *
 * @param onMessageClick 顶部导航栏消息图标点击回调（AI页面默认不显示消息图标，此参数预留）
 */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun AiChatScreen(
    onNavigateToLayout: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = {},
    viewModel: AiChatViewModel = hiltViewModel(),
    userNickname: String = "",
    onMessageClick: (() -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    // 注：error 已改为 SharedFlow，不再使用 collectAsStateWithLifecycle
    // 改用下方 LaunchedEffect + collect 收集一次性事件
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 收集错误消息（一次性事件，使用 SharedFlow collect 避免配置变化后重复消费）
    LaunchedEffect(Unit) {
        viewModel.error.collect { msg ->
            snackbarHostState.showSnackbar(message = msg)
        }
    }

    // 自动滚动到底部 — 分两个维度，避免流式高频更新导致滚动卡顿：
    // 1. 消息数量变化（新消息出现）：立即滚动
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    // 2. 流式内容更新（最后一条消息 content 变化）：防抖滚动
    //    SSE 每秒可能推送多次 content，直接监听会导致 animateScrollToItem 高频调用产生抖动
    //    使用 snapshotFlow + debounce(200ms) 合并高频更新，平滑滚动
    LaunchedEffect(Unit) {
        snapshotFlow { messages.lastOrNull()?.content }
            .distinctUntilChanged()
            .debounce(200)
            .collect {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 顶部导航栏 - 固定在顶部，不响应键盘弹出（避免被推到屏幕外）
        GreenTopNavBar(
            title = "AI助手",
            userNickname = userNickname.ifBlank { "未登录" },
            unreadCount = -1 // 不显示消息图标
        ) {
            // 排料计算入口
            TopBarActionIcon(
                icon = Icons.Default.ViewInAr,
                contentDescription = "排料计算",
                onClick = onNavigateToLayout
            )
            // 知识库入口
            TopBarActionIcon(
                icon = Icons.Default.MenuBook,
                contentDescription = "知识库",
                onClick = onNavigateToKnowledge
            )
            // 清空对话按钮
            TopBarActionIcon(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "清空对话",
                onClick = { viewModel.clearChat() }
            )
        }
            // 消息列表 - 响应键盘高度自动收缩（weight根据剩余空间分配）
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            onRetry = { viewModel.retryLastMessage() }
                        )
                    }

                    // 快捷提问（仅对话开始时显示）
                    if (messages.size <= 1) {
                        item {
                            QuickQuestionsSection(
                                questions = viewModel.quickQuestions,
                                onQuestionClick = { viewModel.sendMessage(it) }
                            )
                        }
                    }
                }
                // Snackbar 浮在消息列表底部（输入栏上方），不挤压消息列表布局
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 输入栏 - 仅此处应用 imePadding，随键盘上推，不影响顶部导航栏
            InputBar(
                text = inputText,
                onTextChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage() },
                isLoading = isLoading,
                // 流式生成中提供"停止生成"入口，点击调用 stopGeneration()
                onStop = { viewModel.stopGeneration() }
            )
        }
}

/**
 * 消息气泡
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI头像
        if (!isUser) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = AppColors.Green400
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = "AI",
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 气泡内容
        Column(
            modifier = Modifier.widthIn(max = screenWidth * 0.75f)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = if (isUser) AppColors.Green400 else Color.White,
                shadowElevation = if (isUser) 0.dp else 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = if (isUser) Color.White else AppColors.TextPrimary
                    )

                    // 流式加载指示器
                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = if (isUser) Color.White else AppColors.Green400
                        )
                    }
                }
            }

            // 错误重试
            if (message.isError) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            AppColors.Error.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "发送失败",
                        fontSize = 12.sp,
                        color = AppColors.Error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重试",
                            modifier = Modifier.size(16.dp),
                            tint = AppColors.Error
                        )
                    }
                }
            }
        }

        // 用户头像
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = AppColors.Green200
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "我",
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp),
                    tint = AppColors.Green700
                )
            }
        }
    }
}

/**
 * 快捷提问区域
 */
@Composable
private fun QuickQuestionsSection(
    questions: List<String>,
    onQuestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "你可以问我：",
            fontSize = 13.sp,
            color = AppColors.TextTertiary,
            modifier = Modifier.padding(start = 4.dp)
        )

        questions.forEach { question ->
            Surface(
                onClick = { onQuestionClick(question) },
                shape = RoundedCornerShape(16.dp),
                color = AppColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question,
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * 底部输入栏
 *
 * 发送按钮采用微信样式：仅在输入框获得焦点（用户点击输入框准备输入）时才显示，
 * 并以水平展开动画从右侧滑入；失去焦点时自动收起。
 * 按钮样式：绿色圆形背景 + 白色发送图标（微信风格）。
 *
 * 键盘适配：使用 windowInsetsPadding 应用 ime ∪ navigationBars 的 insets。
 * - 键盘未弹出：应用系统导航栏高度（手势条/虚拟按键），避免输入栏被遮挡
 * - 键盘弹出时：应用键盘高度（navigationBars 被 ime 覆盖，取并集最大值，避免叠加空白）
 * 修复：原 imePadding + navigationBarsPadding 串联使用，键盘弹出后两者叠加导致输入栏与键盘间出现大量空白
 */
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    onStop: () -> Unit = {}
) {
    Surface(
        shadowElevation = 2.dp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            // 使用 windowInsetsPadding 应用 ime 和 navigationBars 的并集
            // 避免 imePadding + navigationBarsPadding 串联时键盘弹出后产生叠加空白
            .windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f),
                placeholder = {
                    Text(
                        "输入消息...",
                        fontSize = 14.sp,
                        color = AppColors.TextPlaceholder
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    focusedContainerColor = AppColors.Background,
                    unfocusedContainerColor = AppColors.Background,
                    cursorColor = AppColors.Green400
                ),
                maxLines = 4,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = AppColors.TextPrimary
                )
            )

            // 发送按钮：输入第一个文字后在输入框右侧拉幕式优雅展开，清空后收起
            // 微信样式：绿色圆形背景 + 白色发送图标
            // 流式生成中（isLoading=true）：按钮变为"停止"，点击调用 onStop() 取消SSE流
            AnimatedVisibility(
                visible = text.isNotEmpty() || isLoading,
                enter = expandHorizontally(
                    expandFrom = Alignment.End,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.End,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentHeight()
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isLoading) {
                        // 流式生成中：显示"停止"按钮，点击取消当前SSE流
                        Surface(
                            onClick = onStop,
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.Error,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    "停止",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // 发送按钮：圆角长方形 + "发送"文字
                        val canSend = text.isNotBlank() && !isLoading
                        Surface(
                            onClick = { if (canSend) onSend() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (canSend) AppColors.Green400 else AppColors.Green100,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    "发送",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (canSend) Color.White else AppColors.TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
