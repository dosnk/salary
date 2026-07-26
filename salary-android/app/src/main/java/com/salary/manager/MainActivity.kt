package com.salary.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.salary.manager.navigation.AppNavHost
import com.salary.manager.ui.theme.SalaryManagerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity，唯一入口
 *
 * 全局启用文本长按选择复制：
 * - 通过 SelectionContainer 包裹整个 AppNavHost，使所有页面中的 Text 组件支持长按选择
 * - SelectionContainer 不会拦截点击/滑动等手势，仅在长按文本时触发系统选择菜单
 * - TextField 内部有自己的选择逻辑，不受影响
 * - Dialog/ModalBottomSheet 在独立 window 渲染，需在内部单独包裹 SelectionContainer
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalaryManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 全局启用文本选择：长按任意 Text 可触发系统选择菜单（复制/全选）
                    SelectionContainer {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
