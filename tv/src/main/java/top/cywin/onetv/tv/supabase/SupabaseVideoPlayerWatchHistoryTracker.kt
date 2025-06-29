package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.tv.supabase.sync.SupabaseAppExitSyncManager
import top.cywin.onetv.tv.supabase.sync.SupabaseWatchHistorySyncService

/**
 * 视频播放器观看历史跟踪器
 * 负责在用户观看视频时记录观看时长，并在适当的时候保存到本地和同步到服务器
 */
class SupabaseVideoPlayerWatchHistoryTracker(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "WatchHistoryTracker" //日志查看关键字
    
    // 当前播放的频道
    private var currentChannel: Channel? = null
    
    // 开始观看时间
    private var startWatchTime: Long = 0
    
    // 累计观看时长（秒）
    private var watchDuration: Long = 0
    
    // 跟踪定时器
    private var trackingJob: Job? = null
    
    // 是否正在跟踪
    private var isTracking = false
    
    // 最小记录时长（秒）- 15秒，避免记录快速浏览的频道
    private val MIN_RECORD_DURATION = 15L

    // 最小频道切换间隔（毫秒）- 5秒内的频繁切换不记录，避免快速切换产生碎片
    private val MIN_SWITCH_INTERVAL = 5_000L
    private var lastSwitchTime = 0L
    
    // 频道切换/关闭应用时保存，不再定时保存
    private val CHECK_INTERVAL = 60_000L // 仅作为内部检查时间，减少资源占用
    
    // 全局单例实例，用于在应用退出时访问
    companion object {
        private var INSTANCE: SupabaseVideoPlayerWatchHistoryTracker? = null
        
        fun getInstance(): SupabaseVideoPlayerWatchHistoryTracker? {
            return INSTANCE
        }
        
        fun setInstance(tracker: SupabaseVideoPlayerWatchHistoryTracker) {
            INSTANCE = tracker
        }
        
        fun clearInstance() {
            INSTANCE = null
        }
    }
    
    init {
        Log.d(TAG, "初始化观看历史跟踪器")
        // 保存实例到全局单例
        setInstance(this)
        
        // 初始化时加载历史记录数据
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "初始化时加载本地观看历史数据")
                SupabaseWatchHistorySessionManager.initialize(context)
                Log.d(TAG, "观看历史数据加载完成")
            } catch (e: Exception) {
                Log.e(TAG, "加载观看历史数据失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 开始跟踪观看历史
     * @param channel 当前观看的频道
     */
    fun startTracking(channel: Channel) {
        Log.d(TAG, "[WatchTracker] 开始跟踪观看历史请求: ${channel.name}, urlCount=${channel.urlList.size}")
        
        if (isTracking && currentChannel == channel) {
            // 已经在跟踪相同频道，不需要重新开始
            Log.d(TAG, "[WatchTracker] 已经在跟踪相同频道: ${channel.name}, 不重新开始")
            return
        }
        
        // 如果之前在跟踪其他频道，先保存之前的记录
        if (isTracking && currentChannel != null && currentChannel != channel) {
            Log.d(TAG, "[WatchTracker] 切换频道: 从 ${currentChannel?.name} 到 ${channel.name}, 先保存之前的记录")
            
            // 确保更新最新的观看时长
            updateWatchDuration()
            
            // 频道切换防抖：检查是否过于频繁切换
            val currentTime = System.currentTimeMillis()
            val timeSinceLastSwitch = currentTime - lastSwitchTime

            val previousChannel = currentChannel
            val finalDuration = watchDuration

            if (previousChannel != null && finalDuration >= MIN_RECORD_DURATION) {
                if (timeSinceLastSwitch >= MIN_SWITCH_INTERVAL) {
                    Log.d(TAG, "[WatchTracker] 切换频道保存记录: ${previousChannel.name}, 时长=${finalDuration}秒")

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // 使用本地管理器记录观看历史
                            SupabaseWatchHistorySessionManager.addWatchHistory(
                                context = context,
                                channelName = previousChannel.name,
                                channelUrl = previousChannel.urlList.firstOrNull() ?: "",
                                duration = finalDuration
                            )

                            Log.d(TAG, "[WatchTracker] 切换频道时成功保存观看历史: ${previousChannel.name}, 时长: $finalDuration 秒")
                            Log.d(TAG, "[WatchTracker] 记录已保存到本地缓存，将在应用/账户退出时统一同步到服务器")
                        } catch (e: Exception) {
                            Log.e(TAG, "[WatchTracker] 切换频道时保存观看历史失败: ${e.message}", e)
                        }
                    }

                    lastSwitchTime = currentTime
                } else {
                    Log.d(TAG, "[WatchTracker] 频道切换过于频繁，跳过记录: ${previousChannel.name}, 切换间隔=${timeSinceLastSwitch}ms < ${MIN_SWITCH_INTERVAL}ms（防止快速切换产生碎片记录）")
                }
            } else if (previousChannel != null && finalDuration > 0) {
                Log.d(TAG, "[WatchTracker] 观看时长不足，跳过记录: ${previousChannel.name}, 观看时长=${finalDuration}秒 < ${MIN_RECORD_DURATION}秒（避免记录快速浏览）")
            }
            
            // 重置跟踪状态
            trackingJob?.cancel()
        }
        
        currentChannel = channel
        startWatchTime = System.currentTimeMillis()
        watchDuration = 0
        isTracking = true
        
        Log.d(TAG, "[WatchTracker] 正式开始跟踪观看历史: ${channel.name}, 开始时间=${formatTime(startWatchTime)}")
        
        // 启动定时器，定期保存观看记录
        trackingJob?.cancel()
        trackingJob = coroutineScope.launch {
            while (isTracking) {
                delay(CHECK_INTERVAL)
                if (isTracking) {
                    // 仅更新当前累计时长，不保存
                    updateWatchDuration()
                    Log.d(TAG, "[WatchTracker] 时长更新检查: ${currentChannel?.name}, 累计观看时长=${watchDuration}秒")
                    
                    // 不再在定时器中保存，仅在切换频道或关闭应用时保存
                    // 这样可以更准确统计观看次数，避免同一次观看被多次记录
                    if (watchDuration < MIN_RECORD_DURATION) {
                        Log.d(TAG, "[WatchTracker] 观看时长不足(${watchDuration}秒 < ${MIN_RECORD_DURATION}秒), 继续跟踪: ${currentChannel?.name}")
                    }
                }
            }
        }
    }
    
    /**
     * 停止跟踪观看历史
     * 会保存当前的观看记录
     */
    fun stopTracking() {
        Log.d(TAG, "[WatchTracker] 停止跟踪观看历史请求: ${currentChannel?.name}")
        
        if (!isTracking) {
            Log.d(TAG, "[WatchTracker] 当前未在跟踪任何频道, 无需停止")
            return
        }
        
        // 确保时长是最新的
        updateWatchDuration()
        
        // 只有当观看时间超过最小记录时长时才记录
        if (watchDuration >= MIN_RECORD_DURATION) {
            // 保存当前时长值，避免异步操作中的值变化
            val finalDuration = watchDuration
            val channel = currentChannel
            
            Log.d(TAG, "[WatchTracker] 停止跟踪时保存记录: ${channel?.name}, 累计观看时长=${finalDuration}秒")
            
            if (channel != null && finalDuration > 0) {
                // 记录即将保存的详细信息
                Log.d(TAG, "[WatchTracker] 准备保存观看历史: 频道=${channel.name}, URL=${channel.urlList.firstOrNull() ?: "无URL"}, 时长=${finalDuration}秒")
                
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // 使用本地管理器记录观看历史
                        val historyItem = SupabaseWatchHistorySessionManager.addWatchHistoryAsync(
                            context = context,
                            channelName = channel.name,
                            channelUrl = channel.urlList.firstOrNull() ?: "",
                            duration = finalDuration
                        )
                        
                        Log.d(TAG, "[WatchTracker] 成功保存观看历史: ${channel.name}, 时长: $finalDuration 秒, ID: ${historyItem.id}")
                        Log.d(TAG, "[WatchTracker] 记录已保存到本地缓存，将在应用/账户退出时统一同步到服务器")
                    } catch (e: Exception) {
                        Log.e(TAG, "[WatchTracker] 保存观看历史失败: ${e.message}", e)
                    }
                }
            }
        } else {
            Log.d(TAG, "[WatchTracker] 观看时长不足，跳过记录: ${currentChannel?.name}, 观看时长=${watchDuration}秒 < ${MIN_RECORD_DURATION}秒")
        }
        
        // 重置跟踪状态
        isTracking = false
        trackingJob?.cancel()
        trackingJob = null
        
        Log.d(TAG, "[WatchTracker] 已停止跟踪观看历史: ${currentChannel?.name}")
    }
    
    /**
     * 更新观看时长
     */
    private fun updateWatchDuration() {
        if (!isTracking || startWatchTime == 0L) {
            Log.d(TAG, "[WatchTracker] 更新观看时长: 未在跟踪或开始时间未设置")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startWatchTime
        val oldDuration = watchDuration
        watchDuration = elapsedTime / 1000 // 转换为秒
        
        Log.d(TAG, "[WatchTracker] 更新观看时长: ${currentChannel?.name}, 从${oldDuration}秒更新为${watchDuration}秒, 已观看${formatDuration(elapsedTime)}")
    }
    
    /**
     * 保存观看历史
     */
    private fun saveWatchHistory() {
        val channel = currentChannel
        if (channel == null) {
            Log.e(TAG, "[WatchTracker] 保存观看历史失败: 当前频道为null")
            return
        }
        
        updateWatchDuration() // 确保时长是最新的
        
        if (watchDuration < MIN_RECORD_DURATION) {
            Log.d(TAG, "[WatchTracker] 观看时长不足 $MIN_RECORD_DURATION 秒(当前${watchDuration}秒), 不记录: ${channel.name}")
            return
        }
        
        // 记录即将保存的详细信息
        Log.d(TAG, "[WatchTracker] 准备保存观看历史: 频道=${channel.name}, URL=${channel.urlList.firstOrNull() ?: "无URL"}, 时长=${watchDuration}秒")
        
        // 确保使用局部变量保存当前时长值，避免异步操作中的值变化
        val finalDuration = watchDuration
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 使用本地管理器记录观看历史
                Log.d(TAG, "[WatchTracker] 调用SupabaseWatchHistorySessionManager.addWatchHistory保存记录, 时长=${finalDuration}秒")
                
                SupabaseWatchHistorySessionManager.addWatchHistory(
                    context = context,
                    channelName = channel.name,
                    channelUrl = channel.urlList.firstOrNull() ?: "",
                    duration = finalDuration // 使用保存的时长值
                )
                
                Log.d(TAG, "[WatchTracker] 已成功保存观看历史到本地: ${channel.name}, 时长: $finalDuration 秒")
                
                // 不再立即同步到服务器，仅在应用退出时同步
                Log.d(TAG, "[WatchTracker] 记录已保存到本地，将在应用退出时同步到服务器")
            } catch (e: Exception) {
                Log.e(TAG, "[WatchTracker] 保存观看历史失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 手动同步观看历史到服务器
     * 可在应用退出时调用
     */
    suspend fun syncToServer(): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "[WatchTracker] 开始手动同步观看历史到服务器")

        // 先保存当前记录
        if (isTracking && watchDuration >= MIN_RECORD_DURATION) {
            Log.d(TAG, "[WatchTracker] 同步前先保存当前观看记录: ${currentChannel?.name}, 时长=${watchDuration}秒")
            saveWatchHistory()
            // 给saveWatchHistory一点时间完成
            delay(500)
        }

        // 使用SupabaseAppExitSyncManager确保只同步一次
        try {
            Log.d(TAG, "[WatchTracker] 调用SupabaseAppExitSyncManager.performExitSync执行退出同步")
            val count = SupabaseAppExitSyncManager.performExitSync(context)
            Log.d(TAG, "[WatchTracker] 成功同步 $count 条观看记录到服务器")
            return@withContext count
        } catch (e: Exception) {
            Log.e(TAG, "[WatchTracker] 同步观看历史到服务器失败: ${e.message}", e)
            return@withContext 0
        }
    }
    
    /**
     * 应用退出时调用，确保保存观看记录
     */
    fun onAppExit() {
        Log.d(TAG, "[WatchTracker] 应用退出处理: 准备保存最终观看记录")
        
        if (isTracking) {
            Log.d(TAG, "[WatchTracker] 应用退出时停止跟踪: ${currentChannel?.name}")
            // 确保更新最新的观看时长
            updateWatchDuration()
            
            // 即使不到最小记录时间，也尝试保存（应用退出是特殊情况）
            if (watchDuration > 0 && currentChannel != null) {
                Log.d(TAG, "[WatchTracker] 应用退出时强制保存记录: ${currentChannel?.name}, 时长=${watchDuration}秒")
                
                // 使用局部变量保存当前值
                val channel = currentChannel
                val finalDuration = watchDuration
                
                if (channel != null && finalDuration > 0) {
                    try {
                        // 同步方式直接保存，确保不丢失
                        Log.d(TAG, "[WatchTracker] 应用退出时直接保存观看历史: ${channel.name}, 时长=${finalDuration}秒")
                        SupabaseWatchHistorySessionManager.addWatchHistory(
                            context = context,
                            channelName = channel.name,
                            channelUrl = channel.urlList.firstOrNull() ?: "",
                            duration = finalDuration
                        )
                        Log.d(TAG, "应用已退出，保存观看记录成功，保存的记录是${channel.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "[WatchTracker] 应用退出时保存记录失败: ${e.message}", e)
                    }
                }
            } else {
                Log.d(TAG, "[WatchTracker] 应用退出时没有正在观看的频道或观看时长为0，无需保存")
            }
            
            // 正常停止跟踪
            isTracking = false
            trackingJob?.cancel()
            trackingJob = null
            currentChannel = null
            watchDuration = 0
        } else {
            Log.d(TAG, "[WatchTracker] 应用退出时没有正在跟踪的频道，无需保存记录")
        }
        
        // 应用退出时同步所有本地缓存的观看历史到服务器
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "[WatchTracker] 应用退出时开始同步所有观看历史到服务器")
                val count = syncToServer()
                Log.d(TAG, "[WatchTracker] 应用退出时成功同步 $count 条观看记录到服务器")
            } catch (e: Exception) {
                Log.e(TAG, "[WatchTracker] 应用退出时同步观看历史失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 格式化时间戳为可读时间
     */
    private fun formatTime(timeInMillis: Long): String {
        val date = java.util.Date(timeInMillis)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    /**
     * 格式化时长
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
    }
} 