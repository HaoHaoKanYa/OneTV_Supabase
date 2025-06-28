package top.cywin.onetv.tv.ui.screens.channel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlinx.collections.immutable.toPersistentList
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.tv.ui.screens.channelurl.components.ChannelUrlItemList
import top.cywin.onetv.tv.ui.theme.MyTVTheme

@Composable
fun ChannelUrlPanel(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    currentUrlProvider: () -> String = { "" },
    onUrlSelected: (String) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp)) // 添加圆角，与频道列表一致
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .padding(20.dp)
    ) {
        // 多线路列表 - 移除标题，直接显示列表
        ChannelUrlItemList(
            modifier = Modifier.width(268.dp),
            urlListProvider = { channelProvider().urlList.toPersistentList() },
            currentUrlProvider = currentUrlProvider,
            onSelected = onUrlSelected,
            onUserAction = onUserAction,
        )
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun ChannelUrlPanelPreview() {
    MyTVTheme {
        ChannelUrlPanel(
            channelProvider = { Channel.EXAMPLE },
            currentUrlProvider = { Channel.EXAMPLE.urlList.first() },
        )
    }
}
