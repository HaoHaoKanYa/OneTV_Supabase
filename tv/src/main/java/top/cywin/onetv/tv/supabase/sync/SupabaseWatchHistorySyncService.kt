package top.cywin.onetv.tv.supabase.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Supabase观看历史同步服务
 * 负责在本地和服务器之间同步观看历史数据
 */
object SupabaseWatchHistorySyncService {
    private const val TAG = "WatchHistorySyncService"

    // 同步锁，防止并发同步导致重复上传
    private val syncLock = Mutex()
    private var isSyncing = false

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
        // 检查是否已有同步在进行
        if (isSyncing) {
            Log.d(TAG, "同步正在进行中，跳过此次请求")
            return@withContext 0
        }

        // 使用锁保护同步过程
        return@withContext syncLock.withLock {
            if (isSyncing) {
                Log.d(TAG, "同步正在进行中（锁内检查），跳过此次请求")
                return@withLock 0
            }

            isSyncing = true
            try {
                Log.d(TAG, "开始同步观看历史到服务器")

                // 获取用户ID
                val userId = SupabaseSessionManager.getCachedUserData(context)?.userid
                if (userId == null) {
                    Log.e(TAG, "同步失败: 未获取到用户ID")
                    return@withLock 0
                }

                performSyncToServer(context, userId, items)
            } finally {
                isSyncing = false
            }
        }
    }

    /**
     * 执行实际的同步操作
     */
    private suspend fun performSyncToServer(context: Context, userId: String, items: List<WatchHistoryItem>? = null): Int {
        
        // 获取本地观看历史数据
        val historyJson = getHistoryJson(context)
        
        if (historyJson.isNullOrBlank()) {
            Log.d(TAG, "本地无观看历史数据，无需同步")
            return 0
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
            return 0
        }
        
        // 改进的同步逻辑：不仅基于ID格式，还要检查记录的实际内容
        val pendingItems = if (items != null) {
            items
        } else {
            // 先基于ID格式进行初步过滤
            val uuidItems = watchHistoryItems.filter { it.id.contains("-") }

            if (uuidItems.isEmpty()) {
                Log.d(TAG, "没有UUID格式的本地记录需要同步")
                emptyList()
            } else if (uuidItems.size <= 5) {
                // 记录较少时，直接依赖服务器端的重复检查，不预先获取服务器记录
                Log.d(TAG, "本地待同步记录较少(${uuidItems.size}条)，依赖服务器端重复检查")
                uuidItems
            } else {
                // 记录较多时，先获取服务器记录进行客户端去重
                Log.d(TAG, "本地待同步记录较多(${uuidItems.size}条)，进行客户端预去重")

                val serverItems = try {
                    getServerWatchHistory(context, userId)
                } catch (e: Exception) {
                    Log.w(TAG, "获取服务器记录失败，将使用基于ID的简单过滤: ${e.message}")
                    // 如果获取服务器记录失败，直接使用UUID过滤的记录
                    emptyList()
                }

                if (serverItems.isEmpty()) {
                    // 如果没有服务器记录（获取失败或确实为空），直接使用UUID过滤的记录
                    Log.d(TAG, "无服务器记录用于去重，直接使用UUID过滤结果")
                    uuidItems
                } else {
                    // 创建服务器记录的唯一标识集合
                    val serverRecordHashes = serverItems.map { item ->
                        generateRecordHash(userId, item.channelName, item.channelUrl, item.watchStart)
                    }.toSet()

                    Log.d(TAG, "服务器已有记录数: ${serverItems.size}, 唯一标识数: ${serverRecordHashes.size}")

                    // 过滤本地记录：检查服务器是否已有相同内容的记录
                    uuidItems.filter { localItem ->
                        val localRecordHash = generateRecordHash(userId, localItem.channelName, localItem.channelUrl, localItem.watchStart)
                        val notOnServer = !serverRecordHashes.contains(localRecordHash)

                        if (!notOnServer) {
                            Log.d(TAG, "跳过已存在于服务器的记录: 频道=${localItem.channelName}, 时间=${localItem.watchStart}")
                        }

                        notOnServer
                    }
                }
            }
        }

        if (pendingItems.isEmpty()) {
            Log.d(TAG, "没有需要同步的观看历史记录")
            return 0
        }

        Log.d(TAG, "发现 ${pendingItems.size} 条需要同步的观看历史记录（已去除服务器重复）")
        
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

            // 重新从服务器获取最新数据，确保本地记录有正确的服务器ID
            try {
                Log.d(TAG, "重新从服务器同步数据以获取正确的ID")
                val syncFromServerSuccess = syncFromServer(context, 200)

                if (syncFromServerSuccess) {
                    Log.d(TAG, "成功从服务器同步最新数据，本地记录ID已更新")
                } else {
                    Log.w(TAG, "从服务器同步最新数据失败，但上传操作已完成")
                }
            } catch (e: Exception) {
                Log.e(TAG, "重新同步服务器数据时出错: ${e.message}", e)
                // 不影响上传结果，只是ID可能不是最新的
            }

            // 通知历史会话管理器重新加载数据
            SupabaseWatchHistorySessionManager.reloadFromLocal(context)
        }
        
        return syncCount
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

            if (success) {
                // 解析服务器返回的详细信息
                val dataObject = jsonObject?.get("data") as? JsonObject
                val inserted = dataObject?.get("inserted")?.let { element ->
                    when (element) {
                        is JsonPrimitive -> {
                            if (element.isString) element.content.toIntOrNull() ?: 0
                            else element.intOrNull ?: 0
                        }
                        else -> 0
                    }
                } ?: 0
                val duplicates = dataObject?.get("duplicates")?.let { element ->
                    when (element) {
                        is JsonPrimitive -> {
                            if (element.isString) element.content.toIntOrNull() ?: 0
                            else element.intOrNull ?: 0
                        }
                        else -> 0
                    }
                } ?: 0
                val total = dataObject?.get("total")?.let { element ->
                    when (element) {
                        is JsonPrimitive -> {
                            if (element.isString) element.content.toIntOrNull() ?: 0
                            else element.intOrNull ?: 0
                        }
                        else -> 0
                    }
                } ?: 0

                val message = jsonObject?.get("message")?.let {
                    (it as? JsonPrimitive)?.content
                } ?: "批量同步完成"

                Log.d(TAG, "批量同步结果: $message - 插入:$inserted, 重复:$duplicates, 总计:$total")
                return@withContext inserted
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
                    // 检查是否是重复记录
                    val dataObject = jsonObject?.get("data") as? JsonObject
                    val isDuplicate = dataObject?.get("duplicate")?.let { element ->
                        when (element) {
                            is JsonPrimitive -> {
                                if (element.isString) element.content.toBoolean()
                                else element.booleanOrNull ?: false
                            }
                            else -> false
                        }
                    } ?: false

                    val message = jsonObject?.get("message")?.let {
                        (it as? JsonPrimitive)?.content
                    } ?: "同步成功"

                    if (isDuplicate) {
                        Log.d(TAG, "单条同步跳过重复记录: ${item.channelName} - $message")
                    } else {
                        successCount++
                        Log.d(TAG, "单条同步成功: ${item.channelName} - $message")
                    }
                } else {
                    val errorMsg = jsonObject?.get("error")?.let {
                        (it as? JsonPrimitive)?.content
                    } ?: "未知错误"
                    Log.e(TAG, "单条同步失败: ${item.channelName} - ${errorMsg}")
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
        
        // 获取用户ID - 尝试多种方式
        val userId = SupabaseSessionManager.getCachedUserData(context)?.userid
            ?: run {
                // 如果缓存中没有，尝试从Repository获取
                try {
                    val repository = top.cywin.onetv.core.data.repositories.supabase.SupabaseRepository()
                    val currentUser = repository.getCurrentUser()
                    currentUser?.id
                } catch (e: Exception) {
                    Log.w(TAG, "从Repository获取用户ID失败: ${e.message}")
                    null
                }
            }

        if (userId == null) {
            Log.e(TAG, "同步失败: 未获取到用户ID，请确保用户已登录")
            return@withContext false
        }

        Log.d(TAG, "获取到用户ID: $userId")
        
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

                // 注意：根据项目设定，只有在用户退出或应用退出时才上传本地数据到服务器
                // 这里不应该自动上传本地数据
                if (pendingLocalItems.isNotEmpty()) {
                    Log.d(TAG, "发现 ${pendingLocalItems.size} 条本地待同步记录，但根据项目设定，只在用户/应用退出时上传")
                }

                return@withContext true
            } else {
                Log.d(TAG, "服务器没有新记录需要合并")
                
                // 修复错误：移除自动上传逻辑
                // 根据项目设定，不应该在这里自动同步记录，而应该只在应用退出或账号退出时同步
                if (pendingLocalItems.isNotEmpty()) {
                    Log.d(TAG, "发现 ${pendingLocalItems.size} 条本地待同步记录，但根据项目设定，只在用户/应用退出时上传")
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
            
            // 2. 根据项目设定，不自动上传本地数据到服务器
            callback.onSyncProgress(2, 3)
            Log.d(TAG, "根据项目设定，跳过自动上传本地数据到服务器")

            // 3. 完成同步
            callback.onSyncProgress(3, 3)
            callback.onSyncCompleted(0) // 上传数量为0，因为没有自动上传
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

    /**
     * 生成记录的唯一标识哈希
     * 基于用户ID、频道名、频道URL、观看开始时间
     */
    private fun generateRecordHash(userId: String, channelName: String, channelUrl: String, watchStart: String): String {
        return "${userId}_${channelName}_${channelUrl}_${watchStart}"
    }

    /**
     * 获取服务器上的观看历史记录（用于去重）
     */
    private suspend fun getServerWatchHistory(context: Context, userId: String): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取服务器观看历史记录用于去重检查")

            val apiClient = SupabaseApiClient()
            val response = apiClient.getWatchHistory(
                page = 1,
                pageSize = 1000, // 获取更多记录用于去重
                timeRange = "all",
                sortBy = "watch_start",
                sortOrder = "desc"
            )

            val jsonObject = response as? JsonObject
            val itemsArray = jsonObject?.get("items") as? JsonArray

            if (itemsArray == null) {
                Log.w(TAG, "服务器响应中没有items数组")
                return@withContext emptyList()
            }

            val serverItems = mutableListOf<WatchHistoryItem>()

            // 解析服务器返回的每条记录
            for (jsonElement in itemsArray) {
                try {
                    val item = jsonElement as? JsonObject ?: continue

                    val id = item["id"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val channelName = item["channel_name"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val channelUrl = item["channel_url"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val watchStart = item["watch_start"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val watchEnd = item["watch_end"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                    val duration = item["duration"]?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() } ?: 0

                    if (id.isBlank() || channelName.isBlank() || duration <= 0) {
                        continue
                    }

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
                    Log.w(TAG, "解析服务器记录失败: ${e.message}")
                    continue
                }
            }

            Log.d(TAG, "成功获取服务器记录: ${serverItems.size}条")
            return@withContext serverItems
        } catch (e: Exception) {
            Log.e(TAG, "获取服务器观看历史失败", e)
            return@withContext emptyList()
        }
    }
}