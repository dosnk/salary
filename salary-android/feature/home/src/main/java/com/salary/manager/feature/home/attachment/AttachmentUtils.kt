package com.salary.manager.feature.home.attachment

import java.net.URLEncoder

/**
 * 附件相关工具函数集合
 *
 * 用途：
 * - MIME 类型判断（图片/视频/媒体）
 * - 文件大小格式化
 * - 附件 URL 路径分段编码（解决旧数据中文/空格路径导致 Uri.parse 崩溃的问题）
 *
 * 说明：合并前主页与工程详情页各持有一份重复实现，本文件是唯一权威版本。
 */

/**
 * 判断是否为图片类型
 */
internal fun isImageType(type: String?): Boolean =
    type?.startsWith("image/") == true

/**
 * 判断是否为视频类型
 */
internal fun isVideoType(type: String?): Boolean =
    type?.startsWith("video/") == true

/**
 * 判断是否为媒体文件（图片或视频）
 */
internal fun isMediaFile(type: String?): Boolean =
    isImageType(type) || isVideoType(type)

/**
 * 格式化文件大小为人类可读字符串
 * < 1KB       → "xxxB"
 * < 1MB       → "xx.xKB"
 * >= 1MB      → "xx.xMB"
 */
internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024))
    }
}

/**
 * 对附件 URL 做路径分段 UTF-8 编码。
 *
 * 场景：旧数据附件路径包含中文（如 /upload/202602/状元府6栋1403/xxx.jpg），
 * 直接交给 Uri.parse 或 Intent 会引发 URISyntaxException / ActivityNotFoundException。
 * 此函数保留协议头、"/"、"?" 分隔符不变，仅对分段做 UTF-8 编码。
 *
 * 幂等：已含 % 编码的分段（例如"%E7%8A%B6"）不会再次编码。
 *
 * @param url 原始 URL（可能包含中文或空格）
 * @return 编码后的安全 URL；异常时原样返回，保证不抛出
 */
internal fun encodeAttachmentUrl(url: String): String {
    if (url.isEmpty()) return url
    return try {
        // 拆分 query
        val questionIdx = url.indexOf('?')
        val pathPart = if (questionIdx >= 0) url.substring(0, questionIdx) else url
        val queryPart = if (questionIdx >= 0) url.substring(questionIdx) else ""

        // 拆分协议头，只对 path 部分逐段编码
        val schemeEnd = pathPart.indexOf("://")
        val (prefix, path) = if (schemeEnd >= 0) {
            val hostEnd = pathPart.indexOf('/', schemeEnd + 3)
            if (hostEnd >= 0) {
                pathPart.substring(0, hostEnd) to pathPart.substring(hostEnd)
            } else {
                pathPart to ""
            }
        } else {
            "" to pathPart
        }

        val encodedPath = path.split('/').joinToString("/") { seg ->
            when {
                seg.isEmpty() -> ""
                // 已含 % 编码则视为已编码，保持不变，避免重复编码
                seg.contains('%') -> seg
                else -> URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
        }
        prefix + encodedPath + queryPart
    } catch (_: Throwable) {
        url
    }
}
