package top.cywin.onetv.tv.supabase

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseRepository
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import top.cywin.onetv.core.data.utils.Logger
import top.cywin.onetv.tv.MainActivity
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import java.io.IOException
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
        
        fun d(message: String) {
            logger.d(message)
            LoginProgressState.addLogMessage(message)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置线程异常处理，防止未捕获的异常导致应用崩溃
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log.e("未捕获的异常: ${throwable.message}", throwable)
            // 记录错误但不退出应用
        }
        
        // 初始化Supabase客户端
        SupabaseClient.initialize(this)
        
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
     * 登录界面内容，支持屏幕旋转
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
                SupabaseAuthUI.LoginScreen(
                    onLoginSuccess = onLoginSuccess
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
     * 处理登录成功事件
     * 保存会话并跳转到主界面
     */
    private fun handleLoginSuccess() {
        lifecycleScope.launch {
            try {
                log.i("开始处理登录流程...")
                
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
                    
                    // 保存会话到SharedPreferences
                    saveSessionToPreferences(accessToken)
                    log.i("📝 会话令牌已保存")
                    
                    // 创建MainViewModel实例
                    val mainViewModel = ViewModelProvider(this@SupabaseLoginActivity)
                        .get(top.cywin.onetv.tv.ui.screens.main.MainViewModel::class.java)
                    
                    // 强制执行缓存清除，无论用户是否为VIP
                    log.i("🧹 开始清除缓存...")
                    mainViewModel.clearAllCache(true) {
                        log.i("✅ 缓存清除完成")
                        
                        // 在清除旧缓存后，通过后台线程安全获取新数据
                        lifecycleScope.launch {
                            log.i("🔄 正在刷新用户数据...")
                            // 使用挂起函数强制刷新用户数据
                            val success = withContext(Dispatchers.IO) {
                                forceRefreshUserDataSuspend(mainViewModel, this@SupabaseLoginActivity)
                            }
                            
                            if (success) {
                                log.i("✅ 用户数据刷新成功")
                                // 获取用户个人资料并保存到缓存
                                try {
                                    log.i("📊 正在获取用户详细资料...")
                                    val userData = repository.getUserData(accessToken)
                                    log.i("✅ 用户资料获取成功")
                                    
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            // 确保保存正确类型的对象
                                            SupabaseCacheManager.saveCache(
                                                this@SupabaseLoginActivity,
                                                SupabaseCacheKey.USER_DATA,
                                                userData,
                                                // 使用新的getUserCacheStrategy方法
                                                SupabaseCacheManager.getUserCacheStrategy(userData)
                                            )
                                            
                                            // 保存最后加载时间
                                            SupabaseCacheManager.saveCache(
                                                this@SupabaseLoginActivity,
                                                SupabaseCacheKey.LAST_LOADED_TIME,
                                                System.currentTimeMillis()
                                            )
                                            
                                            log.i("💾 用户资料已保存到主缓存")
                                            
                                            // 同时保存为原始JSON，便于调试
                                            try {
                                                val gson = Gson()
                                                val userDataJson = gson.toJson(userData)
                                                SupabaseCacheManager.saveCache(
                                                    this@SupabaseLoginActivity,
                                                    SupabaseCacheKey.USER_DATA_JSON,
                                                    userDataJson
                                                )
                                                log.i("💾 用户资料已保存为原始JSON格式")
                                            } catch (e: Exception) {
                                                log.e("❌ 保存用户资料JSON失败: ${e.message}")
                                            }
                                            
                                            // 同步保存到USER_PROFILE缓存
                                            try {
                                                SupabaseUserProfileInfoSessionManager.saveUserProfileData(this@SupabaseLoginActivity, userData)
                                                log.i("💾 用户资料已同步到个人资料缓存")
                                            } catch (e: Exception) {
                                                log.e("❌ 保存到个人资料缓存失败: ${e.message}")
                                            }
                                            
                                            // 同步保存到USER_SETTINGS缓存（创建默认设置）
                                            try {
                                                val defaultSettings = SupabaseUserSettingsSessionManager.UserSettings(
                                                    userId = userData.userid,
                                                    displayName = userData.username,
                                                    updatedAt = System.currentTimeMillis().toString()
                                                )
                                                SupabaseUserSettingsSessionManager.saveUserSettings(this@SupabaseLoginActivity, defaultSettings)
                                                log.i("💾 默认用户设置已同步到设置缓存")
                                            } catch (e: Exception) {
                                                log.e("❌ 保存到用户设置缓存失败: ${e.message}")
                                            }
                                            
                                            // 同步保存VIP状态到缓存
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
                                                log.i("💾 VIP状态已同步到缓存")
                                            } catch (e: Exception) {
                                                log.e("❌ 保存VIP状态到缓存失败: ${e.message}")
                                            }
                                            
                                            // 初始化观看历史
                                            try {
                                                log.i("🕒 初始化观看历史...")
                                                SupabaseWatchHistorySessionManager.initializeAsync(this@SupabaseLoginActivity)
                                                log.i("✅ 观看历史初始化完成")
                                            } catch (e: Exception) {
                                                log.e("❌ 初始化观看历史失败: ${e.message}")
                                            }
                                        } catch (e: Exception) {
                                            log.e("❌ 保存用户资料到缓存失败: ${e.message}")
                                        }
                                    }
                                    log.i("💾 所有用户资料已保存到本地缓存")
                                    
                                    // 记录登录设备信息
                                    log.i("📱 记录设备登录信息...")
                                    val deviceInfo = getDeviceInfo()
                                    // 在IO线程中执行网络请求
                                    withContext(Dispatchers.IO) {
                                        recordLoginLog(accessToken, deviceInfo)
                                    }
                                    
                                    // 所有数据准备完成后跳转到主界面
                                    log.i("🚀 所有数据准备完成，准备进入主界面...")
                                    safeNavigateToMainActivity()
                                } catch (e: Exception) {
                                    log.e("❌ 获取用户资料失败: ${e.message}")
                                    safeNavigateToMainActivity()
                                }
                            } else {
                                log.e("❌ 用户数据刷新失败")
                                safeNavigateToMainActivity()
                            }
                        }
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