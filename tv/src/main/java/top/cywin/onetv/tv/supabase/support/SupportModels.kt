package top.cywin.onetv.tv.supabase.support

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "SupportModels"

/**
 * 解析和清理日期时间字符串，支持微秒格式和时区偏移
 * 支持格式：
 * - 2025-07-02T11:11:32.902096+08:00 (微秒+时区)
 * - 2025-07-02T11:11:32.902+08:00 (毫秒+时区)
 * - 2025-07-02T11:11:32+08:00 (秒+时区)
 * - 2025-07-02T11:11:32Z (UTC)
 * - 2025-07-02T11:11:32 (本地时间)
 */
private fun parseAndCleanDateTime(dateTimeString: String): String {
    Log.d(TAG, "parseAndCleanDateTime: 原始时间字符串 = '$dateTimeString'")

    if (dateTimeString.isBlank()) {
        Log.w(TAG, "parseAndCleanDateTime: 时间字符串为空")
        return ""
    }

    try {
        // 清理字符串，移除引号和空格
        var cleaned = dateTimeString.trim().removeSurrounding("\"")
        Log.d(TAG, "parseAndCleanDateTime: 清理引号后 = '$cleaned'")

        // 处理时区偏移 (+08:00, -05:00 等)
        if (cleaned.contains("+") || cleaned.contains("-")) {
            val timeZoneIndex = maxOf(cleaned.lastIndexOf("+"), cleaned.lastIndexOf("-"))
            if (timeZoneIndex > 10) { // 确保不是日期部分的减号
                cleaned = cleaned.substring(0, timeZoneIndex)
                Log.d(TAG, "parseAndCleanDateTime: 移除时区后 = '$cleaned'")
            }
        }

        // 处理UTC标记
        if (cleaned.endsWith("Z")) {
            cleaned = cleaned.dropLast(1)
            Log.d(TAG, "parseAndCleanDateTime: 移除Z后 = '$cleaned'")
        }

        // 处理微秒：如果小数点后超过3位，截取到3位（毫秒）
        if (cleaned.contains(".")) {
            val parts = cleaned.split(".")
            if (parts.size == 2 && parts[1].length > 3) {
                cleaned = "${parts[0]}.${parts[1].substring(0, 3)}"
                Log.d(TAG, "parseAndCleanDateTime: 截取微秒到毫秒 = '$cleaned'")
            }
        }

        Log.d(TAG, "parseAndCleanDateTime: 最终清理结果 = '$cleaned'")
        return cleaned

    } catch (e: Exception) {
        Log.e(TAG, "parseAndCleanDateTime: 清理时间字符串失败，原始值='$dateTimeString'", e)
        return dateTimeString // 返回原始值，让调用者处理
    }
}

/**
 * 客服对话数据模型
 */
