package top.cywin.onetv.tv.supabase.support

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

/**
 * 客服支持ViewModel
 * 管理1对1客服对话和用户反馈功能
 */
class SupportViewModel : ViewModel() {
    
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
                Log.d(TAG, "刷新用户信息")
                // 这里可以添加刷新用户信息的逻辑
                // 例如重新从服务器获取用户数据
                Log.d(TAG, "用户信息刷新完成")
            } catch (e: Exception) {
                Log.e(TAG, "刷新用户信息失败", e)
            }
        }
    }

    /**
     * 清除用户缓存
     */
    fun clearUserCache() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "清除用户缓存")
                // 这里可以添加清除缓存的逻辑
                Log.d(TAG, "用户缓存清除完成")
            } catch (e: Exception) {
                Log.e(TAG, "清除用户缓存失败", e)
            }
        }
    }

    /**
     * 查看系统日志（仅管理员）
     */
    fun viewSystemLogs() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "查看系统日志")
                // 这里可以添加查看系统日志的逻辑
                Log.d(TAG, "系统日志查看完成")
            } catch (e: Exception) {
                Log.e(TAG, "查看系统日志失败", e)
            }
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
