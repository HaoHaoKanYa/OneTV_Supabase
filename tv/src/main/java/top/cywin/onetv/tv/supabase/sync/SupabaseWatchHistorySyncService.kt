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
    private const val TAG = "WatchHistorySync"

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
        val localPendingItems = watchHistoryItems.filter { item ->
            item.id?.contains("-") ?: false
        }

        if (localPendingItems.isEmpty()) {
            Log.d(TAG, "没有需要同步的观看历史记录")
            return 0
        }

        Log.d(TAG, "发现 ${localPendingItems.size} 条本地记录需要检查同步状态")

        // 获取服务器上已有的记录进行去重检查
        val serverItems = try {
            Log.d(TAG, "获取服务器观看历史数据")
            val items = getServerWatchHistory(context, userId, 1000) // 获取更多记录用于去重
            if (items.isEmpty()) {
                Log.d(TAG, "从服务器获取观看历史失败或为空")
            } else {
                Log.d(TAG, "从服务器获取到 ${items.size} 条记录用于去重检查")
            }
            items
        } catch (e: Exception) {
            when {
                e.message?.contains("Unable to resolve host") == true -> {
                    Log.e(TAG, "网络连接失败，无法获取服务器记录进行去重: ${e.message}")
                    // 网络问题时，暂停同步避免重复上传
                    Log.w(TAG, "由于网络问题，暂停同步以避免重复记录")
                    return 0
                }
                e.message?.contains("coroutine scope left") == true ||
                e.message?.contains("LeftCompositionCancellationException") == true -> {
                    Log.w(TAG, "协程作用域已取消，停止同步操作")
                    return 0
                }
                e is kotlinx.coroutines.CancellationException -> {
                    Log.w(TAG, "协程被取消，停止同步操作: ${e.message}")
                    return 0
                }
                else -> {
                    Log.w(TAG, "获取服务器记录失败，将同步所有本地记录: ${e.message}")
                    emptyList()
                }
            }
        }

        // 创建服务器记录的唯一标识集合用于去重
        // 使用更精确的去重逻辑，考虑时间格式差异
        val serverItemKeys = serverItems.map { serverItem ->
            val normalizedTime = normalizeTimeString(serverItem.watchStart)
            "${serverItem.channelName}:${serverItem.channelUrl}:$normalizedTime"
        }.toSet()

        Log.d(TAG, "服务器记录唯一标识: ${serverItemKeys.take(3)}...") // 显示前3个用于调试

        // 过滤出真正需要同步的记录（服务器上不存在的记录）
        val pendingItems = localPendingItems.filter { localItem ->
            val normalizedLocalTime = normalizeTimeString(localItem.watchStart)
            val localKey = "${localItem.channelName}:${localItem.channelUrl}:$normalizedLocalTime"
            val exists = serverItemKeys.contains(localKey)

            if (exists) {
                Log.d(TAG, "跳过重复记录: ${localItem.channelName} - $normalizedLocalTime")
            } else {
                Log.d(TAG, "需要同步记录: ${localItem.channelName} - $normalizedLocalTime")
            }

            !exists
        }

        val duplicateCount = localPendingItems.size - pendingItems.size
        if (duplicateCount > 0) {
            Log.d(TAG, "发现 $duplicateCount 条重复记录，跳过同步")
        }

        if (pendingItems.isEmpty()) {
            Log.d(TAG, "所有本地记录在服务器上已存在，无需同步")
            return 0
        }

        Log.d(TAG, "发现 ${pendingItems.size} 条需要同步的观看历史记录")

        // 统一同步策略：无论记录数量多少，都使用批量同步模式
        // 根据项目设定，当观看历史记录≥1条时，全部采用批量同步服务器上传
        // 单条同步逻辑已暂时注释，不再使用
        val syncCount = run {
            // 统一使用批量upsert同步模式处理所有记录
            Log.d(TAG, "使用批量upsert同步模式处理 ${pendingItems.size} 条记录")
            batchUpsertSyncToServer(context, pendingItems, userId)
        }

        // 注释掉的原混合同步策略代码：
        // val syncCount = if (pendingItems.size <= 10) {
        //     // 少量记录(≤10条)，使用单条同步模式
        //     Log.d(TAG, "使用单条同步模式处理 ${pendingItems.size} 条记录")
        //     individualSyncToServer(context, pendingItems, userId)
        // } else {
        //     // 大量记录(>10条)，使用批量upsert同步模式
        //     Log.d(TAG, "使用批量upsert同步模式处理 ${pendingItems.size} 条记录")
        //     batchUpsertSyncToServer(context, pendingItems, userId)
        // }
        
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
     * 注释：根据项目设定，单条同步逻辑已暂时全部注释掉，不再使用
     * 现在统一使用批量同步服务器上传，无论记录数量是1条还是多条
     */
    /*
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
    */

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
        val apiClient = SupabaseApiClient.getInstance()
        
        // 确保使用最新的会话令牌
        val sessionToken = SupabaseSessionManager.getSession(context)
        if (sessionToken == null) {
            Log.e(TAG, "批量同步失败: 无有效会话令牌")
            return@withContext 0
        }

        // 验证会话令牌是否有效
        try {
            val userData = SupabaseSessionManager.getCachedUserData(context)
            if (userData == null) {
                Log.e(TAG, "批量同步失败: 用户数据缓存丢失")
                return@withContext 0
            }
            Log.d(TAG, "使用用户会话: ${userData.userid.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "验证用户会话失败: ${e.message}")
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
            // 使用batchUpsertWatchHistory方法，传递用户ID
            val response = apiClient.batchUpsertWatchHistory(batchData, userId)

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
            when {
                e.message?.contains("Unable to resolve host") == true -> {
                    Log.e(TAG, "批量同步网络连接失败: ${e.message}")
                }
                e.message?.contains("coroutine scope left") == true ||
                e.message?.contains("LeftCompositionCancellationException") == true -> {
                    Log.w(TAG, "批量同步协程作用域已取消: ${e.message}")
                }
                e is kotlinx.coroutines.CancellationException -> {
                    Log.w(TAG, "批量同步协程被取消: ${e.message}")
                }
                e.message?.contains("Software caused connection abort") == true -> {
                    Log.w(TAG, "批量同步网络连接中断，但数据可能已成功发送到服务器: ${e.message}")
                    // 网络连接中断但服务器可能已收到数据，这种情况下不算完全失败
                    Log.i(TAG, "提示：如果服务器日志显示数据已保存，则同步实际上是成功的")
                }
                else -> {
                    Log.e(TAG, "批量同步过程中出错: ${e.message}", e)
                }
            }
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
            val apiClient = SupabaseApiClient.getInstance()
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
        val apiClient = SupabaseApiClient.getInstance()

        try {
            // 调用watch_history Edge Function
            // 使用getWatchHistory方法，不再需要设置session token
            val response = apiClient.getWatchHistory(
                page = 1,
                pageSize = limit,
                timeRange = "all",
                sortBy = "watch_start",  // 修正排序字段
                sortOrder = "desc",
                context = context
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

    /**
     * 标准化时间字符串，用于去重比较
     * 统一转换为北京时间（UTC+8），便于理解和调试
     */
    private fun normalizeTimeString(timeString: String): String {
        return try {
            val beijingZone = java.time.ZoneId.of("Asia/Shanghai") // 北京时间

            val instant = when {
                // 处理带时区的ISO格式：2025-06-30T23:06:20.054+08:00
                timeString.contains("+") || timeString.contains("Z") -> {
                    java.time.Instant.parse(timeString)
                }
                // 处理不带时区的格式：2025-06-30 23:06:20.054
                timeString.contains(" ") -> {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    val localDateTime = java.time.LocalDateTime.parse(timeString, formatter)
                    // 假设服务器时间是北京时间
                    localDateTime.atZone(beijingZone).toInstant()
                }
                else -> {
                    // 尝试直接解析
                    java.time.Instant.parse(timeString)
                }
            }

            // 转换为北京时间，去掉毫秒精度以避免微小差异
            val beijingTime = instant.atZone(beijingZone)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            val normalizedTime = beijingTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            Log.d(TAG, "时间标准化为北京时间: $timeString -> $normalizedTime")
            normalizedTime

        } catch (e: Exception) {
            // 如果解析失败，尝试其他格式
            try {
                // 尝试解析带时区偏移的格式：2025-06-30T23:06:20.054+08:00
                val formatter = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
                val offsetDateTime = java.time.OffsetDateTime.parse(timeString, formatter)
                val beijingTime = offsetDateTime.atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                val normalizedTime = beijingTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Log.d(TAG, "时间标准化为北京时间(偏移): $timeString -> $normalizedTime")
                normalizedTime
            } catch (e2: Exception) {
                // 如果都解析失败，返回原始字符串
                Log.w(TAG, "无法解析时间格式: $timeString, 使用原始值")
                timeString
            }
        }
    }
}