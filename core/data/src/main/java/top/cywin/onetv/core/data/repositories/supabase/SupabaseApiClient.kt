@file:OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
package top.cywin.onetv.core.data.repositories.supabase

import android.util.Log
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.cywin.onetv.core.data.utils.Logger
import io.ktor.client.statement.bodyAsText
import io.github.jan.supabase.safeBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.intOrNull

/**
 * Supabase API客户端
 * 用于调用Supabase Edge Functions，替代原Cloudflare API调用
 * 使用单例模式确保sessionToken在整个应用生命周期中保持一致
 */
class SupabaseApiClient private constructor() {
    private val client = SupabaseClient.client
    private val functions = client.functions
    private val storage = client.storage
    private val auth = client.auth
    private val log = Logger.create("SupabaseApiClient")

    // 添加会话令牌字段
    private var sessionToken: String? = null

    companion object {
        @Volatile
        private var INSTANCE: SupabaseApiClient? = null

        /**
         * 获取SupabaseApiClient单例实例
         */
        fun getInstance(): SupabaseApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseApiClient().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 设置会话令牌
     * @param token 会话令牌
     */
    suspend fun setSessionToken(token: String) {
        log.d("设置会话令牌: ${token.take(10)}...")
        this.sessionToken = token

        // 更新Supabase客户端的会话状态
        try {
            // 使用提供的token恢复会话
            auth.retrieveUser(token)
            log.d("成功更新Supabase客户端会话状态")
        } catch (e: Exception) {
            log.w("更新Supabase客户端会话状态失败: ${e.message}")
            // 即使失败也继续，因为我们仍然可以手动使用token
        }
    }

    /**
     * 检查是否已设置会话令牌
     * @return 如果已设置返回true，否则返回false
     */
    fun hasSessionToken(): Boolean {
        return !sessionToken.isNullOrEmpty()
    }

    /**
     * 获取会话令牌状态信息（用于调试）
     * @return 会话令牌状态描述
     */
    fun getSessionTokenStatus(): String {
        return if (sessionToken != null) {
            "已设置 (${sessionToken!!.take(10)}...)"
        } else {
            "未设置"
        }
    }
    
    /**
     * 获取IPTV频道列表
     * @param ispType 运营商类型：yidong(移动)、dianxin(电信)或public(公共)
     * @return 返回M3U格式的频道列表
     */
    suspend fun getIptvChannels(ispType: String): String = withContext(Dispatchers.IO) {
        try {
            log.d("调用Supabase函数获取IPTV频道: ispType=$ispType")
            log.d("使用的Supabase URL: ${SupabaseClient.getUrl()}")

            // 检查并记录sessionToken状态
            if (sessionToken != null) {
                log.d("使用sessionToken调用IPTV频道API: ${sessionToken!!.take(10)}...")
            } else {
                log.w("⚠️ sessionToken为空，将以游客身份调用IPTV频道API")
            }

            val response = functions.invoke(
                function = "iptv-channels?ispType=$ispType",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                    // ✅ 添加Authorization头传递用户token
                    sessionToken?.let { token ->
                        append("Authorization", "Bearer $token")
                        log.d("已添加Authorization头到IPTV频道请求")
                    }
                }
            )
            log.d("成功获取IPTV频道列表，响应大小: ${response.toString().length} 字节")
            return@withContext response.bodyAsText()
        } catch (e: Exception) {
            log.e("获取IPTV频道列表失败", e)

            // 添加更详细的错误日志
            val errorDetails = when (e) {
                is io.github.jan.supabase.exceptions.HttpRequestException -> {
                    "HTTP错误: ${e.message}\n" +
                    "请求URL: ${e.message?.substringAfter("to ")?.substringBefore(" (") ?: "未知"}\n" +
                    "Supabase客户端URL: ${SupabaseClient.getUrl()}\n" +
                    "SessionToken状态: ${if (sessionToken != null) "已设置" else "未设置"}\n" +
                    "请确认Supabase客户端已正确初始化，环境变量已正确设置"
                }
                else -> "错误类型: ${e.javaClass.simpleName}, 消息: ${e.message}"
            }
            log.e("错误详情 - $errorDetails")

            throw e
        }
    }

    /**
     * 直接从存储获取M3U文件
     * @param ispType 运营商类型：yidong(移动)、dianxin(电信)或public(公共)
     * @return 返回M3U格式的频道列表
     */
    suspend fun getIptvChannelsFromStorage(ispType: String): String = withContext(Dispatchers.IO) {
        try {
            val fileName = when (ispType) {
                "yidong" -> "wuzhou_cmcc.m3u"
                "dianxin" -> "wuzhou_telecom.m3u"
                "public" -> "onetv_api_result.m3u"
                else -> throw IllegalArgumentException("无效的运营商类型")
            }
            
            // 从storage中获取文件
            val bytes = storage.from("iptv-sources").downloadAuthenticated(fileName)
            return@withContext bytes.decodeToString()
        } catch (e: Exception) {
            log.e("从存储获取IPTV频道列表失败", e)
            throw e
        }
    }
    
    /**
     * 获取用户资料
     * @return 用户资料JSON对象
     */
    suspend fun getUserProfile(): JsonObject = withContext(Dispatchers.IO) {
        try {
            // 使用 GET 请求方式调用 Edge Function
            val response = functions.invoke(
                function = "user-profile",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            val result = response.safeBody<JsonObject>()
            log.d("成功获取用户资料")
            return@withContext result
        } catch (e: Exception) {
            log.e("获取用户资料失败", e)
            throw e
        }
    }
    
    /**
     * 更新用户资料
     * @param username 用户名
     * @param avatarUrl 头像URL
     * @return 更新结果
     */
    suspend fun updateUserProfile(username: String? = null, avatarUrl: String? = null): JsonObject = withContext(Dispatchers.IO) {
        try {
            val updateData = buildJsonObject {
                username?.let { put("username", it) }
                avatarUrl?.let { put("avatar_url", it) }
            }
            
            val response = functions.invoke(
                function = "user-profile",
                body = updateData
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("更新用户资料失败", e)
            throw e
        }
    }
    
    /**
     * 获取VIP状态
     * @return VIP状态信息
     */
    suspend fun getVipStatus(): JsonObject = withContext(Dispatchers.IO) {
        try {
            // 使用 GET 请求方式调用 Edge Function
            val response = functions.invoke(
                function = "vip-management/status",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            val result = response.safeBody<JsonObject>()
            log.d("成功获取VIP状态")
            return@withContext result
        } catch (e: Exception) {
            log.e("获取VIP状态失败", e)
            throw e
        }
    }
    
    /**
     * 激活VIP
     * @param activationCode 激活码
     * @return 激活结果
     */
    suspend fun activateVip(activationCode: String): JsonObject = withContext(Dispatchers.IO) {
        try {
            val requestData = buildJsonObject {
                put("activationCode", activationCode)
            }
            
            val response = functions.invoke(
                function = "vip-management/activate",
                body = requestData
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("激活VIP失败", e)
            throw e
        }
    }
    
    /**
     * 续费VIP
     * @param months 续费月数
     * @return 续费结果
     */
    suspend fun renewVip(months: Int): JsonObject = withContext(Dispatchers.IO) {
        try {
            val requestData = buildJsonObject {
                put("months", months)
            }
            
            val response = functions.invoke(
                function = "vip-management/renew",
                body = requestData
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("续费VIP失败", e)
            throw e
        }
    }
    
    /**
     * 获取收藏频道列表
     * @return 收藏频道列表
     */
    suspend fun getFavoriteChannels(): JsonArray = withContext(Dispatchers.IO) {
        try {
            val response = functions.invoke(
                function = "channel-favorites",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            return@withContext response.safeBody<JsonArray>()
        } catch (e: Exception) {
            log.e("获取收藏频道列表失败", e)
            throw e
        }
    }
    
    /**
     * 添加收藏频道
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @return 添加结果
     */
    suspend fun addFavoriteChannel(channelName: String, channelUrl: String): JsonObject = withContext(Dispatchers.IO) {
        try {
            val requestData = buildJsonObject {
                put("channelName", channelName)
                put("channelUrl", channelUrl)
            }
            
            val response = functions.invoke(
                function = "channel-favorites",
                body = requestData
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("添加收藏频道失败", e)
            throw e
        }
    }
    
    /**
     * 删除收藏频道（通过ID）
     * @param favoriteId 收藏ID
     * @return 删除结果
     */
    suspend fun deleteFavoriteChannelById(favoriteId: String): JsonObject = withContext(Dispatchers.IO) {
        try {
            val response = functions.invoke(
                function = "channel-favorites",
                headers = io.ktor.http.Headers.build {
                    append("Method", "DELETE")
                },
                body = mapOf("id" to favoriteId)
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("删除收藏频道失败", e)
            throw e
        }
    }
    
    /**
     * 删除收藏频道（通过URL）
     * @param channelUrl 频道URL
     * @return 删除结果
     */
    suspend fun deleteFavoriteChannelByUrl(channelUrl: String): JsonObject = withContext(Dispatchers.IO) {
        try {
            val response = functions.invoke(
                function = "channel-favorites",
                headers = io.ktor.http.Headers.build {
                    append("Method", "DELETE")
                },
                body = mapOf("url" to channelUrl)
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("删除收藏频道失败", e)
            throw e
        }
    }

    /**
     * 记录用户登录日志
     * @param deviceInfo 设备信息
     * @param ipAddress IP地址
     * @return 记录结果
     */
    suspend fun logUserLogin(deviceInfo: String, ipAddress: String): JsonObject = withContext(Dispatchers.IO) {
        try {
            val requestData = buildJsonObject {
                put("deviceInfo", deviceInfo)
                put("ipAddress", ipAddress)
            }
            
            val response = functions.invoke(
                function = "user-login-log",
                body = requestData
            )
            
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("记录用户登录失败", e)
            throw e
        }
    }

    /**
     * 获取服务信息
     * 从Supabase Edge Functions获取服务公告和更新信息
     * @return 包含content和last_updated的JsonObject
     */
    suspend fun getServiceInfo(): JsonObject = withContext(Dispatchers.IO) {
        try {
            val response = functions.invoke(
                function = "service-info",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            
            val result = response.safeBody<JsonObject>()
            log.d("成功获取服务信息")
            return@withContext result
        } catch (e: Exception) {
            log.e("获取服务信息失败", e)
            throw e
        }
    }
    
    /**
     * 获取在线用户数据
     * 从Supabase Edge Functions获取实时在线人数统计数据
     * @return 在线用户数据响应
     */
    suspend fun getOnlineUsers(): SupabaseOnlineUsersResponse = withContext(Dispatchers.IO) {
        try {
            val response = functions.invoke(
                function = "online-users",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            
            val result = response.safeBody<SupabaseOnlineUsersResponse>()
            log.d("成功获取在线人数数据: total=${result.total}")
            return@withContext result
        } catch (e: Exception) {
            log.e("获取在线人数数据失败", e)
            // 兜底返回，避免界面崩溃
            return@withContext SupabaseOnlineUsersResponse(
                total = 0,
                base = 0,
                real = 0,
                updated = System.currentTimeMillis() / 1000,
                status = "error",
                message = e.message
            )
        }
    }

    /**
     * 获取观看历史数据（分页）
     * @param page 页码
     * @param pageSize 每页条目数
     * @param timeRange 时间范围 (all, today, week, month, year)
     * @param sortBy 排序字段 (watch_start, channel_name, duration)
     * @param sortOrder 排序方向 (asc, desc)
     * @param method HTTP方法 (GET 或 POST)，默认为 GET
     * @return 观看历史数据和分页信息
     */
    suspend fun getWatchHistory(
        page: Int = 1,
        pageSize: Int = 20,
        timeRange: String = "all",
        sortBy: String = "watch_start",
        sortOrder: String = "desc",
        method: String = "GET",
        context: android.content.Context? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        try {
            log.d("获取观看历史: page=$page, timeRange=$timeRange, sortBy=$sortBy, method=$method")

            // 获取当前用户ID
            val currentUserId = if (context != null) {
                SupabaseSessionManager.getCachedUserData(context)?.userid
            } else {
                null
            }
            if (currentUserId == null) {
                log.e("获取观看历史失败: 无有效用户ID")
                return@withContext buildJsonObject {
                    put("items", JsonArray(emptyList()))
                    put("pagination", buildJsonObject {
                        put("page", page)
                        put("pageSize", pageSize)
                        put("totalItems", 0)
                        put("totalPages", 0)
                    })
                    put("error", "无有效用户ID")
                }
            }

            val response = functions.invoke(
                "watch_history?action=list&page=$page&pageSize=$pageSize&timeRange=$timeRange&sortBy=$sortBy&sortOrder=$sortOrder&userId=$currentUserId"
            ) {
                this.method = io.ktor.http.HttpMethod.Get
            }
            
            // 检查响应是否为空
            val responseText = response.bodyAsText()
            if (responseText.isBlank()) {
                log.e("获取观看历史失败: 空响应")
                return@withContext buildJsonObject {
                    put("items", JsonArray(emptyList()))
                    put("pagination", buildJsonObject {
                        put("page", page)
                        put("pageSize", pageSize)
                        put("totalItems", 0)
                        put("totalPages", 0)
                    })
                    put("error", "空响应")
                }
            }
            
            // 尝试解析JSON
            try {
                val result = response.safeBody<JsonElement>()
                if (result is JsonObject) {
                    val pagination = result.jsonObject["pagination"]?.jsonObject
                    val totalItems = pagination?.get("totalItems")?.toString() ?: "0"
                    log.d("成功获取观看历史数据，共${totalItems}条记录")
                }
                return@withContext result
            } catch (e: Exception) {
                log.e("解析观看历史数据失败", e)
                log.e("原始响应: $responseText")
                
                // 返回默认数据结构
                return@withContext buildJsonObject {
                    put("items", JsonArray(emptyList()))
                    put("pagination", buildJsonObject {
                        put("page", page)
                        put("pageSize", pageSize)
                        put("totalItems", 0)
                        put("totalPages", 0)
                    })
                    put("error", "解析失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log.e("获取观看历史失败", e)
            
            // 返回默认数据结构而不是抛出异常
            return@withContext buildJsonObject {
                put("items", JsonArray(emptyList()))
                put("pagination", buildJsonObject {
                    put("page", page)
                    put("pageSize", pageSize)
                    put("totalItems", 0)
                    put("totalPages", 0)
                })
                put("error", "请求失败: ${e.message}")
            }
        }
    }

    /**
     * 获取观看历史统计数据
     * @param timeRange 时间范围 (all, today, week, month, year)
     * @return 观看历史统计数据
     */
    suspend fun getWatchStatistics(timeRange: String = "all"): JsonElement = withContext(Dispatchers.IO) {
        try {
            log.d("获取观看历史统计: timeRange=$timeRange")
            
            val response = functions.invoke(
                "watch_history?action=statistics&timeRange=$timeRange"
            ) {
                this.method = io.ktor.http.HttpMethod.Get
            }
            
            // 检查响应是否为空
            val responseText = response.bodyAsText()
            if (responseText.isBlank()) {
                log.e("获取观看历史统计失败: 空响应")
                return@withContext buildJsonObject {
                    put("statistics", buildJsonObject {
                        put("totalWatchTime", 0)
                        put("totalChannels", 0)
                        put("totalWatches", 0)
                        put("mostWatchedChannel", null)
                        put("error", "空响应")
                    })
                }
            }
            
            // 尝试解析JSON
            try {
                val result = response.safeBody<JsonElement>()
                log.d("成功获取观看历史统计数据")
                return@withContext result
            } catch (e: Exception) {
                log.e("解析观看历史统计数据失败", e)
                log.e("原始响应: $responseText")
                
                // 返回默认数据
                return@withContext buildJsonObject {
                    put("statistics", buildJsonObject {
                        put("totalWatchTime", 0)
                        put("totalChannels", 0)
                        put("totalWatches", 0)
                        put("mostWatchedChannel", null)
                        put("error", "解析失败: ${e.message}")
                    })
                }
            }
        } catch (e: Exception) {
            log.e("获取观看历史统计失败", e)
            
            // 返回默认数据
            return@withContext buildJsonObject {
                put("statistics", buildJsonObject {
                    put("totalWatchTime", 0)
                    put("totalChannels", 0)
                    put("totalWatches", 0)
                    put("mostWatchedChannel", null)
                    put("error", "请求失败: ${e.message}")
                })
            }
        }
    }

    /**
     * 记录观看历史
     * @param channelName 频道名称
     * @param channelUrl 频道URL
     * @param duration 观看时长（秒）
     * @return 记录结果
     */
    suspend fun recordWatchHistory(
        channelName: String,
        channelUrl: String,
        duration: Long,
        userId: String? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        try {
            log.d("记录观看历史: 频道=$channelName, URL=$channelUrl, 时长=${duration}秒")
            
            // 确保参数有效
            if (channelName.isBlank() || channelUrl.isBlank() || duration <= 0) {
                log.e("记录观看历史失败: 无效参数")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "无效参数")
                }
            }
            
            // 获取当前用户ID - 优先使用传入的userId，否则从auth获取
            val currentUserId = userId ?: try {
                auth.currentUserOrNull()?.id
            } catch (e: Exception) {
                log.e("获取用户ID失败", e)
                null
            }

            if (currentUserId == null) {
                log.e("记录观看历史失败: 无有效用户ID")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "无有效用户ID")
                }
            }

            val requestData = buildJsonObject {
                put("channelName", channelName)
                put("channelUrl", channelUrl)
                put("duration", duration)
                put("userId", currentUserId)  // 使用app-configs方式时需要在请求体中包含用户ID
            }

            log.d("使用app-configs方式调用Edge Function，用户ID=${currentUserId.take(8)}...")

            // 调用观看历史记录Edge Function，Edge Function内部使用SERVICE_ROLE_KEY
            log.d("调用观看历史记录Edge Function")

            // 直接调用Edge Function，Edge Function内部会使用SERVICE_ROLE_KEY访问数据库
            val response = functions.invoke(
                function = "watch_history",
                body = requestData
            )
            
            val responseText = response.bodyAsText()
            log.d("观看历史记录响应: $responseText")
            
            // 检查响应是否为空
            if (responseText.isBlank()) {
                log.e("记录观看历史失败: 空响应")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "空响应")
                }
            }
            
            try {
                val jsonResponse = response.safeBody<JsonElement>()
                
                // 安全地访问JsonElement
                val success = when (val successValue = jsonResponse) {
                    is JsonObject -> {
                        when (val successField = successValue.jsonObject["success"]) {
                            is JsonPrimitive -> successField.content.toBoolean()
                            else -> false
                        }
                    }
                    else -> false
                }
                
                if (success) {
                    log.d("成功记录观看历史")
                } else {
                    val errorMsg = when (jsonResponse) {
                        is JsonObject -> {
                            when (val errorField = jsonResponse.jsonObject["error"]) {
                                is JsonPrimitive -> errorField.content
                                else -> "未知错误"
                            }
                        }
                        else -> "未知错误"
                    }
                    log.e("记录观看历史API返回失败: $errorMsg")
                }
                
                return@withContext jsonResponse
            } catch (e: Exception) {
                log.e("解析观看历史记录响应失败", e)
                
                // 尝试手动解析JSON
                try {
                    if (responseText.contains("\"success\"") && responseText.contains("true")) {
                        return@withContext buildJsonObject {
                            put("success", true)
                            put("data", JsonArray(emptyList()))
                        }
                    }
                } catch (e2: Exception) {
                    // 忽略手动解析错误
                }
                
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "解析响应失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log.e("记录观看历史请求失败", e)
            return@withContext buildJsonObject {
                put("success", false)
                put("error", "请求失败: ${e.message}")
            }
        }
    }

    /**
     * 批量upsert同步观看历史到服务器
     */
    suspend fun batchUpsertWatchHistory(
        records: List<Map<String, Any?>>,
        userId: String? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        try {
            log.d("批量upsert观看历史: 记录数量=${records.size}")

            if (records.isEmpty()) {
                log.w("批量upsert观看历史: 记录为空, 跳过请求")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "无观看记录")
                }
            }

            // 构建JSON数组
            val jsonRecords = JsonArray(
                records.map { record ->
                    buildJsonObject {
                        put("channelName", record["channelName"]?.toString() ?: "")
                        put("channelUrl", record["channelUrl"]?.toString() ?: "")
                        put("duration", record["duration"]?.toString()?.toLongOrNull() ?: 0L)
                        put("watchStart", record["watchStart"]?.toString() ?: "")
                        put("watchEnd", record["watchEnd"]?.toString() ?: "")
                    }
                }
            )

            // 获取当前用户ID - 优先使用传入的userId，否则从auth获取
            val currentUserId = userId ?: try {
                auth.currentUserOrNull()?.id
            } catch (e: Exception) {
                log.e("获取用户ID失败", e)
                null
            }

            if (currentUserId == null) {
                log.e("批量upsert观看历史失败: 无有效用户ID")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "无有效用户ID")
                }
            }

            val requestData = buildJsonObject {
                put("records", jsonRecords)
                put("userId", currentUserId)  // 使用app-configs方式时需要在请求体中包含用户ID
            }

            log.d("准备发送批量upsert观看历史请求: URL=watch_history_upsert, 用户ID=${currentUserId.take(8)}..., 内容大小=${jsonRecords.toString().length}字节")

            // 使用普通客户端调用Edge Function，Edge Function内部使用SERVICE_ROLE_KEY
            log.d("调用观看历史同步Edge Function")

            // 直接调用Edge Function，Edge Function内部会使用SERVICE_ROLE_KEY访问数据库
            val response = functions.invoke(
                function = "watch_history_upsert",
                body = requestData
            )

            val responseText = response.bodyAsText()
            log.d("批量upsert观看历史响应: ${responseText.take(500)}${if (responseText.length > 500) "..." else ""}")

            // 检查响应是否为空
            if (responseText.isBlank()) {
                log.e("批量upsert观看历史失败: 空响应")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "服务器返回空响应")
                }
            }

            // 解析JSON响应
            return@withContext Json.parseToJsonElement(responseText)

        } catch (e: Exception) {
            log.e("批量upsert观看历史异常: ${e.message}", e)
            return@withContext buildJsonObject {
                put("success", false)
                put("error", e.message ?: "未知错误")
            }
        }
    }

    /**
     * 批量记录观看历史（旧版本，保持兼容性）
     * @param records 观看记录列表，每条记录包含channelName, channelUrl, duration, watchStart, watchEnd
     * @return 记录结果
     */
    suspend fun batchRecordWatchHistory(
        records: List<Map<String, Any?>>
    ): JsonElement = withContext(Dispatchers.IO) {
        try {
            log.d("批量记录观看历史: 记录数量=${records.size}")
            
            if (records.isEmpty()) {
                log.w("批量记录观看历史: 记录为空, 跳过请求")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "无观看记录")
                }
            }
            
            // 记录前几条记录的内容以便调试
            records.take(3).forEachIndexed { index, record ->
                log.d("批量记录 #${index+1}: 频道=${record["channelName"]}, 时长=${record["duration"]}, 开始=${record["watchStart"]}")
            }
            
            // 构建请求数据
            val jsonRecords = kotlinx.serialization.json.buildJsonArray {
                records.forEach { record ->
                    add(buildJsonObject {
                        record["channelName"]?.let { put("channelName", it.toString()) }
                        record["channelUrl"]?.let { put("channelUrl", it.toString()) }
                        record["duration"]?.let { 
                            when (it) {
                                is Long -> put("duration", it)
                                is Int -> put("duration", it.toLong())
                                is String -> put("duration", it.toLongOrNull() ?: 0L)
                                else -> put("duration", 0L)
                            }
                        }
                        record["watchStart"]?.let { put("watchStart", it.toString()) }
                        record["watchEnd"]?.let { put("watchEnd", it.toString()) }
                    })
                }
            }
            
            val requestData = buildJsonObject {
                put("records", jsonRecords)
            }
            
            log.d("准备发送批量观看历史请求: URL=watch_history?action=batch, 内容大小=${jsonRecords.toString().length}字节")
            
            val response = functions.invoke(
                function = "watch_history?action=batch",
                body = requestData
            )
            
            val responseText = response.bodyAsText()
            log.d("批量观看历史响应: ${responseText.take(500)}${if (responseText.length > 500) "..." else ""}")
            
            // 检查响应是否为空
            if (responseText.isBlank()) {
                log.e("批量记录观看历史失败: 空响应")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "空响应")
                }
            }
            
            try {
                val jsonResponse = response.safeBody<JsonElement>()
                
                // 安全地访问JsonElement
                val success = when (val successValue = jsonResponse) {
                    is JsonObject -> {
                        when (val successField = successValue.jsonObject["success"]) {
                            is JsonPrimitive -> successField.content.toBoolean()
                            else -> false
                        }
                    }
                    else -> false
                }
                
                if (success) {
                    // 获取插入记录数
                    val inserted = when (jsonResponse) {
                        is JsonObject -> {
                            val data = jsonResponse.jsonObject["data"]
                            when (data) {
                                is JsonObject -> {
                                    when (val insertedField = data.jsonObject["inserted"]) {
                                        is JsonPrimitive -> {
                                            if (insertedField.isString) {
                                                insertedField.content.toIntOrNull() ?: 0
                                            } else {
                                                insertedField.intOrNull ?: 0
                                            }
                                        }
                                        else -> 0
                                    }
                                }
                                else -> 0
                            }
                        }
                        else -> 0
                    }
                    
                    log.d("成功批量记录观看历史: 插入${inserted}条记录")
                } else {
                    val errorMsg = when (jsonResponse) {
                        is JsonObject -> {
                            when (val errorField = jsonResponse.jsonObject["error"]) {
                                is JsonPrimitive -> errorField.content
                                else -> "未知错误"
                            }
                        }
                        else -> "未知错误"
                    }
                    log.e("批量记录观看历史API返回失败: $errorMsg")
                }
                
                return@withContext jsonResponse
            } catch (e: Exception) {
                log.e("解析批量观看历史记录响应失败", e)
                log.e("原始响应: $responseText")
                
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "解析响应失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log.e("批量记录观看历史请求失败", e)
            return@withContext buildJsonObject {
                put("success", false)
                put("error", "请求失败: ${e.message}")
            }
        }
    }

    /**
     * 获取用户设置
     * @return 用户设置数据
     */
    suspend fun getUserSettings(): JsonObject = withContext(Dispatchers.IO) {
        try {
            log.d("获取用户设置")
            
            val response = functions.invoke(
                function = "user-settings",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            
            val responseText = response.bodyAsText()
            if (responseText.isBlank()) {
                log.e("获取用户设置失败: 空响应")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "空响应")
                    put("settings", buildJsonObject {
                        put("theme", "dark")
                        put("notification_enabled", true)
                        put("player_settings", buildJsonObject {
                            put("autoPlay", true)
                            put("highQuality", true)
                        })
                        put("language_preference", "zh-CN")
                        put("timezone", "Asia/Shanghai")
                    })
                }
            }
            
            val result = response.safeBody<JsonObject>()
            log.d("成功获取用户设置")
            return@withContext result
        } catch (e: Exception) {
            log.e("获取用户设置失败", e)
            // 返回默认设置
            return@withContext buildJsonObject {
                put("success", false)
                put("error", e.message)
                put("settings", buildJsonObject {
                    put("theme", "dark")
                    put("notification_enabled", true)
                    put("player_settings", buildJsonObject {
                        put("autoPlay", true)
                        put("highQuality", true)
                    })
                    put("language_preference", "zh-CN")
                    put("timezone", "Asia/Shanghai")
                })
            }
        }
    }
    