@Serializable
data class SupportConversation(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("support_id") val supportId: String? = null,
    @SerialName("conversation_title") val conversationTitle: String = "客服对话",
    val status: String = "open", // open, closed, waiting
    val priority: String = "normal", // low, normal, high, urgent
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_CLOSED = "closed"
        const val STATUS_WAITING = "waiting"
        
        const val PRIORITY_LOW = "low"
        const val PRIORITY_NORMAL = "normal"
        const val PRIORITY_HIGH = "high"
        const val PRIORITY_URGENT = "urgent"
    }
    
    /**
     * 获取状态显示文本
     */
    fun getStatusText(): String {
        Log.d(TAG, "SupportConversation.getStatusText: 获取状态文本 - status = $status")
        return when (status) {
            STATUS_OPEN -> "进行中"
            STATUS_CLOSED -> "已关闭"
            STATUS_WAITING -> "等待客服"
            else -> {
                Log.w(TAG, "SupportConversation.getStatusText: 未知状态 = $status")
                "未知状态"
            }
        }
    }
    
    /**
     * 获取优先级显示文本
     */
    fun getPriorityText(): String {
        Log.d(TAG, "SupportConversation.getPriorityText: 获取优先级文本 - priority = $priority")
        return when (priority) {
            PRIORITY_LOW -> "低"
            PRIORITY_NORMAL -> "普通"
            PRIORITY_HIGH -> "高"
            PRIORITY_URGENT -> "紧急"
            else -> {
                Log.w(TAG, "SupportConversation.getPriorityText: 未知优先级 = $priority，使用默认值")
                "普通"
            }
        }
    }
    
    /**
     * 获取格式化的最后消息时间
     */
    fun getFormattedLastMessageTime(): String {
        Log.d(TAG, "SupportConversation.getFormattedLastMessageTime: 格式化时间 - lastMessageAt = '$lastMessageAt'")

        if (lastMessageAt.isBlank()) {
            Log.w(TAG, "SupportConversation.getFormattedLastMessageTime: lastMessageAt为空，使用创建时间")
            return getFormattedCreatedTime()
        }

        return try {
            // 处理多种时间格式，包括微秒和时区
            val cleanedTime = parseAndCleanDateTime(lastMessageAt)
            Log.d(TAG, "SupportConversation.getFormattedLastMessageTime: 清理后的时间 = '$cleanedTime'")

            val dateTime = LocalDateTime.parse(cleanedTime)
            val formatted = dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            Log.d(TAG, "SupportConversation.getFormattedLastMessageTime: 格式化成功 = '$formatted'")
            formatted
        } catch (e: Exception) {
            Log.e(TAG, "SupportConversation.getFormattedLastMessageTime: 时间格式化失败，原始值='$lastMessageAt'", e)
            // 如果lastMessageAt格式化失败，尝试使用创建时间
            try {
                getFormattedCreatedTime()
            } catch (e2: Exception) {
                Log.e(TAG, "SupportConversation.getFormattedLastMessageTime: 创建时间也格式化失败", e2)
                "未知时间"
            }
        }
    }

    /**
     * 获取格式化的创建时间
     */
    fun getFormattedCreatedTime(): String {
        Log.d(TAG, "SupportConversation.getFormattedCreatedTime: 格式化创建时间 - createdAt = '$createdAt'")
        return try {
            val cleanedTime = parseAndCleanDateTime(createdAt)
            val dateTime = LocalDateTime.parse(cleanedTime)
            val formatted = dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            Log.d(TAG, "SupportConversation.getFormattedCreatedTime: 格式化成功 = '$formatted'")
            formatted
        } catch (e: Exception) {
            Log.e(TAG, "SupportConversation.getFormattedCreatedTime: 创建时间格式化失败，原始值='$createdAt'", e)
            "未知时间"
        }
    }


}

/**
 * 客服消息数据模型
 */
@Serializable
data class SupportMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("message_text") val messageText: String,
    @SerialName("message_type") val messageType: String = "text", // text, image, file, system
    @SerialName("is_from_support") val isFromSupport: Boolean = false,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_FILE = "file"
        const val TYPE_SYSTEM = "system"
    }
    
    /**
     * 获取格式化的时间 - 北京时间
     */
    fun getFormattedTime(): String {
        return try {
            // 解析ISO时间字符串并转换为北京时间
            val cleanTime = if (createdAt.contains("T")) {
                createdAt.replace("Z", "").take(19) // 移除时区和微秒
            } else {
                createdAt.take(19)
            }

            val dateTime = LocalDateTime.parse(cleanTime)
            // 假设输入时间是UTC，转换为北京时间
            val beijingTime = dateTime.atZone(java.time.ZoneId.of("UTC"))
                .withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
            beijingTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            // 如果解析失败，尝试简单格式化
            try {
                createdAt.take(5) // 取前5个字符作为时间
            } catch (e2: Exception) {
                "00:00"
            }
        }
    }
    
    /**
     * 获取格式化的日期时间
     */
    fun getFormattedDateTime(): String {
        return try {
            val dateTime = LocalDateTime.parse(createdAt.replace("Z", ""))
            dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        } catch (e: Exception) {
            "未知时间"
        }
    }
    
    /**
     * 检查消息是否已读
     */
    fun isRead(): Boolean {
        return readAt != null
    }
}

/**
 * 用户反馈数据模型
 */
