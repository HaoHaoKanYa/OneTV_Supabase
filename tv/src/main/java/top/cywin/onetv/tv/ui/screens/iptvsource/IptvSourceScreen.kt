package top.cywin.onetv.tv.ui.screens.iptvsource

import android.graphics.Color.rgb
import android.graphics.fonts.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import top.cywin.onetv.core.data.entities.iptvsource.IptvSource
import top.cywin.onetv.core.data.entities.iptvsource.IptvSourceList
import top.cywin.onetv.core.data.utils.Constants
import top.cywin.onetv.tv.ui.material.Drawer
import top.cywin.onetv.tv.ui.material.DrawerPosition
import top.cywin.onetv.tv.ui.material.LocalPopupManager
import top.cywin.onetv.tv.ui.material.SimplePopup
import top.cywin.onetv.tv.ui.screens.iptvsource.components.IptvSourceItem
import top.cywin.onetv.tv.ui.screens.settings.components.SettingsCategoryPush
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.focusOnLaunchedSaveable
import top.cywin.onetv.tv.ui.utils.handleKeyEvents
import top.cywin.onetv.tv.ui.utils.ifElse
import kotlin.math.max
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun IptvSourceScreen(
    modifier: Modifier = Modifier,
    iptvSourceListProvider: () -> IptvSourceList = { IptvSourceList() },
    currentIptvSourceProvider: () -> IptvSource = { IptvSource() },
    onIptvSourceSelected: (IptvSource) -> Unit = {},
    onIptvSourceDeleted: (IptvSource) -> Unit = {},
    onClose: () -> Unit = {},
) {
    // 获取 IPTV 源列表，并将默认源添加到列表中
    val iptvSourceList = iptvSourceListProvider().let { Constants.IPTV_SOURCE_LIST + it }
    // 获取当前选中的 IPTV 源
    val currentIptvSource = currentIptvSourceProvider()
    // 获取当前选中的 IPTV 源的索引
    val currentIptvSourceIdx = iptvSourceList.indexOf(currentIptvSource)

    val focusManager = LocalFocusManager.current


    Drawer(
        position = DrawerPosition.Bottom,
        onDismissRequest = onClose,
        header = { Text("自定义直播源",color = Color(rgb(255, 255, 255)),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold)// 设置加粗)
                  },
    ) {
        // 记住列表的滚动状态
        val listState = rememberLazyListState(max(0, currentIptvSourceIdx - 2))

        LazyColumn(
            modifier = modifier.height(240.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 渲染 IPTV 源列表
            itemsIndexed(iptvSourceList) { index, source ->
                // 判断当前源是否为默认源
                if (source.name == "移动-关注公众号【壹来了】") {
                    // 如果是默认源，显示名称和提示文本，并允许选择
                    ListItem(
                        modifier = Modifier.ifElse(
                            max(0, currentIptvSourceIdx) == index,
                            Modifier.focusOnLaunchedSaveable(iptvSourceList),
                        ),
                        selected = index == currentIptvSourceIdx, // 选中状态
                        onClick = { onIptvSourceSelected(source) },
                        headlineContent = {
                            Column {
                                // 显示默认源的名称
                                Text("移动测试：移动-关注公众号【壹来了】",color = Color(rgb(135, 206, 250)),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold)// 设置加粗
                                // 显示提示文本
                                Text("声明：壹来电视仅为空壳软件不提供任何直播源，本条线路仅为调试软件用途，" +
                                        "使用者请在24小时内删除！更多精彩内容请添加自定义直播源。",color = Color(rgb(255, 69, 0)))
                            }
                        },
                        trailingContent = {
                            // 在默认源旁边显示选择的小圆点
                            RadioButton(
                                selected = index == currentIptvSourceIdx,
                                onClick = { onIptvSourceSelected(source) }
                            )
                        }
                    )

                } else if (source.name == "电信-關注公众号【壹来了】") {
                    // 第二条默认源，显示为声明文本
                    ListItem(
                        modifier = Modifier.ifElse(
                            max(0, currentIptvSourceIdx) == index,
                            Modifier.focusOnLaunchedSaveable(iptvSourceList),
                        ),
                        selected = index == currentIptvSourceIdx, // 选中状态
                        onClick = { onIptvSourceSelected(source) },
                        headlineContent = {
                            Column {
                                // 显示第二条默认源的名称
                                Text("电信测试：电信-關注公众号【壹来了】", color = Color(rgb(135, 206, 250)),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold)// 设置加粗)
                                // 显示声明文本
                                Text("声明：壹来电视仅为空壳软件不提供任何直播源，本条线路仅为调试软件用途，" +
                                        "使用者请在24小时内删除！更多精彩内容请添加自定义直播源。",color = Color(rgb(255, 69, 0)))
                            }
                        },
                        trailingContent = {
                            // 显示选择的小圆点
                            RadioButton(
                                selected = index == currentIptvSourceIdx,
                                onClick = { onIptvSourceSelected(source) }
                            )
                        }
                    )
                } else if (source.name == "公共-关注公眾号【壹来了】") {
                    // 第三条默认源，显示为声明文本
                    ListItem(
                        modifier = Modifier.ifElse(
                            max(0, currentIptvSourceIdx) == index,
                            Modifier.focusOnLaunchedSaveable(iptvSourceList),
                        ),
                        selected = index == currentIptvSourceIdx, // 选中状态
                        onClick = { onIptvSourceSelected(source) },
                        headlineContent = {
                            Column {
                                // 显示第三条默认源的名称
                                Text("公共测试：公共-关注公眾号【壹来了】", color = Color(rgb(135, 206, 250)),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold)// 设置加粗)
                                // 显示声明文本
                                Text("声明：壹来电视仅为空壳软件不提供任何直播源，本条线路仅为调试软件用途，" +
                                        "使用者请在24小时内删除！更多精彩内容请添加自定义直播源。",color = Color(rgb(255, 69, 0)))
                            }
                        },
                        trailingContent = {
                            // 显示选择的小圆点
                            RadioButton(
                                selected = index == currentIptvSourceIdx,
                                onClick = { onIptvSourceSelected(source) }
                            )
                        }
                    )
                }
                else {
                    // 如果不是默认源，显示自定义源项
                    IptvSourceItem(
                        modifier = Modifier.ifElse(
                            max(0, currentIptvSourceIdx) == index,
                            Modifier.focusOnLaunchedSaveable(iptvSourceList),
                        ),
                        iptvSourceProvider = { source },
                        isSelectedProvider = { index == currentIptvSourceIdx },
                        onSelected = { onIptvSourceSelected(source) },
                        onDeleted = {
                            if (source == iptvSourceList.last()) {
                                focusManager.moveFocus(FocusDirection.Up)
                            }
                            onIptvSourceDeleted(source)
                        },
                    )
                }
            }

            // 添加自定义直播源项
            item {
                val popupManager = LocalPopupManager.current
                val focusRequester = remember { FocusRequester() }
                var isFocused by remember { mutableStateOf(false) }
                var showPush by remember { mutableStateOf(false) }

                ListItem(
                    modifier = modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                        .handleKeyEvents(
                            isFocused = { isFocused },
                            focusRequester = focusRequester,
                            onSelect = {
                                popupManager.push(focusRequester, true)
                                showPush = true
                            },
                        ),
                    selected = false,
                    onClick = {},
                    headlineContent = {
                        Text("添加自定义直播源")
                    },
                )

                SimplePopup(
                    visibleProvider = { showPush },
                    onDismissRequest = { showPush = false },
                ) {
                    SettingsCategoryPush()
                }
            }
        }
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun IptvSourceScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            IptvSourceScreen(
                iptvSourceListProvider = {
                    // 模拟从 Constants 中获取默认源，并添加到列表中
                    IptvSourceList(
                        listOf(
                            IptvSource(name = "直播源1", url = "http://1.2.3.4/iptv.m3u"),
                            IptvSource(name = "直播源2", url = "http://1.2.3.4/iptv.m3u"),

                        )
                    )
                },
                currentIptvSourceProvider = {
                    IptvSource(name = "直播源1", url = "http://1.2.3.4/iptv.m3u")
                },
            )
        }
    }
}
