package top.cywin.onetv.tv.supabase

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.TimeZone

/**
 * 观看历史会话管理器
 * 负责在本地管理和缓存观看历史记录，并在适当的时候与服务器同步
 * 完全使用统一缓存管理器实现存储
 */
object SupabaseWatchHistorySessionManager {
    private const val TAG = "WatchHistoryManager"
    
    // 本地缓存的观看历史数据
    private val _watchHistoryItems = CopyOnWriteArrayList<SupabaseWatchHistoryItem>()
    
    // 观看统计数据
    private val _watchStatistics = MutableLiveData<SupabaseWatchStatistics>()
    val watchStatistics: LiveData<SupabaseWatchStatistics> = _watchStatistics
    
    // 是否有待同步的数据
    private var hasPendingChanges = false
    
    // 当前用户ID
    private var currentUserId: String? = null
    
    // JSON序列化工具
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false
        isLenient = true
    }
    
    /**
     * 初始化管理器，从本地存储加载数据
     */
    fun initialize(context: Context) {
        Log.d(TAG, "初始化观看历史管理器")
        
        try {
        // 获取当前用户ID
        val userId = try {
            // 使用SupabaseSessionManager获取用户ID
                kotlinx.coroutines.runBlocking { 
                    SupabaseSessionManager.getCachedUserData(context)?.userid
                }
        } catch (e: Exception) {
            Log.e(TAG, "获取用户ID失败", e)
            null
        }
        
        Log.d(TAG, "当前用户ID: $userId")
            currentUserId = userId
            
            // 使用超时保护
            val loadJob = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(5000) { // 5秒超时
                    try {
                        loadFromLocalAsync(context)
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "加载本地观看历史失败", e)
                        _watchHistoryItems.clear()
                        false
                    }
                }
            }
            
            if (loadJob == null) {
                Log.e(TAG, "加载观看历史超时，已强制终止")
                _watchHistoryItems.clear()
            }
            
            updateStatistics()
            Log.d(TAG, "观看历史管理器初始化完成, 加载了 ${_watchHistoryItems.size} 条记录")
        } catch (e: Exception) {
            Log.e(TAG, "观看历史管理器初始化失败", e)
            _watchHistoryItems.clear()
            // 确保即使初始化失败，也不会影响应用运行
        }
    }
    
    /**
     * 初始化管理器，从本地存储加载数据（协程版本）
     */
    suspend fun initializeAsync(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "初始化观看历史管理器（协程版本）")
        
        try {
            // 获取当前用户ID
            val userId = try {
                SupabaseSessionManager.getCachedUserData(context)?.userid
            } catch (e: Exception) {
                Log.e(TAG, "获取用户ID失败", e)
                null
            }
            
            Log.d(TAG, "当前用户ID: $userId")
            currentUserId = userId
            
            // 使用超时保护
            val loadSuccess = kotlinx.coroutines.withTimeoutOrNull(5000) { // 5秒超时
                try {
                    loadFromLocalAsync(context)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "加载本地观看历史失败", e)
                    _watchHistoryItems.clear()
                    false
                }
            }
            
            if (loadSuccess == null) {
                Log.e(TAG, "加载观看历史超时，已强制终止")
                _watchHistoryItems.clear()
            }
            
        updateStatistics()
            Log.d(TAG, "观看历史管理器初始化完成, 加载了 ${_watchHistoryItems.size} 条记录")
        } catch (e: Exception) {
            Log.e(TAG, "观看历史管理器初始化失败", e)
            _watchHistoryItems.clear()
            // 确保即使初始化失败，也不会影响应用运行
        }
    }
    
    /**
     * 从本地存储加载观看历史数据
     */
    private fun loadFromLocal(context: Context) {
        try {
            Log.d(TAG, "从本地存储加载观看历史数据")
            
            // 使用runBlocking调用协程版本
            kotlinx.coroutines.runBlocking {
                loadFromLocalAsync(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载本地观看历史失败", e)
            _watchHistoryItems.clear()
        }
    }
    
    /**
     * 从本地存储重新加载观看历史数据
     * 供同步服务调用，用于更新内存中的数据
     */
    fun reloadFromLocal(context: Context) {
        Log.d(TAG, "重新加载观看历史数据（同步服务请求）")
        loadFromLocal(context)
        updateStatistics()
    }
    
    /**
     * 从本地存储重新加载观看历史数据（协程版本）
     * 供同步服务调用，用于更新内存中的数据
     */
    suspend fun reloadFromLocalAsync(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "重新加载观看历史数据（同步服务请求，协程版本）")
        loadFromLocalAsync(context)
        updateStatistics()
    }
    
    /**
     * 从本地存储加载观看历史数据（协程版本）
     */
    private suspend fun loadFromLocalAsync(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "从本地存储加载观看历史数据（协程版本）")
            
            // 使用专门的方法获取观看历史
            val items = SupabaseCacheManager.getWatchHistory(context)
            
            _watchHistoryItems.clear()
            _watchHistoryItems.addAll(items)
                    Log.d(TAG, "从本地加载了 ${items.size} 条观看历史记录")
                    
                    // 记录最近10条记录的细节信息
                    val recentItems = items.take(10)
                    recentItems.forEachIndexed { index, item ->
                        Log.d(TAG, "本地记录 #${index+1}: 频道=${item.channelName}, 时长=${item.duration}秒, ID=${item.id}")
                    }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "加载本地观看历史失败", e)
            _watchHistoryItems.clear()
            false
        }
    }
    
    /**
     * 保存观看历史数据到本地存储
     */
    private fun saveToLocal(context: Context) {
        try {
            Log.d(TAG, "保存观看历史数据到本地存储, 条数: ${_watchHistoryItems.size}")
            
            // 使用runBlocking调用协程版本
            kotlinx.coroutines.runBlocking {
                saveToLocalAsync(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存观看历史到本地失败", e)
        }
    }
    
    /**
     * 保存观看历史数据到本地存储（协程版本）
     */
    private suspend fun saveToLocalAsync(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "保存观看历史数据到本地存储, 条数: ${_watchHistoryItems.size}")
                
            // 使用专门的方法保存观看历史
            SupabaseCacheManager.saveWatchHistory(context, _watchHistoryItems)
            
            Log.d(TAG, "观看历史保存成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存观看历史到本地失败", e)
            false
        }
    }
    
    /**
     * 更新观看统计数据
     */
    private fun updateStatistics() {
        try {
            // 计算总观看时长
            val totalWatchTime = _watchHistoryItems.sumOf { it.duration }

            // 计算观看的频道数量
            val channelSet = _watchHistoryItems.map { it.channelName }.toSet()
            val totalChannels = channelSet.size

            // 计算总观看次数
            val totalWatches = _watchHistoryItems.size

            // 计算每个频道的统计数据
            val channelWatchTime = mutableMapOf<String, Long>()
            val channelWatchCount = mutableMapOf<String, Int>()

            _watchHistoryItems.forEach { item ->
                val currentTime = channelWatchTime.getOrDefault(item.channelName, 0L)
                val currentCount = channelWatchCount.getOrDefault(item.channelName, 0)

                channelWatchTime[item.channelName] = currentTime + item.duration
                channelWatchCount[item.channelName] = currentCount + 1
            }

            // 找出观看时长最长的频道
            val mostWatchedEntry = channelWatchTime.entries.maxByOrNull { it.value }
            val mostWatchedChannel = mostWatchedEntry?.key ?: ""
            val mostWatchedTime = mostWatchedEntry?.value ?: 0L

            // 创建频道统计列表，按观看时长降序排列
            val channelStatistics = channelWatchTime.entries.map { (channelName, totalDuration) ->
                ChannelStatistic(
                    channelName = channelName,
                    watchCount = channelWatchCount[channelName] ?: 0,
                    totalDuration = totalDuration
                )
            }.sortedByDescending { it.totalDuration }

            // 更新统计数据
            _watchStatistics.postValue(
                SupabaseWatchStatistics(
                    totalWatchTime = totalWatchTime,
                    totalChannels = totalChannels,
                    totalWatches = totalWatches,
                    mostWatchedChannel = mostWatchedChannel,
                    mostWatchedTime = mostWatchedTime,
                    channelStatistics = channelStatistics
                )
            )

            Log.d(TAG, "更新观看统计: 总时长=${totalWatchTime}秒, 频道数=${totalChannels}, 观看次数=${totalWatches}, 最多观看=${mostWatchedChannel}(${mostWatchedTime}秒), 频道统计数=${channelStatistics.size}")

            // 记录前几个频道的统计信息
            channelStatistics.take(5).forEachIndexed { index, stat ->
                Log.d(TAG, "频道统计 #${index+1}: ${stat.channelName} - 观看${stat.watchCount}次, 总时长${stat.totalDuration}秒")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新观看统计失败", e)
        }
    }
    
    /**
     * 添加观看历史记录
     * @param context 应用上下文
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @param duration 观看时长（秒）
     * @return 添加的记录
     */
    fun addWatchHistory(
        context: Context,
        channelName: String,
        channelUrl: String,
        duration: Long
    ): SupabaseWatchHistoryItem {
        Log.d(TAG, "添加观看历史: 频道=$channelName, URL=${channelUrl.take(30)}..., 时长=${duration}秒")
        
        // 使用北京时间创建记录，便于理解和调试
        val beijingZone = java.time.ZoneId.of("Asia/Shanghai")
        val now = java.time.ZonedDateTime.now(beijingZone)
        val startTime = now.minusSeconds(duration)

        val nowString = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val startTimeString = startTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        // 创建新记录
        val newItem = SupabaseWatchHistoryItem(
            id = UUID.randomUUID().toString(),
            channelName = channelName,
            channelUrl = channelUrl,
            duration = duration,
            watchStart = startTimeString,
            watchEnd = nowString,
            userId = currentUserId
        )
        
        // 添加到内存列表
        _watchHistoryItems.add(0, newItem)
        
        // 保存到本地
        saveToLocal(context)
        
        // 更新统计数据
        updateStatistics()
        
        // 标记有待同步的数据
        hasPendingChanges = true
        
        return newItem
    }
    
    /**
     * 添加观看历史记录（协程版本）
     * @param context 应用上下文
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @param duration 观看时长（秒）
     * @return 添加的记录
     */
    suspend fun addWatchHistoryAsync(
        context: Context,
        channelName: String, 
        channelUrl: String, 
        duration: Long
    ): SupabaseWatchHistoryItem = withContext(Dispatchers.IO) {
        Log.d(TAG, "添加观看历史（协程版本）: 频道=$channelName, URL=${channelUrl.take(30)}..., 时长=${duration}秒")
        
        // 使用北京时间创建记录，便于理解和调试
        val beijingZone = java.time.ZoneId.of("Asia/Shanghai")
        val now = java.time.ZonedDateTime.now(beijingZone)
        val startTime = now.minusSeconds(duration)

        val nowString = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val startTimeString = startTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        // 创建新记录
        val newItem = SupabaseWatchHistoryItem(
            id = UUID.randomUUID().toString(),
            channelName = channelName,
            channelUrl = channelUrl,
            duration = duration,
            watchStart = startTimeString,
            watchEnd = nowString,
            userId = currentUserId
        )
        
        // 添加到内存列表
        _watchHistoryItems.add(0, newItem)
        
        // 保存到本地
        saveToLocalAsync(context)
        
        // 更新统计数据
        updateStatistics()
        
        // 标记有待同步的数据
        hasPendingChanges = true
        
        return@withContext newItem
    }
    
    /**
     * 获取观看历史记录列表
     * @param timeRange 时间范围过滤
     * @param sortBy 排序方式
     * @param page 页码
     * @param pageSize 每页记录数
     * @return 观看历史记录列表、统计数据和分页信息
     */
    fun getWatchHistory(
        timeRange: String = "全部",
        sortBy: String = "时间",
        page: Int = 1,
        pageSize: Int = 20
    ): Triple<List<SupabaseWatchHistoryItem>, SupabaseWatchStatistics, SupabaseWatchHistoryPagination> {
        Log.d(TAG, "获取观看历史: 时间范围=$timeRange, 排序=$sortBy, 页码=$page, 每页=$pageSize")
        
        // 根据时间范围过滤
        val filteredItems = filterByTimeRange(_watchHistoryItems, timeRange)
        
        // 根据排序方式排序
        val sortedItems = sortItems(filteredItems, sortBy)
        
        // 分页
        val totalItems = sortedItems.size
        val totalPages = (totalItems + pageSize - 1) / pageSize
        val startIndex = (page - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, totalItems)
        
        val pagedItems = if (startIndex < totalItems) {
            sortedItems.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
        
        val pagination = SupabaseWatchHistoryPagination(
            currentPage = page,
            totalPages = totalPages,
            pageSize = pageSize,
            totalItems = totalItems
        )
        
        val statistics = _watchStatistics.value ?: SupabaseWatchStatistics()
        
        Log.d(TAG, "返回观看历史: 过滤后=${filteredItems.size}, 分页后=${pagedItems.size}, 总页数=${pagination.totalPages}")
        
        return Triple(pagedItems, statistics, pagination)
    }
    
    /**
     * 根据时间范围过滤观看历史记录
     */
    private fun filterByTimeRange(items: List<SupabaseWatchHistoryItem>, timeRange: String): List<SupabaseWatchHistoryItem> {
        if (timeRange == "全部") {
            return items
        }
        
        val now = java.time.Instant.now()
        val startInstant = when (timeRange) {
            "今天" -> {
                val today = java.time.LocalDate.now()
                today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            }
            "本周" -> {
                val today = java.time.LocalDate.now()
                val startOfWeek = today.minusDays(today.dayOfWeek.value - 1L)
                startOfWeek.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            }
            "本月" -> {
                val today = java.time.LocalDate.now()
                val startOfMonth = today.withDayOfMonth(1)
                startOfMonth.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            }
            "今年" -> {
                val today = java.time.LocalDate.now()
                val startOfYear = today.withDayOfYear(1)
                startOfYear.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            }
            else -> {
                return items
            }
        }
        
        return items.filter { item ->
            try {
                val watchStartInstant = java.time.Instant.parse(item.watchStart)
                watchStartInstant.isAfter(startInstant) || watchStartInstant.equals(startInstant)
            } catch (e: Exception) {
                Log.e(TAG, "解析时间失败: ${item.watchStart}", e)
                false
            }
        }
    }
    
    /**
     * 根据排序方式排序观看历史记录
     */
    private fun sortItems(items: List<SupabaseWatchHistoryItem>, sortBy: String): List<SupabaseWatchHistoryItem> {
        return when (sortBy) {
            "时间" -> items.sortedByDescending { it.watchStart }
            "频道名" -> items.sortedBy { it.channelName }
            "观看时长" -> items.sortedByDescending { it.duration }
            else -> items.sortedByDescending { it.watchStart }
        }
    }
    
    /**
     * 清空观看历史
     * @param context 应用上下文
     */
    fun clearHistory(context: Context) {
        Log.d(TAG, "清空观看历史")
        
        _watchHistoryItems.clear()
        updateStatistics()
        
        // 保存空列表到本地
        saveToLocal(context)
    }
    
    /**
     * 清空观看历史（协程版本）
     * @param context 应用上下文
     */
    suspend fun clearHistoryAsync(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "清空观看历史（协程版本）")
        
        _watchHistoryItems.clear()
        updateStatistics()
        
        // 保存空列表到本地
        saveToLocalAsync(context)
    }
    
    /**
     * 检查是否有待同步的数据
     * @return 如果有待同步的数据则返回true
     */
    fun hasPendingChanges(): Boolean {
        return hasPendingChanges
    }
    
    /**
     * 重置待同步标记
     */
    fun resetPendingChanges() {
        hasPendingChanges = false
    }

    /**
     * 获取当前内存中的观看历史记录列表
     * @return 观看历史记录列表的副本
     */
    fun getAllWatchHistoryItems(): List<SupabaseWatchHistoryItem> {
        return _watchHistoryItems.toList()
    }
} 