package top.cywin.onetv.tv.ui.screens.videoplayer.player // 定义视频播放器的包名

import android.view.SurfaceView // 引入SurfaceView类，用于显示视频内容
import kotlinx.coroutines.CoroutineScope // 引入协程作用域
import kotlinx.coroutines.Job // 引入Job，用于管理协程
import kotlinx.coroutines.delay // 引入delay函数，用于延迟执行
import kotlinx.coroutines.launch // 引入launch函数，用于启动协程
import top.cywin.onetv.tv.ui.utils.Configs // 引入Configs类，获取配置信息

abstract class VideoPlayer( // 定义一个抽象类VideoPlayer
    private val coroutineScope: CoroutineScope, // 协程作用域，用于管理协程
) {
    protected var metadata = Metadata() // 初始化元数据对象

    open fun initialize() { // 初始化播放器
        clearAllListeners() // 清除所有事件监听器
    }

    open fun release() { // 释放播放器资源
        clearAllListeners() // 清除所有事件监听器
    }

    abstract fun prepare(url: String) // 准备视频，抽象方法，子类必须实现

    abstract fun play() // 播放视频，抽象方法，子类必须实现

    abstract fun pause() // 暂停视频，抽象方法，子类必须实现

    abstract fun seekTo(position: Long) // 跳转到指定位置，抽象方法，子类必须实现

    open fun stop() { // 停止视频播放
        loadTimeoutJob?.cancel() // 如果加载超时协程存在，则取消它
        interruptJob?.cancel() // 如果中断协程存在，则取消它
        currentPosition = 0L // 重置当前播放位置
    }

    abstract fun setVideoSurfaceView(surfaceView: SurfaceView) // 设置视频显示的SurfaceView，抽象方法，子类必须实现

    private var loadTimeoutJob: Job? = null // 加载超时的协程任务
    private var interruptJob: Job? = null // 中断的协程任务
    private var currentPosition = 0L // 当前播放位置

    private val onResolutionListeners = mutableListOf<(width: Int, height: Int) -> Unit>() // 分辨率变化的监听器列表
    private val onErrorListeners = mutableListOf<(error: PlaybackException?) -> Unit>() // 错误发生时的监听器列表
    private val onReadyListeners = mutableListOf<() -> Unit>() // 播放器准备好的监听器列表
    private val onBufferingListeners = mutableListOf<(buffering: Boolean) -> Unit>() // 缓冲状态变化的监听器列表
    private val onPreparedListeners = mutableListOf<() -> Unit>() // 播放器准备好的监听器列表
    private val onIsPlayingChanged = mutableListOf<(isPlaying: Boolean) -> Unit>() // 播放状态变化的监听器列表
    private val onDurationChanged = mutableListOf<(duration: Long) -> Unit>() // 视频时长变化的监听器列表
    private val onCurrentPositionChanged = mutableListOf<(position: Long) -> Unit>() // 当前播放位置变化的监听器列表
    private val onMetadataListeners = mutableListOf<(metadata: Metadata) -> Unit>() // 元数据变化的监听器列表
    private val onInterruptListeners = mutableListOf<() -> Unit>() // 播放中断的监听器列表

    private fun clearAllListeners() { // 清除所有的监听器
        onResolutionListeners.clear()
        onErrorListeners.clear()
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onPreparedListeners.clear()
        onIsPlayingChanged.clear()
        onDurationChanged.clear()
        onCurrentPositionChanged.clear()
        onMetadataListeners.clear()
        onInterruptListeners.clear()
    }

    protected fun triggerResolution(width: Int, height: Int) { // 触发分辨率变化的监听器
        onResolutionListeners.forEach { it(width, height) }
    }

    protected fun triggerError(error: PlaybackException?) { // 触发错误的监听器
        onErrorListeners.forEach { it(error) }
        if (error != PlaybackException.LOAD_TIMEOUT) { // 如果不是加载超时错误，则取消加载超时的协程任务
            loadTimeoutJob?.cancel()
            loadTimeoutJob = null
        }
    }

    protected fun triggerReady() { // 触发播放器准备好的监听器
        onReadyListeners.forEach { it() }
        loadTimeoutJob?.cancel() // 取消加载超时的协程任务
    }

    protected fun triggerBuffering(buffering: Boolean) { // 触发缓冲状态变化的监听器
        onBufferingListeners.forEach { it(buffering) }
    }

    protected fun triggerPrepared() { // 触发播放器准备好的监听器
        onPreparedListeners.forEach { it() }
        loadTimeoutJob?.cancel() // 取消加载超时的协程任务
        loadTimeoutJob = coroutineScope.launch { // 启动加载超时的协程任务
            delay(Configs.videoPlayerLoadTimeout)
            triggerError(PlaybackException.LOAD_TIMEOUT)
        }
        interruptJob?.cancel() // 取消中断协程任务
        interruptJob = null
        metadata = Metadata() // 重置元数据
    }

    protected fun triggerIsPlayingChanged(isPlaying: Boolean) { // 触发播放状态变化的监听器
        onIsPlayingChanged.forEach { it(isPlaying) }
    }

    protected fun triggerDuration(duration: Long) { // 触发视频时长变化的监听器
        onDurationChanged.forEach { it(duration) }
    }

    protected fun triggerMetadata(metadata: Metadata) { // 触发元数据变化的监听器
        onMetadataListeners.forEach { it(metadata) }
    }

    protected fun triggerCurrentPosition(position: Long) { // 触发当前播放位置变化的监听器
        if (currentPosition != position) { // 如果位置发生变化，则启动中断协程
            interruptJob?.cancel()
            interruptJob = coroutineScope.launch {
                delay(Configs.videoPlayerLoadTimeout)
                onInterruptListeners.forEach { it() }
            }
        }
        currentPosition = position // 更新当前播放位置
        onCurrentPositionChanged.forEach { it(position) }
    }

    fun onResolution(listener: (width: Int, height: Int) -> Unit) { // 添加分辨率变化的监听器
        onResolutionListeners.add(listener)
    }

    fun onError(listener: (error: PlaybackException?) -> Unit) { // 添加错误发生时的监听器
        onErrorListeners.add(listener)
    }

    fun onReady(listener: () -> Unit) { // 添加播放器准备好的监听器
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: (buffering: Boolean) -> Unit) { // 添加缓冲状态变化的监听器
        onBufferingListeners.add(listener)
    }

    fun onPrepared(listener: () -> Unit) { // 添加播放器准备好的监听器
        onPreparedListeners.add(listener)
    }

    fun onIsPlayingChanged(listener: (isPlaying: Boolean) -> Unit) { // 添加播放状态变化的监听器
        onIsPlayingChanged.add(listener)
    }

    fun onDurationChanged(listener: (duration: Long) -> Unit) { // 添加视频时长变化的监听器
        onDurationChanged.add(listener)
    }

    fun onCurrentPositionChanged(listener: (position: Long) -> Unit) { // 添加当前播放位置变化的监听器
        onCurrentPositionChanged.add(listener)
    }

    fun onMetadata(listener: (metadata: Metadata) -> Unit) { // 添加元数据变化的监听器
        onMetadataListeners.add(listener)
    }

    fun onInterrupt(listener: () -> Unit) { // 添加播放中断的监听器
        onInterruptListeners.add(listener)
    }

    data class PlaybackException(val errorCodeName: String, val errorCode: Int) : // 定义播放异常类
        Exception(errorCodeName) {
        companion object { // 定义一些常见的播放异常
            val UNSUPPORTED_TYPE = PlaybackException("UNSUPPORTED_TYPE", 10002)
            val LOAD_TIMEOUT = PlaybackException("LOAD_TIMEOUT", 10003)
        }
    }

    /** 元数据 */
    data class Metadata( // 定义视频的元数据
        /** 视频编码 */
        val videoMimeType: String = "",
        /** 视频宽度 */
        val videoWidth: Int = 0,
        /** 视频高度 */
        val videoHeight: Int = 0,
        /** 视频颜色 */
        val videoColor: String = "",
        /** 视频帧率 */
        val videoFrameRate: Float = 0f,
        /** 视频比特率 */
        val videoBitrate: Int = 0,
        /** 视频解码器 */
        val videoDecoder: String = "",

        /** 音频编码 */
        val audioMimeType: String = "",
        /** 音频通道 */
        val audioChannels: Int = 0,
        /** 音频采样率 */
        val audioSampleRate: Int = 0,
        /** 音频解码器 */
        val audioDecoder: String = "",
    )
}
