package com.salary.manager.feature.home.dashboard

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * 媒体文件类型判断工具
 */
private fun isImageType(type: String?): Boolean =
    type?.startsWith("image/") == true

private fun isVideoType(type: String?): Boolean =
    type?.startsWith("video/") == true

/**
 * 格式化时长（毫秒 → mm:ss 或 h:mm:ss）
 */
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

/**
 * 媒体查看器弹窗（参考微信聊天媒体查看样式）
 *
 * 功能：
 * - 图片：全屏黑色背景，双指缩放、双击放大、拖动平移
 * - 视频：全屏黑色背景，自动播放，播放/暂停/进度条/时长
 * - 顶部标题栏（文件名+关闭），点击切换控制栏显隐
 *
 * @param fileUrl 完整的媒体文件URL
 * @param fileName 文件名（标题栏显示）
 * @param fileType 文件MIME类型（判断图片/视频）
 * @param onDismiss 关闭回调
 */
@Composable
fun MediaViewerDialog(
    fileUrl: String,
    fileName: String,
    fileType: String?,
    onDismiss: () -> Unit
) {
    // 使用全屏Dialog，禁止外部点击关闭（避免误触）
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 控制栏显隐状态（点击切换）
            var controlsVisible by remember { mutableStateOf(true) }

            when {
                isImageType(fileType) -> {
                    ImageViewerContent(
                        url = fileUrl,
                        onTapToggle = { controlsVisible = !controlsVisible }
                    )
                }
                isVideoType(fileType) -> {
                    VideoViewerContent(
                        url = fileUrl,
                        controlsVisible = controlsVisible,
                        onTapToggle = { controlsVisible = !controlsVisible }
                    )
                }
                else -> {
                    // 不支持的类型提示
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "此文件类型无法预览",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // 顶部控制栏（文件名 + 关闭按钮）
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color(0x88000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileName,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图片查看内容
 * 支持双指缩放、双击放大/还原、拖动平移
 */
@Composable
private fun ImageViewerContent(
    url: String,
    onTapToggle: () -> Unit
) {
    // 缩放和平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        // 限制缩放范围 1.0 ~ 5.0
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        // 缩放为1时不允许平移（避免图片偏移）
        if (newScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
        scale = newScale
    }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 单击切换控制栏，双击切换放大/还原
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            // 还原
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // 放大到2倍
                            scale = 2f
                        }
                    },
                    onTap = { onTapToggle() }
                )
            }
            .transformable(state = transformableState),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = "图片预览",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

/**
 * 视频查看内容
 * 使用Media3 ExoPlayer播放，自动播放，自定义控制栏
 *
 * @param url 视频URL
 * @param controlsVisible 控制栏是否可见
 * @param onTapToggle 点击切换控制栏回调
 */
@Composable
private fun VideoViewerContent(
    url: String,
    controlsVisible: Boolean,
    onTapToggle: () -> Unit
) {
    val context = LocalContext.current

    // 创建ExoPlayer实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // 组件销毁时释放播放器
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // 播放状态
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    // 监听播放器状态
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    // 更新当前进度
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.duration > 0) {
                currentPosition = exoPlayer.currentPosition.coerceIn(0L, exoPlayer.duration)
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapToggle() })
            },
        contentAlignment = Alignment.Center
    ) {
        // 视频播放器（Media3 PlayerView）
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    // 隐藏自带的控制栏，用自定义的
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 加载中指示器
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        // 底部控制栏（播放/暂停 + 进度条 + 时长）
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放/暂停按钮
                    IconButton(onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 当前进度时间
                    Text(
                        text = formatDuration(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    // 进度条
                    Slider(
                        value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                        onValueChange = { fraction ->
                            if (totalDuration > 0) {
                                val newPos = (fraction * totalDuration).toLong()
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x66FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    // 总时长
                    Text(
                        text = formatDuration(totalDuration),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