@Serializable
data class UserFeedback(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("feedback_type") val feedbackType: String = "general", // bug, feature, complaint, suggestion, general
    val title: String,
    val description: String,
    val status: String = "submitted", // submitted, reviewing, resolved, closed
    val priority: String = "normal", // low, normal, high
    @SerialName("admin_response") val adminResponse: String? = null,
    @SerialName("admin_id") val adminId: String? = null,
    @SerialName("device_info") val deviceInfo: JsonObject? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null
) {
    companion object {
        const val TYPE_BUG = "bug"
        const val TYPE_FEATURE = "feature"
        const val TYPE_COMPLAINT = "complaint"
        const val TYPE_SUGGESTION = "suggestion"
        const val TYPE_GENERAL = "general"

        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_REVIEWING = "reviewing"
        const val STATUS_RESOLVED = "resolved"
        const val STATUS_CLOSED = "closed"
        const val STATUS_WITHDRAWN = "withdrawn"

        const val PRIORITY_LOW = "low"
        const val PRIORITY_NORMAL = "normal"
        const val PRIORITY_HIGH = "high"
    }
    

    
    /**
     * 获取状态显示文本
     */
    fun getStatusText(): String {
        Log.d(TAG, "UserFeedback.getStatusText: 获取状态文本 - status = '$status'")
        return when (status) {
            STATUS_SUBMITTED -> "已提交"
            STATUS_REVIEWING -> "处理中"
            STATUS_RESOLVED -> "已解决"
            STATUS_CLOSED -> "已关闭"
            STATUS_WITHDRAWN -> "已撤回"
            else -> {
                Log.w(TAG, "UserFeedback.getStatusText: 未知状态 = '$status'")
                "未知状态"
            }
        }
    }

    /**
     * 获取格式化的创建时间
     */
    fun getFormattedCreatedTime(): String {
        Log.d(TAG, "UserFeedback.getFormattedCreatedTime: 格式化创建时间 - createdAt = '$createdAt'")
        return try {
            val cleanedTime = parseAndCleanDateTime(createdAt)
            Log.d(TAG, "UserFeedback.getFormattedCreatedTime: 清理后的时间 = '$cleanedTime'")

            val dateTime = LocalDateTime.parse(cleanedTime)
            val formatted = dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            Log.d(TAG, "UserFeedback.getFormattedCreatedTime: 格式化成功 = '$formatted'")
            formatted
        } catch (e: Exception) {
            Log.e(TAG, "UserFeedback.getFormattedCreatedTime: 创建时间格式化失败，原始值='$createdAt'", e)
            "未知时间"
        }
    }

    /**
     * 获取反馈类型显示文本
     */
    fun getTypeText(): String {
        Log.d(TAG, "UserFeedback.getTypeText: 获取类型文本 - feedbackType = '$feedbackType'")
        return when (feedbackType) {
            "bug" -> "问题报告"
            "feature" -> "功能建议"
            "complaint" -> "投诉建议"
            "suggestion" -> "改进建议"
            "general" -> "一般反馈"
            else -> {
                Log.w(TAG, "UserFeedback.getTypeText: 未知类型 = '$feedbackType'")
                "未知类型"
            }
        }
    }
    
    /**
     * 获取优先级显示文本
     */
    fun getPriorityText(): String {
        return when (priority) {
            PRIORITY_LOW -> "低"
            PRIORITY_NORMAL -> "普通"
            PRIORITY_HIGH -> "高"
            else -> "普通"
        }
    }


}

/**
 * 发送消息请求模型
 */
@Serializable
data class SendSupportMessageRequest(
    val conversationId: String,
    val messageText: String,
    val messageType: String = SupportMessage.TYPE_TEXT
)

/**
 * 创建对话请求模型
 */
@Serializable
data class CreateConversationRequest(
    val title: String = "客服对话",
    val priority: String = SupportConversation.PRIORITY_NORMAL,
    val initialMessage: String
)

/**
 * 提交反馈请求模型
 */
@Serializable
data class SubmitFeedbackRequest(
    val feedbackType: String,
    val title: String,
    val description: String,
    val priority: String = UserFeedback.PRIORITY_NORMAL,
    val deviceInfo: JsonObject? = null,
    val appVersion: String? = null
)

/**
 * 扩展的客服对话显示模型（用于工作台显示）
 */
