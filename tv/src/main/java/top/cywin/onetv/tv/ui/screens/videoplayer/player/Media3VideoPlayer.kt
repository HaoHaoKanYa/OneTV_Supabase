package top.cywin.onetv.tv.ui.screens.videoplayer.player

import android.content.Context // 导入Context类，用于访问应用上下文
import android.net.Uri // 导入Uri类，用于处理URL
import android.view.SurfaceView // 导入SurfaceView类，用于显示视频
import androidx.annotation.OptIn // 导入OptIn注解，用于标记不稳定的API
import androidx.media3.common.C // 导入C类，常量定义
import androidx.media3.common.Format // 导入Format类，处理媒体格式
import androidx.media3.common.MediaItem // 导入MediaItem类，表示多媒体项
import androidx.media3.common.Player // 导入Player类，播放器接口
import androidx.media3.common.VideoSize // 导入VideoSize类，表示视频尺寸
import androidx.media3.common.util.UnstableApi // 导入UnstableApi，用于标记不稳定的API
import androidx.media3.common.util.Util // 导入Util工具类，提供各种帮助方法
import androidx.media3.datasource.DefaultDataSource // 导入DefaultDataSource类，默认数据源
import androidx.media3.datasource.DefaultHttpDataSource // 导入DefaultHttpDataSource类，HTTP数据源
import androidx.media3.exoplayer.DecoderReuseEvaluation // 导入DecoderReuseEvaluation类，解码器复用评估
import androidx.media3.exoplayer.DefaultRenderersFactory // 导入DefaultRenderersFactory类，默认渲染器工厂
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON // 导入扩展渲染器模式常量
import androidx.media3.exoplayer.ExoPlayer // 导入ExoPlayer类，ExoPlayer播放器
import androidx.media3.exoplayer.analytics.AnalyticsListener // 导入AnalyticsListener类，用于播放器分析
import androidx.media3.exoplayer.hls.HlsMediaSource // 导入HlsMediaSource类，用于HLS媒体源
import androidx.media3.exoplayer.source.MediaSource // 导入MediaSource类，媒体源接口
import androidx.media3.exoplayer.source.ProgressiveMediaSource // 导入ProgressiveMediaSource类，渐进式媒体源
import androidx.media3.exoplayer.util.EventLogger // 导入EventLogger类，用于事件日志
import kotlinx.coroutines.CoroutineScope // 导入CoroutineScope类，用于协程作用域
import kotlinx.coroutines.Job // 导入Job类，用于协程任务
import kotlinx.coroutines.delay // 导入delay函数，用于延迟操作
import kotlinx.coroutines.launch // 导入launch函数，用于启动协程
import top.cywin.onetv.tv.ui.utils.Configs // 导入Configs类，配置类

