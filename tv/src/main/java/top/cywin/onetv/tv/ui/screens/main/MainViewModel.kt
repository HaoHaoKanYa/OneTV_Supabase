package top.cywin.onetv.tv.ui.screens.main

import android.app.Application
import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.cywin.onetv.core.data.entities.channel.ChannelList
import top.cywin.onetv.core.data.entities.epg.EpgList
import top.cywin.onetv.core.data.repositories.epg.EpgRepository
import top.cywin.onetv.core.data.repositories.iptv.BaseIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.GuestIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.IptvRepository
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserRepository
import top.cywin.onetv.core.data.utils.ChannelUtil
import top.cywin.onetv.core.data.utils.Constants
import top.cywin.onetv.tv.ui.utils.Configs
import top.cywin.onetv.tv.ui.material.Snackbar
import top.cywin.onetv.tv.ui.material.SnackbarType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories
import top.cywin.onetv.tv.supabase.SupabaseCacheManager

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
                iptvRepo = if (SupabaseSessionManager.getSession(appContext).isNullOrEmpty()) {
                    GuestIptvRepository(source)
                } else {
                    try {
                        IptvRepository(source, SupabaseSessionManager.getValidSession(appContext))
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "创建仓库失败", e)
                        GuestIptvRepository(source)
                    }
                }
                refreshChannel()
                refreshEpg()
            } catch (e: Exception) {
                // 当捕获到 CancellationException 时，记录详细中文解释说明该错误是协程取消的正常现象
                if (e is CancellationException) {
                    Log.e("MainViewModel", "初始化错误: 协程被取消 - 此异常由协程取消机制触发。\n" +
                            "可能原因包括：\n" +
                            "1. 用户界面切换或退出导致父协程取消\n" +
                            "2. 清理缓存后调用 init() 重新初始化时，原有协程任务被取消\n" +
                            "该错误为正常现象，不影响后续的数据加载和 UI 更新。", e)
                } else {
                    Log.e("MainViewModel", "初始化错误: ${e.message}", e)
                }
                _uiState.value = MainUiState.Error(e.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            Log.d("MainViewModel", "开始退出登录流程")
            SupabaseSessionManager.clearSession(appContext)
            Log.d("MainViewModel", "会话已清除")
            SupabaseSessionManager.clearLastLoadedTime(appContext)
            Log.d("MainViewModel", "时间戳已重置")
            
            // 清除用户资料和设置缓存
            SupabaseCacheManager.clearAllCachesOnLogout(appContext)
            Log.d("MainViewModel", "用户资料和设置缓存已清除")
            
            clearAllCache(clearUserCache = true)
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
                    SupabaseSessionManager.clearUserCache(appContext).also {
                        Log.d("MainViewModel", "🗑️ 用户缓存清除结果：$it")
                    }
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
                        Log.d("MainViewModel", "�� 缓存检查结果｜用户ID=${it?.userid ?: "空"}｜VIP=${it?.is_vip ?: "未登录"}")
                    }
                    if (userData?.is_vip == true) {
                        // 【步骤4】VIP用户：基础缓存过期阈值为30天
                        val vipBaseThreshold = 30L * 24 * 3600 * 1000
                        val lastLoaded = SupabaseSessionManager.getLastLoadedTime(appContext)
                        val currentTime = System.currentTimeMillis()
                        val isExpired = (currentTime - lastLoaded) > vipBaseThreshold
                        Log.d("MainViewModel", "VIP自动清理检查｜使用30天阈值 " +
                                "当前时间：${formatBeijingTime(currentTime)}｜" +
                                "最后加载时间：${if (lastLoaded == 0L) "未记录" else formatBeijingTime(lastLoaded)}｜" +
                                "时间差：${currentTime - lastLoaded}ms｜过期：$isExpired")
                        // 【修改点】撤销VIP剩余时间的计算，不再判断VIP剩余时间是否不足48小时
                        // 仅依据基础缓存时间是否超过30天来触发自动清理
                        if (isExpired) {
                            Log.d("MainViewModel", "🚮 触发自动清理｜VIP缓存已超过30天")
                            SupabaseSessionManager.clearUserCache(appContext)
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
                SupabaseSessionManager.saveLastLoadedTime(context, System.currentTimeMillis())

                // 步骤4：重建仓库确保数据一致性
                iptvRepo = IptvRepository(source, session)
                Log.d("MainViewModel", "🔄 IPTV仓库已重建")

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
                    SupabaseSessionManager.saveLastLoadedTime(appContext, System.currentTimeMillis())
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

                // 步骤4：刷新频道和节目单
                Log.d("MainViewModel", "🔄 正在刷新频道数据...")
                refreshChannel()
                Log.d("MainViewModel", "🔄 正在刷新节目单数据...")
                refreshEpg()

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
                top.cywin.onetv.tv.ui.utils.Configs.IptvHybridMode.DISABLE -> channelGroupList
                top.cywin.onetv.tv.ui.utils.Configs.IptvHybridMode.IPTV_FIRST -> {
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
                top.cywin.onetv.tv.ui.utils.Configs.IptvHybridMode.HYBRID_FIRST -> {
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
            EpgList.clearCache()
            val channelGroupList = (_uiState.value as MainUiState.Ready).channelGroupList
            flow {
                val epgList = EpgRepository(Configs.epgSourceCurrent).getEpgList(
                    filteredChannels = channelGroupList.channelList.map { it.epgName },
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
