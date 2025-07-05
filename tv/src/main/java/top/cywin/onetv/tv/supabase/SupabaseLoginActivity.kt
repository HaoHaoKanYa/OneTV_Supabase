package top.cywin.onetv.tv.supabase

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseRepository
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.tv.supabase.SupabaseCacheManager
import top.cywin.onetv.core.data.utils.Logger
import top.cywin.onetv.tv.MainActivity
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.supabase.SupabaseUserProfileInfoSessionManager
import top.cywin.onetv.tv.supabase.SupabaseUserSettingsSessionManager
import top.cywin.onetv.tv.supabase.SupabaseWatchHistorySessionManager
import java.io.IOException
import java.io.File
import kotlin.coroutines.resume
import com.google.gson.Gson

/**
 * Supabase登录活动
 * 使用Supabase Auth UI进行用户身份验证，支持横竖屏切换
 */
class SupabaseLoginActivity : ComponentActivity() {
    private val repository = SupabaseRepository()
    private val log = object {
        private val logger = Logger.create("SupabaseLoginActivity")

        fun i(message: String) {
            logger.i(message)
            LoginProgressState.addLogMessage(message)
        }

        fun e(message: String, throwable: Throwable? = null) {
            logger.e(message, throwable)
            LoginProgressState.addLogMessage("错误: $message")
        }

        fun w(message: String) {
            logger.w(message)
            LoginProgressState.addLogMessage("警告: $message")
        }

        fun d(message: String) {
            logger.d(message)
            LoginProgressState.addLogMessage(message)
        }
    }

