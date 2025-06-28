package top.cywin.onetv.tv.ui.screens.main.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.cywin.onetv.core.data.entities.epg.EpgProgramme
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeReserve
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeReserveList
import top.cywin.onetv.core.data.utils.ChannelUtil
import top.cywin.onetv.core.data.utils.Constants
import top.cywin.onetv.core.data.utils.Loggable
import top.cywin.onetv.tv.ui.material.Snackbar
import top.cywin.onetv.tv.ui.screens.settings.SettingsViewModel
import top.cywin.onetv.tv.ui.screens.videoplayer.VideoPlayerState
import top.cywin.onetv.tv.ui.screens.videoplayer.rememberVideoPlayerState
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerState,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val settingsViewModel: SettingsViewModel,
) : Loggable() {
    private val TAG = "MainContentState"
    
    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelUrlIdx by mutableIntStateOf(0)
    val currentChannelUrlIdx get() = _currentChannelUrlIdx

    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    private var _isSettingsScreenVisible by mutableStateOf(false)
    var isSettingsScreenVisible
        get() = _isSettingsScreenVisible
        set(value) {
            _isSettingsScreenVisible = value
        }

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelUrlScreenVisible by mutableStateOf(false)
    var isChannelUrlScreenVisible
        get() = _isChannelUrlScreenVisible
        set(value) {
            _isChannelUrlScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    // 新增：主界面多线路显示状态
    private var _isMainChannelUrlScreenVisible by mutableStateOf(false)
    var isMainChannelUrlScreenVisible
        get() = _isMainChannelUrlScreenVisible
        set(value) {
            _isMainChannelUrlScreenVisible = value
        }

    init {
        Log.d(TAG, "初始化MainContentState, 视频播放器状态: ${if(videoPlayerState != null) "已初始化" else "未初始化"}")
        
        val channelGroupList = channelGroupListProvider()

        changeCurrentChannel(channelGroupList.channelList.getOrElse(settingsViewModel.iptvLastChannelIdx) {
            channelGroupList.channelList.firstOrNull() ?: Channel()
        })

        videoPlayerState.onReady {
            Log.d(TAG, "视频播放器准备就绪: ${_currentChannel.name}")
            settingsViewModel.iptvPlayableHostList += getUrlHost(_currentChannel.urlList[_currentChannelUrlIdx])
            coroutineScope.launch {
                val name = _currentChannel.name
                val urlIdx = _currentChannelUrlIdx
                delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                if (name == _currentChannel.name && urlIdx == _currentChannelUrlIdx) {
                    _isTempChannelScreenVisible = false
                }
            }
        }

        videoPlayerState.onError {
            Log.d(TAG, "视频播放器错误: ${_currentChannel.name}")
            if (_currentPlaybackEpgProgramme != null) return@onError

            settingsViewModel.iptvPlayableHostList -= getUrlHost(_currentChannel.urlList[_currentChannelUrlIdx])

            if (_currentChannelUrlIdx < _currentChannel.urlList.size - 1) {
                changeCurrentChannel(_currentChannel, _currentChannelUrlIdx + 1)
            }
        }

        videoPlayerState.onInterrupt {
            Log.d(TAG, "视频播放器中断: ${_currentChannel.name}")
            changeCurrentChannel(
                _currentChannel,
                _currentChannelUrlIdx,
                _currentPlaybackEpgProgramme
            )
        }
        
        Log.d(TAG, "MainContentState初始化完成")
    }

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()

        val favoriteChannelNameList = settingsViewModel.iptvChannelFavoriteList
        val favoriteChannelList =
            channelGroupList.channelList.filter { it.name in favoriteChannelNameList }

        return if (_currentChannel in favoriteChannelList && _currentChannel != favoriteChannelList.first()) {
            val currentIdx = favoriteChannelList.indexOf(_currentChannel)
            favoriteChannelList[currentIdx - 1]
        } else if (settingsViewModel.iptvChannelFavoriteChangeBoundaryJumpOut) {
            settingsViewModel.iptvChannelFavoriteListVisible = false
            channelGroupList.channelList.lastOrNull()
        } else {
            favoriteChannelList.lastOrNull()
        }

    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()

        val favoriteChannelNameList = settingsViewModel.iptvChannelFavoriteList
        val favoriteChannelList =
            channelGroupList.channelList.filter { it.name in favoriteChannelNameList }

        return if (_currentChannel in favoriteChannelList && _currentChannel != favoriteChannelList.last()) {
            val currentIdx = favoriteChannelList.indexOf(_currentChannel)
            favoriteChannelList[currentIdx + 1]
        } else if (settingsViewModel.iptvChannelFavoriteChangeBoundaryJumpOut) {
            settingsViewModel.iptvChannelFavoriteListVisible = false
            channelGroupList.channelList.firstOrNull()
        } else {
            favoriteChannelList.firstOrNull()
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            val currentIdx = channelGroupList.channelIdx(_currentChannel)
            return channelGroupList.channelList.getOrElse(currentIdx - 1) {
                channelGroupList.channelList.lastOrNull() ?: Channel()
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            val currentIdx = channelGroupList.channelIdx(_currentChannel)
            return channelGroupList.channelList.getOrElse(currentIdx + 1) {
                channelGroupList.channelList.firstOrNull() ?: Channel()
            }
        }
    }

    private fun getUrlIdx(urlList: List<String>, urlIdx: Int? = null): Int {
        val idx = if (urlIdx == null) urlList.indexOfFirst {
            settingsViewModel.iptvPlayableHostList.contains(getUrlHost(it))
        }
        else (urlIdx + urlList.size) % urlList.size

        return max(0, min(idx, urlList.size - 1))
    }

    fun changeCurrentChannel(
        channel: Channel,
        urlIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
    ) {
        Log.d(TAG, "切换频道请求: ${channel.name}, urlIdx=$urlIdx, 当前频道=${_currentChannel.name}")
        
        // 如果是相同频道且URL索引相同，则不需要切换
        if (channel == _currentChannel && urlIdx == _currentChannelUrlIdx && playbackEpgProgramme == _currentPlaybackEpgProgramme) {
            Log.d(TAG, "相同频道和URL索引，不需要切换: ${channel.name}")
            return
        }

        // 如果是相同频道但URL索引不同，则更新URL索引并从播放列表中移除当前URL
        if (channel == _currentChannel && urlIdx != _currentChannelUrlIdx) {
            Log.d(TAG, "相同频道但URL索引不同: ${channel.name}, 从${_currentChannelUrlIdx}切换到$urlIdx")
            settingsViewModel.iptvPlayableHostList -= getUrlHost(_currentChannel.urlList[_currentChannelUrlIdx])
        }

        _isTempChannelScreenVisible = true

        _currentChannel = channel
        settingsViewModel.iptvLastChannelIdx =
            channelGroupListProvider().channelIdx(_currentChannel)

        _currentChannelUrlIdx = getUrlIdx(_currentChannel.urlList, urlIdx)

        _currentPlaybackEpgProgramme = playbackEpgProgramme

        var url = _currentChannel.urlList[_currentChannelUrlIdx]
        if (_currentPlaybackEpgProgramme != null) {
            val timeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val query = listOf(
                "playseek=",
                timeFormat.format(_currentPlaybackEpgProgramme!!.startAt),
                "-",
                timeFormat.format(_currentPlaybackEpgProgramme!!.endAt),
            ).joinToString("")
            url = if (URI(url).query.isNullOrBlank()) "$url?$query" else "$url&$query"
            url = ChannelUtil.urlToCanPlayback(url)
        }

        log.d("播放${_currentChannel.name}（${_currentChannelUrlIdx + 1}/${_currentChannel.urlList.size}）: $url")
        Log.d(TAG, "播放频道: ${_currentChannel.name}（${_currentChannelUrlIdx + 1}/${_currentChannel.urlList.size}）, URL=${url.take(50)}${if(url.length > 50) "..." else ""}")

        if (ChannelUtil.isHybridWebViewUrl(url)) {
            Log.d(TAG, "检测到WebView URL, 停止播放器")
            videoPlayerState.stop()
        } else {
            Log.d(TAG, "准备播放频道: ${_currentChannel.name}")
            videoPlayerState.prepareChannel(_currentChannel, _currentChannelUrlIdx)
        }
    }

    fun changeCurrentChannelToPrev() {
        val prevChannel = getPrevChannel()
        Log.d(TAG, "切换到上一个频道: ${prevChannel.name}")
        changeCurrentChannel(prevChannel)
    }

    fun changeCurrentChannelToNext() {
        val nextChannel = getNextChannel()
        Log.d(TAG, "切换到下一个频道: ${nextChannel.name}")
        changeCurrentChannel(nextChannel)
    }

    fun favoriteChannelOrNot(channel: Channel) {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return

        if (settingsViewModel.iptvChannelFavoriteList.contains(channel.name)) {
            settingsViewModel.iptvChannelFavoriteList -= channel.name
            Snackbar.show("取消收藏：${channel.name}")
            Log.d(TAG, "取消收藏频道: ${channel.name}")
        } else {
            settingsViewModel.iptvChannelFavoriteList += channel.name
            Snackbar.show("已收藏：${channel.name}")
            Log.d(TAG, "收藏频道: ${channel.name}")
        }
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消预约：${reverse.channel} - ${reverse.programme}")
            Log.d(TAG, "取消预约节目: ${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已预约：${channel.name} - ${programme.title}")
            Log.d(TAG, "预约节目: ${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        urlIdx: Int? = _currentChannelUrlIdx,
    ): Boolean {
        val currentUrlIdx = getUrlIdx(channel.urlList, urlIdx)
        return ChannelUtil.urlSupportPlayback(channel.urlList[currentUrlIdx])
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerState = rememberVideoPlayerState(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = viewModel(),
) = remember {
    MainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = channelGroupListProvider,
        settingsViewModel = settingsViewModel,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}