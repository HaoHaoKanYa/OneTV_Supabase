package top.cywin.onetv.tv.ui.screens.channel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.core.data.entities.epg.EpgProgramme
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeRecent
import top.cywin.onetv.tv.R
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelInfo
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelNumber
import top.cywin.onetv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids

@Composable
fun ChannelTempScreen(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    channelUrlIdxProvider: () -> Int = { 0 },
    channelNumberProvider: () -> Int = { 0 },
    showChannelLogoProvider: () -> Boolean = { false },
    recentEpgProgrammeProvider: () -> EpgProgrammeRecent? = { null },
    isInTimeShiftProvider: () -> Boolean = { false },
    currentPlaybackEpgProgrammeProvider: () -> EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },
) {
    val childPadding = rememberChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        ChannelNumber(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = childPadding.top, end = childPadding.end),
            channelNumberProvider = { channelNumberProvider().toString().padStart(2, '0') },
        )

        ChannelInfo(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x00000000),
                            Color(0xB4000000),
                            Color(0xF3000000),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY),
                    )
                )
                .padding(childPadding.paddingValues),
            channelProvider = channelProvider,
            channelUrlIdxProvider = channelUrlIdxProvider,
            recentEpgProgrammeProvider = recentEpgProgrammeProvider,
            isInTimeShiftProvider = isInTimeShiftProvider,
            currentPlaybackEpgProgrammeProvider = currentPlaybackEpgProgrammeProvider,
            videoPlayerMetadataProvider = videoPlayerMetadataProvider,
            showChannelLogoProvider = showChannelLogoProvider,
        )

        // 暂时注释掉二维码显示功能
        // 加载二维码图片并显示
        /*
        Image(
            painter = painterResource(id = R.drawable.gongzhonghao_qr_image),
            contentDescription = "公众号二维码",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = childPadding.end + 1.dp,
                    bottom = childPadding.bottom + 1.dp
                )
                .size(80.dp) // 设置图片大小
                .clip(RoundedCornerShape(8.dp)) // 设置裁剪方式，这里是圆角裁剪
        )
        */
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun ChannelTempScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelTempScreen(
                channelProvider = { Channel.EXAMPLE.copy(name = "长标题".repeat(4)) },
                channelUrlIdxProvider = { 0 },
                channelNumberProvider = { 8 },
                recentEpgProgrammeProvider = { EpgProgrammeRecent.EXAMPLE },
            )
        }
    }
}