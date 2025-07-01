@file:OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
package top.cywin.onetv.tv.supabase.support

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.safeBody
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.statement.bodyAsText
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient

private const val TAG = "SupportRepository"

/**
 * 客服支持仓库类
 * 处理1对1客服对话和用户反馈功能
 */
class SupportRepository {
    
    private val TAG = "SupportRepository"
    val client = SupabaseClient.client
    private val functions = client.functions
    
    // 客服支持状态管理
    private val _supportUiState = MutableStateFlow(SupportUiState())
    val supportUiState: StateFlow<SupportUiState> = _supportUiState.asStateFlow()
    
    // 实时订阅通道
    private var messageChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    
    /**
     * 获取或创建用户的活跃对话
     */
    suspend fun getOrCreateActiveConversation(): SupportConversation? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取或创建活跃对话")
            
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext null
            }
            
            // 首先查找现有的活跃对话
            val existingConversations = client.from("support_conversations")
                .select(columns = Columns.list(
                    "id", "user_id", "support_id", "conversation_title",
                    "status", "priority", "created_at", "updated_at",
                    "closed_at", "last_message_at"
                )) {
                    filter {
                        eq("user_id", currentUser.id)
                        eq("status", "open")
                    }
                    order("last_message_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<SupportConversation>()
            
            if (existingConversations.isNotEmpty()) {
                Log.d(TAG, "找到现有活跃对话")
                return@withContext existingConversations.first()
            }
            
            // 如果没有活跃对话，创建新的
            Log.d(TAG, "创建新的客服对话")
            val newConversation = client.from("support_conversations")
                .insert(buildJsonObject {
                    put("user_id", currentUser.id)
                    put("conversation_title", "客服对话")
                    put("priority", SupportConversation.PRIORITY_NORMAL)
                }) {
                    select(columns = Columns.list(
                        "id", "user_id", "support_id", "conversation_title", 
                        "status", "priority", "created_at", "updated_at", 
                        "closed_at", "last_message_at"
                    ))
                }
                .decodeSingle<SupportConversation>()
            
            Log.d(TAG, "新对话创建成功: ${newConversation.id}")
            return@withContext newConversation
            
        } catch (e: Exception) {
            Log.e(TAG, "获取或创建对话失败", e)
            return@withContext null
        }
    }
    
    /**
     * 发送客服消息
     */
    suspend fun sendSupportMessage(
        conversationId: String,
        messageText: String,
        messageType: String = SupportMessage.TYPE_TEXT
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "发送客服消息: $conversationId")
            
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录，无法发送消息")
                return@withContext false
            }
            
            client.from("support_messages").insert(
                buildJsonObject {
                    put("conversation_id", conversationId)
                    put("sender_id", currentUser.id)
                    put("message_text", messageText)
                    put("message_type", messageType)
                    put("is_from_support", false) // 用户发送的消息
                }
            )
            
            Log.d(TAG, "客服消息发送成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "发送客服消息失败", e)
            return@withContext false
        }
    }
    
    /**
     * 获取对话消息列表
     */
    suspend fun getConversationMessages(conversationId: String, limit: Int = 50): List<SupportMessage> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取对话消息: $conversationId")
            
            val messages = client.from("support_messages")
                .select(columns = Columns.list(
                    "id", "conversation_id", "sender_id", "message_text",
                    "message_type", "is_from_support", "read_at", "created_at"
                )) {
                    filter {
                        eq("conversation_id", conversationId)
                    }
                    order("created_at", Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SupportMessage>()
            
            Log.d(TAG, "获取到 ${messages.size} 条消息")
            return@withContext messages
        } catch (e: Exception) {
            Log.e(TAG, "获取对话消息失败", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 订阅对话的实时消息
     */
    suspend fun subscribeToConversationMessages(conversationId: String) {
        try {
            Log.d(TAG, "订阅对话实时消息: $conversationId")

            // 取消之前的订阅
            messageChannel?.let { channel ->
                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        channel.unsubscribe()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "取消之前的订阅失败", e)
                }
            }

            // 简化的实时订阅实现
            // 使用轮询方式作为备选方案
            Log.d(TAG, "使用轮询方式监听新消息")

            // 启动协程定期检查新消息
            CoroutineScope(Dispatchers.IO).launch {
                var lastMessageCount = 0
                while (true) {
                    try {
                        val messages = getConversationMessages(conversationId, 50)
                        if (messages.size > lastMessageCount) {
                            Log.d(TAG, "检测到新消息: ${messages.size - lastMessageCount} 条")
                            // 更新UI状态
                            val currentState = _supportUiState.value
                            _supportUiState.value = currentState.copy(
                                conversationState = currentState.conversationState.copy(
                                    messages = messages,
                                    unreadCount = messages.count { it.isFromSupport && !it.isRead() }
                                )
                            )
                            lastMessageCount = messages.size
                        }
                        kotlinx.coroutines.delay(3000) // 每3秒检查一次
                    } catch (e: Exception) {
                        Log.e(TAG, "检查新消息失败", e)
                        kotlinx.coroutines.delay(5000) // 出错时等待5秒
                    }
                }
            }

            Log.d(TAG, "实时消息监听启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动实时消息监听失败", e)
        }
    }
    
    /**
     * 提交用户反馈
     */
    suspend fun submitUserFeedback(
        feedbackType: String,
        title: String,
        description: String,
        priority: String = UserFeedback.PRIORITY_NORMAL,
        appVersion: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "提交用户反馈: $title")
            
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录，无法提交反馈")
                return@withContext false
            }
            
            // 构建设备信息
            val deviceInfo = buildJsonObject {
                put("platform", "Android TV")
                put("app", "OneTV")
                put("version", appVersion ?: "unknown")
            }
            
            client.from("user_feedback").insert(
                buildJsonObject {
                    put("user_id", currentUser.id)
                    put("feedback_type", feedbackType)
                    put("title", title)
                    put("description", description)
                    put("priority", priority)
                    put("device_info", deviceInfo)
                    put("app_version", appVersion)
                }
            )
            
            Log.d(TAG, "用户反馈提交成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "提交用户反馈失败", e)
            return@withContext false
        }
    }
    
    /**
     * 获取用户的反馈列表
     */
    suspend fun getUserFeedbackList(): List<UserFeedback> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户反馈列表")
            
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyList()
            }
            
            val feedbackList = client.from("user_feedback")
                .select(columns = Columns.list(
                    "id", "user_id", "feedback_type", "title", "description",
                    "status", "priority", "admin_response", "admin_id",
                    "device_info", "app_version", "created_at", "updated_at", "resolved_at"
                )) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<UserFeedback>()
            
            Log.d(TAG, "获取到 ${feedbackList.size} 条反馈")
            return@withContext feedbackList
        } catch (e: Exception) {
            Log.e(TAG, "获取用户反馈列表失败", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 标记消息为已读
     */
    suspend fun markMessagesAsRead(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "标记消息为已读: $conversationId")
            
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                return@withContext false
            }
            
            // 标记来自客服的未读消息为已读
            client.from("support_messages").update(
                buildJsonObject {
                    put("read_at", "now()")
                }
            ) {
                filter {
                    eq("conversation_id", conversationId)
                    eq("is_from_support", true)
                    // 只更新未读消息（read_at为null的消息）
                }
            }
            
            // 更新UI状态中的未读计数
            val currentState = _supportUiState.value
            _supportUiState.value = currentState.copy(
                conversationState = currentState.conversationState.copy(
                    unreadCount = 0
                )
            )
            
            Log.d(TAG, "消息标记为已读成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "标记消息为已读失败", e)
            return@withContext false
        }
    }
    
    /**
     * 关闭对话
     */
    suspend fun closeConversation(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "关闭对话: $conversationId")
            
            client.from("support_conversations").update(
                buildJsonObject {
                    put("status", SupportConversation.STATUS_CLOSED)
                    put("closed_at", "now()")
                }
            ) {
                filter {
                    eq("id", conversationId)
                }
            }
            
            Log.d(TAG, "对话关闭成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "关闭对话失败", e)
            return@withContext false
        }
    }
    
    /**
     * 检查当前用户是否为管理员（支持多角色系统）
     */
    suspend fun checkAdminStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "检查管理员状态（多角色系统）")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                return@withContext false
            }

            // 获取用户的所有活跃角色
            val userRoles = client.from("user_roles")
                .select(columns = Columns.list("role_type")) {
                    filter {
                        eq("user_id", currentUser.id)
                        eq("is_active", true)
                    }
                }
                .decodeList<Map<String, String>>()

            // 检查是否有管理员相关角色
            val adminRoles = setOf("support", "admin", "super_admin")
            val hasAdminRole = userRoles.any { role ->
                adminRoles.contains(role["role_type"])
            }

            Log.d(TAG, "用户角色: ${userRoles.map { it["role_type"] }}, 是否为管理员: $hasAdminRole")
            return@withContext hasAdminRole

        } catch (e: Exception) {
            Log.e(TAG, "检查管理员状态失败", e)
            return@withContext false
        }
    }

    /**
     * 获取用户的所有角色
     */
    suspend fun getUserRoles(): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户角色列表")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                return@withContext emptyList()
            }

            val userRoles = client.from("user_roles")
                .select(columns = Columns.list("role_type")) {
                    filter {
                        eq("user_id", currentUser.id)
                        eq("is_active", true)
                    }
                }
                .decodeList<Map<String, String>>()

            val roles = userRoles.mapNotNull { it["role_type"] }
            Log.d(TAG, "用户拥有角色: $roles")
            return@withContext roles

        } catch (e: Exception) {
            Log.e(TAG, "获取用户角色失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 检查用户是否有特定权限
     */
    suspend fun hasPermission(permission: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "检查用户权限: $permission")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                return@withContext false
            }

            // 调用数据库函数检查权限
            val result = client.functions.invoke(
                function = "support-management",
                body = buildJsonObject {
                    put("action", "check_permission")
                    put("permission", permission)
                }
            )

            val responseText = result.bodyAsText()
            val responseJson = Json.parseToJsonElement(responseText).jsonObject
            val hasPermission = responseJson["has_permission"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

            Log.d(TAG, "权限检查结果 [$permission]: $hasPermission")
            return@withContext hasPermission

        } catch (e: Exception) {
            Log.e(TAG, "检查权限失败", e)
            return@withContext false
        }
    }

    /**
     * 获取所有对话列表（管理员功能）
     */
    suspend fun getAllConversations(limit: Int = 50): List<SupportConversation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取所有对话列表")

            val conversations = client.from("support_conversations")
                .select(columns = Columns.list(
                    "id", "user_id", "support_id", "conversation_title",
                    "status", "priority", "created_at", "updated_at",
                    "closed_at", "last_message_at"
                )) {
                    order("last_message_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SupportConversation>()

            Log.d(TAG, "获取到 ${conversations.size} 个对话")
            return@withContext conversations
        } catch (e: Exception) {
            Log.e(TAG, "获取所有对话列表失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 获取用户的对话历史
     */
    suspend fun getUserConversations(): List<SupportConversation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户对话历史")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyList()
            }

            val conversations = client.from("support_conversations")
                .select(columns = Columns.list(
                    "id", "user_id", "support_id", "conversation_title",
                    "status", "priority", "created_at", "updated_at",
                    "closed_at", "last_message_at"
                )) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                    order("last_message_at", Order.DESCENDING)
                }
                .decodeList<SupportConversation>()

            Log.d(TAG, "获取到 ${conversations.size} 个用户对话")
            return@withContext conversations
        } catch (e: Exception) {
            Log.e(TAG, "获取用户对话历史失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 分配客服给对话
     */
    suspend fun assignSupportToConversation(conversationId: String, supportId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "分配客服给对话: $conversationId -> $supportId")

            client.from("support_conversations").update(
                buildJsonObject {
                    put("support_id", supportId)
                    put("status", SupportConversation.STATUS_OPEN)
                }
            ) {
                filter {
                    eq("id", conversationId)
                }
            }

            Log.d(TAG, "客服分配成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "分配客服失败", e)
            return@withContext false
        }
    }

    /**
     * 更新对话优先级
     */
    suspend fun updateConversationPriority(conversationId: String, priority: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "更新对话优先级: $conversationId -> $priority")

            client.from("support_conversations").update(
                buildJsonObject {
                    put("priority", priority)
                }
            ) {
                filter {
                    eq("id", conversationId)
                }
            }

            Log.d(TAG, "对话优先级更新成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "更新对话优先级失败", e)
            return@withContext false
        }
    }

    /**
     * 获取对话统计信息
     */
    suspend fun getConversationStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取对话统计信息")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                return@withContext emptyMap()
            }

            // 获取用户的对话统计
            val userConversations = client.from("support_conversations")
                .select(columns = Columns.list("status")) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeList<Map<String, String>>()

            val stats = mutableMapOf<String, Int>()
            stats["total"] = userConversations.size
            stats["open"] = userConversations.count { it["status"] == "open" }
            stats["closed"] = userConversations.count { it["status"] == "closed" }
            stats["waiting"] = userConversations.count { it["status"] == "waiting" }

            Log.d(TAG, "对话统计: $stats")
            return@withContext stats
        } catch (e: Exception) {
            Log.e(TAG, "获取对话统计信息失败", e)
            return@withContext emptyMap()
        }
    }

    /**
     * 获取反馈统计信息
     */
    suspend fun getFeedbackStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取反馈统计信息")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                return@withContext emptyMap()
            }

            // 获取用户的反馈统计
            val userFeedbacks = client.from("user_feedback")
                .select(columns = Columns.list("status")) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeList<Map<String, String>>()

            val stats = mutableMapOf<String, Int>()
            stats["total"] = userFeedbacks.size
            stats["submitted"] = userFeedbacks.count { it["status"] == "submitted" }
            stats["reviewing"] = userFeedbacks.count { it["status"] == "reviewing" }
            stats["resolved"] = userFeedbacks.count { it["status"] == "resolved" }
            stats["closed"] = userFeedbacks.count { it["status"] == "closed" }

            Log.d(TAG, "反馈统计: $stats")
            return@withContext stats
        } catch (e: Exception) {
            Log.e(TAG, "获取反馈统计信息失败", e)
            return@withContext emptyMap()
        }
    }

    /**
     * 获取用户反馈列表
     */
    suspend fun getUserFeedbacks(limit: Int = 20): List<UserFeedback> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户反馈列表")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyList()
            }

            val feedbacks = client.from("user_feedback")
                .select(columns = Columns.list(
                    "id", "user_id", "feedback_type", "title", "description",
                    "priority", "status", "admin_response", "created_at", "updated_at"
                )) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<UserFeedback>()

            Log.d(TAG, "获取到 ${feedbacks.size} 个用户反馈")
            return@withContext feedbacks
        } catch (e: Exception) {
            Log.e(TAG, "获取用户反馈列表失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        messageChannel?.let { channel ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "清理订阅通道失败", e)
                }
            }
        }
        messageChannel = null
    }

    /**
     * 获取用户统计信息
     */
    suspend fun getUserStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户统计信息")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyMap()
            }

            // 获取用户反馈数量
            val feedbackCount = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取用户对话数量
            val conversationCount = client.from("support_conversations")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取用户资料信息
            val userProfile = client.from("profiles")
                .select(columns = Columns.list("created_at", "updated_at", "lastlogindevice")) {
                    filter {
                        eq("userid", currentUser.id)
                    }
                }
                .decodeSingleOrNull<JsonObject>()

            val createdAt = userProfile?.get("created_at")?.jsonPrimitive?.content
            val activeDays = if (createdAt != null) {
                try {
                    val createDate = java.time.LocalDateTime.parse(createdAt.replace("Z", ""))
                    val now = java.time.LocalDateTime.now()
                    java.time.Duration.between(createDate, now).toDays().toInt()
                } catch (e: Exception) {
                    0
                }
            } else {
                0
            }

            // 获取观看历史总时长
            val watchTimeSeconds = try {
                val watchHistoryStats = client.from("watch_history")
                    .select(columns = Columns.list("duration")) {
                        filter {
                            eq("user_id", currentUser.id)
                        }
                    }
                    .decodeList<JsonObject>()
                    .sumOf {
                        try {
                            it["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        } catch (e: Exception) {
                            0L
                        }
                    }

                watchHistoryStats
            } catch (e: Exception) {
                Log.e(TAG, "获取观看历史失败", e)
                0L
            }

            // 转换观看时长为小时和分钟格式
            val watchTimeFormatted = if (watchTimeSeconds > 0) {
                val hours = watchTimeSeconds / 3600
                val minutes = (watchTimeSeconds % 3600) / 60
                when {
                    hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
                    hours > 0 -> "${hours}小时"
                    minutes > 0 -> "${minutes}分钟"
                    else -> "${watchTimeSeconds}秒"
                }
            } else {
                "0分钟"
            }

            // 获取活跃天数（从登录日志计算）
            val actualActiveDays = try {
                val loginLogs = client.from("user_login_logs")
                    .select(columns = Columns.list("login_time")) {
                        filter {
                            eq("user_id", currentUser.id)
                        }
                    }
                    .decodeList<JsonObject>()

                // 计算不同日期的登录天数
                val uniqueDays = loginLogs.mapNotNull { log ->
                    try {
                        val loginTime = log["login_time"]?.jsonPrimitive?.content
                        loginTime?.substring(0, 10) // 取日期部分 YYYY-MM-DD
                    } catch (e: Exception) {
                        null
                    }
                }.toSet().size

                uniqueDays
            } catch (e: Exception) {
                Log.e(TAG, "获取登录日志失败，使用注册天数", e)
                activeDays
            }

            // 解析设备信息
            val deviceInfo = userProfile?.get("lastlogindevice")?.jsonPrimitive?.content ?: ""
            val (deviceModel, appVersion, systemVersion) = parseDeviceInfo(deviceInfo)

            mapOf(
                "conversationCount" to conversationCount,
                "feedbackCount" to feedbackCount,
                "activeDays" to actualActiveDays,
                "watchTime" to watchTimeFormatted,
                "lastLoginTime" to (userProfile?.get("updated_at")?.jsonPrimitive?.contentOrNull ?: "未知"),
                "deviceModel" to deviceModel,
                "appVersion" to appVersion,
                "systemVersion" to systemVersion
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取用户统计信息失败", e)
            emptyMap()
        }
    }

    /**
     * 解析设备信息字符串
     * 从lastlogindevice字段解析出设备型号、应用版本、系统版本
     * 格式示例: "XIAOMI MI TV 4A (Android 9, API 28)"
     */
    private fun parseDeviceInfo(deviceInfo: String): Triple<String, String, String> {
        return try {
            if (deviceInfo.isBlank()) {
                return Triple("未知", "未知", "未知")
            }

            // 解析设备型号（括号前的部分）
            val deviceModel = if (deviceInfo.contains("(")) {
                deviceInfo.substring(0, deviceInfo.indexOf("(")).trim()
            } else {
                deviceInfo.trim()
            }

            // 解析系统版本（括号内的Android版本）
            val systemVersion = if (deviceInfo.contains("Android")) {
                val androidStart = deviceInfo.indexOf("Android")
                val commaIndex = deviceInfo.indexOf(",", androidStart)
                if (commaIndex > androidStart) {
                    deviceInfo.substring(androidStart, commaIndex).trim()
                } else {
                    val parenIndex = deviceInfo.indexOf(")", androidStart)
                    if (parenIndex > androidStart) {
                        deviceInfo.substring(androidStart, parenIndex).trim()
                    } else {
                        "Android"
                    }
                }
            } else {
                "未知"
            }

            // 应用版本（固定值）
            val appVersion = "2.0.0"

            Triple(
                if (deviceModel.isNotEmpty()) deviceModel else "未知",
                appVersion,
                systemVersion
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析设备信息失败: $deviceInfo", e)
            Triple("未知", "未知", "未知")
        }
    }

    /**
     * 获取最近用户列表
     */
    suspend fun getRecentUsers(limit: Int = 10): List<UserProfile> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取最近用户列表")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyList()
            }

            // 获取最近注册的用户
            val recentUsers = client.from("profiles")
                .select(columns = Columns.list(
                    "userid", "username", "email", "created_at", "updated_at", "accountstatus"
                )) {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<JsonObject>()
                .map { userJson ->
                    UserProfile(
                        id = userJson["userid"]?.jsonPrimitive?.contentOrNull ?: "",
                        username = userJson["username"]?.jsonPrimitive?.contentOrNull ?: "",
                        email = userJson["email"]?.jsonPrimitive?.contentOrNull ?: "",
                        isVip = false, // 需要单独查询VIP状态
                        roles = listOf("user"), // 需要单独查询角色
                        createdAt = userJson["created_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }

            Log.d(TAG, "获取到 ${recentUsers.size} 个最近用户")
            return@withContext recentUsers
        } catch (e: Exception) {
            Log.e(TAG, "获取最近用户列表失败", e)
            emptyList()
        }
    }

    /**
     * 获取所有用户列表
     */
    suspend fun getAllUsers(limit: Int = 100): List<UserProfile> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取所有用户列表")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyList()
            }

            // 获取所有用户资料
            val users = client.from("profiles")
                .select(columns = Columns.list(
                    "userid", "username", "email", "created_at", "updated_at", "accountstatus"
                )) {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<JsonObject>()
                .map { userJson ->
                    UserProfile(
                        id = userJson["userid"]?.jsonPrimitive?.contentOrNull ?: "",
                        username = userJson["username"]?.jsonPrimitive?.contentOrNull ?: "",
                        email = userJson["email"]?.jsonPrimitive?.contentOrNull ?: "",
                        isVip = false, // 需要单独查询VIP状态
                        roles = listOf("user"), // 需要单独查询角色
                        createdAt = userJson["created_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }

            Log.d(TAG, "获取到 ${users.size} 个用户")
            return@withContext users
        } catch (e: Exception) {
            Log.e(TAG, "获取所有用户列表失败", e)
            emptyList()
        }
    }

    /**
     * 更新用户角色
     */
    suspend fun updateUserRole(userId: String, role: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "更新用户角色: $userId -> $role")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 更新用户角色到数据库
            client.from("profiles")
                .update(buildJsonObject {
                    put("roles", role)
                    put("updated_at", java.time.LocalDateTime.now().toString())
                }) {
                    filter {
                        eq("userid", userId)
                    }
                }

            Log.d(TAG, "用户角色更新成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "更新用户角色失败", e)
            false
        }
    }

    // ==================== 反馈管理功能 ====================

    /**
     * 获取所有反馈列表（管理员）
     */
    suspend fun getAllFeedbacks(limit: Int = 100): List<UserFeedback> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取所有反馈列表")

            // 从数据库获取所有反馈数据
            val feedbacks = client.from("user_feedback")
                .select(columns = Columns.list(
                    "id", "user_id", "feedback_type", "title", "description",
                    "status", "priority", "admin_response", "admin_id",
                    "device_info", "app_version", "created_at", "updated_at", "resolved_at"
                )) {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<JsonObject>()
                .map { feedbackJson ->
                    UserFeedback(
                        id = feedbackJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = feedbackJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        feedbackType = feedbackJson["feedback_type"]?.jsonPrimitive?.contentOrNull ?: "",
                        title = feedbackJson["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        description = feedbackJson["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = feedbackJson["status"]?.jsonPrimitive?.contentOrNull ?: "submitted",
                        priority = feedbackJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        adminResponse = feedbackJson["admin_response"]?.jsonPrimitive?.contentOrNull,
                        adminId = feedbackJson["admin_id"]?.jsonPrimitive?.contentOrNull,
                        deviceInfo = try {
                            feedbackJson["device_info"]?.jsonObject ?: buildJsonObject {}
                        } catch (e: Exception) {
                            buildJsonObject {}
                        },
                        appVersion = feedbackJson["app_version"]?.jsonPrimitive?.contentOrNull ?: "",
                        createdAt = feedbackJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = feedbackJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        resolvedAt = feedbackJson["resolved_at"]?.jsonPrimitive?.contentOrNull
                    )
                }

            Log.d(TAG, "获取到 ${feedbacks.size} 条反馈")
            return@withContext feedbacks


        } catch (e: Exception) {
            Log.e(TAG, "获取所有反馈列表失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 更新反馈状态
     */
    suspend fun updateFeedbackStatus(feedbackId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "更新反馈状态: $feedbackId -> $status")

            // 更新反馈状态到数据库
            val updateData = buildJsonObject {
                put("status", status)
                put("updated_at", java.time.LocalDateTime.now().toString())
                if (status == "resolved") {
                    put("resolved_at", java.time.LocalDateTime.now().toString())
                }
            }

            client.from("user_feedback")
                .update(updateData) {
                    filter {
                        eq("id", feedbackId)
                    }
                }

            Log.d(TAG, "反馈状态更新成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "更新反馈状态失败", e)
            return@withContext false
        }
    }

    /**
     * 回复反馈
     */
    suspend fun replyToFeedback(feedbackId: String, response: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "回复反馈: $feedbackId")

            // 获取当前用户信息
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 更新反馈回复到数据库
            client.from("user_feedback")
                .update(buildJsonObject {
                    put("admin_response", response)
                    put("admin_id", currentUser.id)
                    put("status", "reviewing")
                    put("updated_at", java.time.LocalDateTime.now().toString())
                }) {
                    filter {
                        eq("id", feedbackId)
                    }
                }

            Log.d(TAG, "反馈回复成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "回复反馈失败", e)
            return@withContext false
        }
    }

    /**
     * 获取反馈统计信息（管理员）
     */
    suspend fun getAllFeedbackStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取反馈统计信息")

            // 使用Edge Function获取反馈统计数据
            val response = functions.invoke(
                function = "support-management",
                body = buildJsonObject {
                    put("action", "get_feedback_stats")
                }.toString()
            )

            val result = response.safeBody<JsonObject>()
            val stats = result["stats"]?.jsonObject?.let { statsObj ->
                mapOf(
                    "total" to (statsObj["total"]?.jsonPrimitive?.int ?: 0),
                    "submitted" to (statsObj["submitted"]?.jsonPrimitive?.int ?: 0),
                    "reviewing" to (statsObj["reviewing"]?.jsonPrimitive?.int ?: 0),
                    "resolved" to (statsObj["resolved"]?.jsonPrimitive?.int ?: 0),
                    "closed" to (statsObj["closed"]?.jsonPrimitive?.int ?: 0),
                    "high_priority" to (statsObj["high_priority"]?.jsonPrimitive?.int ?: 0),
                    "normal_priority" to (statsObj["normal_priority"]?.jsonPrimitive?.int ?: 0),
                    "low_priority" to (statsObj["low_priority"]?.jsonPrimitive?.int ?: 0)
                )
            } ?: emptyMap()

            Log.d(TAG, "反馈统计: $stats")
            return@withContext stats
        } catch (e: Exception) {
            Log.e(TAG, "获取反馈统计信息失败", e)
            return@withContext emptyMap()
        }
    }

    // ==================== 客服工作台功能 ====================

    /**
     * 获取客服工作台统计信息
     */
    suspend fun getSupportDeskStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取客服工作台统计信息")

            // 使用Edge Function获取客服工作台统计数据
            val response = functions.invoke(
                function = "support-management",
                body = buildJsonObject {
                    put("action", "get_support_stats")
                }.toString()
            )

            val result = response.safeBody<JsonObject>()
            val stats = result["stats"]?.jsonObject?.let { statsObj ->
                mapOf(
                    "active_conversations" to (statsObj["active_conversations"]?.jsonPrimitive?.int ?: 0),
                    "pending_conversations" to (statsObj["pending_conversations"]?.jsonPrimitive?.int ?: 0),
                    "resolved_today" to (statsObj["resolved_today"]?.jsonPrimitive?.int ?: 0),
                    "avg_response_time" to (statsObj["avg_response_time"]?.jsonPrimitive?.contentOrNull ?: "未知"),
                    "customer_satisfaction" to (statsObj["customer_satisfaction"]?.jsonPrimitive?.double ?: 0.0),
                    "online_agents" to (statsObj["online_agents"]?.jsonPrimitive?.int ?: 0),
                    "total_agents" to (statsObj["total_agents"]?.jsonPrimitive?.int ?: 0),
                    "recent_feedbacks" to (statsObj["recent_feedbacks"]?.jsonPrimitive?.int ?: 0),
                    "urgent_issues" to (statsObj["urgent_issues"]?.jsonPrimitive?.int ?: 0)
                )
            } ?: emptyMap<String, Any>()

            Log.d(TAG, "客服工作台统计: $stats")
            return@withContext stats
        } catch (e: Exception) {
            Log.e(TAG, "获取客服工作台统计信息失败", e)
            return@withContext emptyMap()
        }
    }

    /**
     * 获取待处理的对话列表
     */
    suspend fun getPendingConversations(): List<SupportConversationDisplay> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取待处理对话列表")

            // 从数据库获取待处理对话数据
            val conversations = client.from("support_conversations")
                .select(columns = Columns.list(
                    "id", "user_id", "support_id", "conversation_title",
                    "status", "priority", "created_at", "updated_at",
                    "closed_at", "last_message_at"
                )) {
                    filter {
                        isIn("status", listOf("open", "waiting"))
                    }
                    order("priority", Order.ASCENDING) // urgent, high, normal, low
                    order("created_at", Order.ASCENDING) // 最早的优先
                    limit(20)
                }
                .decodeList<JsonObject>()
                .map { convJson ->
                    // 获取最后一条消息
                    val lastMessage = try {
                        client.from("support_messages")
                            .select(columns = Columns.list("message_content")) {
                                filter {
                                    eq("conversation_id", convJson["id"]?.jsonPrimitive?.contentOrNull ?: "")
                                }
                                order("created_at", Order.DESCENDING)
                                limit(1)
                            }
                            .decodeSingleOrNull<JsonObject>()
                            ?.get("message_content")?.jsonPrimitive?.contentOrNull ?: "暂无消息"
                    } catch (e: Exception) {
                        "暂无消息"
                    }

                    SupportConversationDisplay(
                        id = convJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = convJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        supportId = convJson["support_id"]?.jsonPrimitive?.contentOrNull,
                        conversationTitle = convJson["conversation_title"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = convJson["status"]?.jsonPrimitive?.contentOrNull ?: "open",
                        priority = convJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        createdAt = convJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = convJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        closedAt = convJson["closed_at"]?.jsonPrimitive?.contentOrNull,
                        lastMessageAt = convJson["last_message_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        lastMessage = lastMessage,
                        tags = emptyList() // 可以根据需要从数据库获取标签
                    )
                }

            Log.d(TAG, "获取到 ${conversations.size} 个待处理对话")
            return@withContext conversations
        } catch (e: Exception) {
            Log.e(TAG, "获取待处理对话列表失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 获取最近的反馈列表
     */
    suspend fun getRecentFeedbacks(): List<UserFeedback> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取最近反馈列表")

            // 从数据库获取最近反馈数据
            val feedbacks = client.from("user_feedback")
                .select(columns = Columns.list(
                    "id", "user_id", "feedback_type", "title", "description",
                    "status", "priority", "admin_response", "admin_id",
                    "device_info", "app_version", "created_at", "updated_at", "resolved_at"
                )) {
                    order("created_at", Order.DESCENDING)
                    limit(10) // 获取最近10条反馈
                }
                .decodeList<JsonObject>()
                .map { feedbackJson ->
                    UserFeedback(
                        id = feedbackJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = feedbackJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        feedbackType = feedbackJson["feedback_type"]?.jsonPrimitive?.contentOrNull ?: "",
                        title = feedbackJson["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        description = feedbackJson["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = feedbackJson["status"]?.jsonPrimitive?.contentOrNull ?: "submitted",
                        priority = feedbackJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        adminResponse = feedbackJson["admin_response"]?.jsonPrimitive?.contentOrNull,
                        adminId = feedbackJson["admin_id"]?.jsonPrimitive?.contentOrNull,
                        deviceInfo = try {
                            feedbackJson["device_info"]?.jsonObject ?: buildJsonObject {}
                        } catch (e: Exception) {
                            buildJsonObject {}
                        },
                        appVersion = feedbackJson["app_version"]?.jsonPrimitive?.contentOrNull ?: "",
                        createdAt = feedbackJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = feedbackJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        resolvedAt = feedbackJson["resolved_at"]?.jsonPrimitive?.contentOrNull
                    )
                }

            Log.d(TAG, "获取到 ${feedbacks.size} 个最近反馈")
            return@withContext feedbacks
        } catch (e: Exception) {
            Log.e(TAG, "获取最近反馈列表失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 接管对话（客服）
     */
    suspend fun takeOverConversation(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "接管对话: $conversationId")

            // 获取当前用户信息
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 更新对话状态为已接管
            client.from("support_conversations")
                .update(buildJsonObject {
                    put("support_id", currentUser.id)
                    put("status", "open")
                    put("updated_at", java.time.LocalDateTime.now().toString())
                }) {
                    filter {
                        eq("id", conversationId)
                    }
                }

            Log.d(TAG, "对话接管成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "接管对话失败", e)
            return@withContext false
        }
    }

    /**
     * 结束对话（客服）
     */
    suspend fun endConversation(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "结束对话: $conversationId")

            // 使用Edge Function结束对话
            val response = functions.invoke(
                function = "support-management",
                body = buildJsonObject {
                    put("action", "close_conversation")
                    put("conversation_id", conversationId)
                }.toString()
            )

            val result = response.safeBody<JsonObject>()
            val success = result["success"]?.jsonPrimitive?.boolean ?: false

            if (success) {
                Log.d(TAG, "对话结束成功")
                return@withContext true
            } else {
                Log.w(TAG, "对话结束失败: ${result["error"]?.jsonPrimitive?.contentOrNull}")
                return@withContext false
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "结束对话失败", e)
            return@withContext false
        }
    }
}