@OptIn(UnstableApi::class) // 标记使用不稳定API的部分
class Media3VideoPlayer(
    private val context: Context, // 定义上下文，供ExoPlayer使用
    private val coroutineScope: CoroutineScope, // 定义协程作用域，用于启动协程
) : VideoPlayer(coroutineScope) { // 继承自VideoPlayer类，并传递协程作用域

    // 定义ExoPlayer实例，使用懒加载初始化
    private val videoPlayer by lazy {
        val renderersFactory = DefaultRenderersFactory(context) // 创建渲染器工厂
            .setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON) // 启用扩展渲染器模式

        ExoPlayer // 创建ExoPlayer实例
            .Builder(context) // 使用构建器模式
            .setRenderersFactory(renderersFactory) // 设置渲染器工厂
            .build() // 构建ExoPlayer实例
            .apply { playWhenReady = true } // 设置播放器在准备好时自动播放
    }

    // 定义数据源工厂，用于创建不同的数据源
    private val dataSourceFactory by lazy {
        DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().apply { // 设置HTTP数据源的各种配置
                setUserAgent(Configs.videoPlayerUserAgent) // 设置User-Agent
                setConnectTimeoutMs(Configs.videoPlayerLoadTimeout.toInt()) // 设置连接超时时间
                setReadTimeoutMs(Configs.videoPlayerLoadTimeout.toInt()) // 设置读取超时时间
                setKeepPostFor302Redirects(true) // 保持POST请求用于302重定向
                setAllowCrossProtocolRedirects(true) // 允许跨协议重定向
            },
        )
    }

    // 定义用于存储每种内容类型的尝试状态
    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()

    // 定义更新播放器进度的协程任务
    private var updatePositionJob: Job? = null

    // 根据Uri和内容类型获取媒体源
    private fun getMediaSource(uri: Uri, contentType: Int? = null): MediaSource? {
        val mediaItem = MediaItem.fromUri(uri) // 创建MediaItem实例

        // 使用进度式媒体源处理所有内容，包括RTSP
        return when (val type = contentType ?: Util.inferContentType(uri)) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem) // HLS媒体源
            }

            C.CONTENT_TYPE_RTSP, C.CONTENT_TYPE_OTHER -> {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem) // 其他媒体源
            }

            else -> {
                // 如果类型不支持，触发错误
                triggerError(
                    PlaybackException.UNSUPPORTED_TYPE.copy(
                        errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                    )
                )
                null
            }
        }
    }
    
    // 准备播放，接收Uri和内容类型参数
    private fun prepare(uri: Uri, contentType: Int? = null) {
        val mediaSource = getMediaSource(uri, contentType) // 获取媒体源

        if (mediaSource != null) {
            contentTypeAttempts[contentType ?: Util.inferContentType(uri)] = true // 标记尝试的类型
            videoPlayer.setMediaSource(mediaSource) // 设置媒体源
            videoPlayer.prepare() // 准备播放器
            videoPlayer.play() // 播放
            triggerPrepared() // 触发准备完成的回调
        }
        updatePositionJob?.cancel() // 取消当前的更新任务
        updatePositionJob = null // 重置任务
    }

    // 播放器事件监听器
    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height) // 触发分辨率变化回调
        }

        override fun onPlayerError(ex: androidx.media3.common.PlaybackException) {
            when (ex.errorCode) { // 处理不同的错误代码
                androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                    videoPlayer.seekToDefaultPosition() // 重置到默认位置
                    videoPlayer.prepare() // 重新准备播放器
                }

                // 当解析容器不支持时，尝试使用其他解析容器
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                    videoPlayer.currentMediaItem?.localConfiguration?.uri?.let {
                        if (contentTypeAttempts[C.CONTENT_TYPE_HLS] != true) {
                            prepare(it, C.CONTENT_TYPE_HLS) // 尝试HLS解析
                        } else if (contentTypeAttempts[C.CONTENT_TYPE_OTHER] != true) {
                            prepare(it, C.CONTENT_TYPE_OTHER) // 尝试其他解析
                        } else {
                            val type = Util.inferContentType(it)
                            triggerError(
                                PlaybackException.UNSUPPORTED_TYPE.copy(
                                    errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                                )
                            ) // 如果都不支持，触发错误
                        }
                    }
                }

                else -> {
                    triggerError(PlaybackException(ex.errorCodeName, ex.errorCode)) // 触发其他错误
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                triggerError(null) // 播放缓冲时触发错误
                triggerBuffering(true) // 标记正在缓冲
            } else if (playbackState == Player.STATE_READY) {
                triggerReady() // 播放器准备完成时触发回调

                updatePositionJob?.cancel() // 取消当前的进度更新任务
                updatePositionJob = coroutineScope.launch { // 启动新的协程任务
                    while (true) {
                        val livePosition =
                            System.currentTimeMillis() - videoPlayer.currentLiveOffset // 获取当前播放进度

                        triggerCurrentPosition(if (livePosition > 0) livePosition else videoPlayer.currentPosition)
                        delay(1000) // 每秒更新一次
                    }
                }

                triggerDuration(videoPlayer.duration) // 触发视频时长回调
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false) // 停止缓冲时更新状态
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            triggerIsPlayingChanged(isPlaying) // 播放状态变化时触发回调
        }
    }

    // 播放器元数据监听器
    private val metadataListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toString() ?: "",
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate
            )
            triggerMetadata(metadata) // 触发元数据回调
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate
            )
            triggerMetadata(metadata) // 触发元数据回调
        }
    }

    // 事件日志记录器
    private val eventLogger = EventLogger()

    // 初始化播放器
    override fun initialize() {
        super.initialize() // 调用父类的初始化方法
        videoPlayer.addListener(playerListener) // 添加播放器事件监听器
        videoPlayer.addAnalyticsListener(metadataListener) // 添加元数据监听器
        videoPlayer.addAnalyticsListener(eventLogger) // 添加事件日志监听器
    }

    // 释放播放器资源
    override fun release() {
        videoPlayer.removeListener(playerListener) // 移除事件监听器
        videoPlayer.removeAnalyticsListener(metadataListener) // 移除元数据监听器
        videoPlayer.removeAnalyticsListener(eventLogger) // 移除事件日志监听器
        videoPlayer.release() // 释放播放器资源
        super.release() // 调用父类的释放方法
    }

    // 准备播放，接收URL参数
    override fun prepare(url: String) {
        contentTypeAttempts.clear() // 清空内容类型尝试记录
        prepare(Uri.parse(url)) // 解析URL并准备播放
    }

    // 播放视频
    override fun play() {
        videoPlayer.play() // 播放视频
    }

    // 暂停视频
    override fun pause() {
        videoPlayer.pause() // 暂停视频
    }

    // 跳转到指定位置
    override fun seekTo(position: Long) {
        videoPlayer.seekTo(position) // 跳转到指定位置
    }

    // 停止播放
    override fun stop() {
        videoPlayer.stop() // 停止播放器
        updatePositionJob?.cancel() // 取消进度更新任务
        super.stop() // 调用父类的停止方法
    }

    // 设置视频显示的SurfaceView
    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        videoPlayer.setVideoSurfaceView(surfaceView) // 设置SurfaceView用于显示视频
    }
}
