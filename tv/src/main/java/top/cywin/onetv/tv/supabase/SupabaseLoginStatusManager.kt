package top.cywin.onetv.tv.supabase

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.utils.Logger

/**
 * 登录状态管理器
 * 负责管理登录过程中的状态、进度反馈和用户体验优化
 */
class SupabaseLoginStatusManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val logger = Logger.create("SupabaseLoginStatusManager")
    
    // 登录阶段枚举
    enum class LoginStage {
        IDLE,                    // 空闲状态
        STAGE_1_CRITICAL,        // 阶段1：关键操作 (0-1秒)
        STAGE_2_FUNCTIONAL,      // 阶段2：功能数据 (1-3秒)
        STAGE_3_HEAVY,          // 阶段3：重型数据 (3秒+)
        COMPLETED,              // 完成
        ERROR                   // 错误
    }
    
    // 登录状态数据类
    data class LoginStatus(
        val stage: LoginStage = LoginStage.IDLE,
        val message: String = "",
        val progress: Float = 0f,
        val icon: String = "🔐",
        val canNavigate: Boolean = false,
        val error: String? = null
    )
    
    // 状态流
    private val _loginStatus = MutableStateFlow(LoginStatus())
    val loginStatus: StateFlow<LoginStatus> = _loginStatus
    
    // Snackbar状态
    val snackbarHostState = SnackbarHostState()
    
    /**
     * 开始登录流程
     */
    fun startLogin() {
        logger.i("🚀 开始优化登录流程")
        updateStatus(
            stage = LoginStage.STAGE_1_CRITICAL,
            message = "验证用户身份...",
            progress = 0.1f,
            icon = "🔐"
        )
    }
    
    /**
     * 阶段1：关键操作 (0-1秒)
     * 必须同步执行，确保权限正确
     */
    suspend fun executeStage1Critical(
        onClearCache: suspend () -> Unit,
        onSaveSession: suspend (String) -> Unit,
        onGetBasicUserData: suspend () -> Any?,
        accessToken: String
    ): Boolean {
        return try {
            logger.i("🔑 执行阶段1：关键操作")

            // 1. 清除所有缓存 (确保用户获得正确权限)
            updateStatus(
                stage = LoginStage.STAGE_1_CRITICAL,
                message = "清除所有缓存...",
                progress = 0.2f,
                icon = "🧹"
            )
            onClearCache()
            
            // 2. 保存新会话令牌
            updateStatus(
                stage = LoginStage.STAGE_1_CRITICAL,
                message = "保存会话令牌...",
                progress = 0.5f,
                icon = "💾"
            )
            onSaveSession(accessToken)
            
            // 3. 获取用户基础权限数据
            updateStatus(
                stage = LoginStage.STAGE_1_CRITICAL,
                message = "获取用户权限...",
                progress = 0.8f,
                icon = "👤"
            )
            val userData = onGetBasicUserData()
            
            // 4. 标记可以跳转主界面
            updateStatus(
                stage = LoginStage.STAGE_1_CRITICAL,
                message = "准备进入主界面...",
                progress = 1.0f,
                icon = "✅",
                canNavigate = true
            )
            
            logger.i("✅ 阶段1完成，用户可以进入主界面")
            true
        } catch (e: Exception) {
            logger.e("❌ 阶段1执行失败: ${e.message}", e)
            updateStatus(
                stage = LoginStage.ERROR,
                message = "登录失败: ${e.message}",
                progress = 0f,
                icon = "❌",
                error = e.message
            )
            false
        }
    }
    
    /**
     * 阶段2：功能数据 (1-3秒)
     * 在主界面后台执行，用户可以开始使用
     * 使用GlobalScope确保Activity销毁后仍能继续执行
     */
    fun executeStage2Functional(
        onPreheatCache: suspend () -> Unit,
        onUpdateUserSession: suspend () -> Unit,
        onRecordLoginLog: suspend () -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                logger.i("⚡ 执行阶段2：功能数据")

                updateStatus(
                    stage = LoginStage.STAGE_2_FUNCTIONAL,
                    message = "加载用户设置...",
                    progress = 0.3f,
                    icon = "⚙️"
                )

                // 1. 基础缓存预热 (VIP状态、用户设置等)
                retryOperation("基础缓存预热") { onPreheatCache() }

                // 2. 写入用户会话 (在线统计)
                updateStatus(
                    stage = LoginStage.STAGE_2_FUNCTIONAL,
                    message = "更新在线状态...",
                    progress = 0.6f,
                    icon = "🌐"
                )
                retryOperation("用户会话更新") { onUpdateUserSession() }

                // 3. 记录设备登录信息
                updateStatus(
                    stage = LoginStage.STAGE_2_FUNCTIONAL,
                    message = "记录登录信息...",
                    progress = 0.9f,
                    icon = "📱"
                )
                retryOperation("登录日志记录") { onRecordLoginLog() }

                logger.i("✅ 阶段2完成，功能数据加载完毕")
                showSnackbar("功能数据加载完成", "✅")

            } catch (e: Exception) {
                logger.e("❌ 阶段2执行失败: ${e.message}", e)
                showSnackbar("部分功能加载失败", "⚠️")
            }
        }
    }
    
    /**
     * 阶段3：重型数据 (3秒+)
     * 延迟执行，不影响核心功能使用
     * 使用GlobalScope确保Activity销毁后仍能继续执行
     */
    fun executeStage3Heavy(
        onSyncWatchHistory: suspend () -> Unit,
        onFullCachePreheat: suspend () -> Unit,
        onInitializeWatchHistoryManager: suspend () -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                logger.i("🔄 执行阶段3：重型数据")

                // 1. 观看历史强制同步 (最耗时)
                updateStatus(
                    stage = LoginStage.STAGE_3_HEAVY,
                    message = "同步观看历史...",
                    progress = 0.2f,
                    icon = "🕒"
                )
                retryOperation("观看历史同步") { onSyncWatchHistory() }

                // 2. 完整缓存预热
                updateStatus(
                    stage = LoginStage.STAGE_3_HEAVY,
                    message = "预热应用缓存...",
                    progress = 0.6f,
                    icon = "🔥"
                )
                retryOperation("完整缓存预热") { onFullCachePreheat() }

                // 3. 观看历史管理器初始化
                updateStatus(
                    stage = LoginStage.STAGE_3_HEAVY,
                    message = "初始化历史管理...",
                    progress = 0.9f,
                    icon = "📚"
                )
                retryOperation("历史管理器初始化") { onInitializeWatchHistoryManager() }

                // 4. 完成所有加载
                updateStatus(
                    stage = LoginStage.COMPLETED,
                    message = "所有数据加载完成",
                    progress = 1.0f,
                    icon = "🎉"
                )

                logger.i("🎉 阶段3完成，所有数据加载完毕")
                showSnackbar("所有数据同步完成", "🎉")

            } catch (e: Exception) {
                logger.e("❌ 阶段3执行失败: ${e.message}", e)
                showSnackbar("历史数据同步失败", "⚠️")
            }
        }
    }
    
    /**
     * 更新登录状态
     */
    private fun updateStatus(
        stage: LoginStage,
        message: String,
        progress: Float,
        icon: String,
        canNavigate: Boolean = false,
        error: String? = null
    ) {
        _loginStatus.value = LoginStatus(
            stage = stage,
            message = message,
            progress = progress,
            icon = icon,
            canNavigate = canNavigate,
            error = error
        )
        logger.i("$icon $message (${(progress * 100).toInt()}%)")
    }
    
    /**
     * 显示Snackbar提示
     */
    private fun showSnackbar(message: String, icon: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("$icon $message")
        }
    }
    
    /**
     * 重试机制
     * 对于网络相关操作进行重试
     */
    private suspend fun retryOperation(operationName: String, maxRetries: Int = 3, operation: suspend () -> Unit) {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                operation()
                if (attempt > 0) {
                    logger.i("✅ $operationName 重试成功 (第${attempt + 1}次尝试)")
                }
                return
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    logger.w("⚠️ $operationName 失败，准备重试 (第${attempt + 1}次尝试): ${e.message}")
                    delay(1000L * (attempt + 1)) // 递增延迟
                } else {
                    logger.e("❌ $operationName 重试失败，已达到最大重试次数: ${e.message}")
                }
            }
        }

        // 如果所有重试都失败，抛出最后一个异常
        lastException?.let { throw it }
    }

    /**
     * 重置状态
     */
    fun reset() {
        _loginStatus.value = LoginStatus()
        logger.i("🔄 登录状态已重置")
    }
}
