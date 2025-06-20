package top.cywin.onetv.tv.ui.screens.videoplayer

import android.util.Log
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.tv.supabase.SupabaseVideoPlayerWatchHistoryTracker
import top.cywin.onetv.tv.ui.screens.videoplayer.player.Media3VideoPlayer
import top.cywin.onetv.tv.ui.screens.videoplayer.player.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class VideoPlayerState(
    private val instance: VideoPlayer, // 视频播放器实例
    private var defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }, // 默认显示模式提供者
    private val watchHistoryTracker: SupabaseVideoPlayerWatchHistoryTracker? = null, // 观看历史跟踪器
) {
    private val TAG = "VideoPlayerState"
    
    /** 显示模式 */
    var displayMode by mutableStateOf(defaultDisplayModeProvider()) // 当前显示模式

    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f) // 当前视频的宽高比，默认 16:9

    /** 错误 */
    var error by mutableStateOf<String?>(null) // 存储错误信息

    /** 正在缓冲 */
    var isBuffering by mutableStateOf(false) // 视频是否正在缓冲

    /** 正在播放 */
    var isPlaying by mutableStateOf(false) // 视频是否正在播放

    /** 总时长 */
    var duration by mutableLongStateOf(0L) // 视频总时长

    /** 当前播放位置 */
    var currentPosition by mutableLongStateOf(0L) // 当前播放进度

    /** 元数据 */
    var metadata by mutableStateOf(VideoPlayer.Metadata()) // 当前视频元数据（如标题、作者等）
    
    /** 当前播放的频道 */
    private var _currentChannel: Channel? = null
    val currentChannel: Channel? get() = _currentChannel
    
    /** 播放开始时间 */
    private var playStartTime: Long = 0

    init {
        Log.d(TAG, "初始化VideoPlayerState, 观看历史跟踪器: ${if(watchHistoryTracker != null) "已启用" else "未启用"}")
    }
    
    // 初始化视频播放器
    fun prepare(url: String) {
        Log.d(TAG, "准备播放URL: ${url.take(50)}${if(url.length > 50) "..." else ""}")
        error = null // 清空错误
        instance.prepare(url) // 准备视频播放
        playStartTime = System.currentTimeMillis()
        Log.d(TAG, "记录播放开始时间: ${formatTime(playStartTime)}")
    }
    
    // 准备播放频道
    fun prepareChannel(channel: Channel, urlIndex: Int = 0) {
        Log.d(TAG, "准备播放频道: ${channel.name}, URL索引: $urlIndex, 跟踪器状态: ${if(watchHistoryTracker != null) "已启用" else "未启用"}")
        
        // 如果当前有播放的频道，先停止跟踪
        if (_currentChannel != null && _currentChannel != channel) {
            Log.d(TAG, "切换频道，先停止跟踪当前频道: ${_currentChannel?.name}")
            if (watchHistoryTracker != null) {
                watchHistoryTracker.stopTracking()
            }
        }
        
        _currentChannel = channel
        val url = if (channel.urlList.isNotEmpty() && urlIndex < channel.urlList.size) {
            channel.urlList[urlIndex]
        } else {
            Log.e(TAG, "频道URL无效: ${channel.name}, urlIndex=$urlIndex, urlList大小=${channel.urlList.size}")
            return
        }
        
        prepare(url)
        
        // 开始跟踪观看历史
        if (watchHistoryTracker != null) {
            Log.d(TAG, "开始跟踪频道观看历史: ${channel.name}")
            watchHistoryTracker.startTracking(channel)
        } else {
            Log.w(TAG, "观看历史跟踪器未初始化, 无法跟踪: ${channel.name}")
        }
    }

    // 播放视频
    fun play() {
        Log.d(TAG, "播放视频: ${_currentChannel?.name ?: "未知频道"}")
        instance.play() // 调用播放器的播放方法
        
        // 如果之前已经停止了跟踪，重新开始跟踪
        if (watchHistoryTracker != null && _currentChannel != null && !isPlaying) {
            Log.d(TAG, "重新开始跟踪观看历史: ${_currentChannel?.name}")
            watchHistoryTracker.startTracking(_currentChannel!!)
        }
    }

    // 暂停视频
    fun pause() {
        Log.d(TAG, "暂停视频: ${_currentChannel?.name ?: "未知频道"}")
        instance.pause() // 调用播放器的暂停方法
        
        // 暂停时不停止跟踪，因为用户可能会继续播放
        Log.d(TAG, "暂停视频，但继续跟踪观看历史")
    }

    // 跳转到指定播放位置
    fun seekTo(position: Long) {
        Log.d(TAG, "跳转到位置: $position")
        instance.seekTo(position) // 调用播放器的跳转方法
    }

    // 停止播放
    fun stop() {
        Log.d(TAG, "停止播放: ${_currentChannel?.name ?: "未知频道"}")
        instance.stop() // 调用播放器的停止方法
        
        // 停止跟踪观看历史
        if (watchHistoryTracker != null && _currentChannel != null) {
            Log.d(TAG, "停止跟踪观看历史: ${_currentChannel?.name}")
            watchHistoryTracker.stopTracking()
        }
        _currentChannel = null
    }

    // 设置视频的 SurfaceView
    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        Log.d(TAG, "设置视频SurfaceView")
        instance.setVideoSurfaceView(surfaceView) // 绑定 SurfaceView 用于显示视频
    }

    // 保存播放器事件的监听器
    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onInterruptListeners = mutableListOf<() -> Unit>()

    // 注册播放器准备好的监听器
    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    // 注册播放器错误的监听器
    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    // 注册播放器中断的监听器
    fun onInterrupt(listener: () -> Unit) {
        onInterruptListeners.add(listener)
    }

    // 初始化播放器，绑定各种事件监听器
    fun initialize() {
        Log.d(TAG, "初始化播放器实例")
        instance.initialize() // 初始化播放器实例
        instance.onResolution { width, height ->
            if (width > 0 && height > 0) {
                aspectRatio = width.toFloat() / height // 设置视频的宽高比
                Log.d(TAG, "视频分辨率: ${width}x${height}, 宽高比: $aspectRatio")
            }
        }
        instance.onError { ex ->
            Log.e(TAG, "播放器错误: ${ex?.errorCodeName}(${ex?.errorCode})")
            error = ex?.let { "${it.errorCodeName}(${it.errorCode})" } // 发生错误时记录错误信息
                ?.apply { 
                    onErrorListeners.forEach { it.invoke() } // 执行错误监听器
                } 
            
            // 错误时也停止跟踪观看历史
            if (watchHistoryTracker != null && _currentChannel != null) {
                Log.d(TAG, "播放器错误, 停止跟踪观看历史: ${_currentChannel?.name}")
                watchHistoryTracker.stopTracking()
            }
        }
        instance.onReady {
            Log.d(TAG, "播放器准备就绪: ${_currentChannel?.name ?: "未知频道"}")
            onReadyListeners.forEach { it.invoke() } // 播放器准备好时执行监听器
            error = null // 清除错误信息
            displayMode = defaultDisplayModeProvider() // 设置默认显示模式
            
            // 确保开始跟踪观看历史
            if (watchHistoryTracker != null && _currentChannel != null) {
                Log.d(TAG, "播放器就绪，确保开始跟踪观看历史: ${_currentChannel?.name}")
                watchHistoryTracker.startTracking(_currentChannel!!)
            }
        }
        instance.onBuffering {
            isBuffering = it // 设置缓冲状态
            Log.d(TAG, "缓冲状态变化: $it")
            if (it) error = null // 如果正在缓冲，清除错误信息
        }
        instance.onPrepared { 
            Log.d(TAG, "视频准备完成")
        } // 视频准备完成时的回调
        instance.onIsPlayingChanged { 
            isPlaying = it // 播放状态改变时的回调
            Log.d(TAG, "播放状态变化: $it, 频道: ${_currentChannel?.name ?: "未知频道"}")
        } 
        instance.onDurationChanged { 
            duration = it // 视频时长改变时的回调
            Log.d(TAG, "视频时长变化: $it")
        } 
        instance.onCurrentPositionChanged { currentPosition = it } // 当前播放位置改变时的回调
        instance.onMetadata { metadata = it } // 元数据更新时的回调
        instance.onInterrupt { 
            Log.d(TAG, "播放中断: ${_currentChannel?.name ?: "未知频道"}")
            onInterruptListeners.forEach { it.invoke() } // 播放中断时的回调
            
            // 中断时也停止跟踪观看历史
            if (watchHistoryTracker != null && _currentChannel != null) {
                Log.d(TAG, "播放中断, 停止跟踪观看历史: ${_currentChannel?.name}")
                watchHistoryTracker.stopTracking()
            }
        }
        
        Log.d(TAG, "播放器事件监听器已设置完成")
    }

    // 释放播放器资源
    fun release() {
        Log.d(TAG, "释放播放器资源: ${_currentChannel?.name ?: "未知频道"}")
        onReadyListeners.clear() // 清除准备好监听器
        onErrorListeners.clear() // 清除错误监听器
        
        // 停止跟踪观看历史
        if (watchHistoryTracker != null && _currentChannel != null) {
            Log.d(TAG, "释放资源时停止跟踪观看历史: ${_currentChannel?.name}")
            // 调用onAppExit确保保存当前观看记录
            watchHistoryTracker.onAppExit()
        }
        _currentChannel = null
        
        instance.release() // 释放播放器资源
    }
    
    /**
     * 格式化时间戳为可读时间
     */
    private fun formatTime(timeInMillis: Long): String {
        val date = java.util.Date(timeInMillis)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        return format.format(date)
    }
}

