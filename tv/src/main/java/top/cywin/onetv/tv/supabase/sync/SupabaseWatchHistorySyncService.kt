package top.cywin.onetv.tv.supabase.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import top.cywin.onetv.tv.supabase.SupabaseWatchHistorySessionManager
import top.cywin.onetv.tv.supabase.WatchHistoryItem
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.UUID
import io.ktor.http.Headers
import io.github.jan.supabase.SupabaseClient

/**
 * Supabase观看历史同步服务
 * 负责在本地和服务器之间同步观看历史数据
 */
object SupabaseWatchHistorySyncService {
    private const val TAG = "WatchHistorySyncService"
    
    // JSON序列化工具
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false
        isLenient = true
    }
    
    /**
     * 获取观看历史JSON字符串
     */
    private suspend fun getHistoryJson(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val result = SupabaseCacheManager.getCache<String>(
            context, 
            SupabaseCacheKey.WATCH_HISTORY, 
            null
        )
            Log.d(TAG, "成功获取观看历史JSON: ${result?.substring(0, Math.min(100, result?.length ?: 0))}")
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "获取观看历史JSON失败", e)
            return@withContext null
        }
    }
    
    /**
     * 同步本地观看历史到服务器
     * @param context 应用上下文
     * @param items 待同步的观看历史记录列表，为null时同步所有观看历史记录
     * @return 成功同步的记录数
     */
    suspend fun syncToServer(context: Context, items: List<WatchHistoryItem>? = null): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始同步观看历史到服务器")
        
        // 获取用户ID
        val userId = SupabaseSessionManager.getCachedUserData(context)?.userid
        if (userId == null) {
            Log.e(TAG, "同步失败: 未获取到用户ID")
            return@withContext 0
        }
        
        // 获取本地观看历史数据
        val historyJson = getHistoryJson(context)
        
        if (historyJson.isNullOrBlank()) {
            Log.d(TAG, "本地无观看历史数据，无需同步")
            return@withContext 0
        }
        
        // 解析本地数据
        val watchHistoryItems = try {
            // 尝试使用kotlinx.serialization解析
            try {
                json.decodeFromString<List<WatchHistoryItem>>(historyJson)
            } catch (e: Exception) {
                Log.d(TAG, "使用kotlinx.serialization解析失败，尝试使用Gson", e)
                
                // 备选：使用Gson解析
                val gson = Gson()
                val type = object : TypeToken<List<WatchHistoryItem>>() {}.type
                gson.fromJson<List<WatchHistoryItem>>(historyJson, type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析本地历史数据失败", e)
            return@withContext 0
        }
        
        // 找出需要同步的项目（UUID格式的ID，包含"-"）
        val pendingItems = items ?: watchHistoryItems.filter { it.id.contains("-") }
        
        if (pendingItems.isEmpty()) {
            Log.d(TAG, "没有需要同步的观看历史记录")
            return@withContext 0
        }
        
        Log.d(TAG, "发现 ${pendingItems.size} 条需要同步的观看历史记录")
        
        // 根据记录数量选择同步策略
        val syncCount = if (pendingItems.size <= 10) {
            // 少量记录，使用单条同步
            individualSyncToServer(context, pendingItems, userId)
        } else {
            // 大量记录，使用批量同步
            batchSyncToServer(context, pendingItems, userId)
        }
        
        if (syncCount > 0) {
            Log.d(TAG, "成功同步 $syncCount 条记录到服务器，更新本地数据")
            
            // 更新本地数据，将同步成功的项目ID更新为服务器返回的ID
            val updatedItems = mutableListOf<WatchHistoryItem>()
            val historyJsonStr = getHistoryJson(context)
            val allItems = try {
                if (historyJsonStr != null) {
                    json.decodeFromString<List<WatchHistoryItem>>(historyJsonStr)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析全部历史数据失败", e)
                return@withContext 0
            }
            
            // 将所有项目添加到更新列表中
            updatedItems.addAll(allItems)
            
            // 保存更新后的数据
            val newHistoryJson = json.encodeToString(updatedItems)
            SupabaseCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY, newHistoryJson)
            
            // 通知历史会话管理器重新加载数据
            SupabaseWatchHistorySessionManager.reloadFromLocal(context)
        }
        
        return@withContext syncCount
    }
    
    /**
     * 批量同步观看历史到服务器
     * 适用于大量记录的情况
     */
    private suspend fun batchSyncToServer(
        context: Context,
        pendingItems: List<WatchHistoryItem>,
        userId: String
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始批量同步，记录数: ${pendingItems.size}")
        val apiClient = SupabaseApiClient()
        
        // 准备批量同步数据
        val batchData = pendingItems.map { item ->
            mapOf(
                "channelName" to item.channelName,
                "channelUrl" to item.channelUrl,
                "duration" to item.duration,
                "watchStart" to item.watchStart
            )
        }
        
        try {
            // 调用批量同步API
            val response = apiClient.batchRecordWatchHistory(batchData)
            
            val jsonObject = response as? JsonObject
            val success = jsonObject?.get("success")?.let {
                (it as? JsonPrimitive)?.content?.contains("true")
            } ?: false
            
            val count = jsonObject?.get("count")?.let {
                (it as? JsonPrimitive)?.content?.toIntOrNull()
            } ?: 0
            
            if (success) {
                Log.d(TAG, "批量同步成功: $count 条记录")
                return@withContext count
            } else {
                val errorMsg = jsonObject?.get("error")?.let {
                    (it as? JsonPrimitive)?.content
                } ?: "未知错误"
                Log.e(TAG, "批量同步失败: ${errorMsg}")
                return@withContext 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "批量同步观看记录失败", e)
            return@withContext 0
        }
    }
    
    /**
     * 单条同步观看历史到服务器
     * 适用于少量记录的情况
     */
    private suspend fun individualSyncToServer(
        context: Context,
        pendingItems: List<WatchHistoryItem>,
        userId: String
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始单条同步，记录数: ${pendingItems.size}")
        val apiClient = SupabaseApiClient()
        var successCount = 0
        
        for (item in pendingItems) {
            try {
                Log.d(TAG, "单条同步: 频道=${item.channelName}, 时长=${item.duration}, 用户ID=$userId")
                
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
        
        Log.d(TAG, "单条同步完成，成功同步: $successCount 条记录")
        return@withContext successCount
    }
    
    /**
     * 从服务器同步观看历史记录并合并到本地
     * @param context 应用上下文
     * @param maxRecords 最大记录数，默认200条
     * @return 如果同步成功返回true，否则返回false
     */
    suspend fun syncFromServer(
        context: Context, 
        maxRecords: Int = 200
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始从服务器同步观看历史，最大记录数: $maxRecords")
        
        // 获取用户ID
        val userId = SupabaseSessionManager.getCachedUserData(context)?.userid
        if (userId == null) {
            Log.e(TAG, "同步失败: 未获取到用户ID")
            return@withContext false
        }
        
        try {
            // 使用SupabaseApiClient的方法
            val apiClient = SupabaseApiClient()
            val response = apiClient.getWatchHistory(
                page = 1,
                pageSize = maxRecords,
                timeRange = "all",
                sortBy = "watch_start",
                sortOrder = "desc"
                // 不再传递method参数，使用默认值
            )
            
            // 解析服务器数据
            Log.d(TAG, "服务器原始响应: ${response.toString().take(500)}")
            
            val jsonObject = response as? JsonObject
            Log.d(TAG, "服务器响应JSON: $jsonObject")
            
            val itemsArray = jsonObject?.get("items") as? JsonArray
            if (itemsArray == null) {
                Log.e(TAG, "从服务器获取观看历史失败: 无效响应")
                return@withContext false
            }
            
            Log.d(TAG, "服务器返回记录数: ${itemsArray.size}")
            val serverItems = mutableListOf<WatchHistoryItem>()
            
            // 解析服务器返回的每条记录
            for (jsonElement in itemsArray) {
                try {
                    val item = jsonElement as? JsonObject ?: continue
                    
                    // 提取字段值
                    val id = item["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val channelName = item["channel_name"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val channelUrl = item["channel_url"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val watchStart = item["watch_start"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val watchEnd = item["watch_end"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val duration = item["duration"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: 0
                    
                    Log.d(TAG, "解析服务器记录: ID=$id, 频道=$channelName, 时长=$duration 秒")
                    
                    if (id.isBlank() || channelName.isBlank() || duration <= 0) {
                        Log.w(TAG, "跳过无效记录: ID=$id, 频道=$channelName, 时长=$duration")
                        continue
                    }
                    
                    // 创建历史项目对象
                    val historyItem = WatchHistoryItem(
                        id = id,
                        channelName = channelName,
                        channelUrl = channelUrl,
                        watchStart = formatIsoDateTime(watchStart),
                        watchEnd = formatIsoDateTime(watchEnd),
                        duration = duration
                    )
                    
                    serverItems.add(historyItem)
                } catch (e: Exception) {
                    Log.e(TAG, "解析历史项目失败: ${e.message}")
                    // 跳过此项，继续处理下一项
                }
            }
            
            Log.d(TAG, "成功解析服务器记录: ${serverItems.size}条")
            
            // 获取本地数据
            val historyJson = getHistoryJson(context)
            
            val localItems = if (!historyJson.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<WatchHistoryItem>>(historyJson)
                } catch (e: Exception) {
                    Log.e(TAG, "解析本地历史数据失败", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // 合并本地和服务器数据
            val localIds = localItems.map { it.id }.toSet()
            val serverIds = serverItems.map { it.id }.toSet()
            
            // 找出服务器上有但本地没有的记录
            val newServerItems = serverItems.filter { !localIds.contains(it.id) }
            
            // 找出本地有但可能未同步到服务器的记录（通常是UUID格式的ID，包含"-"），并且服务器没有这些id
            val pendingLocalItems = localItems.filter { it.id.contains("-") && !serverIds.contains(it.id) }
            
            Log.d(TAG, "本地记录数: ${localItems.size}, 服务器新记录数: ${newServerItems.size}, 本地待同步记录数: ${pendingLocalItems.size}")
            
            if (newServerItems.isNotEmpty()) {
                // 合并数据
                val mergedItems = localItems.toMutableList()
                mergedItems.addAll(newServerItems)
                
                // 按时间排序（最新的在前）
                val sortedItems = mergedItems.sortedByDescending { 
                    try {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.parse(it.watchStart)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                
                // 保存合并后的数据
                val newHistoryJson = json.encodeToString(sortedItems)
                SupabaseCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY, newHistoryJson)
                SupabaseCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY_LAST_LOADED, System.currentTimeMillis())
                
                Log.d(TAG, "已合并 ${newServerItems.size} 条服务器新记录到本地，总记录数: ${sortedItems.size}")
                
                // 通知历史会话管理器重新加载数据
                SupabaseWatchHistorySessionManager.reloadFromLocal(context)
                
                // 如果有本地待同步记录，尝试同步到服务器
                if (pendingLocalItems.isNotEmpty()) {
                    Log.d(TAG, "尝试将 ${pendingLocalItems.size} 条本地记录同步到服务器")
                    val syncCount = syncToServer(context, pendingLocalItems)
                    Log.d(TAG, "成功同步 $syncCount 条记录到服务器")
                }
                
                return@withContext true
            } else {
                Log.d(TAG, "服务器没有新记录需要合并")
                
                // 如果有本地待同步记录，尝试同步到服务器
                if (pendingLocalItems.isNotEmpty()) {
                    Log.d(TAG, "尝试将 ${pendingLocalItems.size} 条本地记录同步到服务器")
                    val syncCount = syncToServer(context, pendingLocalItems)
                    Log.d(TAG, "成功同步 $syncCount 条记录到服务器")
                }
                
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "从服务器同步观看历史失败", e)
            return@withContext false
        }
    }
    
    /**
     * 格式化ISO格式的日期时间
     * 将ISO格式的日期时间转换为本地格式
     */
    private fun formatIsoDateTime(isoDateTime: String): String {
        if (isoDateTime.isNullOrBlank()) return ""
        
        // 尝试多种可能的ISO日期格式
        val inputFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        
        for (formatPattern in inputFormats) {
            try {
                val inputFormat = SimpleDateFormat(formatPattern, java.util.Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                
                // 截取字符串以适应格式
                val truncatedDateTime = when {
                    formatPattern.contains(".SSS") && isoDateTime.contains(".") -> {
                        // 确保毫秒部分正确处理
                        val parts = isoDateTime.split(".")
                        if (parts.size >= 2) {
                            val millisPart = parts[1]
                            val millis = if (millisPart.contains("Z")) {
                                millisPart.substring(0, Math.min(3, millisPart.indexOf("Z")))
                            } else {
                                millisPart.substring(0, Math.min(3, millisPart.length))
                            }
                            "${parts[0]}.${millis}Z"
                        } else {
                            isoDateTime
                        }
                    }
                    formatPattern.endsWith("'Z'") && !isoDateTime.endsWith("Z") -> "$isoDateTime'Z'"
                    !formatPattern.endsWith("'Z'") && isoDateTime.endsWith("Z") -> isoDateTime.substring(0, isoDateTime.length - 1)
                    else -> isoDateTime
                }
                
                val date = inputFormat.parse(truncatedDateTime)
                if (date != null) {
                    return outputFormat.format(date)
                }
            } catch (e: Exception) {
                // 此格式解析失败，尝试下一个
                continue
            }
        }
        
        // 所有格式都解析失败，返回原始字符串
        Log.e(TAG, "所有日期格式解析失败: $isoDateTime")
        return isoDateTime
    }
    
    /**
     * 清空本地观看历史
     */
    suspend fun clearLocalHistory(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始清空本地观看历史")
            SupabaseCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY, "[]")
            Log.d(TAG, "已清空本地观看历史")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "清空本地观看历史失败", e)
            return@withContext false
        }
    }
    
    /**
     * 同步回调接口
     */
    interface SyncCallback {
        fun onSyncStarted()
        fun onSyncProgress(progress: Int, total: Int)
        fun onSyncCompleted(successCount: Int)
        fun onSyncFailed(errorMessage: String)
    }
    
    /**
     * 带回调的同步方法
     */
    suspend fun syncWithCallback(context: Context, callback: SyncCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onSyncStarted()
            
            // 1. 先从服务器同步到本地
            callback.onSyncProgress(1, 3)
            val syncFromSuccess = syncFromServer(context)
            
            if (!syncFromSuccess) {
                callback.onSyncFailed("从服务器同步失败")
                return@withContext
            }
            
            // 2. 再从本地同步到服务器
            callback.onSyncProgress(2, 3)
            val syncToCount = syncToServer(context)
            
            // 3. 完成同步
            callback.onSyncProgress(3, 3)
            callback.onSyncCompleted(syncToCount)
        } catch (e: Exception) {
            Log.e(TAG, "同步过程中出错", e)
            callback.onSyncFailed(e.message ?: "未知错误")
        }
    }
    
    /**
     * 获取待同步记录数
     */
    suspend fun getPendingSyncCount(context: Context): Int = withContext(Dispatchers.IO) {
        val historyJsonStr = getHistoryJson(context)
        
        if (historyJsonStr.isNullOrBlank()) {
            return@withContext 0
        }
        
        try {
            val items = json.decodeFromString<List<WatchHistoryItem>>(historyJsonStr)
            return@withContext items.count { it.id.contains("-") }
        } catch (e: Exception) {
            Log.e(TAG, "获取待同步记录数失败", e)
            return@withContext 0
        }
    }
} 