    /**
     * 更新用户设置
     * @param settings 用户设置数据
     * @return 更新结果
     */
    suspend fun updateUserSettings(settings: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        try {
            log.d("更新用户设置: ${settings.toString().take(100)}...")
            
            val response = functions.invoke(
                function = "user-settings",
                body = settings
            )
            
            val responseText = response.bodyAsText()
            if (responseText.isBlank()) {
                log.e("更新用户设置失败: 空响应")
                return@withContext buildJsonObject {
                    put("success", false)
                    put("error", "空响应")
                }
            }
            
            val result = response.safeBody<JsonObject>()
            
            // 安全地访问JsonElement
            val success = when (val successValue = result) {
                is JsonObject -> {
                    when (val successField = successValue.jsonObject["success"]) {
                        is JsonPrimitive -> successField.content.toBoolean()
                        else -> false
                    }
                }
                else -> false
            }
            
            if (success) {
                log.d("成功更新用户设置")
            } else {
                val errorMsg = when (val errorField = result.jsonObject["error"]) {
                    is JsonPrimitive -> errorField.content
                    else -> "未知错误"
                }
                log.e("更新用户设置API返回失败: $errorMsg")
            }
            
            return@withContext result
        } catch (e: Exception) {
            log.e("更新用户设置失败", e)
            return@withContext buildJsonObject {
                put("success", false)
                put("error", "请求失败: ${e.message}")
            }
        }
    }

