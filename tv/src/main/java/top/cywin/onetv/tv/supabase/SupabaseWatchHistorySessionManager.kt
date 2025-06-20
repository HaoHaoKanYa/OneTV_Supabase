package top.cywin.onetv.tv.supabase

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
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
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 观看历史会话管理器
 * 负责在本地管理和缓存观看历史记录，并在适当的时候与服务器同步
 */
object SupabaseWatchHistorySessionManager {
    private const val TAG = "SupabaseWatchHistorySessionManager"
    private const val PREF_NAME = "watch_history_prefs"
    private const val KEY_WATCH_HISTORY = "watch_history_data"
    private const val KEY_LAST_SYNC = "last_sync_time"
    private const val KEY_USER_ID = "user_id"
    
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
    @SuppressLint("LongLogTag")
    fun initialize(context: Context) {
        Log.d(TAG, "初始化观看历史管理器")
        
        // 获取当前用户ID
        val userId = try {
            // 使用SupabaseSessionManager获取用户ID
            val userData = SupabaseSessionManager.getCachedUserData(context)
            userData?.userid
        } catch (e: Exception) {
            Log.e(TAG, "获取用户ID失败", e)
            null
        }
        
        Log.d(TAG, "当前用户ID: $userId")
        
        // 如果用户ID有变化，需要重新加载数据
        val prefs = getPreferences(context)
        val savedUserId = prefs.getString(KEY_USER_ID, null)
        
        if (userId != null) {
            if (userId != savedUserId) {
                Log.d(TAG, "用户ID变更: $savedUserId -> $userId, 重新加载数据")
                // 保存新的用户ID
                prefs.edit().putString(KEY_USER_ID, userId).apply()
                currentUserId = userId
            } else {
                Log.d(TAG, "用户ID未变更: $userId")
                currentUserId = userId
            }
        } else {
            Log.d(TAG, "未获取到用户ID，使用本地数据")
        }
        
        loadFromLocal(context)
        updateStatistics()
        Log.d(TAG, "观看历史管理器初始化完成, 加载了 ${watchHistoryItems.size} 条记录")
    }
    
