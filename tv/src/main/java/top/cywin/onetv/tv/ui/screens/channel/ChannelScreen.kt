package top.cywin.onetv.tv.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.cywin.onetv.core.data.entities.channel.ChannelList
import top.cywin.onetv.core.data.entities.epg.EpgList
import top.cywin.onetv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.cywin.onetv.core.data.entities.epg.EpgProgramme
import top.cywin.onetv.tv.ui.material.Snackbar
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelInfo
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelItemGrid
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelItemGroupList
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelNumber
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelUrlPanel
import top.cywin.onetv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.cywin.onetv.tv.ui.screens.datetime.components.DateTimeDetail
import top.cywin.onetv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.ifElse

@Composable
fun ChannelScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    currentChannelProvider: () -> Channel = { Channel() },
    currentChannelUrlIdxProvider: () -> Int = { 0 },
    showChannelLogoProvider: () -> Boolean = { false },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    epgListProvider: () -> EpgList = { EpgList() },
    showEpgProgrammeProgressProvider: () -> Boolean = { false },
    isInTimeShiftProvider: () -> Boolean = { false },
    currentPlaybackEpgProgrammeProvider: () -> EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },
    channelFavoriteEnabledProvider: () -> Boolean = { false },
    channelFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    channelFavoriteListVisibleProvider: () -> Boolean = { false },
    onChannelFavoriteListVisibleChange: (Boolean) -> Unit = {},
    // 多线路相关参数
    showChannelUrlListProvider: () -> Boolean = { false },
    onChannelUrlSelected: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)

    Row(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
    ) {
        // 左侧：原有的频道列表
        Box(
            modifier = if (showChannelUrlListProvider()) {
                Modifier.weight(1f)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            ChannelScreenTopRight(
                channelNumberProvider = {
                    (channelGroupListProvider().channelIdx(currentChannelProvider()) + 1)
                        .toString()
                        .padStart(2, '0')
                },
            )

            ChannelScreenBottom(
                channelGroupListProvider = channelGroupListProvider,
                currentChannelProvider = currentChannelProvider,
                currentChannelUrlIdxProvider = currentChannelUrlIdxProvider,
                showChannelLogoProvider = showChannelLogoProvider,
                onChannelSelected = onChannelSelected,
                onChannelFavoriteToggle = onChannelFavoriteToggle,
                epgListProvider = epgListProvider,
                showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                isInTimeShiftProvider = isInTimeShiftProvider,
                currentPlaybackEpgProgrammeProvider = currentPlaybackEpgProgrammeProvider,
                videoPlayerMetadataProvider = videoPlayerMetadataProvider,
                channelFavoriteEnabledProvider = channelFavoriteEnabledProvider,
                channelFavoriteListProvider = channelFavoriteListProvider,
                channelFavoriteListVisibleProvider = channelFavoriteListVisibleProvider,
                onChannelFavoriteListVisibleChange = onChannelFavoriteListVisibleChange,
                onUserAction = { screenAutoCloseState.active() },
            )
        }

        // 多线路组件 - 放在频道列表和EPG之间的位置
        if (showChannelUrlListProvider()) {
            ChannelUrlPanel(
                modifier = Modifier.width(320.dp),
                channelProvider = currentChannelProvider,
                currentUrlProvider = { currentChannelProvider().urlList[currentChannelUrlIdxProvider()] },
                onUrlSelected = onChannelUrlSelected,
                onUserAction = { screenAutoCloseState.active() },
            )
        }
    }
}

@Composable
fun ChannelScreenTopRight(
    modifier: Modifier = Modifier,
    channelNumberProvider: () -> String = { "" },
) {
    val childPadding = rememberChildPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = childPadding.top, end = childPadding.end),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelNumber(channelNumberProvider = channelNumberProvider)

            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Spacer(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onSurface)
                        .width(2.dp)
                        .height(30.dp),
                )
            }

            DateTimeDetail()
        }
    }
}

@Composable
private fun ChannelScreenBottom(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    currentChannelProvider: () -> Channel = { Channel() },
    currentChannelUrlIdxProvider: () -> Int = { 0 },
    showChannelLogoProvider: () -> Boolean = { false },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    epgListProvider: () -> EpgList = { EpgList() },
    showEpgProgrammeProgressProvider: () -> Boolean = { false },
    isInTimeShiftProvider: () -> Boolean = { false },
    currentPlaybackEpgProgrammeProvider: () -> EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },
    channelFavoriteEnabledProvider: () -> Boolean = { false },
    channelFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    channelFavoriteListVisibleProvider: () -> Boolean = { false },
    onChannelFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChannelInfo(
                modifier = Modifier.padding(start = childPadding.start, end = childPadding.end),
                channelProvider = currentChannelProvider,
                channelUrlIdxProvider = currentChannelUrlIdxProvider,
                recentEpgProgrammeProvider = {
                    epgListProvider().recentProgramme(currentChannelProvider())
                },
                isInTimeShiftProvider = isInTimeShiftProvider,
                currentPlaybackEpgProgrammeProvider = currentPlaybackEpgProgrammeProvider,
                videoPlayerMetadataProvider = videoPlayerMetadataProvider,
                showChannelLogoProvider = showChannelLogoProvider,
            )

            ChannelScreenBottomChannelItemListAllAndFavorite(
                channelGroupListProvider = channelGroupListProvider,
                currentChannelProvider = currentChannelProvider,
                showChannelLogoProvider = showChannelLogoProvider,
                onChannelSelected = onChannelSelected,
                onChannelFavoriteToggle = onChannelFavoriteToggle,
                epgListProvider = epgListProvider,
                showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                channelFavoriteEnabledProvider = channelFavoriteEnabledProvider,
                channelFavoriteListProvider = channelFavoriteListProvider,
                channelFavoriteListVisibleProvider = channelFavoriteListVisibleProvider,
                onChannelFavoriteListVisibleChange = onChannelFavoriteListVisibleChange,
                onUserAction = onUserAction,
            )
        }
    }
}