data class SupportConversationDisplay(
    val id: String,
    val userId: String,
    val userName: String = "", // 用户名
    val userEmail: String = "", // 用户邮箱
    val supportId: String? = null,
    val conversationTitle: String = "客服对话",
    val status: String = "open", // open, closed, waiting
    val priority: String = "normal", // low, normal, high, urgent
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String? = null,
    val lastMessageAt: String,
    val lastMessage: String = "", // 最后一条消息内容
    val tags: List<String> = emptyList(), // 标签
    val customerSatisfaction: Double? = null // 客户满意度
) {
    companion object {
        fun fromSupportConversation(
            conversation: SupportConversation,
            userName: String = "",
            userEmail: String = "",
            lastMessage: String = "",
            tags: List<String> = emptyList(),
            customerSatisfaction: Double? = null
        ): SupportConversationDisplay {
            return SupportConversationDisplay(
                id = conversation.id,
                userId = conversation.userId,
                userName = userName,
                userEmail = userEmail,
                supportId = conversation.supportId,
                conversationTitle = conversation.conversationTitle,
                status = conversation.status,
                priority = conversation.priority,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
                closedAt = conversation.closedAt,
                lastMessageAt = conversation.lastMessageAt,
                lastMessage = lastMessage,
                tags = tags,
                customerSatisfaction = customerSatisfaction
            )
        }
    }
}

/**
 * 客服对话状态
 */
data class SupportConversationState(
    val conversation: SupportConversation? = null,
    val messages: List<SupportMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = false,
    val unreadCount: Int = 0
)

/**
 * 用户反馈状态
 */