    /**
     * 从本地存储加载观看历史数据
     */
    private fun loadFromLocal(context: Context) {
        try {
            Log.d(TAG, "从本地存储加载观看历史数据")
            val prefs = getPreferences(context)
            val historyJson = prefs.getString(KEY_WATCH_HISTORY, null)
            
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
            val prefs = getPreferences(context)
            
            try {
                val historyJson = json.encodeToString(watchHistoryItems.toList())
                Log.d(TAG, "JSON序列化成功, 长度: ${historyJson.length}, 第一条记录ID: ${if (watchHistoryItems.isNotEmpty()) watchHistoryItems[0].id else "无"}")
                
                prefs.edit()
                    .putString(KEY_WATCH_HISTORY, historyJson)
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .apply()
                
                Log.d(TAG, "已保存 ${watchHistoryItems.size} 条观看历史记录到本地")
            } catch (e: Exception) {
                Log.e(TAG, "序列化观看历史数据失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存观看历史到本地失败", e)
        }
    }
    
    /**
     * 获取SharedPreferences实例
     * 使用用户ID作为文件名的一部分，确保不同用户的数据隔离
     */
    private fun getPreferences(context: Context): SharedPreferences {
        val prefName = if (currentUserId != null) {
            "${PREF_NAME}_${currentUserId}"
        } else {
            PREF_NAME
        }
        return context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }
    
    /**
     * 记录频道观看历史
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @param duration 观看时长（秒）
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
        
        // 如果当前用户ID为空，尝试重新获取
        if (currentUserId == null) {
            try {
                val userData = SupabaseSessionManager.getCachedUserData(context)
                currentUserId = userData?.userid
                
                // 如果获取到了用户ID，更新本地存储
                if (currentUserId != null) {
                    val prefs = getPreferences(context)
                    prefs.edit().putString(KEY_USER_ID, currentUserId).apply()
                    Log.d(TAG, "已更新用户ID: $currentUserId")
                } else {
                    Log.w(TAG, "无法获取用户ID，将使用匿名模式记录")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取用户ID失败", e)
            }
        }
        
        val now = Date()
        val startTime = Date(now.time - duration * 1000)
        
        val historyItem = WatchHistoryItem(
            id = java.util.UUID.randomUUID().toString(),
            channelName = channelName,
            channelUrl = channelUrl,
            watchStart = formatDateTime(startTime),
            watchEnd = formatDateTime(now),
            duration = duration
        )
        
        Log.d(TAG, "创建观看历史记录: ID=${historyItem.id}, 开始=${historyItem.watchStart}, 结束=${historyItem.watchEnd}, 时长=${historyItem.duration}秒, 用户ID=$currentUserId")
        
        // 添加到本地缓存
        watchHistoryItems.add(0, historyItem) // 添加到列表开头
        Log.d(TAG, "添加到本地缓存, 当前缓存记录数: ${watchHistoryItems.size}")
        
        // 更新统计数据
        updateStatistics()
        
        // 保存到本地
        saveToLocal(context)
        
        // 标记为有待同步的数据
        hasPendingChanges = true
        
        Log.d(TAG, "完成记录观看历史: $channelName, 时长: $duration 秒, 待同步状态: $hasPendingChanges")
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
        
        // 应用时间筛选
        val filteredItems = filterByTimeRange(watchHistoryItems, timeRange)
        Log.d(TAG, "时间筛选后记录数: ${filteredItems.size}")
        
        // 应用排序
        val sortedItems = sortItems(filteredItems, sortBy)
        
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
        if (timeRange == "全部") return items
        
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
            else -> return items
        }
        
        Log.d(TAG, "时间范围筛选: 范围=$timeRange, 起始时间=${formatDateTime(startTime)}")
        
        val filteredItems = items.filter { 
            try {
                parseDateTime(it.watchStart).after(startTime)
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
     * 将本地缓存的观看历史同步到服务器
     * @return 同步成功的记录数
     */
    suspend fun syncToServer(context: Context): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始同步观看历史到服务器, 待同步状态: $hasPendingChanges, 本地记录数: ${watchHistoryItems.size}")
        
        if (!hasPendingChanges) {
            Log.d(TAG, "没有需要同步的数据, 跳过同步")
            return@withContext 0
        }
        
        if (watchHistoryItems.isEmpty()) {
            Log.d(TAG, "本地记录为空, 跳过同步")
            return@withContext 0
        }
        
        if (currentUserId == null) {
            Log.e(TAG, "同步失败: 未获取到用户ID")
            return@withContext 0
        }
        
        val apiClient = SupabaseApiClient()
        var successCount = 0
        
        try {
            // 复制一份列表，避免并发修改
            val itemsToSync = ArrayList(watchHistoryItems)
            Log.d(TAG, "待同步记录总数: ${itemsToSync.size}")
            
            // 筛选出本地生成的ID（包含-的UUID格式）的记录，这些是未同步的记录
            val pendingItems = itemsToSync.filter { it.id.contains("-") }
            Log.d(TAG, "筛选出未同步记录数: ${pendingItems.size}")
            
            if (pendingItems.isEmpty()) {
                Log.d(TAG, "没有待同步的记录, 跳过同步")
                return@withContext 0
            }
            
            // 使用批量上传API
            try {
                // 准备批量上传数据
                val records = pendingItems.map { item ->
                    mapOf(
                        "channelName" to item.channelName,
                        "channelUrl" to item.channelUrl,
                        "duration" to item.duration,
                        "watchStart" to item.watchStart,
                        "watchEnd" to item.watchEnd,
                        "user_id" to currentUserId
                    )
                }
                
                Log.d(TAG, "准备批量上传 ${records.size} 条记录, 用户ID: $currentUserId")
                
                // 记录前几条记录的内容以便调试
                records.take(3).forEachIndexed { index, record ->
                    Log.d(TAG, "批量上传记录 #${index+1}: 频道=${record["channelName"]}, 时长=${record["duration"]}, 用户ID=${record["user_id"]}")
                }
                
                val response = apiClient.batchRecordWatchHistory(records)
                Log.d(TAG, "批量上传API响应: $response")
                
                // 解析响应
                val jsonObject = response as? JsonObject
                val success = jsonObject?.get("success")?.let {
                    (it as? JsonPrimitive)?.content?.contains("true")
                } ?: false
                
                val data = jsonObject?.get("data") as? JsonObject
                val inserted = data?.get("inserted")?.let {
                    (it as? JsonPrimitive)?.content?.toIntOrNull()
                } ?: 0
                
                if (success && inserted > 0) {
                    successCount = inserted
                    Log.d(TAG, "成功批量同步了 $successCount 条观看记录到服务器")
                    
                    // 更新本地记录状态
                    // 获取服务器返回的记录ID
                    val serverRecords = data?.get("records") as? JsonArray
                    if (serverRecords != null) {
                        Log.d(TAG, "服务器返回了 ${serverRecords.size} 条记录")
                        
                        // 创建一个映射，用于更新本地记录的ID
                        val updatedIds = mutableMapOf<String, String>()
                        
                        // 遍历服务器返回的记录，提取ID
                        for (i in 0 until minOf(serverRecords.size, pendingItems.size)) {
                            val serverRecord = serverRecords[i] as? JsonObject
                            val serverId = serverRecord?.get("id")?.let {
                                (it as? JsonPrimitive)?.content
                            }
                            if (serverId != null) {
                                updatedIds[pendingItems[i].id] = serverId
                                Log.d(TAG, "更新记录ID: ${pendingItems[i].id} -> $serverId")
                            }
                        }
                        
                        // 更新本地记录
                        val updatedCount = watchHistoryItems.count { updatedIds.containsKey(it.id) }
                        Log.d(TAG, "待更新本地ID数: $updatedCount")
                        
                        watchHistoryItems.replaceAll { item ->
                            if (updatedIds.containsKey(item.id)) {
                                Log.d(TAG, "更新记录ID: ${item.id} -> ${updatedIds[item.id]}")
                                item.copy(id = updatedIds[item.id]!!)
                            } else {
                                item
                            }
                        }
                        
                        // 保存更新后的记录到本地
                        saveToLocal(context)
                    } else {
                        Log.w(TAG, "服务器未返回记录数组")
                    }
                    
                    hasPendingChanges = false
                } else {
                    val errorMsg = jsonObject?.get("error")?.let {
                        (it as? JsonPrimitive)?.content
                    } ?: "未知错误"
                    Log.e(TAG, "批量同步失败: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "批量同步过程中发生异常", e)
                
                // 回退到单条记录同步
                Log.d(TAG, "尝试单条记录同步...")
                for (item in pendingItems) {
                    try {
                        Log.d(TAG, "单条同步: 频道=${item.channelName}, 时长=${item.duration}, 用户ID=$currentUserId")
                        
                        val response = apiClient.recordWatchHistory(
                            channelName = item.channelName,
                            channelUrl = item.channelUrl,
                            duration = item.duration
                        )
                        
                        val jsonObject = response as? JsonObject
                        val success = jsonObject?.get("success")?.let {
                            (it as? JsonPrimitive)?.content?.contains("true")
                        } ?: false
                        
                        if (success) {
                            successCount++
                            Log.d(TAG, "单条同步成功: ${item.channelName}")
                        } else {
                            val errorMsg = jsonObject?.get("error")?.let {
                                (it as? JsonPrimitive)?.content
                            } ?: "未知错误"
                            Log.e(TAG, "单条同步失败: ${errorMsg}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "同步观看记录失败: ${item.channelName}", e)
                    }
                }
                
                if (successCount > 0) {
                    Log.d(TAG, "成功同步了 $successCount 条观看记录到服务器")
                    hasPendingChanges = false
                    saveToLocal(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步观看历史到服务器失败", e)
        }
        
        Log.d(TAG, "同步完成, 成功同步记录数: $successCount")
        return@withContext successCount
    }
    
    /**
     * 从服务器加载观看历史记录并合并到本地
     */
    suspend fun syncFromServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始从服务器同步观看历史")
        
        if (currentUserId == null) {
            Log.e(TAG, "同步失败: 未获取到用户ID")
            return@withContext false
        }
        
        try {
            val apiClient = SupabaseApiClient()
            
            // 获取服务器端数据
            Log.d(TAG, "请求服务器观看历史数据, 用户ID: $currentUserId")
            val response = apiClient.getWatchHistory(
                page = 1,
                pageSize = 100, // 获取较多数据
                timeRange = "all",
                sortBy = "watch_start",
                sortOrder = "desc"
            )
            
            // 解析服务器数据
            try {
                val jsonObject = response as? JsonObject
                Log.d(TAG, "服务器响应: $jsonObject")
                
                val itemsArray = jsonObject?.get("items") as? JsonArray
                if (itemsArray == null) {
                    Log.e(TAG, "从服务器获取观看历史失败: 无效响应")
                    return@withContext false
                }
                
                Log.d(TAG, "服务器返回记录数: ${itemsArray.size}")
                val serverItems = mutableListOf<WatchHistoryItem>()
                
                for (jsonElement in itemsArray) {
                    try {
                        val item = jsonElement as? JsonObject ?: continue
                        
                        // 提取字段值并记录日志
                        val id = item["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val channelName = item["channel_name"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val channelUrl = item["channel_url"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val watchStart = item["watch_start"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val watchEnd = item["watch_end"]?.let { (it as? JsonPrimitive)?.content }
                        val duration = item["duration"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: 0
                        
                        Log.d(TAG, "解析服务器记录: ID=$id, 频道=$channelName, 时长=$duration 秒")
                        
                        if (id.isBlank() || channelName.isBlank() || duration <= 0) {
                            Log.w(TAG, "跳过无效记录: ID=$id, 频道=$channelName, 时长=$duration")
                            continue
                        }
                        
                        val historyItem = WatchHistoryItem(
                            id = id,
                            channelName = channelName,
                            channelUrl = channelUrl,
                            watchStart = formatIsoDateTime(watchStart),
                            watchEnd = watchEnd?.let { formatIsoDateTime(it) },
                            duration = duration
                        )
                        
                        serverItems.add(historyItem)
                    } catch (e: Exception) {
                        Log.e(TAG, "解析历史项目失败: ${e.message}")
                        // 跳过此项，继续处理下一项
                    }
                }
                
                Log.d(TAG, "成功解析服务器记录: ${serverItems.size}条")
                
                // 合并本地和服务器数据
                val localIds = watchHistoryItems.map { it.id }.toSet()
                val newItems = serverItems.filter { server -> 
                    !localIds.contains(server.id) 
                }
                
                Log.d(TAG, "过滤出新记录: ${newItems.size}条")
                
                if (newItems.isNotEmpty()) {
                    watchHistoryItems.addAll(newItems)
                    updateStatistics()
                    saveToLocal(context)
                    Log.d(TAG, "从服务器同步了 ${newItems.size} 条新的观看记录")
                } else {
                    Log.d(TAG, "没有新的记录需要合并")
                }
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "解析服务器观看历史数据失败", e)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "从服务器同步观看历史失败", e)
            return@withContext false
        }
    }
    
    /**
     * 清空本地观看历史数据
     */
    fun clearLocalHistory(context: Context) {
        watchHistoryItems.clear()
        updateStatistics()
        saveToLocal(context)
        Log.d(TAG, "已清空本地观看历史")
    }
    
    /**
     * 解析日期时间字符串
     */
    private fun parseDateTime(dateTimeStr: String): Date {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            format.parse(dateTimeStr) ?: Date()
        } catch (e: Exception) {
            Log.e(TAG, "解析日期时间失败: $dateTimeStr", e)
            Date()
        }
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