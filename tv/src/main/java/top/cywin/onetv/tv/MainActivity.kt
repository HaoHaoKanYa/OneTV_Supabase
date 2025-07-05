package top.cywin.onetv.tv

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.BuildConfig
import top.cywin.onetv.core.data.repositories.supabase.SupabaseEnvChecker
import top.cywin.onetv.tv.supabase.SupabaseVideoPlayerWatchHistoryTracker
import top.cywin.onetv.tv.supabase.SupabaseWatchHistorySessionManager
import top.cywin.onetv.tv.supabase.sync.SupabaseAppExitSyncManager
import top.cywin.onetv.tv.supabase.sync.SupabaseWatchHistorySyncService
import top.cywin.onetv.tv.ui.App
import top.cywin.onetv.tv.ui.screens.main.MainUiState
import top.cywin.onetv.tv.ui.screens.main.MainViewModel
import top.cywin.onetv.tv.ui.screens.settings.SettingsViewModel
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.utlis.HttpServer
import kotlin.system.exitProcess
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.tv.supabase.SupabaseCacheManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseRepository
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var heartbeatJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查环境变量
        SupabaseEnvChecker.checkAllEnvVariables()

        // 重置同步状态（新的应用会话开始）
        SupabaseAppExitSyncManager.resetSyncState()
        Log.d(TAG, "应用启动，同步状态已重置")

        // 初始化观看历史会话管理器
        Log.d(TAG, "初始化观看历史会话管理器")
        SupabaseWatchHistorySessionManager.initialize(applicationContext)

        setContent {
            // 初始化 ViewModel
            val settingsViewModel: SettingsViewModel = viewModel()
            val mainViewModel: MainViewModel = viewModel()
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).let { insetsController ->
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            MyTVTheme {
                PermissionHandler {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 主应用界面始终存在
                            App(
                                onBackPressed = {
                                    syncWatchHistoryAndExit()
                                },
                                settingsViewModel = settingsViewModel,
                                mainViewModel = mainViewModel
                            )

                            // 只在加载状态显示启动视频
                            if (uiState is MainUiState.Loading) {
                                SplashVideo(
                                    videoResId = R.raw.video_logo,
                                    isLoading = uiState is MainUiState.Loading
                                )
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    // 初始化逻辑（可选）
                }
                
                // 预热缓存逻辑移至这里
                try {
                    Log.d(TAG, "开始预热应用缓存...")
                    SupabaseCacheManager.preheatCache(applicationContext)
                    Log.d(TAG, "应用缓存预热完成")
                    
                    // 如果用户已登录，预热用户缓存并启动心跳
                    val session = SupabaseCacheManager.getCache<String>(applicationContext, SupabaseCacheKey.SESSION)
                    if (!session.isNullOrEmpty()) {
                        val userData = SupabaseCacheManager.getCache<SupabaseUserDataIptv>(applicationContext, SupabaseCacheKey.USER_DATA)
                        if (userData != null) {
                            Log.d(TAG, "检测到已登录用户，开始预热用户缓存...")
                            SupabaseCacheManager.preheatUserCache(applicationContext, userData.userid)
                            Log.d(TAG, "用户缓存预热完成")

                            // ✅ 设置SupabaseApiClient的sessionToken
                            try {
                                val apiClient = top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient.getInstance()
                                apiClient.setSessionToken(session)
                                Log.d(TAG, "✅ 应用启动时已设置SupabaseApiClient sessionToken")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 设置SupabaseApiClient sessionToken失败", e)
                            }

                            // 启动用户会话心跳机制
                            startUserSessionHeartbeat(userData)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "缓存预热失败", e)
                }
            }
            
            Log.d(TAG, "开始HTTP服务器")
            HttpServer.start(applicationContext)
        }
    }
    
    /**
     * 同步观看历史并退出应用
     */
    private fun syncWatchHistoryAndExit() {
        Log.d(TAG, "收到退出请求, 准备同步观看历史并退出应用")
        
        // 先调用观看历史跟踪器的onAppExit方法，保存当前正在观看的频道
        val tracker = SupabaseVideoPlayerWatchHistoryTracker.getInstance()
        if (tracker != null) {
            Log.d(TAG, "退出前调用观看历史跟踪器的onAppExit方法，保存当前观看记录")
            tracker.onAppExit()
            Log.d(TAG, "应用准备退出，保存观看记录成功")
        } else {
            Log.d(TAG, "未找到观看历史跟踪器实例，无法保存当前观看记录")
        }
        
        // 使用GlobalScope确保不受Activity生命周期影响
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "正在同步观看历史到服务器...")
                val startTime = System.currentTimeMillis()

                // 使用applicationContext确保Context不会因Activity销毁而失效
                val appContext = applicationContext
                val count = SupabaseAppExitSyncManager.performExitSync(appContext)
                val endTime = System.currentTimeMillis()
                Log.d(TAG, "成功同步 $count 条观看记录, 耗时 ${endTime - startTime}ms")

                // 延迟一下，确保异步操作有机会完成
                kotlinx.coroutines.delay(1000) // 增加延迟时间

                Log.d(TAG, "应用准备退出")

                // 在主线程执行UI操作
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    finish()
                    Log.d(TAG, "应用已调用finish, 即将完全退出")
                }

                // 再次延迟确保finish()完成
                kotlinx.coroutines.delay(500)
                exitProcess(0)
            } catch (e: Exception) {
                Log.e(TAG, "同步观看历史失败", e)
                // 即使同步失败也要退出
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    finish()
                }
                kotlinx.coroutines.delay(500)
                exitProcess(0)
            }
        }
    }
    
    override fun onStop() {
        Log.d(TAG, "MainActivity onStop 被调用 - 应用进入后台")
        super.onStop()

        // 应用进入后台时同步观看历史
        performBackgroundSync()
    }

    override fun onPause() {
        Log.d(TAG, "MainActivity onPause 被调用")
        super.onPause()

        // 应用暂停时也同步观看历史
        performBackgroundSync()
    }

    /**
     * 执行后台同步
     */
    private fun performBackgroundSync() {
        // 先保存当前观看记录
        val tracker = SupabaseVideoPlayerWatchHistoryTracker.getInstance()
        if (tracker != null) {
            Log.d(TAG, "应用进入后台，保存当前观看记录")
            tracker.onAppExit()
        }

        // 使用GlobalScope异步同步到服务器，避免Activity生命周期影响
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "应用进入后台，开始同步观看历史")
                val appContext = applicationContext
                val count = SupabaseAppExitSyncManager.performExitSync(appContext)
                Log.d(TAG, "后台同步完成，同步了 $count 条观看记录")
            } catch (e: Exception) {
                Log.e(TAG, "后台同步观看历史失败", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy 被调用")
        super.onDestroy()

        // 停止用户会话心跳
        heartbeatJob?.cancel()
        Log.d(TAG, "用户会话心跳已停止")

        // 先调用观看历史跟踪器的onAppExit方法，保存当前正在观看的频道
        val tracker = SupabaseVideoPlayerWatchHistoryTracker.getInstance()
        if (tracker != null) {
            Log.d(TAG, "调用观看历史跟踪器的onAppExit方法，保存当前观看记录")
            tracker.onAppExit()
            Log.d(TAG, "应用已退出，保存观看记录成功")
        } else {
            Log.d(TAG, "未找到观看历史跟踪器实例，无法保存当前观看记录")
        }

        // 使用GlobalScope确保在应用销毁时同步观看历史不受Activity生命周期影响
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "onDestroy 中同步观看历史")
                val appContext = applicationContext
                val count = SupabaseAppExitSyncManager.performExitSync(appContext)
                Log.d(TAG, "onDestroy 中成功同步 $count 条观看记录")
            } catch (e: Exception) {
                Log.e(TAG, "应用销毁时同步观看历史失败", e)
            }
        }
    }

    /**
     * 启动用户会话心跳机制
     * 每5分钟更新一次用户会话，确保在线状态正确统计
     */
    private fun startUserSessionHeartbeat(userData: SupabaseUserDataIptv) {
        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch {
            while (true) {
                try {
                    val apiClient = SupabaseApiClient.getInstance()
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
                    Log.d(TAG, "[心跳] 已刷新用户会话，保持在线状态")
                } catch (e: Exception) {
                    Log.e(TAG, "[心跳] 刷新用户会话失败: ${e.message}")
                }
                kotlinx.coroutines.delay(5 * 60 * 1000) // 5分钟
            }
        }
        Log.d(TAG, "用户会话心跳已启动，每5分钟更新一次")
    }

    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "Unknown Device"
        }
    }
}

@Composable
fun SplashVideo(videoResId: Int, isLoading: Boolean) {
    val context = LocalContext.current

    // 创建并记住ExoPlayer实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // 设置不循环播放
            repeatMode = Player.REPEAT_MODE_OFF
            // 准备视频资源
            val uri = Uri.parse("android.resource://${context.packageName}/$videoResId")
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    // 确保在组件离开Composition时释放资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // 当加载状态变化时处理播放器
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // 显示播放器视图
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // 隐藏控制器
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun PermissionHandler(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap: Map<String, Boolean> ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            hasPermissions = true
        } else {
            (context as? Activity)?.finish()
            exitProcess(0)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(permissions)
        }
    }

    if (hasPermissions) {
        content()
    }
}