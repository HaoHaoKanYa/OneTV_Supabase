package top.cywin.onetv.tv.ui.screens.videoplayerdiaplaymode.components // 引入该文件所在包

import androidx.compose.foundation.layout.Arrangement // 引入排列相关工具
import androidx.compose.foundation.layout.PaddingValues // 引入内边距工具
import androidx.compose.foundation.lazy.LazyColumn // 引入懒加载列布局工具
import androidx.compose.foundation.lazy.items // 引入处理懒加载列表项的工具
import androidx.compose.foundation.lazy.rememberLazyListState // 引入记住懒加载列表状态工具
import androidx.compose.runtime.Composable // 引入可组合函数
import androidx.compose.runtime.LaunchedEffect // 引入启动效果工具
import androidx.compose.runtime.snapshotFlow // 引入快照流工具
import androidx.compose.ui.Modifier // 引入Modifier工具类
import androidx.compose.ui.tooling.preview.Preview // 引入预览功能
import androidx.compose.ui.unit.dp // 引入dp单位
import androidx.tv.material3.ListItem // 引入列表项组件
import androidx.tv.material3.Text // 引入文本组件
import kotlinx.collections.immutable.ImmutableList // 引入不可变列表
import kotlinx.collections.immutable.persistentListOf // 引入持久化列表的创建函数
import kotlinx.collections.immutable.toPersistentList // 引入转化为持久化列表的函数
import kotlinx.coroutines.flow.distinctUntilChanged // 引入去重流处理
import top.cywin.onetv.tv.ui.screens.videoplayer.VideoPlayerDisplayMode // 引入视频播放器显示模式
import top.cywin.onetv.tv.ui.theme.MyTVTheme // 引入主题
import top.cywin.onetv.tv.ui.utils.handleKeyEvents // 引入键盘事件处理函数
import kotlin.math.max // 引入max函数

@Composable // 表明该函数是一个可组合函数
fun VideoPlayerDisplayModeItemList( // 定义 VideoPlayerDisplayModeItemList 可组合函数
    modifier: Modifier = Modifier, // 修饰符，默认为空
    displayModeListProvider: () -> ImmutableList<VideoPlayerDisplayMode> = { persistentListOf() }, // 获取显示模式列表的回调，默认为空列表
    currentDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }, // 获取当前显示模式的回调，默认为原始模式
    onSelected: (VideoPlayerDisplayMode) -> Unit = {}, // 选中显示模式时的回调，默认为空
    onApplyToGlobal: (() -> Unit)? = null, // 应用到全局的回调，默认为null
    onUserAction: () -> Unit = {}, // 用户操作的回调，默认为空
) {
    val displayModeList = displayModeListProvider() // 获取显示模式列表

    val listState =
        rememberLazyListState(max(0, displayModeList.indexOf(currentDisplayModeProvider()) - 2)) // 记住懒加载列表的状态，默认滚动位置为当前显示模式的前两个项

    LaunchedEffect(listState) { // 在listState变化时启动副作用
        snapshotFlow { listState.isScrollInProgress } // 获取列表是否正在滚动的快照
            .distinctUntilChanged() // 过滤掉重复的滚动状态
            .collect { _ -> onUserAction() } // 收集变化并调用onUserAction回调
    }

    LazyColumn( // 使用LazyColumn组件来显示列表
        modifier = modifier, // 应用修饰符
        state = listState, // 设置列表状态
        contentPadding = PaddingValues(vertical = 4.dp), // 设置内容的上下内边距为4dp
        verticalArrangement = Arrangement.spacedBy(10.dp), // 设置垂直排列，每个元素间距10dp
    ) {
        items(displayModeList) { displayMode -> // 使用items函数遍历显示模式列表
            VideoPlayerDisplayModeItem( // 渲染VideoPlayerDisplayModeItem组件
                displayModeProvider = { displayMode }, // 设置显示模式
                isSelectedProvider = { displayMode == currentDisplayModeProvider() }, // 判断是否选中
                onSelected = { onSelected(displayMode) }, // 选中时调用onSelected回调
            )
        }

        if (onApplyToGlobal != null) { // 如果应用到全局的回调不为空
            item { // 渲染一个单独的项
                ListItem( // 使用ListItem组件
                    modifier = modifier.handleKeyEvents(onSelect = onApplyToGlobal), // 处理键盘事件并调用onApplyToGlobal回调
                    selected = false, // 设置为未选中
                    onClick = {}, // 点击时无动作
                    headlineContent = { Text("应用到全局") }, // 设置标题内容为“应用到全局”
                )
            }
        }
    }
}

@Preview // 标记为预览函数，供 UI 预览使用
@Composable // 该函数是一个可组合函数
private fun VideoPlayerDisplayModeItemListPreview() { // 定义 VideoPlayerDisplayModeItemList 组件的预览函数
    MyTVTheme { // 使用 MyTVTheme 主题
        VideoPlayerDisplayModeItemList( // 渲染 VideoPlayerDisplayModeItemList 组件
            displayModeListProvider = {
                VideoPlayerDisplayMode.entries.toPersistentList() // 获取显示模式条目并转换为持久化列表
            },
            currentDisplayModeProvider = { VideoPlayerDisplayMode.ORIGINAL }, // 设置当前显示模式为原始模式
        )
    }
}
