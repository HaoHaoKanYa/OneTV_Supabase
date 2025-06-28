package top.cywin.onetv.tv.ui.screens.main.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.cywin.onetv.core.data.entities.channel.ChannelList
import top.cywin.onetv.core.data.entities.epg.Epg
import top.cywin.onetv.core.data.entities.epg.EpgList
import top.cywin.onetv.core.data.entities.epg.EpgList.Companion.match
import top.cywin.onetv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeReserveList
import top.cywin.onetv.core.data.repositories.epg.EpgRepository
import top.cywin.onetv.core.data.repositories.iptv.IptvRepository
import top.cywin.onetv.core.data.repositories.user.SessionManager.getIptvRepository
import top.cywin.onetv.core.data.utils.ChannelUtil
import top.cywin.onetv.tv.ui.material.PopupContent
import top.cywin.onetv.tv.ui.material.Snackbar
import top.cywin.onetv.tv.ui.material.Visible
import top.cywin.onetv.tv.ui.material.popupable
import top.cywin.onetv.tv.ui.screens.channel.ChannelNumberSelectScreen
import top.cywin.onetv.tv.ui.screens.channel.ChannelScreen
import top.cywin.onetv.tv.ui.screens.channel.ChannelTempScreen
import top.cywin.onetv.tv.ui.screens.channel.rememberChannelNumberSelectState
import top.cywin.onetv.tv.ui.screens.channelurl.ChannelUrlScreen
import top.cywin.onetv.tv.ui.screens.classicchannel.ClassicChannelScreen
import top.cywin.onetv.tv.ui.screens.datetime.DatetimeScreen
import top.cywin.onetv.tv.ui.screens.epg.EpgProgrammeProgressScreen
import top.cywin.onetv.tv.ui.screens.epg.EpgScreen
import top.cywin.onetv.tv.ui.screens.epgreverse.EpgReverseScreen
import top.cywin.onetv.tv.ui.screens.monitor.MonitorScreen
import top.cywin.onetv.tv.ui.screens.quickop.QuickOpScreen
import top.cywin.onetv.tv.ui.screens.settings.SettingsScreen
import top.cywin.onetv.tv.ui.screens.settings.SettingsViewModel
import top.cywin.onetv.tv.ui.screens.update.UpdateScreen
import top.cywin.onetv.tv.ui.screens.videoplayer.VideoPlayerScreen
import top.cywin.onetv.tv.ui.screens.videoplayer.rememberVideoPlayerState
import top.cywin.onetv.tv.ui.screens.videoplayercontroller.VideoPlayerControllerScreen
import top.cywin.onetv.tv.ui.screens.videoplayerdiaplaymode.VideoPlayerDisplayModeScreen
import top.cywin.onetv.tv.ui.screens.webview.WebViewScreen
import top.cywin.onetv.tv.ui.utils.captureBackKey
import top.cywin.onetv.tv.ui.utils.handleDragGestures
import top.cywin.onetv.tv.ui.utils.handleKeyEvents
// 新增导入
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import top.cywin.onetv.tv.ui.screens.main.MainViewModel
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories


