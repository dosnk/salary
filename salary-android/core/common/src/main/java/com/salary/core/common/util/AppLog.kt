package com.salary.core.common.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.salary.core.common.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * 应用统一日志工具
 *
 * 设计目的：
 * - 在 release 构建中自动静默 logcat 输出，避免业务数据（工程金额、projectId、用户信息等）泄露到 logcat
 * - 仅在 debug 构建中输出 logcat，便于开发调试
 * - 替代直接调用 android.util.Log，避免业务层日志不受 BuildConfig.DEBUG 控制
 * - **文件日志**：所有构建均追加写入应用私有目录 filesDir/logs/app.log（无需存储权限），
 *   便于在手机端直接导出排查线上问题（如"登录失败"等无法复现的异常）
 *
 * 使用方式：
 *   AppLog.d(TAG, "调试信息")
 *   AppLog.e(TAG, "错误信息", exception)
 *
 * 注意：
 * - 网络层日志由 OkHttp HttpLoggingInterceptor 控制（release 时 level=NONE）
 * - 业务层日志应统一通过本类输出
 * - 文件日志需先在 Application.onCreate 中调用 AppLog.init(context) 初始化
 */
object AppLog {

    /** 当前日志文件（filesDir/logs/app.log），由 init(context) 初始化 */
    private var logFile: File? = null

    /** 日志文件滚动大小上限：超过后重命名为 app.old.log 并新建，防止日志无限增长 */
    private const val MAX_LOG_SIZE = 2 * 1024 * 1024 // 2MB

    /**
     * 初始化文件日志（在 Application.onCreate 中调用）
     * 日志写入应用私有目录 filesDir/logs/app.log，无需存储权限
     * @param context 应用上下文
     */
    fun init(context: Context) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logFile = File(dir, "app.log")
        } catch (_: Exception) {
            // 初始化失败仅禁用文件日志，不影响业务
            logFile = null
        }
    }

    /**
     * 获取当前日志文件（用于"分享日志"功能）
     * @return 日志文件，未初始化或初始化失败时为 null
     */
    fun getLogFile(): File? = logFile

    /**
     * 清空日志文件（调试用）
     */
    fun clearLogs() {
        synchronized(this) {
            try {
                logFile?.delete()
            } catch (_: Exception) {
                // 删除失败忽略
            }
        }
    }

    /**
     * 分享日志文件
     *
     * 将日志文件（filesDir/logs/app.log）通过系统分享面板导出，
     * 便于用户把问题日志发给管理员/开发者排查（如"登录失败"等无法在开发环境复现的问题）。
     * 登录页与"我的"页共用此入口，未登录用户也能导出日志。
     *
     * @param context 上下文（用于 FileProvider 生成 content:// Uri）
     */
    fun shareLogFile(context: Context) {
        val file = getLogFile()
        if (file == null || !file.exists()) {
            Toast.makeText(context, "暂无日志文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // FileProvider 将内部私有文件暴露为 content:// Uri，authorities 与 Manifest 中一致（兼容 debug/release 包）
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Salary App 日志")
                putExtra(Intent.EXTRA_TEXT, "以下为 App 运行日志，请查看定位问题：")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享日志失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 调试日志，仅 debug 构建输出 logcat；文件日志始终写入 */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(tag, message)
        }
        writeToFile("D", tag, message, null)
    }

    /** 详细日志，仅 debug 构建输出 logcat；文件日志始终写入 */
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.v(tag, message)
        }
        writeToFile("V", tag, message, null)
    }

    /** 信息日志，仅 debug 构建输出 logcat；文件日志始终写入 */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(tag, message)
        }
        writeToFile("I", tag, message, null)
    }

    /**
     * 警告日志，仅 debug 构建输出 logcat；文件日志始终写入
     * 注意：警告级日志通常用于可恢复的异常场景，release 不输出避免泄露业务细节
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                android.util.Log.w(tag, message, throwable)
            } else {
                android.util.Log.w(tag, message)
            }
        }
        writeToFile("W", tag, message, throwable)
    }

    /**
     * 错误日志，仅 debug 构建输出 logcat；文件日志始终写入
     * 注意：错误级日志通常含异常堆栈和业务上下文，release 不输出避免泄露业务细节
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        }
        writeToFile("E", tag, message, throwable)
    }

    /**
     * 追加写入日志文件（线程安全；写入失败静默忽略，不影响业务）
     *
     * 格式：时间戳(毫秒) [级别] [TAG] 消息 + 可选堆栈
     * 滚动：超过 MAX_LOG_SIZE 时将 app.log 重命名为 app.old.log 后新建
     */
    private fun writeToFile(level: String, tag: String, message: String, throwable: Throwable?) {
        synchronized(this) {
            val file = logFile ?: return
            try {
                // 超过上限时滚动：app.log -> app.old.log（保留最近一份旧日志）
                if (file.exists() && file.length() > MAX_LOG_SIZE) {
                    val old = File(file.parentFile, "app.old.log")
                    if (old.exists()) {
                        old.delete()
                    }
                    file.renameTo(old)
                }

                val line = buildString {
                    append(DateFormatter.formatLogTimestamp(Date()))
                    append(" [").append(level).append("] [").append(tag).append("] ")
                    append(message)
                    if (throwable != null) {
                        append('\n')
                        append(android.util.Log.getStackTraceString(throwable))
                    }
                    append('\n')
                }

                FileOutputStream(file, true).use { fos ->
                    fos.write(line.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // 文件写入失败静默忽略，不影响业务
            }
        }
    }
}
