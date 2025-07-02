package top.cywin.onetv.tv.supabase.support

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable

/**
 * 客服支持ViewModel
 * 管理1对1客服对话和用户反馈功能
 */
class SupportViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "SupportViewModel"
    private val supportRepository = SupportRepository()
    
    // UI状态管理
    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()
    
    // 当前输入的消息
    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()
    
    // 当前活跃的对话ID
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()
    
    init {
        // 监听仓库状态变化
        viewModelScope.launch {
            supportRepository.supportUiState.collect { repositoryState ->
                _uiState.value = repositoryState
            }
        }
    }
    
    /**
     * 开始客服对话
     */
    fun startSupportConversation() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "开始客服对话")
                
                // 检查用户登录状态
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        conversationState = _uiState.value.conversationState.copy(
                            error = "请先登录后再使用客服功能"
                        )
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    conversationState = _uiState.value.conversationState.copy(isLoading = true)
                )
                
                // 获取或创建活跃对话
                val conversation = supportRepository.getOrCreateActiveConversation()
                if (conversation != null) {
                    _currentConversationId.value = conversation.id
                    
                    // 加载对话消息
                    val messages = supportRepository.getConversationMessages(conversation.id)
                    
                    // 订阅实时消息
                    supportRepository.subscribeToConversationMessages(conversation.id)
                    
                    // 标记消息为已读
                    supportRepository.markMessagesAsRead(conversation.id)
                    
                    _uiState.value = _uiState.value.copy(
                        conversationState = SupportConversationState(
                            conversation = conversation,
                            messages = messages,
                            isLoading = false,
                            isConnected = true,
                            error = null
                        ),
                        showConversation = true
                    )
                    
                    Log.d(TAG, "客服对话启动成功，加载了 ${messages.size} 条历史消息")
                } else {
                    _uiState.value = _uiState.value.copy(
                        conversationState = _uiState.value.conversationState.copy(
                            isLoading = false,
                            error = "启动客服对话失败"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动客服对话失败", e)
                _uiState.value = _uiState.value.copy(
                    conversationState = _uiState.value.conversationState.copy(
                        isLoading = false,
                        error = "启动客服对话失败: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * 发送消息
     */
    fun sendMessage() {
        Log.d(TAG, "sendMessage: 开始发送消息")
        val message = _currentMessage.value.trim()
        val conversationId = _currentConversationId.value

        Log.d(TAG, "sendMessage: 消息内容 = '$message', 对话ID = $conversationId")
        if (message.isEmpty() || conversationId == null) {
            Log.w(TAG, "sendMessage: 消息为空或对话ID为空，取消发送")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "发送消息: $message")
                
                val success = supportRepository.sendSupportMessage(conversationId, message)
                if (success) {
                    // 清空输入框
                    _currentMessage.value = ""
                    Log.d(TAG, "消息发送成功")
                } else {
                    _uiState.value = _uiState.value.copy(
                        conversationState = _uiState.value.conversationState.copy(
                            error = "消息发送失败"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送消息失败", e)
                _uiState.value = _uiState.value.copy(
                    conversationState = _uiState.value.conversationState.copy(
                        error = "发送消息失败: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * 更新当前输入的消息
     */
    fun updateCurrentMessage(message: String) {
        _currentMessage.value = message
    }
    
    /**
     * 提交用户反馈
     */
    fun submitFeedback(
        feedbackType: String,
        title: String,
        description: String,
        priority: String = UserFeedback.PRIORITY_NORMAL
    ) {
        Log.d(TAG, "submitFeedback: 开始提交反馈")
        Log.d(TAG, "submitFeedback: 类型=$feedbackType, 标题='$title', 优先级=$priority")
        viewModelScope.launch {
            try {
                Log.d(TAG, "提交用户反馈: $title")
                
                // 检查用户登录状态
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        feedbackState = _uiState.value.feedbackState.copy(
                            error = "请先登录后再提交反馈"
                        )
                    )
                    return@launch
                }
                
                _uiState.value = _uiState.value.copy(
                    feedbackState = _uiState.value.feedbackState.copy(isLoading = true)
                )
                
                val success = supportRepository.submitUserFeedback(
                    feedbackType = feedbackType,
                    title = title,
                    description = description,
                    priority = priority,
                    appVersion = "2.0.0" // 可以从BuildConfig获取
                )
                
                if (success) {
                    // 重新加载反馈列表
                    loadUserFeedbackList()
                    
                    _uiState.value = _uiState.value.copy(
                        feedbackState = _uiState.value.feedbackState.copy(
                            isLoading = false,
                            error = null
                        ),
                        showFeedbackForm = false
                    )
                    
                    Log.d(TAG, "用户反馈提交成功")
                } else {
                    _uiState.value = _uiState.value.copy(
                        feedbackState = _uiState.value.feedbackState.copy(
                            isLoading = false,
                            error = "反馈提交失败"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "提交用户反馈失败", e)
                _uiState.value = _uiState.value.copy(
                    feedbackState = _uiState.value.feedbackState.copy(
                        isLoading = false,
                        error = "反馈提交失败: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * 加载用户反馈列表
     */
    fun loadUserFeedbackList() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "加载用户反馈列表")
                
                _uiState.value = _uiState.value.copy(
                    feedbackState = _uiState.value.feedbackState.copy(isLoading = true)
                )
                
                val feedbackList = supportRepository.getUserFeedbackList()
                
                _uiState.value = _uiState.value.copy(
                    feedbackState = UserFeedbackState(
                        feedbackList = feedbackList,
                        isLoading = false,
                        error = null
                    )
                )
                
                Log.d(TAG, "加载了 ${feedbackList.size} 条反馈")
            } catch (e: Exception) {
                Log.e(TAG, "加载用户反馈列表失败", e)
                _uiState.value = _uiState.value.copy(
                    feedbackState = _uiState.value.feedbackState.copy(
                        isLoading = false,
                        error = "加载反馈列表失败: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * 显示客服对话界面
     */
    fun showConversation() {
        _uiState.value = _uiState.value.copy(showConversation = true)
    }
    
    /**
     * 隐藏客服对话界面
     */
    fun hideConversation() {
        _uiState.value = _uiState.value.copy(showConversation = false)
    }
    
    /**
     * 显示反馈表单
     */
    fun showFeedbackForm() {
        _uiState.value = _uiState.value.copy(showFeedbackForm = true)
    }
    
    /**
     * 隐藏反馈表单
     */
    fun hideFeedbackForm() {
        _uiState.value = _uiState.value.copy(showFeedbackForm = false)
    }
    
    /**
     * 显示反馈列表
     */
    fun showFeedbackList() {
        _uiState.value = _uiState.value.copy(showFeedbackList = true)
        loadUserFeedbackList()
    }
    
    /**
     * 隐藏反馈列表
     */
    fun hideFeedbackList() {
        _uiState.value = _uiState.value.copy(showFeedbackList = false)
    }

    /**
     * 显示反馈详情
     */
    fun showFeedbackDetail(feedback: UserFeedback) {
        _uiState.value = _uiState.value.copy(
            showFeedbackDetail = true,
            selectedFeedback = feedback
        )
    }

    /**
     * 隐藏反馈详情
     */
    fun hideFeedbackDetail() {
        _uiState.value = _uiState.value.copy(
            showFeedbackDetail = false,
            selectedFeedback = null
        )
    }

    /**
     * 撤销反馈
     */
    fun withdrawFeedback(feedbackId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "撤销反馈: $feedbackId")
                val success = supportRepository.withdrawFeedback(feedbackId)
                if (success) {
                    showUserMessage("反馈已撤销", UserMessageType.SUCCESS)
                    // 重新加载反馈列表
                    loadUserFeedbackList()
                } else {
                    showUserMessage("撤销反馈失败", UserMessageType.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "撤销反馈失败", e)
                showUserMessage("撤销反馈失败: ${e.message}", UserMessageType.ERROR)
            }
        }
    }

    /**
     * 删除反馈
     */
    fun deleteFeedback(feedbackId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "删除反馈: $feedbackId")
                val success = supportRepository.deleteFeedback(feedbackId)
                if (success) {
                    showUserMessage("反馈已删除", UserMessageType.SUCCESS)
                    // 重新加载反馈列表
                    loadUserFeedbackList()
                } else {
                    showUserMessage("删除反馈失败", UserMessageType.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除反馈失败", e)
                showUserMessage("删除反馈失败: ${e.message}", UserMessageType.ERROR)
            }
        }
    }
    
    /**
     * 关闭当前对话
     */
    fun closeCurrentConversation() {
        val conversationId = _currentConversationId.value
        if (conversationId != null) {
            viewModelScope.launch {
                supportRepository.closeConversation(conversationId)
                _currentConversationId.value = null
                _uiState.value = _uiState.value.copy(
                    conversationState = SupportConversationState(),
                    showConversation = false
                )
            }
        }
    }
    
    /**
     * 检查当前用户是否为管理员（多角色系统）
     */
    fun checkAdminStatus(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val isAdmin = supportRepository.checkAdminStatus()
                callback(isAdmin)
            } catch (e: Exception) {
                Log.e(TAG, "检查管理员状态失败", e)
                callback(false)
            }
        }
    }

    /**
     * 获取当前用户的所有角色
     */
    fun getUserRoles(callback: (List<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val roles = supportRepository.getUserRoles()
                callback(roles)
            } catch (e: Exception) {
                Log.e(TAG, "获取用户角色失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 检查用户是否有特定权限
     */
    fun checkPermission(permission: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val hasPermission = supportRepository.hasPermission(permission)
                callback(hasPermission)
            } catch (e: Exception) {
                Log.e(TAG, "检查权限失败", e)
                callback(false)
            }
        }
    }

    /**
     * 检查用户是否可以创建私聊
     */
    fun canCreatePrivateConversation(callback: (Boolean) -> Unit) {
        checkPermission("support.create_private_conversations", callback)
    }

    /**
     * 检查用户是否可以查看所有对话
     */
    fun canViewAllConversations(callback: (Boolean) -> Unit) {
        checkPermission("support.view_all_conversations", callback)
    }

    /**
     * 检查用户是否可以管理用户
     */
    fun canManageUsers(callback: (Boolean) -> Unit) {
        checkPermission("admin.manage_users", callback)
    }

    /**
     * 显示用户管理界面
     */
    fun showUserManagement() {
        _uiState.value = _uiState.value.copy(showUserManagement = true)
    }

    /**
     * 隐藏用户管理界面
     */
    fun hideUserManagement() {
        _uiState.value = _uiState.value.copy(showUserManagement = false)
    }

    /**
     * 获取用户统计信息
     */
    fun getUserStats(callback: (Map<String, Any>) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "获取用户统计信息")
                val stats = supportRepository.getUserStats()
                callback(stats)
            } catch (e: Exception) {
                Log.e(TAG, "获取用户统计信息失败", e)
                callback(emptyMap())
            }
        }
    }

    /**
     * 获取最近用户列表
     */
    fun getRecentUsers(callback: (List<UserProfile>) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "获取最近用户列表")
                val users = supportRepository.getRecentUsers()
                callback(users)
            } catch (e: Exception) {
                Log.e(TAG, "获取最近用户列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 获取所有用户列表
     */
    fun getAllUsers(callback: (List<UserProfile>) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "获取所有用户列表")
                val users = supportRepository.getAllUsers()
                callback(users)
            } catch (e: Exception) {
                Log.e(TAG, "获取所有用户列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 更新用户角色
     */
    fun updateUserRole(userId: String, role: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "更新用户角色: $userId -> $role")
                val success = supportRepository.updateUserRole(userId, role)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "更新用户角色失败", e)
                callback(false)
            }
        }
    }

    /**
     * 刷新用户信息
     */
    fun refreshUserInfo() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== 开始刷新用户信息 ===")

                // 显示加载提示
                showUserMessage("正在刷新用户信息...")

                // 清除相关缓存
                val context = getApplication<Application>().applicationContext
                Log.d(TAG, "清除用户数据缓存...")
                top.cywin.onetv.tv.supabase.SupabaseCacheManager.clearCache(
                    context,
                    top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey.USER_DATA
                )
                Log.d(TAG, "清除用户资料缓存...")
                top.cywin.onetv.tv.supabase.SupabaseCacheManager.clearCache(
                    context,
                    top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey.USER_PROFILE
                )

                // 重新获取用户数据
                val currentUser = supportRepository.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    Log.d(TAG, "重新获取用户资料数据...")
                    val apiClient = top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient()
                    val userProfile = apiClient.getUserProfile()
                    val username = userProfile["username"]?.jsonPrimitive?.content ?: "未知用户"
                    val email = userProfile["email"]?.jsonPrimitive?.content ?: "未设置"

                    Log.d(TAG, "用户资料刷新成功 - 用户名: $username, 邮箱: $email")

                    // 触发UI更新
                    _uiState.value = _uiState.value.copy(
                        lastRefreshTime = System.currentTimeMillis()
                    )

                    // 显示成功提示
                    showUserMessage("用户信息刷新成功！")
                } else {
                    Log.w(TAG, "用户未登录，无法刷新用户信息")
                    showUserMessage("用户未登录，无法刷新信息")
                }

                Log.d(TAG, "=== 用户信息刷新完成 ===")
            } catch (e: Exception) {
                Log.e(TAG, "刷新用户信息失败", e)
                showUserMessage("刷新失败: ${e.message}")
            }
        }
    }

    /**
     * 清除用户缓存
     */
    fun clearUserCache() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== 开始清除用户缓存 ===")

                // 显示加载提示
                showUserMessage("正在清除缓存...")

                val context = getApplication<Application>().applicationContext

                // 清除所有用户相关缓存
                Log.d(TAG, "清除Supabase缓存管理器中的所有缓存...")
                top.cywin.onetv.tv.supabase.SupabaseCacheManager.clearAllCachesOnLogoutAsync(context)

                // 清除观看历史缓存
                Log.d(TAG, "清除观看历史缓存...")
                top.cywin.onetv.tv.supabase.SupabaseWatchHistorySessionManager.clearHistoryAsync(context)

                // 清除用户资料缓存
                Log.d(TAG, "清除用户资料缓存...")
                top.cywin.onetv.tv.supabase.SupabaseUserProfileInfoSessionManager.logoutCleanupAsync(context)

                // 清除用户设置缓存
                Log.d(TAG, "清除用户设置缓存...")
                top.cywin.onetv.tv.supabase.SupabaseUserSettingsSessionManager.logoutCleanupAsync(context)

                Log.d(TAG, "=== 用户缓存清除完成 ===")

                // 触发UI更新
                _uiState.value = _uiState.value.copy(
                    lastRefreshTime = System.currentTimeMillis()
                )

                // 显示成功提示
                showUserMessage("缓存清除成功！")

            } catch (e: Exception) {
                Log.e(TAG, "清除用户缓存失败", e)
                showUserMessage("缓存清除失败: ${e.message}")
            }
        }
    }

    /**
     * 查看系统日志（仅管理员）
     */
    fun viewSystemLogs() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== 开始查看系统日志 ===")

                // 显示加载提示
                showUserMessage("正在检查权限...")

                // 检查用户权限
                val currentUser = supportRepository.client.auth.currentUserOrNull()
                if (currentUser == null) {
                    Log.w(TAG, "用户未登录，无法查看系统日志")
                    showUserMessage("用户未登录，无法查看系统日志")
                    return@launch
                }

                // 获取用户角色
                getUserRoles { roles ->
                    if (roles.any { it in listOf("admin", "super_admin") }) {
                        // 管理员可以查看系统日志
                        Log.d(TAG, "用户权限验证通过，角色: $roles")
                        showUserMessage("正在加载系统日志...")

                        viewModelScope.launch {
                            try {
                                // 获取系统日志数据
                                Log.d(TAG, "开始获取系统日志数据...")
                                val systemLogs = getSystemLogsData()

                                // 更新UI状态以显示日志
                                _uiState.value = _uiState.value.copy(
                                    systemLogs = systemLogs,
                                    showSystemLogs = true
                                )

                                Log.d(TAG, "=== 系统日志获取成功，共${systemLogs.size}条记录 ===")
                                showUserMessage("系统日志加载成功，共${systemLogs.size}条记录")
                            } catch (e: Exception) {
                                Log.e(TAG, "获取系统日志失败", e)
                                showUserMessage("获取系统日志失败: ${e.message}")
                            }
                        }
                    } else {
                        Log.w(TAG, "用户权限不足，无法查看系统日志，当前角色: $roles")
                        showUserMessage("权限不足，仅管理员可查看系统日志")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "查看系统日志失败", e)
                showUserMessage("查看系统日志失败: ${e.message}")
            }
        }
    }

    /**
     * 显示用户消息提示
     */
    private fun showUserMessage(message: String, type: UserMessageType = UserMessageType.INFO) {
        _uiState.value = _uiState.value.copy(
            userMessage = message,
            showUserMessage = true,
            userMessageType = type
        )

        // 3秒后自动隐藏消息
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            hideUserMessage()
        }
    }

    /**
     * 隐藏用户消息提示
     */
    fun hideUserMessage() {
        _uiState.value = _uiState.value.copy(
            showUserMessage = false,
            userMessage = ""
        )
    }

    /**
     * 获取系统日志数据
     */
    private suspend fun getSystemLogsData(): List<SystemLogEntry> = withContext(Dispatchers.IO) {
        try {
            // 简化实现：返回模拟的系统日志数据
            // 在实际部署时可以连接真实的日志系统
            val currentTime = java.time.LocalDateTime.now()

            listOf(
                SystemLogEntry(
                    id = "log_001",
                    type = "LOGIN",
                    message = "用户登录成功",
                    timestamp = currentTime.minusHours(1).toString(),
                    userId = "user_001",
                    details = mapOf(
                        "ip_address" to "192.168.1.100",
                        "device_info" to "Android TV"
                    )
                ),
                SystemLogEntry(
                    id = "log_002",
                    type = "ERROR",
                    message = "网络连接超时",
                    timestamp = currentTime.minusHours(2).toString(),
                    userId = "system",
                    details = mapOf(
                        "error_code" to "TIMEOUT",
                        "duration" to "30s"
                    )
                ),
                SystemLogEntry(
                    id = "log_003",
                    type = "INFO",
                    message = "系统启动完成",
                    timestamp = currentTime.minusHours(3).toString(),
                    userId = "system",
                    details = mapOf(
                        "startup_time" to "15s",
                        "version" to "2.0.0"
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取系统日志数据失败", e)
            emptyList()
        }
    }

    /**
     * 获取所有对话列表（管理员功能）
     */
    fun getAllConversations(callback: (List<SupportConversation>) -> Unit) {
        viewModelScope.launch {
            try {
                val conversations = supportRepository.getAllConversations()
                callback(conversations)
            } catch (e: Exception) {
                Log.e(TAG, "获取所有对话列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 获取用户对话历史
     */
    fun getUserConversations(callback: (List<SupportConversation>) -> Unit) {
        viewModelScope.launch {
            try {
                val conversations = supportRepository.getUserConversations()
                callback(conversations)
            } catch (e: Exception) {
                Log.e(TAG, "获取用户对话历史失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 分配客服给对话
     */
    fun assignSupportToConversation(conversationId: String, supportId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = supportRepository.assignSupportToConversation(conversationId, supportId)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "分配客服失败", e)
                callback(false)
            }
        }
    }

    /**
     * 更新对话优先级
     */
    fun updateConversationPriority(conversationId: String, priority: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = supportRepository.updateConversationPriority(conversationId, priority)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "更新对话优先级失败", e)
                callback(false)
            }
        }
    }

    /**
     * 获取对话统计信息
     */
    fun getConversationStats(callback: (Map<String, Int>) -> Unit) {
        viewModelScope.launch {
            try {
                val stats = supportRepository.getConversationStats()
                callback(stats)
            } catch (e: Exception) {
                Log.e(TAG, "获取对话统计信息失败", e)
                callback(emptyMap())
            }
        }
    }

    /**
     * 获取反馈统计信息
     */
    fun getFeedbackStats(callback: (Map<String, Int>) -> Unit) {
        viewModelScope.launch {
            try {
                val stats = supportRepository.getFeedbackStats()
                callback(stats)
            } catch (e: Exception) {
                Log.e(TAG, "获取反馈统计信息失败", e)
                callback(emptyMap())
            }
        }
    }

    /**
     * 获取用户反馈列表
     */
    fun getUserFeedbacks(callback: (List<UserFeedback>) -> Unit) {
        viewModelScope.launch {
            try {
                val feedbacks = supportRepository.getUserFeedbacks()
                callback(feedbacks)
            } catch (e: Exception) {
                Log.e(TAG, "获取用户反馈列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            conversationState = _uiState.value.conversationState.copy(error = null),
            feedbackState = _uiState.value.feedbackState.copy(error = null)
        )
    }

    // ==================== 反馈管理功能 ====================

    /**
     * 显示反馈管理界面
     */
    fun showFeedbackManagement() {
        _uiState.value = _uiState.value.copy(showFeedbackManagement = true)
    }

    /**
     * 隐藏反馈管理界面
     */
    fun hideFeedbackManagement() {
        _uiState.value = _uiState.value.copy(showFeedbackManagement = false)
    }

    /**
     * 获取所有反馈列表（管理员）
     */
    fun getAllFeedbacks(callback: (List<UserFeedback>) -> Unit) {
        viewModelScope.launch {
            try {
                val feedbacks = supportRepository.getAllFeedbacks()
                callback(feedbacks)
            } catch (e: Exception) {
                Log.e(TAG, "获取所有反馈列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 更新反馈状态
     */
    fun updateFeedbackStatus(feedbackId: String, status: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = supportRepository.updateFeedbackStatus(feedbackId, status)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "更新反馈状态失败", e)
                callback(false)
            }
        }
    }

    /**
     * 回复反馈
     */
    fun replyToFeedback(feedbackId: String, response: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = supportRepository.replyToFeedback(feedbackId, response)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "回复反馈失败", e)
                callback(false)
            }
        }
    }

    /**
     * 获取反馈统计信息（管理员）
     */
    fun getAllFeedbackStats(callback: (Map<String, Int>) -> Unit) {
        viewModelScope.launch {
            try {
                val stats = supportRepository.getAllFeedbackStats()
                callback(stats)
            } catch (e: Exception) {
                Log.e(TAG, "获取反馈统计信息失败", e)
                callback(emptyMap())
            }
        }
    }

    // ==================== 客服工作台功能 ====================

    /**
     * 显示客服工作台界面
     */
    fun showSupportDesk() {
        _uiState.value = _uiState.value.copy(showSupportDesk = true)
    }

    /**
     * 隐藏客服工作台界面
     */
    fun hideSupportDesk() {
        _uiState.value = _uiState.value.copy(showSupportDesk = false)
    }

    /**
     * 获取客服工作台统计信息
     */
    fun getSupportDeskStats(callback: (Map<String, Any>) -> Unit) {
        viewModelScope.launch {
            try {
                val stats = supportRepository.getSupportDeskStats()
                callback(stats)
            } catch (e: Exception) {
                Log.e(TAG, "获取客服工作台统计信息失败", e)
                callback(emptyMap())
            }
        }
    }

    /**
     * 获取待处理的对话列表
     */
    fun getPendingConversations(callback: (List<SupportConversationDisplay>) -> Unit) {
        viewModelScope.launch {
            try {
                val conversations = supportRepository.getPendingConversations()
                callback(conversations)
            } catch (e: Exception) {
                Log.e(TAG, "获取待处理对话列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 获取最近的反馈列表
     */
    fun getRecentFeedbacks(callback: (List<UserFeedback>) -> Unit) {
        viewModelScope.launch {
            try {
                val feedbacks = supportRepository.getRecentFeedbacks()
                callback(feedbacks)
            } catch (e: Exception) {
                Log.e(TAG, "获取最近反馈列表失败", e)
                callback(emptyList())
            }
        }
    }

    /**
     * 接管对话（客服）
     */
    fun takeOverConversation(conversationId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = supportRepository.takeOverConversation(conversationId)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "接管对话失败", e)
                callback(false)
            }
        }
    }

    /**
     * 结束对话（客服）
     */
    fun endConversation(conversationId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = supportRepository.endConversation(conversationId)
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "结束对话失败", e)
                callback(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理资源
        supportRepository.cleanup()
        closeCurrentConversation()
    }
}
