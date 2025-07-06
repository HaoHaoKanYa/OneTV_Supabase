@file:OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
package top.cywin.onetv.tv.supabase.support

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Job
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.collect
import io.github.jan.supabase.safeBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient

private const val TAG = "SupportRepository"

/**
 * 客服支持仓库类
 * 处理1对1客服对话和用户反馈功能
 */
class SupportRepository(private val context: Context) {

    private val TAG = "SupportRepository"
    val client = SupabaseClient.client
    private val functions = client.functions

    // 重试配置
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    /**
     * 获取应用版本号
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "未知版本"
        } catch (e: Exception) {
            Log.e(TAG, "获取应用版本失败", e)
            "未知版本"
        }
    }

    // 消息更新回调
    private var onMessagesUpdated: ((List<SupportMessage>) -> Unit)? = null

    // 实时订阅通道
    private var messageChannel: RealtimeChannel? = null

    /**
     * 带重试机制的操作执行器
     * 用于处理网络超时和临时错误
     */
    private suspend fun <T> executeWithRetry(
        operation: String,
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                Log.d(TAG, "执行操作: $operation (尝试 ${attempt + 1}/$maxAttempts)")
                return block()
            } catch (e: Exception) {
                lastException = e
                val isTimeout = e.message?.contains("timeout", ignoreCase = true) == true
                val isNetworkError = e.message?.contains("network", ignoreCase = true) == true

                Log.w(TAG, "操作失败 (尝试 ${attempt + 1}/$maxAttempts): $operation", e)

                if (isTimeout || isNetworkError) {
                    if (attempt < maxAttempts - 1) {
                        val delayMs = RETRY_DELAY_MS * (attempt + 1) // 递增延迟
                        Log.d(TAG, "网络错误，${delayMs}ms后重试...")
                        delay(delayMs)
                    }
                } else {
                    // 非网络错误，不重试
                    Log.e(TAG, "非网络错误，停止重试: ${e.message}")
                    throw e
                }
            }
        }

        // 所有重试都失败了
        Log.e(TAG, "操作最终失败: $operation")
        throw lastException ?: Exception("操作失败且无异常信息")
    }
    
    /**
     * 获取或创建用户的活跃对话
     */
    suspend fun getOrCreateActiveConversation(): SupportConversation? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== 开始获取或创建活跃对话 ===")
            Log.d(TAG, "连接状态: 连接中...")

            val currentUser = client.auth.currentUserOrNull()
            Log.d(TAG, "当前用户状态: ${if (currentUser != null) "已登录 - ${currentUser.id}" else "未登录"}")

            if (currentUser == null) {
                Log.w(TAG, "用户未登录，无法获取对话")
                Log.e(TAG, "连接状态: 连接失败 - 用户未登录")
                return@withContext null
            }

            // 首先查找现有的活跃对话
            Log.d(TAG, "查询现有活跃对话...")
            Log.d(TAG, "查询条件: user_id=${currentUser.id}, status=open")

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
                .decodeList<JsonObject>()
                .map { conversationJson ->
                    Log.d(TAG, "解析对话JSON: $conversationJson")
                    SupportConversation(
                        id = conversationJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = conversationJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        supportId = conversationJson["support_id"]?.jsonPrimitive?.contentOrNull,
                        conversationTitle = conversationJson["conversation_title"]?.jsonPrimitive?.contentOrNull ?: "客服对话",
                        status = conversationJson["status"]?.jsonPrimitive?.contentOrNull ?: "open",
                        priority = conversationJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        createdAt = conversationJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = conversationJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        closedAt = conversationJson["closed_at"]?.jsonPrimitive?.contentOrNull,
                        lastMessageAt = conversationJson["last_message_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }

            Log.d(TAG, "查询结果: 找到 ${existingConversations.size} 个活跃对话")

            if (existingConversations.isNotEmpty()) {
                val conversation = existingConversations.first()
                Log.d(TAG, "找到现有活跃对话:")
                Log.d(TAG, "  ID: ${conversation.id}")
                Log.d(TAG, "  标题: ${conversation.conversationTitle}")
                Log.d(TAG, "  状态: ${conversation.status}")
                Log.d(TAG, "  创建时间: ${conversation.createdAt}")
                Log.d(TAG, "  最后消息时间: ${conversation.lastMessageAt}")
                Log.d(TAG, "连接状态: 连接成功 - 使用现有对话")
                return@withContext conversation
            }

            // 如果没有活跃对话，创建新的
            Log.d(TAG, "没有找到活跃对话，开始创建新对话...")
            Log.d(TAG, "插入数据: user_id=${currentUser.id}, conversation_title=客服对话, priority=normal")

            val newConversationJson = client.from("support_conversations")
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
                .decodeSingle<JsonObject>()

            Log.d(TAG, "新对话创建响应JSON: $newConversationJson")

            val newConversation = SupportConversation(
                id = newConversationJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                userId = newConversationJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                supportId = newConversationJson["support_id"]?.jsonPrimitive?.contentOrNull,
                conversationTitle = newConversationJson["conversation_title"]?.jsonPrimitive?.contentOrNull ?: "客服对话",
                status = newConversationJson["status"]?.jsonPrimitive?.contentOrNull ?: "open",
                priority = newConversationJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                createdAt = newConversationJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                updatedAt = newConversationJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                closedAt = newConversationJson["closed_at"]?.jsonPrimitive?.contentOrNull,
                lastMessageAt = newConversationJson["last_message_at"]?.jsonPrimitive?.contentOrNull ?: ""
            )

            Log.d(TAG, "=== 新对话创建成功 ===")
            Log.d(TAG, "  ID: ${newConversation.id}")
            Log.d(TAG, "  标题: ${newConversation.conversationTitle}")
            Log.d(TAG, "  状态: ${newConversation.status}")
            Log.d(TAG, "  创建时间: ${newConversation.createdAt}")
            Log.d(TAG, "  最后消息时间: ${newConversation.lastMessageAt}")
            Log.d(TAG, "连接状态: 连接成功 - 创建新对话")

            return@withContext newConversation

        } catch (e: Exception) {
            Log.e(TAG, "=== 获取或创建对话失败 ===", e)
            Log.e(TAG, "异常详情: ${e.message}")
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "连接状态: 连接失败 - ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * 发送客服消息
     */
    suspend fun sendSupportMessage(
        conversationId: String,
        messageText: String,
        messageType: String = SupportMessage.TYPE_TEXT,
        isFromSupport: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== 开始发送客服消息 ===")
            Log.d(TAG, "对话ID: $conversationId")
            Log.d(TAG, "消息类型: $messageType")
            Log.d(TAG, "消息长度: ${messageText.length} 字符")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录，无法发送消息")
                Log.e(TAG, "消息发送失败: 用户未登录")
                return@withContext false
            }

            Log.d(TAG, "发送者ID: ${currentUser.id}")
            Log.d(TAG, "是否来自客服: $isFromSupport")

            // 使用重试机制发送消息
            executeWithRetry("发送客服消息") {
                Log.d(TAG, "开始向数据库插入消息...")
                val insertResult = client.from("support_messages").insert(
                    buildJsonObject {
                        put("conversation_id", conversationId)
                        put("sender_id", currentUser.id)
                        put("message_text", messageText)
                        put("message_type", messageType)
                        put("is_from_support", isFromSupport)
                    }
                )
                Log.d(TAG, "数据库插入操作完成，结果: $insertResult")
            }

            Log.d(TAG, "=== 客服消息发送成功 ===")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "=== 发送客服消息失败 ===", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            if (e.message?.contains("timeout", ignoreCase = true) == true) {
                Log.e(TAG, "网络超时错误: 消息发送超时，请检查网络连接")
            }
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
                .decodeList<JsonObject>()
                .map { messageJson ->
                    SupportMessage(
                        id = messageJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        conversationId = messageJson["conversation_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        senderId = messageJson["sender_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        messageText = messageJson["message_text"]?.jsonPrimitive?.contentOrNull ?: "",
                        messageType = messageJson["message_type"]?.jsonPrimitive?.contentOrNull ?: "text",
                        isFromSupport = messageJson["is_from_support"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        readAt = messageJson["read_at"]?.jsonPrimitive?.contentOrNull,
                        createdAt = messageJson["created_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }
            
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
    suspend fun subscribeToConversationMessages(
        conversationId: String,
        onMessagesUpdate: (List<SupportMessage>) -> Unit
    ) {
        try {
            Log.d(TAG, "=== 开始订阅对话实时消息 ===")
            Log.d(TAG, "对话ID: $conversationId")

            // 保存回调函数
            onMessagesUpdated = onMessagesUpdate

            // 取消之前的订阅
            messageChannel?.let { channel ->
                try {
                    Log.d(TAG, "取消之前的订阅...")
                    CoroutineScope(Dispatchers.IO).launch {
                        channel.unsubscribe()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "取消之前的订阅失败", e)
                }
            }

            // 首先加载现有消息
            Log.d(TAG, "加载现有消息...")
            val existingMessages = getConversationMessages(conversationId, 50)
            Log.d(TAG, "现有消息数量: ${existingMessages.size}")
            onMessagesUpdate(existingMessages)

            // 使用Supabase Realtime实现真正的实时订阅
            Log.d(TAG, "创建Supabase实时消息订阅...")
            try {
                messageChannel = client.realtime.channel("support_messages_$conversationId")

                // 订阅通道
                messageChannel?.subscribe(blockUntilSubscribed = true)
                Log.d(TAG, "=== 实时消息订阅成功 ===")

                // 监听PostgreSQL数据库变化
                val changeFlow = messageChannel?.postgresChangeFlow<PostgresAction>(
                    schema = "public"
                ) {
                    table = "support_messages"
                    filter("conversation_id", FilterOperator.EQ, conversationId)
                }

                // 启动协程监听数据库变化
                CoroutineScope(Dispatchers.IO).launch {
                    changeFlow?.collect { change ->
                        Log.d(TAG, "=== 收到数据库变化 ===")
                        Log.d(TAG, "变化类型: ${change::class.simpleName}")

                        // 重新加载消息列表
                        try {
                            val messages = getConversationMessages(conversationId, 50)
                            Log.d(TAG, "重新加载消息数量: ${messages.size}")
                            onMessagesUpdate(messages)
                        } catch (e: Exception) {
                            Log.e(TAG, "重新加载消息失败", e)
                        }
                    }
                }

                // 订阅成功后，立即加载一次消息
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val messages = getConversationMessages(conversationId, 50)
                        onMessagesUpdate(messages)
                    } catch (e: Exception) {
                        Log.e(TAG, "加载初始消息失败", e)
                    }
                }

            } catch (realtimeError: Exception) {
                Log.e(TAG, "Realtime订阅失败", realtimeError)
                throw realtimeError
            }

            Log.d(TAG, "=== 实时消息订阅成功 ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== 启动实时消息监听失败 ===", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            throw e
        }
    }

    /**
     * 取消消息订阅
     */
    suspend fun unsubscribeFromMessages() {
        try {
            Log.d(TAG, "取消消息订阅...")

            messageChannel?.let { channel ->
                CoroutineScope(Dispatchers.IO).launch {
                    channel.unsubscribe()
                }
                messageChannel = null
            }

            // Realtime订阅会在channel取消订阅时自动停止

            onMessagesUpdated = null
            Log.d(TAG, "消息订阅已取消")
        } catch (e: Exception) {
            Log.e(TAG, "取消消息订阅失败", e)
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
            Log.d(TAG, "=== 开始提交用户反馈 ===")
            Log.d(TAG, "反馈标题: $title")
            Log.d(TAG, "反馈类型: $feedbackType")
            Log.d(TAG, "反馈优先级: $priority")
            Log.d(TAG, "描述长度: ${description.length} 字符")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录，无法提交反馈")
                Log.e(TAG, "反馈提交失败: 用户未登录")
                return@withContext false
            }

            Log.d(TAG, "用户ID: ${currentUser.id}")

            // 构建设备信息
            val deviceInfo = buildJsonObject {
                put("platform", "Android TV")
                put("app", "OneTV")
                put("version", appVersion ?: "unknown")
            }

            // 使用重试机制提交反馈
            executeWithRetry("提交用户反馈") {
                Log.d(TAG, "开始向数据库插入反馈数据...")
                val insertResult = client.from("user_feedback").insert(
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
                Log.d(TAG, "数据库插入操作完成，结果: $insertResult")
            }
            Log.d(TAG, "=== 用户反馈提交成功 ===")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "=== 提交用户反馈失败 ===", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            if (e.message?.contains("timeout", ignoreCase = true) == true) {
                Log.e(TAG, "网络超时错误: 请检查网络连接或稍后重试")
            }
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
                .decodeList<JsonObject>()
                .map { feedbackJson ->
                    UserFeedback(
                        id = feedbackJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = feedbackJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        feedbackType = feedbackJson["feedback_type"]?.jsonPrimitive?.contentOrNull ?: "general",
                        title = feedbackJson["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        description = feedbackJson["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = feedbackJson["status"]?.jsonPrimitive?.contentOrNull ?: "submitted",
                        priority = feedbackJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        adminResponse = feedbackJson["admin_response"]?.jsonPrimitive?.contentOrNull,
                        adminId = feedbackJson["admin_id"]?.jsonPrimitive?.contentOrNull,
                        deviceInfo = try {
                            feedbackJson["device_info"]?.jsonObject
                        } catch (e: Exception) {
                            null
                        },
                        appVersion = feedbackJson["app_version"]?.jsonPrimitive?.contentOrNull,
                        createdAt = feedbackJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = feedbackJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        resolvedAt = feedbackJson["resolved_at"]?.jsonPrimitive?.contentOrNull
                    )
                }
            
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
            
            // 标记消息为已读成功，不需要在Repository中更新UI状态
            
            Log.d(TAG, "消息标记为已读成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "标记消息为已读失败", e)
            return@withContext false
        }
    }
    
    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    /**
     * 获取用户资料
     */
    suspend fun getUserProfile(userId: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户资料: $userId")

            val userProfile = client.from("profiles")
                .select(columns = Columns.list(
                    "userid", "username", "email", "created_at", "updated_at", "accountstatus"
                )) {
                    filter {
                        eq("userid", userId)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<JsonObject>()

            if (userProfile != null) {
                // 获取用户角色信息
                val userRoles = client.from("user_roles")
                    .select(columns = Columns.list("role_type")) {
                        filter {
                            eq("user_id", userId)
                            eq("is_active", true)
                        }
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { it["role_type"]?.jsonPrimitive?.contentOrNull }

                return@withContext UserProfile(
                    id = userId,
                    username = userProfile["username"]?.jsonPrimitive?.contentOrNull ?: "",
                    email = userProfile["email"]?.jsonPrimitive?.contentOrNull ?: "",
                    isVip = userRoles.contains("vip"),
                    roles = userRoles,
                    createdAt = userProfile["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                    updatedAt = userProfile["updated_at"]?.jsonPrimitive?.contentOrNull
                )
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "获取用户资料失败", e)
            return@withContext null
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

            // 简化权限检查，直接基于用户角色判断
            val userRoles = getUserRoles()
            val hasPermission = when (permission) {
                "support.view_all_conversations" -> {
                    userRoles.any { it in listOf("support", "admin", "super_admin") }
                }
                "support.create_private_conversations" -> {
                    userRoles.any { it in listOf("support", "admin", "super_admin") }
                }
                "admin.manage_users" -> {
                    userRoles.any { it in listOf("admin", "super_admin") }
                }
                "admin.manage_feedback" -> {
                    userRoles.any { it in listOf("admin", "super_admin") }
                }
                else -> false
            }

            Log.d(TAG, "权限检查结果 [$permission]: $hasPermission (基于角色: $userRoles)")
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
                .decodeList<JsonObject>()
                .map { conversationJson ->
                    SupportConversation(
                        id = conversationJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = conversationJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        supportId = conversationJson["support_id"]?.jsonPrimitive?.contentOrNull,
                        conversationTitle = conversationJson["conversation_title"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = conversationJson["status"]?.jsonPrimitive?.contentOrNull ?: "active",
                        priority = conversationJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        createdAt = conversationJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = conversationJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        closedAt = conversationJson["closed_at"]?.jsonPrimitive?.contentOrNull,
                        lastMessageAt = conversationJson["last_message_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }

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
                .decodeList<JsonObject>()
                .map { conversationJson ->
                    SupportConversation(
                        id = conversationJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = conversationJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        supportId = conversationJson["support_id"]?.jsonPrimitive?.contentOrNull,
                        conversationTitle = conversationJson["conversation_title"]?.jsonPrimitive?.contentOrNull ?: "客服对话",
                        status = conversationJson["status"]?.jsonPrimitive?.contentOrNull ?: "open",
                        priority = conversationJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        createdAt = conversationJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = conversationJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        closedAt = conversationJson["closed_at"]?.jsonPrimitive?.contentOrNull,
                        lastMessageAt = conversationJson["last_message_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                }

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

            // 添加详细日志来调试状态问题
            Log.d(TAG, "获取到的反馈数据: $userFeedbacks")
            userFeedbacks.forEachIndexed { index, feedback ->
                Log.d(TAG, "反馈 $index: status = '${feedback["status"]}', 类型: ${feedback["status"]?.javaClass?.simpleName}")
            }

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
                    "priority", "status", "admin_response", "admin_id", "device_info",
                    "app_version", "created_at", "updated_at", "resolved_at"
                )) {
                    filter {
                        eq("user_id", currentUser.id)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<JsonObject>()
                .map { feedbackJson ->
                    val status = feedbackJson["status"]?.jsonPrimitive?.contentOrNull ?: "submitted"
                    Log.d(TAG, "解析反馈状态: 原始值='${feedbackJson["status"]}', 解析后='$status'")

                    UserFeedback(
                        id = feedbackJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = feedbackJson["user_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        feedbackType = feedbackJson["feedback_type"]?.jsonPrimitive?.contentOrNull ?: "general",
                        title = feedbackJson["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        description = feedbackJson["description"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = status,
                        priority = feedbackJson["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                        adminResponse = feedbackJson["admin_response"]?.jsonPrimitive?.contentOrNull,
                        adminId = feedbackJson["admin_id"]?.jsonPrimitive?.contentOrNull,
                        deviceInfo = try {
                            feedbackJson["device_info"]?.jsonObject
                        } catch (e: Exception) {
                            null
                        },
                        appVersion = feedbackJson["app_version"]?.jsonPrimitive?.contentOrNull,
                        createdAt = feedbackJson["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = feedbackJson["updated_at"]?.jsonPrimitive?.contentOrNull ?: "",
                        resolvedAt = feedbackJson["resolved_at"]?.jsonPrimitive?.contentOrNull
                    )
                }

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
        try {
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

            // Realtime订阅已在上面取消

            Log.d(TAG, "资源清理完成")
        } catch (e: Exception) {
            Log.w(TAG, "清理资源失败", e)
        }
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

            // 应用版本（动态获取）
            val appVersion = getAppVersion()

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
     * 获取全局用户统计信息（管理员功能）
     */
    suspend fun getGlobalUserStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取全局用户统计信息")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext emptyMap()
            }

            // 检查管理员权限
            val isAdmin = checkAdminStatus()
            if (!isAdmin) {
                Log.w(TAG, "用户无管理员权限")
                return@withContext emptyMap()
            }

            // 获取总用户数
            val totalUsers = client.from("profiles")
                .select(columns = Columns.list("userid")) {
                    // 不添加过滤条件，获取所有用户
                }
                .decodeList<JsonObject>()
                .size

            // 获取VIP用户数（通过profiles表的is_vip字段查询）
            val vipUsers = client.from("profiles")
                .select(columns = Columns.list("userid")) {
                    filter {
                        eq("is_vip", true)
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取管理员用户数（包括admin和super_admin）
            val adminUsers = client.from("user_roles")
                .select(columns = Columns.list("user_id")) {
                    filter {
                        isIn("role_type", listOf("admin", "super_admin"))
                        eq("is_active", true)
                    }
                }
                .decodeList<JsonObject>()
                .distinctBy { it["user_id"]?.jsonPrimitive?.content }
                .size

            val stats = mapOf(
                "total" to totalUsers,
                "vip" to vipUsers,
                "admin" to adminUsers
            )

            Log.d(TAG, "全局用户统计: $stats")
            return@withContext stats

        } catch (e: Exception) {
            Log.e(TAG, "获取全局用户统计信息失败", e)
            return@withContext emptyMap()
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

            // 获取所有用户资料（包含VIP状态）
            val userProfiles = client.from("profiles")
                .select(columns = Columns.list(
                    "userid", "username", "email", "created_at", "updated_at", "accountstatus", "is_vip"
                )) {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<JsonObject>()

            // 获取所有用户角色信息
            val allUserRoles = client.from("user_roles")
                .select(columns = Columns.list("user_id", "role_type")) {
                    filter {
                        eq("is_active", true)
                    }
                }
                .decodeList<JsonObject>()

            // 将角色信息按用户ID分组
            val userRolesMap = allUserRoles.groupBy {
                it["user_id"]?.jsonPrimitive?.contentOrNull ?: ""
            }.mapValues { (_, roles) ->
                roles.mapNotNull { it["role_type"]?.jsonPrimitive?.contentOrNull }
            }

            // 构建用户列表，包含正确的角色信息和VIP状态
            val users = userProfiles.map { userJson ->
                val userId = userJson["userid"]?.jsonPrimitive?.contentOrNull ?: ""
                val userRoles = userRolesMap[userId] ?: listOf("user")
                val isVip = userJson["is_vip"]?.jsonPrimitive?.booleanOrNull ?: false

                UserProfile(
                    id = userId,
                    username = userJson["username"]?.jsonPrimitive?.contentOrNull ?: "",
                    email = userJson["email"]?.jsonPrimitive?.contentOrNull ?: "",
                    isVip = isVip,  // 使用profiles表中的is_vip字段
                    roles = userRoles,
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
     * 更新用户角色 - 支持多角色系统
     */
    suspend fun updateUserRole(userId: String, role: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "更新用户角色: $userId -> $role")

            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 检查管理员权限
            val isAdmin = checkAdminStatus()
            if (!isAdmin) {
                Log.w(TAG, "用户无管理员权限")
                return@withContext false
            }

            // 获取用户当前角色
            val currentRoles = client.from("user_roles")
                .select(columns = Columns.list("role_type")) {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true)
                    }
                }
                .decodeList<JsonObject>()
                .mapNotNull { it["role_type"]?.jsonPrimitive?.contentOrNull }

            Log.d(TAG, "用户当前角色: $currentRoles")

            if (role == "user") {
                // 设置为普通用户，删除所有角色
                Log.d(TAG, "设置为普通用户，删除所有角色")
                client.from("user_roles")
                    .delete {
                        filter {
                            eq("user_id", userId)
                        }
                    }

                // 更新profiles表
                client.from("profiles")
                    .update(buildJsonObject {
                        put("is_vip", false)
                        put("accountstatus", "Normal")
                        put("updated_at", java.time.LocalDateTime.now().toString())
                    }) {
                        filter {
                            eq("userid", userId)
                        }
                    }
            } else {
                // 添加或移除特定角色
                if (currentRoles.contains(role)) {
                    // 移除角色
                    Log.d(TAG, "ROLE_DELETE_START: 开始移除角色 $role，用户ID: $userId")
                    Log.d(TAG, "ROLE_DELETE_CURRENT_ROLES: 当前用户角色列表: $currentRoles")

                    // 查询要删除的具体记录
                    val recordsToDelete = client.from("user_roles")
                        .select(columns = Columns.list("id", "user_id", "role_type", "is_active")) {
                            filter {
                                eq("user_id", userId)
                                eq("role_type", role)
                            }
                        }
                        .decodeList<JsonObject>()

                    Log.d(TAG, "ROLE_DELETE_RECORDS: 找到要删除的记录: $recordsToDelete")

                    if (recordsToDelete.isEmpty()) {
                        Log.e(TAG, "ROLE_DELETE_ERROR: 没有找到要删除的角色记录")
                        return@withContext false
                    }

                    // 执行删除操作 - 使用物理删除
                    Log.d(TAG, "ROLE_DELETE_SQL: 执行删除SQL - DELETE FROM user_roles WHERE user_id='$userId' AND role_type='$role'")

                    try {
                        // 记录当前用户信息
                        Log.d(TAG, "ROLE_DELETE_USER_INFO: 当前用户ID: ${currentUser.id}")
                        Log.d(TAG, "ROLE_DELETE_USER_INFO: 当前用户邮箱: ${currentUser.email}")

                        // 尝试使用原始SQL方式删除
                        Log.d(TAG, "ROLE_DELETE_ATTEMPT: 尝试删除用户角色记录")
                        Log.d(TAG, "ROLE_DELETE_PARAMS: userId=$userId, role=$role")

                        val deleteResult = client.from("user_roles")
                            .delete {
                                filter {
                                    eq("user_id", userId)
                                    eq("role_type", role)
                                }
                            }

                        Log.d(TAG, "ROLE_DELETE_RESULT: 删除操作返回结果: $deleteResult")

                        // 检查删除结果的类型和内容
                        Log.d(TAG, "ROLE_DELETE_RESULT_TYPE: 删除结果类型: ${deleteResult?.javaClass?.simpleName}")
                        Log.d(TAG, "ROLE_DELETE_RESULT_STRING: 删除结果字符串: ${deleteResult.toString()}")

                        // 尝试解析删除结果
                        if (deleteResult != null) {
                            try {
                                val resultJson = deleteResult.toString()
                                Log.d(TAG, "ROLE_DELETE_RESULT_JSON: $resultJson")

                                // 检查是否包含错误信息
                                if (resultJson.contains("error", ignoreCase = true)) {
                                    Log.e(TAG, "ROLE_DELETE_ERROR_IN_RESULT: 删除结果包含错误信息")
                                }
                                if (resultJson.contains("permission", ignoreCase = true)) {
                                    Log.e(TAG, "ROLE_DELETE_PERMISSION_ERROR: 可能是权限问题")
                                }
                                if (resultJson.contains("policy", ignoreCase = true)) {
                                    Log.e(TAG, "ROLE_DELETE_POLICY_ERROR: 可能是RLS策略问题")
                                }
                            } catch (parseException: Exception) {
                                Log.w(TAG, "ROLE_DELETE_PARSE_ERROR: 无法解析删除结果", parseException)
                            }
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "ROLE_DELETE_EXCEPTION: 删除操作异常", e)
                        Log.e(TAG, "ROLE_DELETE_EXCEPTION_MESSAGE: ${e.message}")
                        Log.e(TAG, "ROLE_DELETE_EXCEPTION_CAUSE: ${e.cause}")
                        Log.e(TAG, "ROLE_DELETE_EXCEPTION_STACK: ${e.stackTraceToString()}")
                        return@withContext false
                    }

                    // 等待数据库操作完成
                    kotlinx.coroutines.delay(200)

                    // 验证删除结果 - 查询所有记录（包括非活跃的）
                    val allRecordsAfterDelete = client.from("user_roles")
                        .select(columns = Columns.list("id", "user_id", "role_type", "is_active")) {
                            filter {
                                eq("user_id", userId)
                                eq("role_type", role)
                            }
                        }
                        .decodeList<JsonObject>()

                    Log.d(TAG, "ROLE_DELETE_VERIFY_ALL: 删除后所有相关记录: $allRecordsAfterDelete")

                    // 验证活跃角色列表
                    val activeRolesAfterDelete = client.from("user_roles")
                        .select(columns = Columns.list("role_type")) {
                            filter {
                                eq("user_id", userId)
                                eq("is_active", true)
                            }
                        }
                        .decodeList<JsonObject>()
                        .mapNotNull { it["role_type"]?.jsonPrimitive?.contentOrNull }

                    Log.d(TAG, "ROLE_DELETE_VERIFY_ACTIVE: 删除后活跃角色列表: $activeRolesAfterDelete")

                    // 检查删除是否成功
                    if (allRecordsAfterDelete.isNotEmpty()) {
                        Log.e(TAG, "ROLE_DELETE_FAILED: 物理删除失败，记录仍然存在")
                        Log.e(TAG, "ROLE_DELETE_FAILED_RECORDS: 仍存在的记录: $allRecordsAfterDelete")

                        // 尝试使用ID直接删除
                        Log.d(TAG, "ROLE_DELETE_RETRY: 尝试使用记录ID直接删除")
                        for (record in allRecordsAfterDelete) {
                            val recordId = record["id"]?.jsonPrimitive?.contentOrNull
                            if (recordId != null) {
                                try {
                                    Log.d(TAG, "ROLE_DELETE_BY_ID: 尝试删除记录ID: $recordId")
                                    val deleteByIdResult = client.from("user_roles")
                                        .delete {
                                            filter {
                                                eq("id", recordId)
                                            }
                                        }
                                    Log.d(TAG, "ROLE_DELETE_BY_ID_RESULT: $deleteByIdResult")
                                } catch (idDeleteException: Exception) {
                                    Log.e(TAG, "ROLE_DELETE_BY_ID_ERROR: 通过ID删除失败", idDeleteException)
                                }
                            }
                        }

                        // 再次验证
                        kotlinx.coroutines.delay(200)
                        val finalCheck = client.from("user_roles")
                            .select(columns = Columns.list("id", "user_id", "role_type", "is_active")) {
                                filter {
                                    eq("user_id", userId)
                                    eq("role_type", role)
                                }
                            }
                            .decodeList<JsonObject>()

                        Log.d(TAG, "ROLE_DELETE_FINAL_CHECK: 最终检查结果: $finalCheck")

                        if (finalCheck.isNotEmpty()) {
                            Log.e(TAG, "ROLE_DELETE_ULTIMATE_FAILURE: 所有删除方法都失败")
                            return@withContext false
                        } else {
                            Log.d(TAG, "ROLE_DELETE_SUCCESS_RETRY: 通过重试删除成功")
                        }
                    } else {
                        Log.d(TAG, "ROLE_DELETE_SUCCESS: 角色 $role 物理删除成功")
                    }
                } else {
                    // 添加角色
                    Log.d(TAG, "添加角色: $role")
                    client.from("user_roles")
                        .insert(buildJsonObject {
                            put("user_id", userId)
                            put("role_type", role)
                            put("is_active", true)
                            put("created_at", java.time.LocalDateTime.now().toString())
                        })
                }

                // 更新profiles表的VIP状态
                val updatedRoles = if (currentRoles.contains(role)) {
                    currentRoles - role
                } else {
                    currentRoles + role
                }

                val isVip = updatedRoles.contains("vip")
                client.from("profiles")
                    .update(buildJsonObject {
                        put("is_vip", isVip)
                        put("accountstatus", if (isVip) "VIP" else "Normal")
                        put("updated_at", java.time.LocalDateTime.now().toString())
                    }) {
                        filter {
                            eq("userid", userId)
                        }
                    }

                Log.d(TAG, "更新后角色: $updatedRoles")
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
     * 撤销反馈（用户）
     */
    suspend fun withdrawFeedback(feedbackId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "撤销反馈: $feedbackId")

            // 获取当前用户信息
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 先查询反馈是否存在且属于当前用户
            val existingFeedback = client.from("user_feedback")
                .select(columns = Columns.list("id", "status", "title")) {
                    filter {
                        eq("id", feedbackId)
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeSingle<JsonObject>()

            Log.d(TAG, "查询到反馈: ${existingFeedback["status"]?.jsonPrimitive?.content}")

            // 检查反馈状态是否允许撤销（只能撤销submitted和reviewing状态的反馈）
            val currentStatus = existingFeedback["status"]?.jsonPrimitive?.content
            if (currentStatus != "submitted" && currentStatus != "reviewing") {
                Log.w(TAG, "反馈状态为 $currentStatus，无法撤销")
                return@withContext false
            }

            // 更新反馈状态为已撤销
            val updateResult = client.from("user_feedback")
                .update(buildJsonObject {
                    put("status", "withdrawn")
                    put("updated_at", java.time.LocalDateTime.now().toString())
                }) {
                    filter {
                        eq("id", feedbackId)
                        eq("user_id", currentUser.id)
                    }
                }

            Log.d(TAG, "反馈撤销操作完成，结果: $updateResult")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "撤销反馈失败", e)
            return@withContext false
        }
    }

    /**
     * 删除反馈（用户）
     * 优先使用Edge Function，失败时回退到直接数据库操作
     */
    suspend fun deleteFeedback(feedbackId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "删除反馈: $feedbackId")

            // 获取当前用户信息
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 先查询反馈是否存在且属于当前用户
            val existingFeedback = client.from("user_feedback")
                .select(columns = Columns.list("id", "title", "user_id")) {
                    filter {
                        eq("id", feedbackId)
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeSingle<JsonObject>()

            Log.d(TAG, "查询到要删除的反馈: ${existingFeedback["title"]?.jsonPrimitive?.content}")

            // 删除前再次确认记录存在
            val beforeDeleteCount = client.from("user_feedback")
                .select(Columns.list("id")) {
                    filter {
                        eq("id", feedbackId)
                        eq("user_id", currentUser.id)
                    }
                }
                .decodeList<JsonObject>()
                .size
            Log.d(TAG, "删除前记录数量: $beforeDeleteCount")

            // 方法1: 尝试使用Edge Function删除反馈
            var success = false
            try {
                Log.d(TAG, "尝试方法1: 通过Edge Function删除反馈...")

                executeWithRetry("删除用户反馈") {
                    Log.d(TAG, "开始通过Edge Function删除反馈...")
                    Log.d(TAG, "删除参数: feedbackId=$feedbackId, userId=${currentUser.id}")

                    // 构建请求体
                    val requestBody = buildJsonObject {
                        put("action", "delete_feedback")
                        put("feedback_id", feedbackId)
                    }
                    val requestBodyString = requestBody.toString()
                    Log.d(TAG, "Edge Function请求体: $requestBodyString")

                    // 使用Edge Function删除反馈
                    val response = functions.invoke(
                        function = "support-management",
                        body = requestBodyString
                    )

                    val result = response.safeBody<JsonObject>()
                    Log.d(TAG, "Edge Function删除结果: $result")

                    // 检查删除结果
                    val edgeSuccess = result["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    if (!edgeSuccess) {
                        val errorMessage = result["error"]?.jsonPrimitive?.content ?: "删除失败"
                        Log.e(TAG, "Edge Function删除失败: $errorMessage")
                        throw Exception("删除失败: $errorMessage")
                    }

                    Log.d(TAG, "Edge Function删除成功")
                    true
                }

                success = true
                Log.d(TAG, "Edge Function删除方法成功")

            } catch (e: Exception) {
                Log.w(TAG, "Edge Function删除失败，尝试备用方法: ${e.message}")
                success = false
            }

            // 方法2: 如果Edge Function失败，使用直接数据库删除
            if (!success) {
                Log.d(TAG, "尝试方法2: 直接数据库删除反馈...")

                try {
                    executeWithRetry("直接删除用户反馈") {
                        Log.d(TAG, "开始直接数据库删除反馈...")

                        // 直接从数据库删除反馈
                        client.from("user_feedback")
                            .delete {
                                filter {
                                    eq("id", feedbackId)
                                    eq("user_id", currentUser.id)
                                }
                            }

                        Log.d(TAG, "直接数据库删除操作完成")
                        true
                    }

                    // 验证直接删除结果
                    val afterCountDirect = client.from("user_feedback")
                        .select(Columns.list("id")) {
                            filter {
                                eq("id", feedbackId)
                                eq("user_id", currentUser.id)
                            }
                        }
                        .decodeList<JsonObject>()
                        .size

                    success = afterCountDirect == 0
                    if (success) {
                        Log.d(TAG, "直接数据库删除方法成功，记录已删除")
                    } else {
                        Log.e(TAG, "直接数据库删除失败，记录仍然存在，删除后记录数量: $afterCountDirect")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "直接数据库删除失败", e)
                    success = false
                }
            }

            if (success) {
                Log.d(TAG, "反馈删除成功")
            } else {
                Log.e(TAG, "所有删除方法都失败")
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e(TAG, "删除反馈失败", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            Log.e(TAG, "异常堆栈: ${e.stackTraceToString()}")
            return@withContext false
        }
    }

    /**
     * 获取反馈统计信息（管理员）
     */
    suspend fun getAllFeedbackStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取反馈统计信息")

            // 直接从数据库获取反馈统计数据，避免Edge Function错误

            // 获取总反馈数
            val total = client.from("user_feedback")
                .select(columns = Columns.list("id"))
                .decodeList<JsonObject>()
                .size

            // 获取各状态的反馈数
            val submitted = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("status", "submitted") }
                }
                .decodeList<JsonObject>()
                .size

            val reviewing = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("status", "reviewing") }
                }
                .decodeList<JsonObject>()
                .size

            val resolved = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("status", "resolved") }
                }
                .decodeList<JsonObject>()
                .size

            val closed = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("status", "closed") }
                }
                .decodeList<JsonObject>()
                .size

            // 获取各优先级的反馈数
            val highPriority = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("priority", "high") }
                }
                .decodeList<JsonObject>()
                .size

            val normalPriority = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("priority", "normal") }
                }
                .decodeList<JsonObject>()
                .size

            val lowPriority = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter { eq("priority", "low") }
                }
                .decodeList<JsonObject>()
                .size

            val stats = mapOf(
                "total" to total,
                "submitted" to submitted,
                "reviewing" to reviewing,
                "resolved" to resolved,
                "closed" to closed,
                "high_priority" to highPriority,
                "normal_priority" to normalPriority,
                "low_priority" to lowPriority
            )

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

            // 直接从数据库获取统计数据，避免Edge Function错误

            // 获取活跃对话数（使用"open"状态）
            val activeConversations = client.from("support_conversations")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("status", "open")
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取待处理对话数
            val pendingConversations = client.from("support_conversations")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("status", "pending")
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取今日已解决数量
            val resolvedToday = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("status", "resolved")
                        gte("resolved_at", java.time.LocalDate.now().toString())
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取最近反馈数量
            val recentFeedbacks = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter {
                        gte("created_at", java.time.LocalDateTime.now().minusDays(7).toString())
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取紧急问题数量
            val urgentIssues = client.from("user_feedback")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("priority", "high")
                        neq("status", "resolved")
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 获取在线客服数量（有活跃对话的客服）
            val onlineAgents = try {
                client.from("support_conversations")
                    .select(columns = Columns.list("support_id")) {
                        filter {
                            eq("status", "open")
                            // 过滤掉support_id为空的记录
                        }
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { it["support_id"]?.jsonPrimitive?.contentOrNull }
                    .filter { it.isNotBlank() && it != "null" }  // 过滤掉空值和"null"字符串
                    .distinct()
                    .size
            } catch (e: Exception) {
                Log.w(TAG, "获取在线客服数量失败，使用默认值", e)
                0
            }

            // 获取总客服数量（有客服角色的用户）
            val totalAgents = client.from("user_roles")
                .select(columns = Columns.list("user_id")) {
                    filter {
                        eq("role_type", "support")
                    }
                }
                .decodeList<JsonObject>()
                .size

            // 计算平均响应时间（基于已关闭对话的处理时间）
            val avgResponseTime = try {
                val closedConversations = client.from("support_conversations")
                    .select(columns = Columns.list("created_at", "closed_at")) {
                        filter {
                            eq("status", "closed")
                            // 过滤掉closed_at为空的记录
                        }
                        limit(50) // 取最近50个已关闭对话
                    }
                    .decodeList<JsonObject>()

                if (closedConversations.isNotEmpty()) {
                    val totalHours = closedConversations.mapNotNull { conversation ->
                        val createdAt = conversation["created_at"]?.jsonPrimitive?.contentOrNull
                        val closedAt = conversation["closed_at"]?.jsonPrimitive?.contentOrNull
                        if (createdAt != null && closedAt != null && closedAt != "null") {
                            try {
                                val created = java.time.LocalDateTime.parse(createdAt.substring(0, 19))
                                val closed = java.time.LocalDateTime.parse(closedAt.substring(0, 19))
                                java.time.Duration.between(created, closed).toHours()
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }.average()
                    "${totalHours.toInt()}小时"
                } else {
                    "暂无数据"
                }
            } catch (e: Exception) {
                "计算失败"
            }

            // 计算客户满意度（基于已解决反馈的评分）
            val customerSatisfaction = try {
                val resolvedFeedbacks = client.from("user_feedback")
                    .select(columns = Columns.list("rating")) {
                        filter {
                            eq("status", "resolved")
                            // 过滤掉rating为空的记录
                        }
                        limit(100) // 取最近100个已解决反馈
                    }
                    .decodeList<JsonObject>()

                if (resolvedFeedbacks.isNotEmpty()) {
                    val ratings = resolvedFeedbacks.mapNotNull { feedback ->
                        val ratingStr = feedback["rating"]?.jsonPrimitive?.contentOrNull
                        if (ratingStr != null && ratingStr != "null") {
                            ratingStr.toDoubleOrNull()
                        } else null
                    }
                    if (ratings.isNotEmpty()) {
                        ratings.average()
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }
            } catch (e: Exception) {
                0.0
            }

            val stats = mapOf(
                "active_conversations" to activeConversations,
                "pending_conversations" to pendingConversations,
                "resolved_today" to resolvedToday,
                "avg_response_time" to avgResponseTime,
                "customer_satisfaction" to customerSatisfaction,
                "online_agents" to onlineAgents,
                "total_agents" to totalAgents,
                "recent_feedbacks" to recentFeedbacks,
                "urgent_issues" to urgentIssues
            )

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
                    val userId = convJson["user_id"]?.jsonPrimitive?.contentOrNull ?: ""

                    // 获取用户信息
                    val userInfo = try {
                        client.from("profiles")
                            .select(columns = Columns.list("username", "email")) {
                                filter {
                                    eq("userid", userId)
                                }
                                limit(1)
                            }
                            .decodeSingleOrNull<JsonObject>()
                    } catch (e: Exception) {
                        Log.w(TAG, "获取用户信息失败: $userId", e)
                        null
                    }

                    val userName = userInfo?.get("username")?.jsonPrimitive?.contentOrNull ?: userId.take(8)
                    val userEmail = userInfo?.get("email")?.jsonPrimitive?.contentOrNull ?: ""

                    // 获取最后一条消息
                    val lastMessage = try {
                        client.from("support_messages")
                            .select(columns = Columns.list("message_text")) {
                                filter {
                                    eq("conversation_id", convJson["id"]?.jsonPrimitive?.contentOrNull ?: "")
                                }
                                order("created_at", Order.DESCENDING)
                                limit(1)
                            }
                            .decodeSingleOrNull<JsonObject>()
                            ?.get("message_text")?.jsonPrimitive?.contentOrNull ?: "暂无消息"
                    } catch (e: Exception) {
                        "暂无消息"
                    }

                    SupportConversationDisplay(
                        id = convJson["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        userId = userId,
                        userName = userName,
                        userEmail = userEmail,
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
     * 关闭对话
     */
    suspend fun closeConversation(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "关闭对话: $conversationId")

            // 获取当前用户信息
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser == null) {
                Log.w(TAG, "用户未登录")
                return@withContext false
            }

            // 更新对话状态为已关闭
            client.from("support_conversations")
                .update(buildJsonObject {
                    put("status", "closed")
                    put("closed_at", java.time.LocalDateTime.now().toString())
                    put("updated_at", java.time.LocalDateTime.now().toString())
                }) {
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

    /**
     * 获取对话统计信息
     */
    suspend fun getConversationStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取对话统计信息")

            // 获取所有对话
            val allConversations = client.from("support_conversations")
                .select()
                .decodeList<SupportConversation>()

            // 统计各种状态的对话数量
            val totalConversations = allConversations.size
            val pendingConversations = allConversations.count { it.status == "pending" }
            val activeConversations = allConversations.count { it.status == "open" }  // 修正：使用"open"状态
            val closedConversations = allConversations.count { it.status == "closed" }
            val newConversations = allConversations.count { it.supportId == null && it.status == "pending" }

            val stats = mapOf(
                "total_conversations" to totalConversations,
                "pending_conversations" to pendingConversations,
                "active_conversations" to activeConversations,
                "closed_conversations" to closedConversations,
                "new_conversations" to newConversations
            )

            Log.d(TAG, "对话统计信息获取成功: $stats")
            return@withContext stats
        } catch (e: Exception) {
            Log.e(TAG, "获取对话统计信息失败", e)
            return@withContext emptyMap()
        }
    }

    /**
     * 根据用户ID获取用户信息
     */
    suspend fun getUserInfoById(userId: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "获取用户信息，用户ID: $userId")

            val response = client.from("profiles")
                .select(columns = Columns.list("userid", "username", "email", "created_at", "updated_at", "accountstatus")) {
                    filter {
                        eq("userid", userId)
                    }
                }
                .decodeSingleOrNull<JsonObject>()

            if (response == null) {
                Log.w(TAG, "未找到用户资料，用户ID: $userId")
                return@withContext null
            }

            // 获取用户角色信息
            val userRoles = try {
                client.from("user_roles")
                    .select(columns = Columns.list("role_type")) {
                        filter {
                            eq("user_id", userId)
                            eq("is_active", true)
                        }
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { it["role_type"]?.jsonPrimitive?.contentOrNull }
            } catch (e: Exception) {
                Log.w(TAG, "获取用户角色失败，使用默认角色", e)
                listOf("user")
            }

            val userProfile = UserProfile(
                id = userId,
                username = response.jsonObject["username"]?.jsonPrimitive?.contentOrNull,
                email = response.jsonObject["email"]?.jsonPrimitive?.contentOrNull,
                isVip = response.jsonObject["accountstatus"]?.jsonPrimitive?.contentOrNull == "VIP",
                roles = userRoles,
                createdAt = response.jsonObject["created_at"]?.jsonPrimitive?.contentOrNull ?: "",
                updatedAt = response.jsonObject["updated_at"]?.jsonPrimitive?.contentOrNull
            )

            Log.d(TAG, "获取用户信息成功: ${userProfile.username}")
            userProfile
        } catch (e: Exception) {
            Log.e(TAG, "获取用户信息失败，用户ID: $userId", e)
            null
        }
    }
}
