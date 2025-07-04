package top.cywin.onetv.tv.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.cywin.onetv.core.util.utils.humanizeMs
import top.cywin.onetv.tv.ui.material.Drawer
import top.cywin.onetv.tv.ui.material.DrawerPosition
import top.cywin.onetv.tv.ui.material.PopupHandleableApplication
import top.cywin.onetv.tv.ui.material.SimplePopup
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.theme.colors
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.focusOnLaunchedSaveable
import top.cywin.onetv.tv.ui.utils.handleKeyEvents
import top.cywin.onetv.tv.ui.utils.ifElse
import kotlin.math.max

@Composable
fun <T> SelectDialog(
    modifier: Modifier = Modifier,
    title: String,
    currentDataProvider: () -> T,
    dataListProvider: () -> List<T>,
    dataText: (T) -> String,
    onDataSelected: (T) -> Unit = {},
    visibleProvider: () -> Boolean = { true },
    onDismissRequest: (() -> Unit)? = null,
) {
    val currentData = currentDataProvider()
    val dataList = dataListProvider()

    SimplePopup(
        visibleProvider = visibleProvider,
        onDismissRequest = onDismissRequest,
    ) {
        Drawer(
            position = DrawerPosition.Bottom,
            onDismissRequest = onDismissRequest,
            header = { Text(title) },
        ) {
            LazyVerticalGrid(
                modifier = modifier,
                columns = GridCells.Fixed(10),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(dataList) { index, data ->
                    SelectDialogItem(
                        modifier = Modifier.ifElse(
                            index == max(0, dataList.indexOf(currentData)),
                            Modifier.focusOnLaunchedSaveable(),
                        ),
                        text = dataText(data),
                        onSelected = { onDataSelected(data) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectDialogItem(
    modifier: Modifier = Modifier,
    text: String,
    onSelected: () -> Unit = {},
) {
    Card(
        onClick = {},
        modifier = modifier
            .handleKeyEvents(onSelect = onSelected),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colors.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.onSurface),
            ),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 12.dp),
        )
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun SelectDialogPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            PopupHandleableApplication {
                SelectDialog(
                    title = "直播源缓存时间",
                    currentDataProvider = { 0L },
                    dataListProvider = {
                        (0..<24).map { it * 1000L * 60 * 60 }
                            .plus((1..15).map { it * 1000L * 60 * 60 * 24 })
                            .plus(listOf(Long.MAX_VALUE))
                    },
                    dataText = {
                        when (it) {
                            0L -> "不缓存"
                            Long.MAX_VALUE -> "永久"
                            else -> it.humanizeMs()
                        }
                    },
                )
            }
        }
    }
}