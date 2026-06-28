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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.component.TopBarActionIcon
import com.salary.core.design.theme.AppColors

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onNavigateToLayout: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = {},
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 新消息时自动滚动到底部
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 顶部导航栏 - 绿色渐变自适应高度
        GreenTopNavBar(
            title = "AI助手",
            userNickname = "",
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
            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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

            // 输入栏
            InputBar(
                text = inputText,
                onTextChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage() },
                isLoading = isLoading
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
 */
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
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

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            Surface(
                onClick = { if (text.isNotBlank() && !isLoading) onSend() },
                shape = CircleShape,
                color = if (text.isNotBlank() && !isLoading) AppColors.Green400 else AppColors.Green100,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(20.dp),
                            tint = if (text.isNotBlank()) Color.White else AppColors.TextTertiary
                        )
                    }
                }
            }
        }
    }
}
