package top.cywin.onetv.tv.ui.screens.channelurl

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.collections.immutable.toPersistentList
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.tv.ui.material.Drawer
import top.cywin.onetv.tv.ui.material.DrawerPosition
import top.cywin.onetv.tv.ui.screens.channelurl.components.ChannelUrlItemList
import top.cywin.onetv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.captureBackKey

@Composable
fun ChannelUrlScreen(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel() },
    currentUrlProvider: () -> String = { "" },
    onUrlSelected: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)

    Drawer(
        modifier = modifier.captureBackKey { onClose() },
        onDismissRequest = onClose,
        position = DrawerPosition.End,
        header = { Text("多线路") },
    ) {
        ChannelUrlItemList(
            modifier = Modifier.width(268.dp),
            urlListProvider = { channelProvider().urlList.toPersistentList() },
            currentUrlProvider = currentUrlProvider,
            onSelected = onUrlSelected,
            onUserAction = { screenAutoCloseState.active() },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun ChannelUrlScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ChannelUrlScreen(
                channelProvider = { Channel.EXAMPLE },
            )
        }
    }
}