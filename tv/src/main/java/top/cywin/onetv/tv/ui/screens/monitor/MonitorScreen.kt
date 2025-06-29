package top.cywin.onetv.tv.ui.screens.monitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.screens.monitor.components.MonitorFps
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids

@Composable
fun MonitorScreen(
    modifier: Modifier = Modifier,
) {
    val childPadding = rememberChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        MonitorFps(modifier = Modifier.padding(start = childPadding.start, top = childPadding.top))
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun MonitorScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            MonitorScreen()
        }
    }
}