@Composable
private fun ChannelScreenBottomChannelItemListAllAndFavorite(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    currentChannelProvider: () -> Channel = { Channel() },
    showChannelLogoProvider: () -> Boolean = { false },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    epgListProvider: () -> EpgList = { EpgList() },
    showEpgProgrammeProgressProvider: () -> Boolean = { false },
    channelFavoriteEnabledProvider: () -> Boolean = { false },
    channelFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    channelFavoriteListVisibleProvider: () -> Boolean = { false },
    onChannelFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    Box(
        modifier = modifier.ifElse(
            showChannelLogoProvider(),
            Modifier.height(250.dp),
            Modifier.height(150.dp),
        ),
    ) {
        if (channelFavoriteListVisibleProvider()) {
            ChannelItemGrid(
                title = "收藏",
                channelListProvider = {
                    val favoriteChannelNameList = channelFavoriteListProvider()
                    ChannelList(channelGroupListProvider().channelList
                        .filter { favoriteChannelNameList.contains(it.name) })
                },
                currentChannelProvider = currentChannelProvider,
                showChannelLogoProvider = showChannelLogoProvider,
                onChannelSelected = onChannelSelected,
                onChannelFavoriteToggle = onChannelFavoriteToggle,
                epgListProvider = epgListProvider,
                showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                onClose = { onChannelFavoriteListVisibleChange(false) },
                onUserAction = onUserAction,
            )
        } else {
            if (channelGroupListProvider().size > 1) {

                ChannelItemGroupList(
                    channelGroupListProvider = channelGroupListProvider,
                    currentChannelProvider = currentChannelProvider,
                    showChannelLogoProvider = showChannelLogoProvider,
                    onChannelSelected = onChannelSelected,
                    onChannelFavoriteToggle = onChannelFavoriteToggle,
                    epgListProvider = epgListProvider,
                    showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                    onToFavorite = {
                        if (!channelFavoriteEnabledProvider()) return@ChannelItemGroupList

                        val favoriteChannelNameList = channelFavoriteListProvider()
                        val favoriteList = channelGroupListProvider().channelList
                            .filter { favoriteChannelNameList.contains(it.name) }

                        if (favoriteList.isNotEmpty()) {
                            onChannelFavoriteListVisibleChange(true)
                        } else {
                            Snackbar.show("没有收藏的频道")
                        }
                    },
                    onUserAction = onUserAction,
                )
            } else {
                ChannelItemGrid(
                    title = "全部",
                    channelListProvider = { channelGroupListProvider().channelList },
                    currentChannelProvider = currentChannelProvider,
                    showChannelLogoProvider = showChannelLogoProvider,
                    onChannelSelected = onChannelSelected,
                    onChannelFavoriteToggle = onChannelFavoriteToggle,
                    epgListProvider = epgListProvider,
                    showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                    onClose = {
                        if (!channelFavoriteEnabledProvider()) return@ChannelItemGrid

                        val favoriteChannelNameList = channelFavoriteListProvider()
                        val favoriteList = channelGroupListProvider().channelList
                            .filter { favoriteChannelNameList.contains(it.name) }

                        if (favoriteList.isNotEmpty()) {
                            onChannelFavoriteListVisibleChange(true)
                        } else {
                            Snackbar.show("没有收藏的频道")
                        }
                    },
                    onUserAction = onUserAction,
                )
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun ChannelScreenTopRightPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelScreenTopRight(channelNumberProvider = { "01" })
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun ChannelScreenBottomPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelScreenBottom(
                channelGroupListProvider = { ChannelGroupList.EXAMPLE },
                currentChannelProvider = { ChannelGroupList.EXAMPLE.first().channelList.first() },
                currentChannelUrlIdxProvider = { 0 },
                epgListProvider = { EpgList.example(ChannelGroupList.EXAMPLE.channelList) },
                showEpgProgrammeProgressProvider = { true },
            )
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun ChannelScreenBottomFavoritePreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelScreenBottom(
                channelGroupListProvider = { ChannelGroupList.EXAMPLE },
                currentChannelProvider = { ChannelGroupList.EXAMPLE.first().channelList.first() },
                currentChannelUrlIdxProvider = { 0 },
                epgListProvider = { EpgList.example(ChannelGroupList.EXAMPLE.channelList) },
                showEpgProgrammeProgressProvider = { true },
                channelFavoriteEnabledProvider = { true },
                channelFavoriteListProvider = {
                    ChannelGroupList.EXAMPLE.first().channelList.map { it.name }.toImmutableList()
                },
                channelFavoriteListVisibleProvider = { true },
            )
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun ChannelScreenBottomFavoriteWithChannelLogoPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelScreenBottom(
                channelGroupListProvider = { ChannelGroupList.EXAMPLE },
                currentChannelProvider = { ChannelGroupList.EXAMPLE.first().channelList.first() },
                currentChannelUrlIdxProvider = { 0 },
                showChannelLogoProvider = { true },
                epgListProvider = { EpgList.example(ChannelGroupList.EXAMPLE.channelList) },
                showEpgProgrammeProgressProvider = { true },
                channelFavoriteEnabledProvider = { true },
                channelFavoriteListProvider = {
                    ChannelGroupList.EXAMPLE.first().channelList.map { it.name }.toImmutableList()
                },
                channelFavoriteListVisibleProvider = { true },
            )
        }
    }
}