@Composable
fun rememberVideoPlayerState(
    defaultDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }, // 默认显示模式提供者
    enableWatchHistory: Boolean = true, // 是否启用观看历史
): VideoPlayerState {
    val context = LocalContext.current // 获取当前上下文
    val lifecycleOwner = LocalLifecycleOwner.current // 获取生命周期所有者
    val coroutineScope = rememberCoroutineScope() // 创建协程作用域
    
    // 创建观看历史跟踪器
    val watchHistoryTracker = if (enableWatchHistory) {
        Log.d("VideoPlayerState", "创建观看历史跟踪器")
        remember { SupabaseVideoPlayerWatchHistoryTracker(context, coroutineScope) }
    } else {
        Log.d("VideoPlayerState", "观看历史跟踪已禁用")
        null
    }
    
    val state = remember {
        VideoPlayerState(
            Media3VideoPlayer(context, coroutineScope), // 使用 Media3VideoPlayer 创建播放器实例
            defaultDisplayModeProvider, // 传入默认显示模式提供者
            watchHistoryTracker, // 传入观看历史跟踪器
        )
    }

    // 初始化播放器状态
    DisposableEffect(Unit) {
        Log.d("VideoPlayerState", "初始化播放器状态")
        state.initialize() // 初始化播放器
        onDispose { 
            Log.d("VideoPlayerState", "清理播放器状态")
            
            // 先停止当前跟踪
            if (state.currentChannel != null) {
                Log.d("VideoPlayerState", "停止当前频道跟踪")
                state.stop()
            }
            
            // 释放资源
            state.release() // 组件销毁时释放资源
            
            // 应用退出时同步观看历史
            if (watchHistoryTracker != null) {
                Log.d("VideoPlayerState", "组件销毁时同步观看历史")
                // 确保调用onAppExit方法保存当前观看记录
                watchHistoryTracker.onAppExit()
                
                // 确保在UI线程之外运行
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val count = watchHistoryTracker.syncToServer()
                        Log.d("VideoPlayerState", "成功同步 $count 条观看记录")
                    } catch (e: Exception) {
                        Log.e("VideoPlayerState", "同步观看历史失败: ${e.message}")
                    }
                }
                // onAppExit已经在release方法中调用，这里不需要重复调用
            }
        }
    }

    // 监听生命周期事件，播放和暂停控制
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("VideoPlayerState", "生命周期恢复, 继续播放")
                state.play() // 在活动恢复时播放
            }
            else if (event == Lifecycle.Event.ON_STOP) {
                Log.d("VideoPlayerState", "生命周期停止, 暂停播放")
                state.pause() // 在活动停止时暂停
            }
            else if (event == Lifecycle.Event.ON_DESTROY) {
                Log.d("VideoPlayerState", "生命周期销毁, 同步观看历史")
                // 确保在UI线程之外运行
                if (watchHistoryTracker != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val count = watchHistoryTracker.syncToServer()
                            Log.d("VideoPlayerState", "生命周期销毁时同步 $count 条观看记录")
                        } catch (e: Exception) {
                            Log.e("VideoPlayerState", "生命周期销毁时同步观看历史失败: ${e.message}")
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer) // 添加生命周期观察者
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } // 组件销毁时移除观察者
    }

    return state // 返回视频播放器状态
}

// 视频播放器显示模式
enum class VideoPlayerDisplayMode(
    val label: String, // 显示模式标签
    val value: Int, // 显示模式值
) {
    /** 原始 */
    ORIGINAL("原始", 0), // 保持原始宽高比

    /** 填充 */
    FILL("填充", 1), // 填充整个容器

    /** 裁剪 */
    CROP("裁剪", 2), // 裁剪多余部分

    /** 4:3 */
    FOUR_THREE("4:3", 3), // 4:3 比例

    /** 16:9 */
    SIXTEEN_NINE("16:9", 4), // 16:9 比例

    /** 2.35:1 */
    WIDE("2.35:1", 5); // 宽屏比例

    companion object {
        // 根据值获取对应的显示模式
        fun fromValue(value: Int): VideoPlayerDisplayMode {
            return entries.firstOrNull { it.value == value } ?: ORIGINAL // 如果找不到，则返回默认的原始模式
        }
    }
}