data class UserFeedbackState(
    val feedbackList: List<UserFeedback> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * 客服支持UI状态
 */
data class SupportUiState(
    val conversationState: SupportConversationState = SupportConversationState(),
    val feedbackState: UserFeedbackState = UserFeedbackState(),
    val currentInputMessage: String = "",
    val showConversation: Boolean = false,
    val showFeedbackForm: Boolean = false,
    val showFeedbackList: Boolean = false,
    val showFeedbackDetail: Boolean = false,
    val selectedFeedback: UserFeedback? = null,
    val showAdminReplyDialog: Boolean = false,
    val adminReplyText: String = "",
    val showRoleManagement: Boolean = false,
    val showUserManagement: Boolean = false,
    val showFeedbackManagement: Boolean = false,
    val showSupportDesk: Boolean = false,
    val showSystemLogs: Boolean = false,
    val systemLogs: List<SystemLogEntry> = emptyList(),
    // 管理员对话管理相关状态
    val showAdminChat: Boolean = false,
    val selectedConversation: SupportConversationDisplay? = null,
    val adminChatMessages: List<SupportMessage> = emptyList(),
    val adminCurrentMessage: String = "",
    val newConversationCount: Int = 0, // 新对话数量，用于红点提示
    val lastRefreshTime: Long = 0L,
    val feedbackRefreshTrigger: Long = 0L, // 用于触发反馈数据刷新
    // 用户消息提示相关
    val userMessage: String = "",
    val showUserMessage: Boolean = false,
    val userMessageType: UserMessageType = UserMessageType.INFO
)

/**
 * 用户消息类型
 */
enum class UserMessageType {
    INFO,    // 信息提示
    SUCCESS, // 成功提示
    WARNING, // 警告提示
    ERROR    // 错误提示
}

/**
 * 用户资料数据类
 */
@Serializable
data class UserProfile(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    val isVip: Boolean = false,
    val roles: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String? = null
) {
    /**
     * 获取格式化的创建时间
     */
    fun getFormattedCreatedTime(): String {
        return try {
            val cleanedTime = parseAndCleanDateTime(createdAt)
            val dateTime = LocalDateTime.parse(cleanedTime)
            dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            "未知时间"
        }
    }

    /**
     * 检查是否有指定角色
     */
    fun hasRole(role: String): Boolean {
        return roles.contains(role)
    }

    /**
     * 检查是否是管理员
     */
    fun isAdmin(): Boolean {
        return roles.contains("admin") || roles.contains("super_admin")
    }
}

/**
 * 系统日志条目数据类
 */
@Serializable
data class SystemLogEntry(
    val id: String,
    val type: String, // LOGIN, ERROR, WARNING, INFO等
    val message: String,
    val timestamp: String,
    val userId: String = "",
    val details: Map<String, String> = emptyMap()
)

/**
 * 用户角色数据类（多角色系统）
 */
@Serializable
data class UserRole(
    val id: String,
    val userId: String,
    val roleType: String,
    val grantedAt: String,
    val expiresAt: String? = null,
    val grantedBy: String? = null,
    val isActive: Boolean = true,
    val rolePermissions: Map<String, JsonElement> = emptyMap(),
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        // 角色类型常量
        const val ROLE_USER = "user"
        const val ROLE_VIP = "vip"
        const val ROLE_MODERATOR = "moderator"
        const val ROLE_SUPPORT = "support"
        const val ROLE_ADMIN = "admin"
        const val ROLE_SUPER_ADMIN = "super_admin"

        // 角色显示名称
        fun getRoleDisplayName(roleType: String): String {
            return when (roleType) {
                ROLE_SUPER_ADMIN -> "超级管理员"
                ROLE_ADMIN -> "管理员"
                ROLE_SUPPORT -> "客服"
                ROLE_MODERATOR -> "版主"
                ROLE_VIP -> "VIP用户"
                ROLE_USER -> "普通用户"
                else -> roleType
            }
        }

        // 角色权限级别
        fun getRoleLevel(roleType: String): Int {
            return when (roleType) {
                ROLE_SUPER_ADMIN -> 6
                ROLE_ADMIN -> 5
                ROLE_SUPPORT -> 4
                ROLE_MODERATOR -> 3
                ROLE_VIP -> 2
                ROLE_USER -> 1
                else -> 0
            }
        }
    }

    /**
     * 检查角色是否已过期
     */
    fun isExpired(): Boolean {
        return expiresAt?.let { expiry ->
            try {
                val expiryTime = LocalDateTime.parse(expiry.replace("Z", ""))
                LocalDateTime.now().isAfter(expiryTime)
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    /**
     * 检查角色是否有效（活跃且未过期）
     */
    fun isEffective(): Boolean {
        return isActive && !isExpired()
    }

    /**
     * 获取格式化的授予时间
     */
    fun getFormattedGrantedTime(): String {
        return try {
            val dateTime = LocalDateTime.parse(grantedAt.replace("Z", ""))
            dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } catch (e: Exception) {
            "时间解析错误"
        }
    }

    /**
     * 获取格式化的过期时间
     */
    fun getFormattedExpiryTime(): String? {
        return expiresAt?.let { expiry ->
            try {
                val dateTime = LocalDateTime.parse(expiry.replace("Z", ""))
                dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } catch (e: Exception) {
                "时间解析错误"
            }
        }
    }
}

/**
 * 角色权限配置数据类
 */
@Serializable
data class RolePermission(
    val roleName: String,
    val permissions: JsonObject,
    val description: String,
    val createdAt: String
) {
    /**
     * 检查是否有特定权限
     */
    fun hasPermission(permissionPath: String): Boolean {
        val pathParts = permissionPath.split(".")
        return when (pathParts.size) {
            1 -> {
                val value = permissions[pathParts[0]]?.jsonPrimitive
                value?.content?.toBoolean() ?: false
            }
            2 -> {
                val category = permissions[pathParts[0]]?.jsonObject
                val value = category?.get(pathParts[1])?.jsonPrimitive
                value?.content?.toBoolean() ?: false
            }
            else -> false
        }
    }
}

/**
 * 用户多角色信息数据类
 */
@Serializable
data class UserMultiRoleInfo(
    val userId: String,
    val username: String,
    val email: String? = null,
    val primaryRole: String,
    val activeRoles: List<String>,
    val inactiveRoles: List<String> = emptyList(),
    val isVip: Boolean = false,
    val accountStatus: String = "active"
) {
    /**
     * 检查是否有指定角色
     */
    fun hasRole(roleType: String): Boolean {
        return activeRoles.contains(roleType)
    }

    /**
     * 检查是否有任意一个指定角色
     */
    fun hasAnyRole(roleTypes: List<String>): Boolean {
        return roleTypes.any { activeRoles.contains(it) }
    }

    /**
     * 获取最高权限级别
     */
    fun getMaxPermissionLevel(): Int {
        return activeRoles.maxOfOrNull { UserRole.getRoleLevel(it) } ?: 1
    }

    /**
     * 检查是否为管理员
     */
    fun isAdmin(): Boolean {
        return hasAnyRole(listOf(UserRole.ROLE_SUPPORT, UserRole.ROLE_ADMIN, UserRole.ROLE_SUPER_ADMIN))
    }
}

/**
 * 角色管理请求模型
 */
@Serializable
data class AddRoleRequest(
    val targetUserId: String,
    val roleType: String,
    val expiresAt: String? = null,
    val rolePermissions: JsonObject? = null
)

@Serializable
data class RemoveRoleRequest(
    val targetUserId: String,
    val roleType: String
)