@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    filteredChannelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val videoPlayerState =
        rememberVideoPlayerState(defaultDisplayModeProvider = { settingsViewModel.videoPlayerDisplayMode })
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = filteredChannelGroupListProvider,
    )
    val channelNumberSelectState = rememberChannelNumberSelectState {
        val idx = it.toInt() - 1
        filteredChannelGroupListProvider().channelList.getOrNull(idx)?.let { channel ->
            mainContentState.changeCurrentChannel(channel)
        }
    }

    Box(
        modifier = modifier
            .popupable()
            // 移除重复的返回键捕获，由外层MainScreenSettingsWrapper统一处理
            .handleKeyEvents(
                onUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onLeft = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx - 1,
                        )
                    }
                },
                onRight = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx + 1,
                        )
                    }
                },
                onSelect = {
                    mainContentState.isChannelScreenVisible = true
                    mainContentState.isMainChannelUrlScreenVisible = true
                },
                onLongSelect = { mainContentState.isQuickOpScreenVisible = true },
                onSettings = { mainContentState.isQuickOpScreenVisible = true },
                onLongLeft = { mainContentState.isEpgScreenVisible = true },
                onLongRight = { mainContentState.isChannelUrlScreenVisible = true },
                onLongDown = { mainContentState.isVideoPlayerControllerScreenVisible = true },
                onNumber = { channelNumberSelectState.input(it) },
            )
            .handleDragGestures(
                onSwipeDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onSwipeUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onSwipeRight = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx - 1,
                        )
                    }
                },
                onSwipeLeft = {
                    if (mainContentState.currentChannel.urlList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelUrlIdx + 1,
                        )
                    }
                },
            ),
    ) {
        VideoPlayerScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        )

        Visible({ ChannelUtil.isHybridWebViewUrl(mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx]) }) {
            WebViewScreen(
                urlProvider = { mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx] },
                onVideoResolutionChanged = { width, height ->
                    videoPlayerState.metadata = videoPlayerState.metadata.copy(
                        videoWidth = width,
                        videoHeight = height,
                    )
                    mainContentState.isTempChannelScreenVisible = false
                },
            )
        }
    }

    Visible({ settingsViewModel.uiShowEpgProgrammePermanentProgress }) {
        EpgProgrammeProgressScreen(
            currentEpgProgrammeProvider = {
                mainContentState.currentPlaybackEpgProgramme
                    ?: epgListProvider().recentProgramme(mainContentState.currentChannel)?.now
            },
            videoPlayerCurrentPositionProvider = { videoPlayerState.currentPosition },
        )
    }

    Visible({
        !mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isSettingsScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelUrlScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        DatetimeScreen(showModeProvider = { settingsViewModel.uiTimeShowMode })
    }

    ChannelNumberSelectScreen(channelNumberProvider = { channelNumberSelectState.channelNumber })

    Visible({
        mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && !mainContentState.isSettingsScreenVisible
                && !mainContentState.isQuickOpScreenVisible
                && !mainContentState.isEpgScreenVisible
                && !mainContentState.isChannelUrlScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        ChannelTempScreen(
            channelProvider = { mainContentState.currentChannel },
            channelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            channelNumberProvider = { filteredChannelGroupListProvider().channelIdx(mainContentState.currentChannel) + 1 },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            recentEpgProgrammeProvider = {
                epgListProvider().recentProgramme(mainContentState.currentChannel)
            },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isEpgScreenVisible },
        onDismissRequest = { mainContentState.isEpgScreenVisible = false },
    ) {
        EpgScreen(
            epgProvider = {
                epgListProvider().match(mainContentState.currentChannel)
                    ?: Epg.empty(mainContentState.currentChannel)
            },
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList.filter {
                    it.channel == mainContentState.currentChannel.name
                })
            },
            supportPlaybackProvider = { mainContentState.supportPlayback() },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = {
                mainContentState.isEpgScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannelUrlIdx,
                    it,
                )
            },
            onEpgProgrammeReserve = { programme ->
                mainContentState.reverseEpgProgrammeOrNot(
                    mainContentState.currentChannel,
                    programme
                )
            },
            onClose = { mainContentState.isEpgScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelUrlScreenVisible },
        onDismissRequest = { mainContentState.isChannelUrlScreenVisible = false },
    ) {
        ChannelUrlScreen(
            channelProvider = { mainContentState.currentChannel },
            currentUrlProvider = { mainContentState.currentChannel.urlList[mainContentState.currentChannelUrlIdx] },
            onUrlSelected = {
                mainContentState.isChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannel.urlList.indexOf(it),
                )
            },
            onClose = { mainContentState.isChannelUrlScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerControllerScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerControllerScreenVisible = false },
    ) {
        val threshold = 1000L * 60 * 60 * 24 * 365
        val hour0 = -28800000L

        VideoPlayerControllerScreen(
            isVideoPlayerPlayingProvider = { videoPlayerState.isPlaying },
            isVideoPlayerBufferingProvider = { videoPlayerState.isBuffering },
            videoPlayerCurrentPositionProvider = {
                if (videoPlayerState.currentPosition >= threshold) videoPlayerState.currentPosition
                else hour0 + videoPlayerState.currentPosition
            },
            videoPlayerDurationProvider = {
                if (videoPlayerState.currentPosition >= threshold) {
                    val playback = mainContentState.currentPlaybackEpgProgramme

                    if (playback != null) {
                        playback.startAt to playback.endAt
                    } else {
                        val programme =
                            epgListProvider().recentProgramme(mainContentState.currentChannel)?.now
                        (programme?.startAt ?: hour0) to (programme?.endAt ?: hour0)
                    }
                } else {
                    hour0 to (hour0 + videoPlayerState.duration)
                }
            },
            onVideoPlayerPlay = { videoPlayerState.play() },
            onVideoPlayerPause = { videoPlayerState.pause() },
            onVideoPlayerSeekTo = { videoPlayerState.seekTo(it) },
            onClose = { mainContentState.isVideoPlayerControllerScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isVideoPlayerDisplayModeScreenVisible },
        onDismissRequest = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
    ) {
        VideoPlayerDisplayModeScreen(
            currentDisplayModeProvider = { videoPlayerState.displayMode },
            onDisplayModeChanged = { videoPlayerState.displayMode = it },
            onApplyToGlobal = {
                mainContentState.isVideoPlayerDisplayModeScreenVisible = false
                settingsViewModel.videoPlayerDisplayMode = videoPlayerState.displayMode
                Snackbar.show("已应用到全局")
            },
            onClose = { mainContentState.isVideoPlayerDisplayModeScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isQuickOpScreenVisible },
        onDismissRequest = { mainContentState.isQuickOpScreenVisible = false },
    ) {
        QuickOpScreen(
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            currentChannelNumberProvider = {
                (filteredChannelGroupListProvider().channelList.indexOf(mainContentState.currentChannel) + 1).toString()
            },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            epgListProvider = epgListProvider,
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            onShowEpg = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isEpgScreenVisible = true
            },
            onShowChannelUrl = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isChannelUrlScreenVisible = true
            },
            onShowVideoPlayerController = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerControllerScreenVisible = true
            },
            onShowVideoPlayerDisplayMode = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isVideoPlayerDisplayModeScreenVisible = true
            },
            onShowMoreSettings = {
                mainContentState.isQuickOpScreenVisible = false
                mainContentState.isSettingsScreenVisible = true
            },
            onClearCache = {
                settingsViewModel.iptvPlayableHostList = emptySet()
                coroutineScope.launch {
                    val source = settingsViewModel.iptvSourceCurrent
                    getIptvRepository(context, source).clearCache()
                    EpgRepository(settingsViewModel.epgSourceCurrent).clearCache()
                    Snackbar.show("缓存已清除，请重启应用")
                }
            },
            onClose = { mainContentState.isQuickOpScreenVisible = false },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && !settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = {
            mainContentState.isChannelScreenVisible = false
            mainContentState.isMainChannelUrlScreenVisible = false
        },
    ) {
        ChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = { mainContentState.favoriteChannelOrNot(it) },
            epgListProvider = epgListProvider,
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList.toImmutableList() },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            // 多线路相关参数
            showChannelUrlListProvider = { mainContentState.isMainChannelUrlScreenVisible },
            onChannelUrlSelected = { url ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannel.urlList.indexOf(url),
                )
            },
            onClose = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
            },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible && settingsViewModel.uiUseClassicPanelScreen },
        onDismissRequest = {
            mainContentState.isChannelScreenVisible = false
            mainContentState.isMainChannelUrlScreenVisible = false
        },
    ) {
        ClassicChannelScreen(
            onShowMoreSettings = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.isSettingsScreenVisible = true
            },
            onNavigateToSettingsCategory = { category ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.isSettingsScreenVisible = true
                mainViewModel.setCurrentSettingsCategory(category)
            },
            channelGroupListProvider = filteredChannelGroupListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelUrlIdxProvider = { mainContentState.currentChannelUrlIdx },
            favoriteChannelListProvider = {
                val favoriteChannelNameList = settingsViewModel.iptvChannelFavoriteList
                ChannelList(filteredChannelGroupListProvider().channelList
                    .filter { favoriteChannelNameList.contains(it.name) })
            },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = { mainContentState.favoriteChannelOrNot(it) },
            epgListProvider = epgListProvider,
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList)
            },
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            supportPlaybackProvider = { mainContentState.supportPlayback(it, null) },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = { channel, programme ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(channel, null, programme)
            },
            onEpgProgrammeReserve = { channel, programme ->
                mainContentState.reverseEpgProgrammeOrNot(channel, programme)
            },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            // 多线路相关参数
            showChannelUrlListProvider = { mainContentState.isMainChannelUrlScreenVisible },
            onChannelUrlSelected = { url ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
                mainContentState.changeCurrentChannel(
                    mainContentState.currentChannel,
                    mainContentState.currentChannel.urlList.indexOf(url),
                )
            },
            onClose = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.isMainChannelUrlScreenVisible = false
            },
        )
    }

    PopupContent(
        visibleProvider = { mainContentState.isSettingsScreenVisible },
        onDismissRequest = { mainContentState.isSettingsScreenVisible = false },
    ) {
        SettingsScreen(
            channelGroupListProvider = channelGroupListProvider,
            onClose = { mainContentState.isSettingsScreenVisible = false },
            initialCategory = mainViewModel.currentSettingsCategory.value ?: SettingsCategories.USER
        )
    }

    EpgReverseScreen(
        epgProgrammeReserveListProvider = { settingsViewModel.epgChannelReserveList },
        onConfirmReserve = { reserve ->
            filteredChannelGroupListProvider().channelList.firstOrNull { it.name == reserve.channel }
                ?.let {
                    mainContentState.changeCurrentChannel(it)
                }
        },
        onDeleteReserve = { reserve ->
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reserve)
        },
    )

    UpdateScreen()

    Visible({ settingsViewModel.debugShowFps }) { MonitorScreen() }
}