package com.salary.core.common.util

import com.salary.core.common.BuildConfig

/**
 * 应用统一日志工具
 *
 * 设计目的：
 * - 在 release 构建中自动静默所有业务日志，避免业务数据（工程金额、projectId、用户信息等）泄露到 logcat
 * - 仅在 debug 构建中输出日志，便于开发调试
 * - 替代直接调用 android.util.Log，避免业务层日志不受 BuildConfig.DEBUG 控制
 *
 * 使用方式：
 *   AppLog.d(TAG, "调试信息")
 *   AppLog.e(TAG, "错误信息", exception)
 *
 * 注意：
 * - 网络层日志由 OkHttp HttpLoggingInterceptor 控制（release 时 level=NONE）
 * - 业务层日志应统一通过本类输出
 */
object AppLog {

    /** 调试日志，仅 debug 构建输出 */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(tag, message)
        }
    }

    /** 详细日志，仅 debug 构建输出 */
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.v(tag, message)
        }
    }

    /** 信息日志，仅 debug 构建输出 */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(tag, message)
        }
    }

    /**
     * 警告日志，仅 debug 构建输出
     * 注意：警告级日志通常用于可恢复的异常场景，release 也不输出避免泄露业务细节
     * 如需 release 输出的关键警告，请直接使用 android.util.Log.w 并确保不含敏感信息
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                android.util.Log.w(tag, message, throwable)
            } else {
                android.util.Log.w(tag, message)
            }
        }
    }

    /**
     * 错误日志，仅 debug 构建输出
     * 注意：错误级日志通常含异常堆栈和业务上下文，release 不输出避免泄露业务细节
     * 如需 release 上报的错误，应接入 Crashlytics 等崩溃收集平台（当前未接入）
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        }
    }
}
