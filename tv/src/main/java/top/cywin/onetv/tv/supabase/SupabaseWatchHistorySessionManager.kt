package top.cywin.onetv.tv.supabase

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.tv.supabase.sync.SupabaseWatchHistorySyncService
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking

/**
 * 观看历史会话管理器
 * 负责在本地管理和缓存观看历史记录，并在适当的时候与服务器同步
 * 完全使用统一缓存管理器实现存储
 * 注意：服务器同步功能已移至专用模块 {@link SupabaseWatchHistorySyncService}
 */
object SupabaseWatchHistorySessionManager {
    private const val TAG = "WatchHistoryManager"
    
    // 本地缓存的观看历史数据
    private val watchHistoryItems = CopyOnWriteArrayList<WatchHistoryItem>()
    
    // 观看统计数据
    private val _watchStatistics = MutableLiveData<WatchStatistics>()
    val watchStatistics: LiveData<WatchStatistics> = _watchStatistics
    
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
                runBlocking { 
                    SupabaseSessionManager.getCachedUserData(context)?.userid
                }
        } catch (e: Exception) {
            Log.e(TAG, "获取用户ID失败", e)
            null
        }
        
        Log.d(TAG, "当前用户ID: $userId")
            currentUserId = userId
            
            // 使用超时保护
            val loadJob = runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(5000) { // 5秒超时
                    try {
                        loadFromLocalAsync(context)
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "加载本地观看历史失败", e)
                        watchHistoryItems.clear()
                        false
                    }
                }
            }
            
            if (loadJob == null) {
                Log.e(TAG, "加载观看历史超时，已强制终止")
                watchHistoryItems.clear()
            }
            
            updateStatistics()
            Log.d(TAG, "观看历史管理器初始化完成, 加载了 ${watchHistoryItems.size} 条记录")
        } catch (e: Exception) {
            Log.e(TAG, "观看历史管理器初始化失败", e)
            watchHistoryItems.clear()
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
                    watchHistoryItems.clear()
                    false
                }
            }
            
            if (loadSuccess == null) {
                Log.e(TAG, "加载观看历史超时，已强制终止")
                watchHistoryItems.clear()
            }
            
        updateStatistics()
        Log.d(TAG, "观看历史管理器初始化完成, 加载了 ${watchHistoryItems.size} 条记录")
        } catch (e: Exception) {
            Log.e(TAG, "观看历史管理器初始化失败", e)
            watchHistoryItems.clear()
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
            runBlocking {
                loadFromLocalAsync(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载本地观看历史失败", e)
            watchHistoryItems.clear()
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
            
            // 使用统一缓存管理器获取数据，指定类型为String
            val historyJson = SupabaseCacheManager.getCache<String>(
                context, 
                SupabaseCacheKey.WATCH_HISTORY
            )
            
            if (!historyJson.isNullOrBlank()) {
                try {
                    val items = json.decodeFromString<List<WatchHistoryItem>>(historyJson)
                    watchHistoryItems.clear()
                    watchHistoryItems.addAll(items)
                    Log.d(TAG, "从本地加载了 ${items.size} 条观看历史记录")
                    
                    // 记录最近10条记录的细节信息
                    val recentItems = items.take(10)
                    recentItems.forEachIndexed { index, item ->
                        Log.d(TAG, "本地记录 #${index+1}: 频道=${item.channelName}, 时长=${item.duration}秒, ID=${item.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析本地观看历史JSON失败", e)
                    watchHistoryItems.clear()
                }
            } else {
                Log.d(TAG, "本地没有观看历史记录")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载本地观看历史失败", e)
            watchHistoryItems.clear()
        }
    }
    
    /**
     * 保存观看历史数据到本地存储
     */
    private fun saveToLocal(context: Context) {
        try {
            Log.d(TAG, "保存观看历史数据到本地存储, 条数: ${watchHistoryItems.size}")
            
            // 使用runBlocking调用协程版本
            runBlocking {
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
            Log.d(TAG, "保存观看历史数据到本地存储（协程版本）, 条数: ${watchHistoryItems.size}")
            
            try {
                val historyJson = json.encodeToString(watchHistoryItems.toList())
                Log.d(TAG, "JSON序列化成功, 长度: ${historyJson.length}, 第一条记录ID: ${if (watchHistoryItems.isNotEmpty()) watchHistoryItems[0].id else "无"}")
                
                // 使用统一缓存管理器保存数据
                SupabaseCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY, historyJson)
                
                // 保存最后同步时间
                SupabaseCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY_LAST_LOADED, System.currentTimeMillis())
                
                Log.d(TAG, "已保存 ${watchHistoryItems.size} 条观看历史记录到本地")
            } catch (e: Exception) {
                Log.e(TAG, "序列化观看历史数据失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存观看历史到本地失败", e)
        }
    }
    
    /**
     * 记录频道观看历史
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @param duration 观看时长（秒）
     * @param context 应用上下文
     */
    fun recordChannelWatch(channelName: String, channelUrl: String, duration: Long, context: Context) {
        Log.d(TAG, "开始记录频道观看历史: 频道=$channelName, URL=${channelUrl.take(50)}${if(channelUrl.length > 50) "..." else ""}, 时长=$duration 秒")
        
        // 严格检查参数有效性
        if (channelName.isBlank()) {
            Log.e(TAG, "记录观看历史失败: 频道名为空")
            return
        }
        
        if (channelUrl.isBlank()) {
            Log.e(TAG, "记录观看历史失败: URL为空")
            return
        }
        
        if (duration <= 0) {
            Log.e(TAG, "记录观看历史失败: 无效时长 $duration")
            return
        }
        
        // 创建观看历史项目
        val now = Date()
        val watchStart = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date(now.time - duration * 1000))
        
        val watchEnd = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(now)
        
        val historyItem = WatchHistoryItem(
            id = UUID.randomUUID().toString(), // 使用UUID创建临时ID，服务器同步后会被替换
            channelName = channelName,
            channelUrl = channelUrl,
            duration = duration,
            watchStart = watchStart,
            watchEnd = watchEnd
        )
        
        // 添加到本地列表
        watchHistoryItems.add(0, historyItem) // 添加到列表开头，保持最新记录在前
        Log.d(TAG, "添加新观看记录: ID=${historyItem.id}, 频道=${historyItem.channelName}, 时长=${historyItem.duration}秒")
        
        // 如果记录超过1000条，删除旧记录
        if (watchHistoryItems.size > 1000) {
            val removed = watchHistoryItems.removeAt(watchHistoryItems.size - 1)
            Log.d(TAG, "记录超过1000条，删除最旧记录: ${removed.channelName}")
        }
        
        // 标记有待同步的更改
        hasPendingChanges = true
        
        // 保存到本地
        saveToLocal(context)
        
        // 更新统计数据
        updateStatistics()
        
        Log.d(TAG, "观看记录已保存，当前总记录数: ${watchHistoryItems.size}")
    }
    
    /**
     * 记录频道观看历史（协程版本）
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @param duration 观看时长（秒）
     * @param context 应用上下文
     */
    suspend fun recordChannelWatchAsync(
        channelName: String, 
        channelUrl: String, 
        duration: Long, 
        context: Context
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始记录频道观看历史（协程版本）: 频道=$channelName, URL=${channelUrl.take(50)}${if(channelUrl.length > 50) "..." else ""}, 时长=$duration 秒")
        
        // 严格检查参数有效性
        if (channelName.isBlank()) {
            Log.e(TAG, "记录观看历史失败: 频道名为空")
            return@withContext
        }
        
        if (channelUrl.isBlank()) {
            Log.e(TAG, "记录观看历史失败: URL为空")
            return@withContext
        }
        
        if (duration <= 0) {
            Log.e(TAG, "记录观看历史失败: 无效时长 $duration")
            return@withContext
        }
        
        // 创建观看历史项目
        val now = Date()
        val watchStart = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date(now.time - duration * 1000))
        
        val watchEnd = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(now)
        
        val historyItem = WatchHistoryItem(
            id = UUID.randomUUID().toString(), // 使用UUID创建临时ID，服务器同步后会被替换
            channelName = channelName,
            channelUrl = channelUrl,
            duration = duration,
            watchStart = watchStart,
            watchEnd = watchEnd
        )
        
        // 添加到本地列表
        watchHistoryItems.add(0, historyItem) // 添加到列表开头，保持最新记录在前
        Log.d(TAG, "添加新观看记录: ID=${historyItem.id}, 频道=${historyItem.channelName}, 时长=${historyItem.duration}秒")
        
        // 如果记录超过1000条，删除旧记录
        if (watchHistoryItems.size > 1000) {
            val removed = watchHistoryItems.removeAt(watchHistoryItems.size - 1)
            Log.d(TAG, "记录超过1000条，删除最旧记录: ${removed.channelName}")
        }
        
        // 标记有待同步的更改
        hasPendingChanges = true
        
        // 保存到本地
        saveToLocalAsync(context)
        
        // 更新统计数据
        updateStatistics()
        
        Log.d(TAG, "观看记录已保存，当前总记录数: ${watchHistoryItems.size}")
    }
    
    /**
     * 清除所有观看历史记录
     */
    fun clearHistory(context: Context) {
        Log.d(TAG, "清除所有观看历史记录，当前记录数: ${watchHistoryItems.size}")
        watchHistoryItems.clear()
        saveToLocal(context)
        updateStatistics()
        Log.d(TAG, "所有观看历史记录已清除")
    }
    
    /**
     * 清除所有观看历史记录（协程版本）
     */
    suspend fun clearHistoryAsync(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "清除所有观看历史记录（协程版本），当前记录数: ${watchHistoryItems.size}")
        watchHistoryItems.clear()
        
        // 清除缓存
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.WATCH_HISTORY)
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.WATCH_HISTORY_LAST_LOADED)
        
        updateStatistics()
        Log.d(TAG, "所有观看历史记录已清除")
    }
    
    /**
     * 更新观看统计数据
     */
    private fun updateStatistics() {
        Log.d(TAG, "更新观看统计数据, 记录数: ${watchHistoryItems.size}")
        
        if (watchHistoryItems.isEmpty()) {
            _watchStatistics.postValue(
                WatchStatistics(
                    totalWatchTime = 0,
                    totalChannels = 0,
                    totalWatches = 0,
                    mostWatchedChannel = null,
                    channelStatistics = emptyList()
                )
            )
            Log.d(TAG, "无观看记录, 统计数据清零")
            return
        }
        
        try {
            // 计算统计数据
            val totalWatchTime = watchHistoryItems.sumOf { it.duration }
            
            // 计算不同频道数
            val uniqueChannels = watchHistoryItems.map { it.channelName }.toSet()
            
            // 按频道分组并计算统计数据
            // 观看次数直接使用记录条数，因为每条记录代表一次完整的观看
            // 每次打开频道观看5秒以上，到切换频道或关闭应用，计为一次观看
            val channelStats = watchHistoryItems
                .groupBy { it.channelName }
                .map { (channelName, items) ->
                    ChannelStatistic(
                        channelName = channelName,
                        watchCount = items.size, // 每条记录代表一次观看
                        totalDuration = items.sumOf { it.duration }
                    )
                }
                .sortedByDescending { it.totalDuration }
                .take(50) // 最多显示50个频道的统计
            
            // 找出最常观看的频道
            val mostWatchedEntry = channelStats.firstOrNull()?.channelName
            
            val statistics = WatchStatistics(
                totalWatchTime = totalWatchTime,
                totalChannels = uniqueChannels.size,
                totalWatches = watchHistoryItems.size,
                mostWatchedChannel = mostWatchedEntry,
                channelStatistics = channelStats
            )
            
            _watchStatistics.postValue(statistics)
            
            Log.d(TAG, "已更新观看统计: 总时长=${totalWatchTime}秒, 频道数=${uniqueChannels.size}, 观看次数=${watchHistoryItems.size}, 最常观看=${mostWatchedEntry ?: "无"}, 频道统计=${channelStats.size}项")
        } catch (e: Exception) {
            Log.e(TAG, "更新统计数据失败", e)
            // 出错时提供默认统计
            _watchStatistics.postValue(
                WatchStatistics(
                    totalWatchTime = watchHistoryItems.sumOf { it.duration },
                    totalChannels = watchHistoryItems.distinctBy { it.channelName }.size,
                    totalWatches = watchHistoryItems.size,
                    mostWatchedChannel = null,
                    channelStatistics = emptyList()
                )
            )
        }
    }
    
    /**
     * 获取观看历史记录（带筛选和排序）
     */
    fun getWatchHistory(
        timeRange: String = "全部",
        sortBy: String = "时间",
        page: Int = 1,
        pageSize: Int = 20
    ): Triple<List<WatchHistoryItem>, WatchStatistics, PaginationData> {
        Log.d(TAG, "获取观看历史: 时间范围=$timeRange, 排序=$sortBy, 页码=$page, 每页=$pageSize")
        
        // 检查初始数据
        Log.d(TAG, "初始数据条数: ${watchHistoryItems.size}")
        if (watchHistoryItems.isNotEmpty()) {
            val sample = watchHistoryItems.first()
            Log.d(TAG, "数据样本: ID=${sample.id}, 频道=${sample.channelName}, 时长=${sample.duration}秒, 开始=${sample.watchStart}")
        }
        
        // 应用时间筛选
        val filteredItems = filterByTimeRange(watchHistoryItems, timeRange)
        Log.d(TAG, "时间筛选后记录数: ${filteredItems.size}")
        
        // 应用排序
        val sortedItems = sortItems(filteredItems, sortBy)
        Log.d(TAG, "排序后记录数: ${sortedItems.size}")
        
        // 应用分页
        val totalItems = sortedItems.size
        val totalPages = (totalItems + pageSize - 1) / pageSize
        val startIndex = (page - 1) * pageSize
        val endIndex = minOf(startIndex + pageSize, totalItems)
        
        val pagedItems = if (startIndex < totalItems) {
            sortedItems.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
        
        // 记录分页后的结果
        Log.d(TAG, "分页后记录数: ${pagedItems.size}, 起始索引=$startIndex, 结束索引=$endIndex")
        
        // 记录前几条记录的详情
        pagedItems.take(5).forEachIndexed { index, item ->
            Log.d(TAG, "返回记录 #${index+1}: ID=${item.id}, 频道=${item.channelName}, 时长=${item.duration}秒, 开始=${item.watchStart}")
        }
        
        // 计算分页数据
        val paginationData = PaginationData(
            page = page,
            pageSize = pageSize,
            totalItems = totalItems,
            totalPages = totalPages
        )
        
        Log.d(TAG, "获取观看历史结果: 总条数=$totalItems, 总页数=$totalPages, 当前页记录数=${pagedItems.size}")
        
        return Triple(pagedItems, _watchStatistics.value ?: calculateStatistics(filteredItems), paginationData)
    }
    
    /**
     * 根据时间范围筛选观看历史
     */
    private fun filterByTimeRange(items: List<WatchHistoryItem>, timeRange: String): List<WatchHistoryItem> {
        if (timeRange == "全部") {
            Log.d(TAG, "时间范围为'全部'，返回所有 ${items.size} 条记录")
            return items
        }
        
        Log.d(TAG, "按时间范围筛选: $timeRange, 原始记录数: ${items.size}")
        
        val now = Date()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = now
        
        val startTime = when (timeRange) {
            "今天" -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.time
            }
            "本周" -> {
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.time
            }
            "本月" -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.time
            }
            "今年" -> {
                calendar.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.time
            }
            else -> {
                Log.d(TAG, "未知的时间范围: $timeRange，返回所有记录")
                return items
            }
        }
        
        Log.d(TAG, "时间范围筛选: 范围=$timeRange, 起始时间=${formatDateTime(startTime)}, 时间戳=${startTime.time}")
        
        // 记录前几条记录的日期解析结果
        items.take(5).forEachIndexed { index, item ->
            try {
                val itemDate = parseDateTime(item.watchStart)
                val isAfter = itemDate.after(startTime)
                Log.d(TAG, "记录 #${index+1} 日期比较: ${item.watchStart} -> ${itemDate.time}, 是否在范围内: $isAfter")
            } catch (e: Exception) {
                Log.e(TAG, "记录 #${index+1} 日期比较失败: ${item.watchStart}", e)
            }
        }
        
        val filteredItems = items.filter { 
            try {
                val itemDate = parseDateTime(it.watchStart)
                val isAfter = itemDate.after(startTime)
                isAfter
            } catch (e: Exception) {
                Log.e(TAG, "解析日期失败: ${it.watchStart}", e)
                false
            }
        }
        
        Log.d(TAG, "时间筛选结果: 从${items.size}条记录筛选出${filteredItems.size}条记录")
        return filteredItems
    }
    
    /**
     * 排序观看历史记录
     */
    private fun sortItems(items: List<WatchHistoryItem>, sortBy: String): List<WatchHistoryItem> {
        Log.d(TAG, "排序观看历史: 方式=$sortBy, 记录数=${items.size}")
        
        val sortedItems = when (sortBy) {
            "频道名" -> items.sortedBy { it.channelName }
            "观看时长" -> items.sortedByDescending { it.duration }
            else -> items.sortedByDescending { parseDateTime(it.watchStart) } // 默认按时间排序
        }
        
        Log.d(TAG, "排序完成: ${sortBy}")
        return sortedItems
    }
    
    /**
     * 计算观看统计数据
     */
    private fun calculateStatistics(items: List<WatchHistoryItem>): WatchStatistics {
        Log.d(TAG, "计算观看统计数据, 记录数: ${items.size}")
        
        if (items.isEmpty()) {
            return WatchStatistics(
                totalWatchTime = 0,
                totalChannels = 0,
                totalWatches = 0,
                mostWatchedChannel = null,
                channelStatistics = emptyList()
            )
        }
        
        val totalWatchTime = items.sumOf { it.duration }
        val uniqueChannels = items.map { it.channelName }.toSet()
        
        // 按频道分组并计算统计数据
        // 观看次数直接使用记录条数，因为每条记录代表一次完整的观看
        // 每次打开频道观看5秒以上，到切换频道或关闭应用，计为一次观看
        val channelStats = items
            .groupBy { it.channelName }
            .map { (channelName, channelItems) ->
                ChannelStatistic(
                    channelName = channelName,
                    watchCount = channelItems.size, // 每条记录代表一次观看
                    totalDuration = channelItems.sumOf { it.duration }
                )
            }
            .sortedByDescending { it.totalDuration }
            .take(50) // 最多显示50个频道的统计
        
        val mostWatchedEntry = channelStats.firstOrNull()?.channelName
        
        val statistics = WatchStatistics(
            totalWatchTime = totalWatchTime,
            totalChannels = uniqueChannels.size,
            totalWatches = items.size,
            mostWatchedChannel = mostWatchedEntry,
            channelStatistics = channelStats
        )
        
        Log.d(TAG, "统计结果: 总时长=${totalWatchTime}秒, 频道数=${uniqueChannels.size}, 观看次数=${items.size}, 最常观看=${mostWatchedEntry ?: "无"}, 频道统计=${channelStats.size}项")
        
        return statistics
    }
    
    /**
     * 解析日期时间字符串
     */
    private fun parseDateTime(dateTimeStr: String): Date {
        if (dateTimeStr.isNullOrBlank()) {
            Log.e(TAG, "日期字符串为空")
            return Date()
        }
        
        // 尝试多种可能的日期格式
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )
        
        for (formatPattern in formats) {
            try {
                val format = java.text.SimpleDateFormat(formatPattern, java.util.Locale.getDefault())
                if (formatPattern.endsWith("'Z'")) {
                    format.timeZone = TimeZone.getTimeZone("UTC")
                }
                
            val parsedDate = format.parse(dateTimeStr)
                if (parsedDate != null) {
                    Log.d(TAG, "成功解析日期: $dateTimeStr -> ${parsedDate.time} (使用格式: $formatPattern)")
                    return parsedDate
                }
            } catch (e: Exception) {
                // 此格式解析失败，尝试下一个
                continue
            }
        }
        
        // 所有格式都解析失败，记录错误并返回当前时间
        Log.e(TAG, "所有日期格式解析失败: $dateTimeStr")
        return Date()
    }
    
    /**
     * 格式化日期对象为字符串
     */
    private fun formatDateTime(date: Date): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * 格式化ISO日期时间字符串为本地格式
     * 将ISO格式的日期时间转换为本地格式
     */
    private fun formatIsoDateTime(isoDateTime: String): String {
        try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            
            val date = inputFormat.parse(isoDateTime.substring(0, 19))
            return outputFormat.format(date ?: java.util.Date())
        } catch (e: Exception) {
            Log.e(TAG, "格式化ISO日期时间失败: $isoDateTime", e)
            return isoDateTime
        }
    }
} 