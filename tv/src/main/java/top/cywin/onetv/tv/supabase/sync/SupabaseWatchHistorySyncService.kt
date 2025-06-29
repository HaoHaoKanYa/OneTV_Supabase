package top.cywin.onetv.tv.supabase.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.tv.supabase.SupabaseCacheManager
import top.cywin.onetv.tv.supabase.SupabaseWatchHistorySessionManager
import top.cywin.onetv.tv.supabase.SupabaseWatchHistoryItem
import java.util.UUID

/**
 * Supabase观看历史同步服务
 * 负责在本地和服务器之间同步观看历史数据
 * 使用统一的数据格式和处理流程
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
     * 获取观看历史数据列表
     */
    private suspend fun getWatchHistoryItems(context: Context): List<SupabaseWatchHistoryItem> = withContext(Dispatchers.IO) {
        try {
            return@withContext SupabaseCacheManager.getWatchHistory(context)
        } catch (e: Exception) {
            Log.e(TAG, "获取观看历史数据列表失败", e)
            return@withContext emptyList<SupabaseWatchHistoryItem>()
        }
    }
    
    /**
     * 同步本地观看历史到服务器
     * @param context 应用上下文
     * @param items 待同步的观看历史记录列表，为null时同步所有观看历史记录
     * @return 成功同步的记录数
     */
    suspend fun syncToServer(context: Context, items: List<SupabaseWatchHistoryItem>? = null): Int = withContext(Dispatchers.IO) {
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
    private suspend fun performSyncToServer(context: Context, userId: String, items: List<SupabaseWatchHistoryItem>? = null): Int {
        
        // 获取本地观看历史数据
        val watchHistoryItems = items ?: getWatchHistoryItems(context)
        
        if (watchHistoryItems.isEmpty()) {
            Log.d(TAG, "本地无观看历史数据，无需同步")
            return 0
        }
        
        // 筛选需要同步的记录
        // 只同步具有本地生成ID（UUID格式）的记录
        val pendingItems = watchHistoryItems.filter { item -> 
            item.id?.contains("-") ?: false 
        }

        if (pendingItems.isEmpty()) {
            Log.d(TAG, "没有需要同步的观看历史记录")
            return 0
        }

        Log.d(TAG, "发现 ${pendingItems.size} 条需要同步的观看历史记录")

        // 混合同步策略：根据记录数量选择最优同步方式
        // ≤10条：单条同步 - 更好的错误处理和实时反馈
        // >10条：批量upsert - 更高效率和并发安全性
        val syncCount = if (pendingItems.size <= 10) {
            // 少量记录(≤10条)，使用单条同步模式
            Log.d(TAG, "使用单条同步模式处理 ${pendingItems.size} 条记录")
            individualSyncToServer(context, pendingItems, userId)
        } else {
            // 大量记录(>10条)，使用批量upsert同步模式
            Log.d(TAG, "使用批量upsert同步模式处理 ${pendingItems.size} 条记录")
            batchUpsertSyncToServer(context, pendingItems, userId)
        }
        
        if (syncCount > 0) {
            Log.d(TAG, "成功同步 $syncCount 条记录到服务器，更新本地数据")

            // 重新从服务器获取最新数据，确保本地记录有正确的服务器ID
            try {
                Log.d(TAG, "重新从服务器同步数据以获取正确的ID")
                val syncFromServerSuccess = syncFromServer(context)

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
            SupabaseWatchHistorySessionManager.reloadFromLocalAsync(context)
        }
        
        return syncCount
    }

    /**
     * 单条记录同步观看历史到服务器
     */
    private suspend fun individualSyncToServer(
        context: Context,
        pendingItems: List<SupabaseWatchHistoryItem>,
        userId: String
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始单条同步，记录数: ${pendingItems.size}")
        val apiClient = SupabaseApiClient()
        var successCount = 0

        // 确保使用最新的会话令牌
        val sessionToken = SupabaseSessionManager.getSession(context)
        if (sessionToken == null) {
            Log.e(TAG, "单条同步失败: 无有效会话令牌")
            return@withContext 0
        }

        // 设置API客户端的会话令牌
        apiClient.setSessionToken(sessionToken)

        for (item in pendingItems) {
            try {
                // 调用watch_history Edge Function
                val response = apiClient.recordWatchHistory(
                    channelName = item.channelName,
                    channelUrl = item.channelUrl,
                    duration = item.duration
                )

                // 检查响应
                val success = when {
                    response is JsonObject && response.jsonObject.containsKey("success") -> {
                        when (val successValue = response.jsonObject["success"]) {
                            is JsonPrimitive -> successValue.content == "true" || successValue.content == "True" || successValue.content.toBoolean()
                            else -> false
                        }
                    }
                    else -> false
                }

                if (success) {
                    Log.d(TAG, "单条同步成功: 频道=${item.channelName}")
                    successCount++
                } else {
                    val error = when {
                        response is JsonObject && response.jsonObject.containsKey("error") -> {
                            when (val errorValue = response.jsonObject["error"]) {
                                is JsonPrimitive -> errorValue.content
                                else -> "未知错误"
                            }
                        }
                        else -> "未知错误"
                    }
                    Log.e(TAG, "单条同步失败: $error, 频道=${item.channelName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "单条同步过程中出错: ${e.message}, 频道=${item.channelName}", e)
            }
        }

        // 重置待同步标记
        if (successCount > 0) {
            SupabaseWatchHistorySessionManager.resetPendingChanges()
        }

        Log.d(TAG, "单条同步完成: 成功=$successCount/${pendingItems.size}")
        return@withContext successCount
    }

    /**
     * 从服务器同步观看历史到本地
     */
    suspend fun syncFromServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始从服务器同步观看历史")

        return@withContext syncLock.withLock {
            if (isSyncing) {
                Log.d(TAG, "同步正在进行中，跳过此次同步")
                return@withLock false
            }

            isSyncing = true

            try {
                // 获取服务器数据
                val serverItems = getServerWatchHistory(context)
                if (serverItems == null) {
                    Log.e(TAG, "获取服务器观看历史失败")
                    return@withLock false
                }

                Log.d(TAG, "从服务器获取到 ${serverItems.size} 条观看历史记录")

                // 获取本地数据
                val localItems = getWatchHistoryItems(context)

                // 合并数据
                val mergedItems = mergeWatchHistory(localItems, serverItems)

                // 保存合并后的数据
                SupabaseCacheManager.saveWatchHistory(context, mergedItems)

                // 通知会话管理器重新加载数据
                SupabaseWatchHistorySessionManager.reloadFromLocalAsync(context)

                Log.d(TAG, "成功从服务器同步观看历史")
                return@withLock true
            } catch (e: Exception) {
                Log.e(TAG, "从服务器同步观看历史失败: ${e.message}", e)
                return@withLock false
            } finally {
                isSyncing = false
            }
        }
    }

    /**
     * 批量upsert同步观看历史到服务器
     */
    private suspend fun batchUpsertSyncToServer(
        context: Context,
        pendingItems: List<SupabaseWatchHistoryItem>,
        userId: String
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始批量upsert同步，记录数: ${pendingItems.size}")
        val apiClient = SupabaseApiClient()
        
        // 确保使用最新的会话令牌
        val sessionToken = SupabaseSessionManager.getSession(context)
        if (sessionToken == null) {
            Log.e(TAG, "批量同步失败: 无有效会话令牌")
            return@withContext 0
        }
        
        // 设置API客户端的会话令牌
        apiClient.setSessionToken(sessionToken)

        // 准备批量同步数据
        val batchData = pendingItems.map { item ->
            mapOf(
                "channelName" to item.channelName,
                "channelUrl" to item.channelUrl,
                "duration" to item.duration,
                "watchStart" to item.watchStart,
                "watchEnd" to item.watchEnd
            )
        }

        try {
            // 调用批量upsert Edge Function
            // 使用batchUpsertWatchHistory方法
            val response = apiClient.batchUpsertWatchHistory(batchData)

            // 检查响应
            val success = when {
                response is JsonObject && response.jsonObject.containsKey("success") -> {
                    when (val successValue = response.jsonObject["success"]) {
                        is JsonPrimitive -> successValue.content == "true" || successValue.content == "True" || successValue.content.toBoolean()
                        else -> false
                    }
                }
                else -> false
            }

            if (success) {
                val data = response.jsonObject["data"]?.jsonObject
                val inserted = when (val insertedValue = data?.get("inserted")) {
                    is JsonPrimitive -> insertedValue.content.toIntOrNull() ?: 0
                        else -> 0
                    }
                val duplicates = when (val duplicatesValue = data?.get("duplicates")) {
                    is JsonPrimitive -> duplicatesValue.content.toIntOrNull() ?: 0
                        else -> 0
                    }

                Log.d(TAG, "批量同步成功: 插入=$inserted, 重复=$duplicates")

                // 重置待同步标记
                if (inserted > 0 || duplicates > 0) {
                    SupabaseWatchHistorySessionManager.resetPendingChanges()
                }

                return@withContext inserted
            } else {
                val error = when {
                    response is JsonObject && response.jsonObject.containsKey("error") -> {
                        when (val errorValue = response.jsonObject["error"]) {
                            is JsonPrimitive -> errorValue.content
                            else -> "未知错误"
                        }
                    }
                    else -> "未知错误"
                }
                Log.e(TAG, "批量同步失败: $error")
                return@withContext 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "批量同步过程中出错: ${e.message}", e)
            return@withContext 0
        }
    }
    
    /**
     * 从服务器同步观看历史到本地
     * @param context 应用上下文
     * @param limit 最大获取记录数
     * @return 是否成功同步
     */
    suspend fun syncFromServer(context: Context, limit: Int = 100): Boolean = withContext(Dispatchers.IO) {
        // 检查是否已有同步在进行
        if (isSyncing) {
            Log.d(TAG, "同步正在进行中，跳过此次请求")
            return@withContext false
        }

        // 使用锁保护同步过程
        return@withContext syncLock.withLock {
            if (isSyncing) {
                Log.d(TAG, "同步正在进行中（锁内检查），跳过此次请求")
                return@withLock false
            }

            isSyncing = true
            try {
                Log.d(TAG, "开始从服务器同步观看历史")

                // 获取用户ID
                val userId = SupabaseSessionManager.getCachedUserData(context)?.userid
                if (userId == null) {
                    Log.e(TAG, "同步失败: 未获取到用户ID")
                    return@withLock false
                }

                // 获取服务器数据
                val serverItems = getServerWatchHistory(context, userId, limit)
                if (serverItems.isEmpty()) {
                    Log.d(TAG, "服务器无观看历史数据")
                    return@withLock true
                }

                Log.d(TAG, "从服务器获取到 ${serverItems.size} 条观看历史记录")
            
            // 获取本地数据
                val localItems = getWatchHistoryItems(context)
                
                // 合并数据
                val mergedItems = mergeWatchHistory(localItems, serverItems)
                
                // 保存合并后的数据
                SupabaseCacheManager.saveWatchHistory(context, mergedItems)
                
                // 通知会话管理器重新加载数据
                SupabaseWatchHistorySessionManager.reloadFromLocalAsync(context)
                
                Log.d(TAG, "成功从服务器同步观看历史")
                return@withLock true
            } catch (e: Exception) {
                Log.e(TAG, "从服务器同步观看历史失败: ${e.message}", e)
                return@withLock false
            } finally {
                isSyncing = false
            }
        }
    }
    
    /**
     * 获取服务器端观看历史数据
     */
    private suspend fun getServerWatchHistory(context: Context): List<SupabaseWatchHistoryItem>? = withContext(Dispatchers.IO) {
        try {
            val apiClient = SupabaseApiClient()
            val sessionToken = SupabaseSessionManager.getSession(context)
            if (sessionToken == null) {
                Log.e(TAG, "获取服务器观看历史失败: 无有效会话令牌")
                return@withContext null
            }

            apiClient.setSessionToken(sessionToken)
            val response = apiClient.getWatchHistory()

            // 解析响应
            if (response is JsonObject && response.jsonObject.containsKey("data")) {
                val dataElement = response.jsonObject["data"]
                if (dataElement is JsonArray) {
                    val items = mutableListOf<SupabaseWatchHistoryItem>()
                    for (element in dataElement.jsonArray) {
                        if (element is JsonObject) {
                            try {
                                val item = parseWatchHistoryItem(element.jsonObject)
                                items.add(item)
                            } catch (e: Exception) {
                                Log.w(TAG, "解析观看历史项失败: ${e.message}")
                            }
                        }
                    }
                    return@withContext items
                }
            }

            Log.w(TAG, "服务器响应格式不正确")
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "获取服务器观看历史失败: ${e.message}", e)
            return@withContext null
        }
    }



    /**
     * 获取服务器端观看历史数据（旧方法，保持兼容性）
     */
    private suspend fun getServerWatchHistory(
        context: Context, 
        userId: String,
        limit: Int = 100
    ): List<SupabaseWatchHistoryItem> = withContext(Dispatchers.IO) {
        Log.d(TAG, "获取服务器观看历史数据")
        val apiClient = SupabaseApiClient()
        
        // 确保使用最新的会话令牌
        val sessionToken = SupabaseSessionManager.getSession(context)
        if (sessionToken != null) {
            apiClient.setSessionToken(sessionToken)
        }

        try {
            // 调用watch_history Edge Function
            // 使用getWatchHistory方法
            val response = apiClient.getWatchHistory(
                page = 1,
                pageSize = limit,
                timeRange = "all",
                sortBy = "time",
                sortOrder = "desc"
            )

            // 检查响应
            val success = when {
                response is JsonObject && response.jsonObject.containsKey("success") -> {
                    when (val successValue = response.jsonObject["success"]) {
                        is JsonPrimitive -> successValue.content == "true" || successValue.content == "True" || successValue.content.toBoolean()
                        else -> false
                    }
                }
                else -> false
            }
            
            if (success) {
                val records = response.jsonObject["items"]?.jsonArray

                if (records != null) {
                    Log.d(TAG, "从服务器获取到 ${records.size} 条观看历史记录")
                    
                    // 转换为SupabaseWatchHistoryItem
                    val items = records.mapNotNull { recordElement ->
                        try {
                            val record = recordElement.jsonObject
                            SupabaseWatchHistoryItem(
                                id = record["id"]?.jsonPrimitive?.content,
                                channelName = record["channel_name"]?.jsonPrimitive?.content 
                                    ?: record["channelName"]?.jsonPrimitive?.content ?: "",
                                channelUrl = record["channel_url"]?.jsonPrimitive?.content 
                                    ?: record["channelUrl"]?.jsonPrimitive?.content ?: "",
                                duration = record["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                                watchStart = record["watch_start"]?.jsonPrimitive?.content 
                                    ?: record["watchStart"]?.jsonPrimitive?.content ?: "",
                                watchEnd = record["watch_end"]?.jsonPrimitive?.content 
                                    ?: record["watchEnd"]?.jsonPrimitive?.content ?: "",
                                userId = record["user_id"]?.jsonPrimitive?.content
                                    ?: record["userId"]?.jsonPrimitive?.content
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "转换服务器记录失败: ${e.message}")
                            null
                        }
                    }
                    
                    return@withContext items
                }
            } else {
                val items = response.jsonObject["items"]?.jsonArray
                if (items != null && items.isNotEmpty()) {
                    Log.d(TAG, "从服务器获取到 ${items.size} 条观看历史记录")
                    
                    // 转换为SupabaseWatchHistoryItem
                    val watchHistoryItems = items.mapNotNull { recordElement ->
                        try {
                            val record = recordElement.jsonObject
                            SupabaseWatchHistoryItem(
                                id = record["id"]?.jsonPrimitive?.content,
                                channelName = record["channelName"]?.jsonPrimitive?.content 
                                    ?: record["channel_name"]?.jsonPrimitive?.content ?: "",
                                channelUrl = record["channelUrl"]?.jsonPrimitive?.content 
                                    ?: record["channel_url"]?.jsonPrimitive?.content ?: "",
                                duration = record["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                                watchStart = record["watchStart"]?.jsonPrimitive?.content 
                                    ?: record["watch_start"]?.jsonPrimitive?.content ?: "",
                                watchEnd = record["watchEnd"]?.jsonPrimitive?.content 
                                    ?: record["watch_end"]?.jsonPrimitive?.content ?: "",
                                userId = record["userId"]?.jsonPrimitive?.content 
                                    ?: record["user_id"]?.jsonPrimitive?.content
                            )
                    } catch (e: Exception) {
                            Log.e(TAG, "转换服务器记录失败: ${e.message}")
                            null
                        }
                    }
                    
                    return@withContext watchHistoryItems
                }
            }

            Log.d(TAG, "从服务器获取观看历史失败或为空")
            return@withContext emptyList<SupabaseWatchHistoryItem>()
        } catch (e: Exception) {
            Log.e(TAG, "获取服务器观看历史出错: ${e.message}", e)
            return@withContext emptyList<SupabaseWatchHistoryItem>()
        }
    }
    
    /**
     * 合并本地和服务器观看历史数据
     * 优先保留服务器数据，但保留本地唯一数据
     */
    private fun mergeWatchHistory(
        localItems: List<SupabaseWatchHistoryItem>,
        serverItems: List<SupabaseWatchHistoryItem>
    ): List<SupabaseWatchHistoryItem> {
        Log.d(TAG, "合并观看历史: 本地=${localItems.size}, 服务器=${serverItems.size}")
        
        // 创建服务器记录的唯一标识集合
        val serverItemKeys = serverItems.map { 
            "${it.channelName}:${it.channelUrl}:${it.watchStart}" 
        }.toSet()
        
        // 过滤本地记录，只保留服务器上没有的记录
        val uniqueLocalItems = localItems.filter { localItem ->
            val localKey = "${localItem.channelName}:${localItem.channelUrl}:${localItem.watchStart}"
            !serverItemKeys.contains(localKey)
        }
        
        Log.d(TAG, "本地唯一记录数: ${uniqueLocalItems.size}")
        
        // 合并服务器记录和唯一本地记录
        val mergedItems = serverItems.toMutableList()
        mergedItems.addAll(uniqueLocalItems)
        
        // 按时间排序
        val sortedItems = mergedItems.sortedByDescending { it.watchStart }
        
        Log.d(TAG, "合并后总记录数: ${sortedItems.size}")
        return sortedItems
    }

    /**
     * 解析观看历史项
     */
    private fun parseWatchHistoryItem(jsonObject: Map<String, Any>): SupabaseWatchHistoryItem {
        return SupabaseWatchHistoryItem(
            id = jsonObject["id"]?.toString(),
            channelName = jsonObject["channel_name"]?.toString() ?: "",
            channelUrl = jsonObject["channel_url"]?.toString() ?: "",
            duration = (jsonObject["duration"] as? Number)?.toLong() ?: 0L,
            watchStart = jsonObject["watch_start"]?.toString() ?: "",
            watchEnd = jsonObject["watch_end"]?.toString() ?: "",
            userId = jsonObject["user_id"]?.toString()
        )
    }

    /**
     * 生成记录的唯一标识
     * 用于检测重复记录
     */
    private fun generateRecordHash(
        userId: String,
        channelName: String,
        channelUrl: String,
        watchStart: String
    ): String {
        return "$userId:$channelName:$channelUrl:$watchStart"
    }
}