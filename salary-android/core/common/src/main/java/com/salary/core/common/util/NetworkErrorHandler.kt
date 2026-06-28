package com.salary.core.common.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 网络错误中文翻译工具
 * 将系统英文网络异常消息和后端错误消息转换为用户友好的中文提示
 */
object NetworkErrorHandler {

    /**
     * 将异常转换为中文错误消息
     * @param e 捕获的异常
     * @param fallback 默认兜底消息，默认为"网络错误"
     * @return 中文错误消息
     */
    fun translate(e: Throwable, fallback: String = "网络错误"): String {
        return when (e) {
            // 连接超时
            is SocketTimeoutException -> "连接服务器超时，请检查网络后重试"

            // DNS解析失败 / 主机不可达
            is UnknownHostException -> "无法连接到服务器，请检查网络或服务器地址是否正确"

            // 连接被拒绝（端口未开放/服务未启动）
            is ConnectException -> "服务器拒绝连接，请确认服务是否已启动"

            // SSL证书错误
            is SSLException -> "安全连接失败，请检查服务器证书配置"

            // 其他情况，根据消息内容判断
            else -> translateByMessage(e.message, fallback)
        }
    }

    /**
     * 将后端返回的错误消息翻译为用户友好的中文提示
     * @param serverMsg 后端返回的msg字段
     * @param fallback 默认兜底消息
     * @return 友好的中文错误消息
     */
    fun translateServerError(serverMsg: String?, fallback: String = "操作失败，请稍后重试"): String {
        if (serverMsg.isNullOrBlank()) return fallback

        return when {
            // 数据库相关错误
            serverMsg.contains("数据库异常", ignoreCase = true) ||
            serverMsg.contains("database", ignoreCase = true) ||
            serverMsg.contains("ECONNREFUSED", ignoreCase = true) ->
                "服务器数据异常，请联系管理员"

            // 认证相关错误
            serverMsg.contains("token", ignoreCase = true) ||
            serverMsg.contains("认证", ignoreCase = true) ||
            serverMsg.contains("授权", ignoreCase = true) ->
                "登录已过期，请重新登录"

            // 权限相关错误
            serverMsg.contains("权限", ignoreCase = true) ||
            serverMsg.contains("forbidden", ignoreCase = true) ||
            serverMsg.contains("无权", ignoreCase = true) ->
                "您没有权限执行此操作"

            // 参数错误
            serverMsg.contains("参数错误", ignoreCase = true) ||
            serverMsg.contains("参数", ignoreCase = true) ->
                "请求参数有误，请检查后重试"

            // 服务器内部错误
            serverMsg.contains("服务器", ignoreCase = true) ||
            serverMsg.contains("internal", ignoreCase = true) ||
            serverMsg.contains("500", ignoreCase = true) ->
                "服务器繁忙，请稍后重试"

            // 限流
            serverMsg.contains("频繁", ignoreCase = true) ||
            serverMsg.contains("限流", ignoreCase = true) ||
            serverMsg.contains("rate limit", ignoreCase = true) ->
                "操作过于频繁，请稍后重试"

            // 其他错误，直接返回后端消息（后端消息本身是中文）
            else -> serverMsg
        }
    }

    /**
     * 根据异常消息中的关键词翻译为中文
     */
    private fun translateByMessage(message: String?, fallback: String): String {
        if (message.isNullOrBlank()) return fallback

        return when {
            // 连接超时
            message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ->
                "连接服务器超时，请检查网络后重试"

            // 连接失败（含端口不可达）
            message.contains("failed to connect", ignoreCase = true) ||
            message.contains("connection refused", ignoreCase = true) ||
            message.contains("ECONNREFUSED", ignoreCase = true) ->
                "无法连接到服务器，请检查服务器地址和端口是否正确"

            // 主机不可达
            message.contains("unreachable", ignoreCase = true) ||
            message.contains("No route to host", ignoreCase = true) ->
                "网络不可达，请检查WiFi连接或服务器地址"

            // DNS解析失败
            message.contains("unable to resolve host", ignoreCase = true) ||
            message.contains("nodename nor servname", ignoreCase = true) ->
                "无法解析服务器地址，请检查输入的地址是否正确"

            // 网络中断
            message.contains("network", ignoreCase = true) &&
            message.contains("unavailable", ignoreCase = true) ->
                "网络不可用，请检查网络连接"

            // 连接被重置
            message.contains("reset", ignoreCase = true) &&
            message.contains("connection", ignoreCase = true) ->
                "连接被服务器中断，请重试"

            // SSL错误
            message.contains("ssl", ignoreCase = true) ||
            message.contains("certificate", ignoreCase = true) ->
                "安全连接失败，请检查服务器证书"

            // JSON解析失败（反序列化错误）
            message.contains("JsonDecodingException", ignoreCase = true) ||
            message.contains("serialization", ignoreCase = true) ->
                "数据格式异常，请更新应用版本"

            // 其他未知错误，返回兜底消息
            else -> fallback
        }
    }
}
