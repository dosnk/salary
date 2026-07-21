package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 关于页面
 * @param versionName 版本号，由调用方从BuildConfig传入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    versionName: String = ""
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontSize = 20.sp, color = AppColors.TextPrimary) },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // 应用图标
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppColors.Green400,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("三人行", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("三人行吊顶管理系统", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("版本 $versionName", fontSize = 14.sp, color = AppColors.TextTertiary)

            Spacer(modifier = Modifier.height(40.dp))

            // 信息卡片
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AboutRow("技术支持", "三人行科技")
                    HorizontalDivider(color = AppColors.SurfaceVariant)
                    AboutRow("技术栈", "Kotlin + Jetpack Compose")
                    HorizontalDivider(color = AppColors.SurfaceVariant)
                    AboutRow("后端", "Node.js + PostgreSQL")
                    HorizontalDivider(color = AppColors.SurfaceVariant)
                    AboutRow("AI引擎", "本地排料 + 国内大模型")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text("© 2026 三人行科技", fontSize = 12.sp, color = AppColors.TextTertiary)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 关于页面信息行 - 左标签右值，长内容单行省略号
 */
@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            maxLines = 1
        )
        Text(
            value,
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
