package top.cywin.onetv.tv.ui.screens.main

import android.app.Application
import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.core.data.entities.channel.ChannelGroup
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.channel.ChannelList
import top.cywin.onetv.core.data.entities.iptvsource.IptvSource
import top.cywin.onetv.core.data.repositories.iptv.BaseIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.GuestIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.IptvRepository
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserRepository
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import top.cywin.onetv.core.data.utils.ChannelUtil
import top.cywin.onetv.core.data.utils.Constants
import top.cywin.onetv.core.data.entities.epg.EpgList
import top.cywin.onetv.core.data.repositories.epg.EpgRepository
import top.cywin.onetv.tv.supabase.sync.SupabaseAppExitSyncManager
import top.cywin.onetv.tv.supabase.sync.SupabaseWatchHistorySyncService
import top.cywin.onetv.tv.ui.material.Snackbar
import top.cywin.onetv.tv.ui.material.SnackbarType
import top.cywin.onetv.tv.ui.utils.Configs
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun formatBeijingTime(time: Long): String {
    if (time <= 0) return "未记录"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(time))
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading())
    private val source = Configs.iptvSourceCurrent
    private val appContext = getApplication<Application>().applicationContext
    private var iptvRepo: BaseIptvRepository = GuestIptvRepository(source)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // 当前选中的设置分类
    private val _currentSettingsCategory = MutableStateFlow<SettingsCategories?>(null)
    val currentSettingsCategory: StateFlow<SettingsCategories?> = _currentSettingsCategory.asStateFlow()

    // 设置当前选中的设置分类
    fun setCurrentSettingsCategory(category: SettingsCategories) {
        _currentSettingsCategory.value = category
        Log.d("MainViewModel", "设置当前设置分类: ${category.name}")
    }

    init {
        init()
    }

    fun init() {
        viewModelScope.launch {
            try {
                _uiState.value = MainUiState.Loading("正在初始化...")
                
                // 使用超时保护
                withTimeoutOrNull(5000) { // 5秒超时
                    try {
                        // 初始化仓库，使用try-catch包装每个可能失败的步骤
                        iptvRepo = try {
                            val session = SupabaseSessionManager.getSession(appContext)
                            if (session.isNullOrEmpty()) {
                                Log.d("MainViewModel", "使用游客仓库初始化")
                                GuestIptvRepository(source)
                            } else {
                                try {
                                    Log.d("MainViewModel", "使用用户仓库初始化")
                                    IptvRepository(source, session)
                                } catch (e: Exception) {
                                    Log.e("MainViewModel", "创建用户仓库失败，回退到游客模式", e)
                                    GuestIptvRepository(source)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "获取会话失败，使用游客模式", e)
                            GuestIptvRepository(source)
                        }
                        
                        // 在后台线程中预加载频道和EPG数据，避免主线程堵塞
                        launch(Dispatchers.IO) {
                            try {
                                Log.d("MainViewModel", "在后台线程中预加载频道数据")
                                refreshChannel()
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "后台加载频道失败", e)
                            }
                            
                            try {
                                Log.d("MainViewModel", "在后台线程中预加载EPG数据")
                                refreshEpg()
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "后台加载EPG失败", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "初始化过程中发生错误", e)
                        // 不要抛出异常，让应用继续运行
                    }
                } ?: run {
                    // 超时处理
                    Log.e("MainViewModel", "初始化超时，确保应用不会卡死")
                    
                    // 确保使用游客模式可以继续使用
                    try {
                        iptvRepo = GuestIptvRepository(source)
                        // 在后台线程中加载频道
                        launch(Dispatchers.IO) {
                            try {
                                refreshChannel()
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "备用初始化失败", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "备用初始化失败", e)
                        _uiState.value = MainUiState.Error("初始化超时，请重试")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "初始化主流程异常", e)
                _uiState.value = MainUiState.Error("初始化失败: ${e.message}")
                
                // 确保使用游客模式可以继续使用
                try {
                    iptvRepo = GuestIptvRepository(source)
                    // 在后台线程中加载频道
                    launch(Dispatchers.IO) {
                        try {
                            refreshChannel()
                        } catch (e2: Exception) {
                            Log.e("MainViewModel", "备用初始化也失败", e2)
                        }
                    }
                } catch (e2: Exception) {
                    Log.e("MainViewModel", "备用初始化也失败", e2)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            Log.d("MainViewModel", "开始退出登录流程")
            
            // 在登出前先同步观看历史到服务器
            withContext(Dispatchers.IO) {
                try {
                    Log.d("MainViewModel", "退出登录前开始同步观看历史到服务器")
                    val syncCount = SupabaseAppExitSyncManager.performExitSync(appContext)
                    Log.d("MainViewModel", "退出登录前成功同步 $syncCount 条观看记录到服务器")
                } catch (e: Exception) {
                    Log.e("MainViewModel", "退出登录前同步观看历史失败", e)
                }
            }
            
            // 在IO线程上执行清除会话和缓存的操作
            withContext(Dispatchers.IO) {
                try {
                    SupabaseSessionManager.clearSession(appContext)
                    Log.d("MainViewModel", "会话已清除")
                    
                    SupabaseSessionManager.clearLastLoadedTime(appContext)
                    Log.d("MainViewModel", "时间戳已重置")
                    
                    // 清除用户资料和设置缓存
                    try {
                        // 清除所有缓存
                        SupabaseCacheManager.clearCache(appContext, SupabaseCacheKey.USER_DATA)
                        Log.d("MainViewModel", "用户资料和设置缓存已清除")
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "清除缓存失败", e)
                    }
                    
                    clearAllCache(clearUserCache = true)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "退出登录过程中发生错误", e)
                }
            }
            
            // 重置为游客仓库
            iptvRepo = GuestIptvRepository(source)
            Log.d("MainViewModel", "已重置为游客仓库")
        }
    }

    /**
     * 清理缓存
     * @param clearUserCache 是否清除用户数据缓存（主动清理时为 true）
     * @param onComplete 清理完成回调
     */
    fun clearAllCache(clearUserCache: Boolean = true, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 【步骤1】清除 TV 设备专用缓存
                val tvCachePath = File(appContext.externalCacheDir?.parent ?: "", "tv_sessions")
                if (tvCachePath.exists()) {
                    tvCachePath.deleteRecursively().also {
                        Log.d("MainViewModel", "🗑️ TV专用会话缓存清除结果：$it")
                    }
                }
                if (clearUserCache) {
                    // 【步骤2】主动清除所有缓存（退出登录时调用）
                    Log.d("MainViewModel", "🔥 开始强制清除所有缓存｜时间：${formatBeijingTime(System.currentTimeMillis())}")
                    
                    // 使用新的缓存管理器清除用户数据缓存
                    SupabaseCacheManager.clearCache(appContext, SupabaseCacheKey.USER_DATA)
                    Log.d("MainViewModel", "🗑️ 用户缓存已清除")
                    
                    EpgList.clearCache().also {
                        Log.d("MainViewModel", "🗑️ EPG缓存已清除")
                    }
                    iptvRepo.clearCache().also {
                        Log.d("MainViewModel", "🗑️ 频道缓存已清除")
                    }
                    Log.d("MainViewModel", "✅ 所有缓存清除完成")
                } else {
                    // 【步骤3】自动清理检查（仅针对VIP用户）
                    Log.d("MainViewModel", "🔍 开始自动缓存清理检查")
                    val userData = SupabaseSessionManager.getCachedUserData(appContext).also {
                        Log.d("MainViewModel", " 缓存检查结果｜用户ID=${it?.userid ?: "空"}｜VIP=${it?.is_vip ?: "未登录"}")
                    }
                    if (userData?.is_vip == true) {
                        // 检查缓存是否有效
                        val isValid = SupabaseCacheManager.isValid(appContext, SupabaseCacheKey.USER_DATA)
                        Log.d("MainViewModel", "VIP自动清理检查｜缓存是否有效：$isValid")
                        
                        if (!isValid) {
                            Log.d("MainViewModel", "🚮 触发自动清理｜VIP缓存已过期")
                            SupabaseCacheManager.clearCache(appContext, SupabaseCacheKey.USER_DATA)
                            EpgList.clearCache()
                            iptvRepo.clearCache()
                        } else {
                            Log.d("MainViewModel", "✅ VIP状态有效｜跳过自动清理")
                        }
                    } else {
                        Log.d("MainViewModel", "🌟 非VIP用户，无需自动清理缓存")
                    }
                    // 【步骤7】清除WebView缓存
                    Log.d("MainViewModel", "🧹 开始清除WebView缓存")
                    clearWebViewCacheAsync()
                }
            }
            Log.d("MainViewModel", "🏁 所有缓存操作完成")
            onComplete()
            init()
        }
    }


    private fun clearWebViewCacheAsync() {
        val context = getApplication<Application>().applicationContext
        val webViewDir = context.cacheDir?.parent?.let { File("$it/app_webview") }
        if (webViewDir?.deleteRecursively() == true) {
            Log.d("MainViewModel", "WebView缓存已清除")
        } else {
            Log.e("MainViewModel", "WebView缓存清除失败")
        }
    }


    /**
     * 强制刷新用户数据
     * - 从服务器获取最新数据并更新缓存
     * - 重建IPTV仓库确保使用最新会话
     * - 刷新频道和节目单
     */
    // 强制刷新用户数据（同步方式）
    fun forceRefreshUserDataSync(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "⏳ 开始同步刷新用户数据｜线程：${Thread.currentThread().name}")

                // 步骤1：获取会话
                val session = SupabaseSessionManager.getSession(context)
                if (session.isNullOrEmpty()) {
                    Log.w("MainViewModel", "⚠️ 会话无效｜终止刷新")
                    onComplete(false)
                    return@launch
                }

                // 步骤2：同步获取用户数据
                val userData = withContext(Dispatchers.IO) {
                    SupabaseUserRepository().getUserData(session).also {
                        Log.d("MainViewModel", """
                        |✅ 用户数据获取成功｜
                        |userId=${it.userid}｜
                        |isVIP=${it.is_vip}｜
                        |vipEnd=${it.vipend ?: "未开通"}
                    """.trimMargin())
                    }
                }

                // 步骤3：立即保存到缓存
                SupabaseSessionManager.saveCachedUserData(context, userData)

                // 步骤4：重建仓库确保数据一致性
                iptvRepo = IptvRepository(source, session)
                Log.d("MainViewModel", "🔄 IPTV仓库已重建")
                
                // 步骤5：在后台线程中刷新频道和节目单
                launch(Dispatchers.IO) {
                    try {
                        refreshChannel()
                        Log.d("MainViewModel", "🔄 后台刷新频道数据完成")
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "后台刷新频道失败", e)
                    }
                    
                    try {
                        refreshEpg()
                        Log.d("MainViewModel", "🔄 后台刷新节目单数据完成")
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "后台刷新节目单失败", e)
                    }
                }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ 同步刷新失败｜${e.message}", e)
                onComplete(false)
            }
        }
    }

    // 强制刷新用户数据（异步方式）
    fun forceRefreshUserData() {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "⏳ 开始强制刷新用户数据流程...")
                val session = SupabaseSessionManager.getSession(appContext)
                Log.d("MainViewModel", "🔑 当前会话状态: ${if (session.isNullOrEmpty()) "空/未登录" else "有效会话"}")

                // 步骤1：从服务器获取用户数据
                if (!session.isNullOrEmpty()) {
                    Log.d("MainViewModel", "🌐 正在从服务器获取用户数据...")
                    val newUserData = withContext(Dispatchers.IO) {
                        SupabaseUserRepository().getUserData(session).also {
                            Log.d("MainViewModel", "✅ 用户数据获取成功｜用户ID: ${it.userid}｜VIP状态: ${it.is_vip}")
                        }
                    }

                    // 步骤2：保存用户数据到缓存
                    SupabaseSessionManager.saveCachedUserData(appContext, newUserData)
                    Log.d("MainViewModel", "💾 用户数据已缓存｜VIP=${newUserData.is_vip}｜到期时间=${newUserData.vipend}")
                }

                // 步骤3：重建IPTV仓库
                iptvRepo = try {
                    if (session.isNullOrEmpty()) {
                        Log.w("MainViewModel", "⚠️ 会话已失效，回退到游客模式")
                        GuestIptvRepository(source)
                    } else {
                        IptvRepository(source, session)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "重建仓库失败", e)
                    GuestIptvRepository(source)
                }

                // 步骤4：在后台线程中刷新频道和节目单
                launch(Dispatchers.IO) {
                    try {
                        Log.d("MainViewModel", "🔄 在后台线程中刷新频道数据...")
                        refreshChannel()
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "后台刷新频道失败", e)
                    }
                    
                    try {
                        Log.d("MainViewModel", "🔄 在后台线程中刷新节目单数据...")
                        refreshEpg()
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "后台刷新节目单失败", e)
                    }
                }

                Log.d("MainViewModel", "🎉 强制刷新流程完成")
                _toastMessage.emit("用户数据已刷新")
            } catch (e: Exception) {
                // 处理401错误
                if (e.message?.contains("401") == true) {
                    Log.w("MainViewModel", "检测到会话过期，触发清理")
                    logout()
                    _uiState.value = MainUiState.Error("会话已过期，请重新登录")
                }
                Log.e("MainViewModel", "❌ 强制刷新失败", e)
                val errorMsg = when {
                    e is java.net.UnknownHostException -> "网络不可用"
                    else -> "错误: ${e.message?.take(20)}..."
                }
                _toastMessage.emit(errorMsg)
            }
        }
    }

    private suspend fun refreshChannel() {
        flow {
            val channelGroupList = iptvRepo.getChannelGroupList(Configs.iptvSourceCacheTime)
            emit(channelGroupList)
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false
                _uiState.value = MainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch { e ->
                if (e.message?.contains("用户未登录") == true) {
                    _uiState.value = MainUiState.Error("会话已过期，请重新登录")
                    SupabaseSessionManager.clearSession(appContext)
                } else {
                    _uiState.value = MainUiState.Error(e.message ?: "未知错误")
                }
            }
            .map { channelGroupList ->
                hybridChannel(channelGroupList)
            }
            .map { hybridResult ->
                _uiState.value = MainUiState.Ready(channelGroupList = hybridResult)
                hybridResult
            }
            .collect()
    }

    private suspend fun hybridChannel(channelGroupList: ChannelGroupList): ChannelGroupList =
        withContext(Dispatchers.Default) {
            when (Configs.iptvHybridMode) {
                Configs.IptvHybridMode.DISABLE -> channelGroupList
                Configs.IptvHybridMode.IPTV_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                urlList = channel.urlList.plus(
                                    ChannelUtil.getHybridWebViewUrl(channel.name) ?: emptyList()
                                )
                            )
                        }))
                    })
                }
                Configs.IptvHybridMode.HYBRID_FIRST -> {
                    ChannelGroupList(channelGroupList.map { group ->
                        group.copy(channelList = ChannelList(group.channelList.map { channel ->
                            channel.copy(
                                urlList = (ChannelUtil.getHybridWebViewUrl(channel.name)
                                    ?: emptyList()).plus(channel.urlList)
                            )
                        }))
                    })
                }
            }
        }

    private suspend fun refreshEpg() {
        if (!Configs.epgEnable) return
        if (_uiState.value is MainUiState.Ready) {
            // 使用EpgList的伴生对象中的clearCache方法
            EpgList.Companion.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList
            
            // 创建一个包含所有频道epgName的列表
            val epgNames = mutableListOf<String>()
            for (group in channelGroupList) {
                for (channel in group.channelList) {
                    if (channel.epgName.isNotEmpty()) {
                        epgNames.add(channel.epgName)
                    }
                }
            }
            
            flow {
                val epgList = EpgRepository(Configs.epgSourceCurrent).getEpgList(
                    filteredChannels = epgNames,
                    refreshTimeThreshold = Configs.epgRefreshTimeThreshold,
                )
                emit(epgList)
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch { _ ->
                    emit(EpgList())
                    Snackbar.show("节目单获取失败，请检查网络连接", type = SnackbarType.ERROR)
                }
                .map { epgList ->
                    _uiState.value = (_uiState.value as MainUiState.Ready).copy(epgList = epgList)
                }
                .collect()
        }
    }

    // 定义消息通知流
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> get() = _toastMessage

    // 发送 Toast 消息
    fun showToast(message: String) {
        viewModelScope.launch {
            _toastMessage.emit(message)
        }
    }

}

sealed interface MainUiState {
    data class Loading(val message: String? = null) : MainUiState
    data class Error(val message: String? = null) : MainUiState
    data class Ready(
        val channelGroupList: ChannelGroupList = ChannelGroupList(),
        val epgList: EpgList = EpgList()
    ) : MainUiState
}
