package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salary.core.design.theme.AppColors

/**
 * 修改密码页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {},
    onSubmit: (oldPassword: String, newPassword: String, callback: (String?) -> Unit) -> Unit = { _, _, callback -> callback(null) }
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    // 弹窗状态：null=不显示，"success"=成功，"fail"=失败
    var dialogState by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("修改密码", fontSize = 20.sp, color = AppColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = AppColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 旧密码
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it; errorMessage = null },
                label = { Text("旧密码", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                        Icon(
                            if (oldPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = AppColors.TextTertiary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    cursorColor = AppColors.Green400
                ),
                singleLine = true
            )

            // 新密码
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; errorMessage = null },
                label = { Text("新密码(6-20位，含字母和数字)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = AppColors.TextTertiary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    cursorColor = AppColors.Green400
                ),
                singleLine = true
            )

            // 确认新密码
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text("确认新密码", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    cursorColor = AppColors.Green400
                ),
                singleLine = true
            )

            // 错误提示
            if (errorMessage != null) {
                Text(errorMessage!!, fontSize = 13.sp, color = AppColors.Error)
            }

            // 密码规则提示
            Text(
                "密码要求：6-20位，必须包含字母和数字",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )

            Spacer(modifier = Modifier.weight(1f))

            // 提交按钮
            Button(
                onClick = {
                    when {
                        oldPassword.isBlank() -> errorMessage = "请输入旧密码"
                        newPassword.length < 6 -> errorMessage = "新密码至少6位"
                        !newPassword.any { it.isLetter() } || !newPassword.any { it.isDigit() } ->
                            errorMessage = "新密码必须包含字母和数字"
                        newPassword != confirmPassword -> errorMessage = "两次密码不一致"
                        else -> {
                            isLoading = true
                            // 调用外部提交回调，由调用方注入真实API调用
                            onSubmit(oldPassword, newPassword) { error ->
                                isLoading = false
                                if (error != null) {
                                    // 失败：弹窗显示错误信息
                                    dialogState = "fail" to error
                                } else {
                                    // 成功：弹窗提示，点击确认后返回
                                    dialogState = "success" to "密码修改成功"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("确认修改", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 修改密码结果弹窗：成功/失败均通过弹窗提示，点击确认按钮关闭
        // 使用 Dialog + usePlatformDefaultWidth=false 实现宽度自适应屏幕
        dialogState?.let { (type, message) ->
            Dialog(
                onDismissRequest = {
                    // 仅失败时允许点击外部关闭；成功时必须点击确认按钮跳转
                    if (type == "fail") dialogState = null
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "提示",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = message,
                            fontSize = 15.sp,
                            color = AppColors.TextSecondary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 确认按钮右对齐
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    val isSuccess = type == "success"
                                    dialogState = null
                                    if (isSuccess) onSuccess()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                            ) {
                                Text("确认", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