    /**
     * 查询当前用户所有会话
     */
    suspend fun getUserSessions(userId: String): JsonObject = withContext(Dispatchers.IO) {
        try {
            val response = functions.invoke(
                function = "user-sessions?user_id=$userId",
                headers = io.ktor.http.Headers.build {
                    append("Method", "GET")
                }
            )
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("获取用户会话失败", e)
            throw e
        }
    }

    /**
     * 新增或刷新会话
     */
    suspend fun updateUserSession(
        userId: String,
        expiresAt: String,
        deviceInfo: String? = null,
        ipAddress: String? = null,
        platform: String? = null,
        appVersion: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        try {
            val requestData = buildJsonObject {
                put("user_id", userId)
                put("expires_at", expiresAt)
                deviceInfo?.let { put("device_info", it) }
                ipAddress?.let { put("ip_address", it) }
                platform?.let { put("platform", it) }
                appVersion?.let { put("app_version", it) }
            }
            val response = functions.invoke(
                function = "user-sessions",
                body = requestData
            )
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("更新用户会话失败", e)
            throw e
        }
    }

    /**
     * 直接用 HTTP DELETE 方法删除 user_sessions（兼容 Supabase Edge Function）
     */
    suspend fun deleteUserSessionHttp(
        userId: String? = null,
        id: String? = null,
        accessToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://sjlmgylmcxrapwxjfzhy.supabase.co/functions/v1/user-sessions"
        val client = HttpClient() // 可替换为全局 client
        val body = buildJsonObject {
            userId?.let { put("user_id", it) }
            id?.let { put("id", it) }
        }
        val response = client.request(url) {
            method = HttpMethod.Delete
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return@withContext response.status.value in 200..299
    }

    /**
     * 兼容 supabase-kt invoke 的删除会话方法（POST+header方式，部分后端可用）
     */
    suspend fun deleteUserSession(id: String? = null, userId: String? = null): JsonObject = withContext(Dispatchers.IO) {
        try {
            val requestData = buildJsonObject {
                id?.let { put("id", it) }
                userId?.let { put("user_id", it) }
            }
            val response = functions.invoke(
                function = "user-sessions",
                headers = io.ktor.http.Headers.build {
                    append("Method", "DELETE")
                },
                body = requestData
            )
            return@withContext response.safeBody<JsonObject>()
        } catch (e: Exception) {
            log.e("删除用户会话失败", e)
            throw e
        }
    }
} 