    // 新增：登录状态管理器
    private lateinit var loginStatusManager: SupabaseLoginStatusManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置线程异常处理，防止未捕获的异常导致应用崩溃
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log.e("未捕获的异常: ${throwable.message}", throwable)
            // 记录错误但不退出应用
        }

        // 初始化Supabase客户端
        SupabaseClient.initialize(this)

        // 初始化登录状态管理器
        loginStatusManager = SupabaseLoginStatusManager(this, lifecycleScope)

        // 检查是否是从设置页面手动进入登录页面（Intent中的标志）
        val isManualLogin = intent.getBooleanExtra(EXTRA_MANUAL_LOGIN, false)
        
        if (isManualLogin) {
            // 如果是手动登录，显示登录界面
            log.i("显示登录界面，用户主动请求登录")
            setContent {
                LoginContent { handleLoginSuccess() }
            }
            return
        }
        
        // 应用启动流程 - 显示启动界面，然后自动进入主界面
        setContent {
            MyTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 显示应用启动界面，例如Logo或加载指示器
                    SplashScreen()
                }
            }
        }
        
        // 立即进入主界面，不等待登录验证
        log.i("立即进入主界面")
        // 300毫秒延迟，让启动界面有时间显示，避免启动感觉卡顿
        Handler().postDelayed({
            safeNavigateToMainActivity()
        }, 300)
        
        // 在后台完成用户验证和数据加载
        lifecycleScope.launch {
            try {
                // 检查本地缓存
                val sessionToken = getSharedPreferences("user", MODE_PRIVATE).getString("session", null)
                if (sessionToken != null && sessionToken.isNotEmpty()) {
                    log.i("检测到本地会话缓存")
                }
                
                // 检查Supabase登录状态
                val user = repository.getCurrentUser()
                if (user != null) {
                    log.i("后台验证：用户已登录: ${user.email}")
                    
                    // 获取访问令牌并更新用户数据
                    val accessToken = repository.getAccessToken()
                    if (accessToken != null) {
                        // 保存会话到SharedPreferences
                        saveSessionToPreferences(accessToken)
                        
                        try {
                            // 后台获取用户资料
                            val userData = repository.getUserData(accessToken)
                            // 新增：应用启动时写入/刷新 user_sessions 表，便于后端统计真实在线用户
                            try {
                                val apiClient = top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient.getInstance()
                                val userId = userData.userid
                                val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                                val expiresAt = now.plusMinutes(30).toString()
                                val deviceInfo = getDeviceInfo()
                                val platform = "android"
                                val appVersion = try {
                                    packageManager.getPackageInfo(packageName, 0).versionName
                                } catch (e: Exception) { null }
                                val sessionResult = apiClient.updateUserSession(
                                    userId = userId,
                                    expiresAt = expiresAt,
                                    deviceInfo = deviceInfo,
                                    ipAddress = null,
                                    platform = platform,
                                    appVersion = appVersion
                                )
                                log.i("✅ 应用启动已写入/刷新 user_sessions 会话，后端可统计在线人数")
                            } catch (e: Exception) {
                                log.e("❌ 应用启动写入 user_sessions 会话失败: ${e.message}")
                            }
                            // 使用挂起函数保存用户数据和时间戳
                            withContext(Dispatchers.IO) {
                                SupabaseSessionManager.saveCachedUserData(this@SupabaseLoginActivity, userData)
                                SupabaseSessionManager.saveLastLoadedTime(this@SupabaseLoginActivity, System.currentTimeMillis())
                            }
                            
                            // 记录登录设备信息
                            val deviceInfo = getDeviceInfo()
                            recordLoginLog(accessToken, deviceInfo)
                            log.i("后台用户资料更新完成")
                        } catch (e: Exception) {
                            log.e("后台用户资料更新失败", e)
                        }
                    }
                } else {
                    log.i("后台验证：用户未登录，使用游客模式")
                }
            } catch (e: Exception) {
                log.e("后台登录验证失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 登录界面内容，支持屏幕旋转和智能反馈
     */
    @Composable
    private fun LoginContent(onLoginSuccess: () -> Unit) {
        // 获取当前屏幕配置
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        MyTVTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 主登录界面
                    SupabaseAuthUI.LoginScreen(
                        onLoginSuccess = onLoginSuccess
                    )

                    // 智能反馈系统
                    LoginStatusOverlay()
                }
            }
        }
    }

    /**
     * 登录状态覆盖层，显示进度和反馈
     */
    @Composable
    private fun LoginStatusOverlay() {
        if (::loginStatusManager.isInitialized) {
            val loginStatus by loginStatusManager.loginStatus.collectAsState()

            Box(modifier = Modifier.fillMaxSize()) {
                // 显示登录进度
                if (loginStatus.stage != SupabaseLoginStatusManager.LoginStage.IDLE) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(32.dp)
                                .widthIn(max = 400.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 状态图标和消息
                                Text(
                                    text = loginStatus.icon,
                                    style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Text(
                                    text = loginStatus.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // 进度条
                                LinearProgressIndicator(
                                    progress = loginStatus.progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                // 进度百分比
                                Text(
                                    text = "${(loginStatus.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                // 错误信息
                                loginStatus.error?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Snackbar主机
                SnackbarHost(
                    hostState = loginStatusManager.snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
    
    /**
     * 处理屏幕配置变更，例如旋转
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 屏幕旋转时不需要特殊处理，Compose会自动重组界面
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val version = Build.VERSION.RELEASE
        val versionCode = Build.VERSION.SDK_INT
        
        return "$manufacturer $model (Android $version, API $versionCode)"
    }
    
    /**
     * 添加登录日志记录函数，直接调用Supabase Edge Function
     */
    private suspend fun recordLoginLog(accessToken: String, deviceInfo: String) {
        try {
            log.i("正在记录登录日志...")
            
            val result = repository.recordLoginLog(accessToken, deviceInfo)
            log.i("✅ 登录日志记录成功")
            
        } catch (e: Exception) {
            log.e("❌ 记录登录日志失败: ${e.message}")
        }
    }
    
    /**
     * 处理登录成功事件 - 优化版本
     * 使用三阶段分层加载，显著提升用户体验
     */
    private fun handleLoginSuccess() {
        lifecycleScope.launch {
            try {
                log.i("🚀 开始优化登录流程...")
                loginStatusManager.startLogin()

                // 获取当前用户信息
                val user = repository.getCurrentUser()
                if (user != null) {
                    log.i("✅ 验证登录成功: ${user.email}")

                    // 获取访问令牌
                    val accessToken = repository.getAccessToken()
                    if (accessToken == null) {
                        log.e("❌ 无法获取访问令牌")
                        safeNavigateToMainActivity()
                        return@launch
                    }

                    // 🔑 阶段1：关键操作 (0-1秒) - 必须同步执行
                    val stage1Success = loginStatusManager.executeStage1Critical(
                        onClearCache = { executeCriticalCacheClearing() },
                        onSaveSession = { token -> saveSessionToPreferences(token) },
                        onGetBasicUserData = { repository.getUserData(accessToken) },
                        accessToken = accessToken
                    )

                    if (stage1Success) {
                        // 获取用户数据，用于后台任务
                        val userData = repository.getUserData(accessToken)

                        // 立即跳转到主界面，用户可以开始使用
                        log.i("🚀 关键操作完成，立即进入主界面")
                        safeNavigateToMainActivity()

                        // ⚡ 阶段2：功能数据 (1-3秒) - 后台执行
                        loginStatusManager.executeStage2Functional(
                            onPreheatCache = { executeFunctionalCachePreheat(userData) },
                            onUpdateUserSession = { executeUserSessionUpdate(userData, accessToken) },
                            onRecordLoginLog = { executeLoginLogRecording(accessToken) }
                        )

                        // 🔄 阶段3：重型数据 (3秒+) - 延迟执行
                        loginStatusManager.executeStage3Heavy(
                            onSyncWatchHistory = { executeWatchHistorySync() },
                            onFullCachePreheat = { executeFullCachePreheat(userData) },
                            onInitializeWatchHistoryManager = { executeWatchHistoryManagerInit() }
                        )
                    } else {
                        log.e("❌ 关键操作失败，使用备用流程")
                        safeNavigateToMainActivity()
                    }
                } else {
                    log.e("❌ 登录成功但无法获取用户信息")
                    safeNavigateToMainActivity()
                }
            } catch (e: Exception) {
                log.e("❌ 登录处理失败: ${e.message}")
                safeNavigateToMainActivity()
            }
        }
    }

    /**
     * 阶段1：执行完整缓存清理
     * 清除所有缓存，确保用户获得正确的权限
     */
    private suspend fun executeCriticalCacheClearing() = withContext(Dispatchers.IO) {
        try {
            log.i("🧹 执行完整缓存清理...")

            // 创建MainViewModel实例来执行完整缓存清理
            val mainViewModel = ViewModelProvider(this@SupabaseLoginActivity)
                .get(top.cywin.onetv.tv.ui.screens.main.MainViewModel::class.java)

            // 使用suspendCancellableCoroutine包装回调式API
            suspendCancellableCoroutine<Unit> { cont ->
                mainViewModel.clearAllCache(true) {
                    log.i("✅ 所有缓存清理完成")
                    cont.resume(Unit)
                }
            }

            // 清理TV专用会话缓存
            val tvCachePath = File(this@SupabaseLoginActivity.externalCacheDir?.parent ?: "", "tv_sessions")
            if (tvCachePath.exists()) {
                tvCachePath.deleteRecursively()
                log.i("🗑️ TV专用会话缓存已清除")
            }

            // 清理EPG缓存
            try {
                top.cywin.onetv.core.data.entities.epg.EpgList.clearCache()
                log.i("🗑️ EPG缓存已清除")
            } catch (e: Exception) {
                log.e("❌ EPG缓存清除失败: ${e.message}")
            }

            // 清理WebView缓存
            try {
                val webViewDir = this@SupabaseLoginActivity.cacheDir?.parent?.let { File("$it/app_webview") }
                if (webViewDir?.deleteRecursively() == true) {
                    log.i("🗑️ WebView缓存已清除")
                } else {
                    log.d("WebView缓存清除跳过（目录不存在或清除失败）")
                }
            } catch (e: Exception) {
                log.e("❌ WebView缓存清除失败: ${e.message}")
            }

            // 删除旧的user_sessions会话
            try {
                val userId = repository.getCurrentUser()?.id ?: ""
                if (userId.isNotBlank()) {
                    logoutAndClearSessions(userId)
                }
            } catch (e: Exception) {
                log.e("❌ 删除旧会话失败: ${e.message}")
            }

            log.i("✅ 完整缓存清理完成，用户将获得正确权限")
        } catch (e: Exception) {
            log.e("❌ 完整缓存清理失败: ${e.message}")
            throw e
        }
    }

    /**
     * 阶段2：执行功能缓存预热
     * 预热基础功能所需的缓存
     */
    private suspend fun executeFunctionalCachePreheat(userData: top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv) = withContext(Dispatchers.IO) {
        try {
            log.i("⚡ 执行功能缓存预热...")

            // 基础缓存预热（不包括观看历史）
            SupabaseCacheManager.preheatUserCache(this@SupabaseLoginActivity, userData.userid, false)

            // 保存基础用户数据到各个SessionManager
            saveBasicUserDataToCache(userData)

            log.i("✅ 功能缓存预热完成")
        } catch (e: Exception) {
            log.e("❌ 功能缓存预热失败: ${e.message}")
            throw e
        }
    }

    /**
     * 阶段2：执行用户会话更新
     */
    private suspend fun executeUserSessionUpdate(userData: top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv, accessToken: String) = withContext(Dispatchers.IO) {
        try {
            log.i("🌐 更新用户会话...")
            val apiClient = top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient.getInstance()
            val userId = userData.userid
            val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            val expiresAt = now.plusMinutes(30).toString()
            val deviceInfo = getDeviceInfo()
            val platform = "android"
            val appVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) { null }

            apiClient.updateUserSession(
                userId = userId,
                expiresAt = expiresAt,
                deviceInfo = deviceInfo,
                ipAddress = null,
                platform = platform,
                appVersion = appVersion
            )
            log.i("✅ 用户会话更新完成")
        } catch (e: Exception) {
            log.e("❌ 用户会话更新失败: ${e.message}")
            throw e
        }
    }

    /**
     * 阶段2：执行登录日志记录
     */
    private suspend fun executeLoginLogRecording(accessToken: String) = withContext(Dispatchers.IO) {
        try {
            log.i("📱 记录登录日志...")
            val deviceInfo = getDeviceInfo()
            recordLoginLog(accessToken, deviceInfo)
            log.i("✅ 登录日志记录完成")
        } catch (e: Exception) {
            log.e("❌ 登录日志记录失败: ${e.message}")
            throw e
        }
    }

    /**
     * 阶段3：执行观看历史同步
     */
    private suspend fun executeWatchHistorySync() = withContext(Dispatchers.IO) {
        try {
            log.i("🕒 同步观看历史...")

            // 确保用户数据已经保存到缓存，等待一下
            delay(1000) // 等待1秒确保用户数据已保存

            val syncResult = top.cywin.onetv.tv.supabase.sync.SupabaseWatchHistorySyncService.syncFromServer(this@SupabaseLoginActivity, 200)

            if (syncResult) {
                log.i("✅ 观看历史同步完成")
            } else {
                log.w("⚠️ 观看历史同步返回false，可能没有数据或用户ID获取失败")
            }
        } catch (e: Exception) {
            log.e("❌ 观看历史同步失败: ${e.message}")
            throw e
        }
    }

    /**
     * 阶段3：执行完整缓存预热
     */
    private suspend fun executeFullCachePreheat(userData: top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv) = withContext(Dispatchers.IO) {
        try {
            log.i("🔥 执行完整缓存预热...")

            // 完整缓存预热（包括观看历史）
            SupabaseCacheManager.preheatUserCache(this@SupabaseLoginActivity, userData.userid, true)

            log.i("✅ 完整缓存预热完成")
        } catch (e: Exception) {
            log.e("❌ 完整缓存预热失败: ${e.message}")
            throw e
        }
    }



    /**
     * 阶段3：执行观看历史管理器初始化
     */
    private suspend fun executeWatchHistoryManagerInit() = withContext(Dispatchers.IO) {
        try {
            log.i("📚 初始化观看历史管理器...")
            SupabaseWatchHistorySessionManager.initializeAsync(this@SupabaseLoginActivity)
            log.i("✅ 观看历史管理器初始化完成")
        } catch (e: Exception) {
            log.e("❌ 观看历史管理器初始化失败: ${e.message}")
            throw e
        }
    }

    /**
     * 保存基础用户数据到缓存
     */
    private suspend fun saveBasicUserDataToCache(userData: top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv) = withContext(Dispatchers.IO) {
        try {
            // 保存到主缓存
            SupabaseCacheManager.saveCache(
                this@SupabaseLoginActivity,
                SupabaseCacheKey.USER_DATA,
                userData
            )

            // 保存最后加载时间
            SupabaseCacheManager.saveCache(
                this@SupabaseLoginActivity,
                SupabaseCacheKey.LAST_LOADED_TIME,
                System.currentTimeMillis()
            )

            // 保存为JSON格式
            try {
                val gson = Gson()
                val userDataJson = gson.toJson(userData)
                SupabaseCacheManager.saveCache(
                    this@SupabaseLoginActivity,
                    SupabaseCacheKey.USER_DATA_JSON,
                    userDataJson
                )
            } catch (e: Exception) {
                log.e("❌ 保存JSON格式失败: ${e.message}")
            }

            // 保存到个人资料缓存
            try {
                SupabaseUserProfileInfoSessionManager.saveUserProfileData(this@SupabaseLoginActivity, userData)
            } catch (e: Exception) {
                log.e("❌ 保存个人资料缓存失败: ${e.message}")
            }

            // 保存默认用户设置
            try {
                val defaultSettings = SupabaseUserSettingsSessionManager.UserSettings(
                    userId = userData.userid,
                    displayName = userData.username,
                    updatedAt = System.currentTimeMillis().toString()
                )
                SupabaseUserSettingsSessionManager.saveUserSettings(this@SupabaseLoginActivity, defaultSettings)
            } catch (e: Exception) {
                log.e("❌ 保存用户设置失败: ${e.message}")
            }

            // 保存VIP状态
            try {
                val vipStatus = top.cywin.onetv.tv.supabase.VipStatus(
                    isVip = userData.is_vip,
                    vipStart = userData.vipstart,
                    vipEnd = userData.vipend,
                    daysRemaining = calculateRemainingDays(userData.vipend)
                )
                SupabaseCacheManager.saveCache(
                    this@SupabaseLoginActivity,
                    SupabaseCacheKey.USER_VIP_STATUS,
                    vipStatus
                )
            } catch (e: Exception) {
                log.e("❌ 保存VIP状态失败: ${e.message}")
            }

            log.i("💾 基础用户数据保存完成")
        } catch (e: Exception) {
            log.e("❌ 保存基础用户数据失败: ${e.message}")
            throw e
        }
    }

    
    /**
     * 将MainViewModel.forceRefreshUserDataSync的回调式API包装为一个挂起函数，
     * 便于在协程中使用并获取返回的Boolean值。
     */
    private suspend fun forceRefreshUserDataSuspend(
        mainViewModel: top.cywin.onetv.tv.ui.screens.main.MainViewModel, 
        context: android.content.Context
    ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        mainViewModel.forceRefreshUserDataSync(context) { success ->
            cont.resume(success)
        }
    }
    
    /**
     * 将会话保存到SharedPreferences
     */
    private fun saveSessionToPreferences(token: String) {
        // 保留原来的SharedPreferences存储方式，确保兼容性
        getSharedPreferences("user", MODE_PRIVATE).edit()
            .putString("session", token)
            .apply()

        // 新增：同时保存到SupabaseCacheManager (使用协程作用域)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseCacheManager.saveCache(
                    this@SupabaseLoginActivity,
                    SupabaseCacheKey.SESSION,
                    token
                )
                Log.i("SupabaseLoginActivity", "📝 会话令牌已保存到缓存管理器")

                // ✅ 设置SupabaseApiClient的sessionToken
                try {
                    val apiClient = top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient.getInstance()
                    apiClient.setSessionToken(token)
                    Log.i("SupabaseLoginActivity", "✅ 登录时已设置SupabaseApiClient sessionToken")
                } catch (e: Exception) {
                    Log.e("SupabaseLoginActivity", "❌ 设置SupabaseApiClient sessionToken失败", e)
                }
            } catch (e: Exception) {
                Log.e("SupabaseLoginActivity", "❌ 保存会话令牌到缓存管理器失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 安全地跳转到主界面
     */
    private fun safeNavigateToMainActivity() {
        try {
            log.i("🚀 正在启动主界面...")
            val intent = Intent(this@SupabaseLoginActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            log.e("❌ 跳转失败: ${e.message}")
            
            // 备用跳转方式
            try {
                log.i("🔄 尝试备用方式启动主界面...")
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(intent)
                finish()
            } catch (e2: Exception) {
                log.e("❌ 备用跳转也失败: ${e2.message}")
                // 即使跳转失败，也清除进度日志，避免界面卡在登录状态
                LoginProgressState.clearLogMessages()
            }
        } finally {
            // 在任何情况下，跳转后都应清除进度日志
            Handler().postDelayed({
                LoginProgressState.clearLogMessages()
            }, 500) // 短暂延迟确保动画效果完成
        }
    }
    
    @Composable
    private fun SplashScreen() {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "壹来电视",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    
    /**
     * 计算VIP剩余天数
     * @param vipEndDate VIP结束日期字符串
     * @return 剩余天数，如果无效则返回0
     */
    private fun calculateRemainingDays(vipEndDate: String?): Int {
        if (vipEndDate.isNullOrEmpty()) return 0
        
        try {
            // 解析日期字符串
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val endDate = dateFormat.parse(vipEndDate)
            
            if (endDate != null) {
                // 计算当前日期到结束日期的毫秒差
                val currentTime = System.currentTimeMillis()
                val diffMillis = endDate.time - currentTime
                
                // 转换为天数
                return (diffMillis / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
            }
        } catch (e: Exception) {
            Log.e("SupabaseLoginActivity", "计算VIP剩余天数失败", e)
        }
        
        return 0
    }
    
    override fun onDestroy() {
        super.onDestroy()

        // 重置登录状态管理器，避免内存泄漏
        if (::loginStatusManager.isInitialized) {
            loginStatusManager.reset()
        }

        log.i("🔄 SupabaseLoginActivity 已销毁，后台任务将继续执行")
    }


    
    // 在MainViewModel.clearAllCache回调或其它登出流程后，主动删除user_sessions会话
    private fun logoutAndClearSessions(userId: String) {
        lifecycleScope.launch {
            try {
                val apiClient = top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient.getInstance()
                val accessToken = repository.getAccessToken()
                var deleted = false
                if (accessToken != null) {
                    // 优先用 HTTP DELETE 真正删除
                    deleted = apiClient.deleteUserSessionHttp(userId = userId, accessToken = accessToken)
                    if (deleted) {
                        log.i("✅ 已用 HTTP DELETE 成功删除 user_sessions 会话")
                    } else {
                        log.e("❌ HTTP DELETE 删除 user_sessions 会话失败，尝试 supabase-kt 兼容方式")
                    }
                }
                if (!deleted) {
                    // 兜底：兼容 supabase-kt 方式
                    val result = apiClient.deleteUserSession(userId = userId)
                    log.i("✅ 已用 supabase-kt 兼容方式删除 user_sessions 会话: $result")
                }
            } catch (e: Exception) {
                log.e("❌ 删除 user_sessions 会话失败: ${e.message}")
            }
        }
    }
    
    companion object {
        // 标识是否是手动登录的Intent Extra键
        const val EXTRA_MANUAL_LOGIN = "extra_manual_login"
    }
}

// 登录进度状态管理类
object LoginProgressState {
    private val _logMessages = mutableStateListOf<String>()
    val logMessages: List<String> get() = _logMessages
    
    // 添加日志消息
    fun addLogMessage(message: String) {
        _logMessages.add(message)
        // 限制日志消息数量，避免过多
        if (_logMessages.size > 20) {
            _logMessages.removeAt(0)
        }
    }
    
    // 清除日志消息
    fun clearLogMessages() {
        _logMessages.clear()
    }
} 