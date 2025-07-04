package top.cywin.onetv.tv.ui.screens.epgreverse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import kotlinx.coroutines.delay
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeReserve
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeReserveList
import top.cywin.onetv.tv.ui.material.Drawer
import top.cywin.onetv.tv.ui.material.DrawerPosition
import top.cywin.onetv.tv.ui.material.PopupContent
import top.cywin.onetv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.focusOnLaunched
import top.cywin.onetv.tv.ui.utils.handleKeyEvents

@Composable
fun EpgReverseScreen(
    modifier: Modifier = Modifier,
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    onDeleteReserve: (EpgProgrammeReserve) -> Unit = {},
    onConfirmReserve: (EpgProgrammeReserve) -> Unit = {},
) {
    var activeProgrammeReserve by remember { mutableStateOf<EpgProgrammeReserve?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val epgProgrammeReserveList = epgProgrammeReserveListProvider()

            epgProgrammeReserveList.forEach {
                if (System.currentTimeMillis() > it.startAt) {
                    onDeleteReserve(it)
                    activeProgrammeReserve = it
                }
            }

            delay(60 * 1000)
        }
    }

    PopupContent(
        visibleProvider = { activeProgrammeReserve != null },
        onDismissRequest = { activeProgrammeReserve = null },
    ) {
        EpgReverseScreenContent(
            modifier = modifier,
            activeProgrammeReserveProvider = { activeProgrammeReserve!! },
            onIgnoreReserve = { activeProgrammeReserve = null },
            onConfirmReserve = {
                onConfirmReserve(activeProgrammeReserve!!)
                activeProgrammeReserve = null
            },
        )
    }
}

@Composable
private fun EpgReverseScreenContent(
    modifier: Modifier = Modifier,
    activeProgrammeReserveProvider: () -> EpgProgrammeReserve = { EpgProgrammeReserve() },
    onIgnoreReserve: () -> Unit = {},
    onConfirmReserve: () -> Unit = {},
) {
    rememberScreenAutoCloseState(onTimeout = onIgnoreReserve)
    val activeProgrammeReserve = activeProgrammeReserveProvider()

    Drawer(
        onDismissRequest = onIgnoreReserve,
        position = DrawerPosition.Bottom,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.sizeIn(maxWidth = 556.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("您所预约的节目即将开始", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${activeProgrammeReserve.channel} - ${activeProgrammeReserve.programme}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WideButton(
                    modifier = Modifier
                        .focusOnLaunched()
                        .handleKeyEvents(onSelect = onConfirmReserve),
                    onClick = {},
                ) {
                    Text("立即前往")
                }

                WideButton(
                    modifier = Modifier.handleKeyEvents(onSelect = onIgnoreReserve),
                    onClick = {},
                ) {
                    Text("忽略")
                }
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun EpgReverseScreenContentPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            EpgReverseScreenContent(
                activeProgrammeReserveProvider = {
                    EpgProgrammeReserve(
                        channel = "测试频道",
                        programme = "测试节目".repeat(10),
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
            )
        }
    }
}