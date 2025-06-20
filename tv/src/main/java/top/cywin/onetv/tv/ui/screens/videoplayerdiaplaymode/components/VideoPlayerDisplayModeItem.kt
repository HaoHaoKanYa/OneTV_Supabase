package top.cywin.onetv.tv.ui.screens.videoplayerdiaplaymode.components // 引入该文件所在包

import androidx.compose.foundation.layout.Arrangement // 引入排列相关工具
import androidx.compose.foundation.layout.Column // 引入列布局工具
import androidx.compose.foundation.layout.padding // 引入设置内边距的工具
import androidx.compose.runtime.Composable // 引入可组合函数
import androidx.compose.ui.Modifier // 引入Modifier工具类
import androidx.compose.ui.tooling.preview.Preview // 引入预览功能
import androidx.compose.ui.unit.dp // 引入dp单位
import androidx.tv.material3.ListItem // 引入列表项组件
import androidx.tv.material3.RadioButton // 引入单选按钮组件
import androidx.tv.material3.Text // 引入文本组件
import top.cywin.onetv.tv.ui.screens.videoplayer.VideoPlayerDisplayMode // 引入视频播放器显示模式
import top.cywin.onetv.tv.ui.theme.MyTVTheme // 引入主题
import top.cywin.onetv.tv.ui.utils.focusOnLaunchedSaveable // 引入焦点获取保存函数
import top.cywin.onetv.tv.ui.utils.handleKeyEvents // 引入键盘事件处理函数
import top.cywin.onetv.tv.ui.utils.ifElse // 引入ifElse扩展函数

@Composable // 表明该函数是一个可组合函数
fun VideoPlayerDisplayModeItem( // 定义一个名为 VideoPlayerDisplayModeItem 的可组合函数
    modifier: Modifier = Modifier, // 修饰符，默认为空
    displayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }, // 获取显示模式的回调，默认为原始模式
    isSelectedProvider: () -> Boolean = { false }, // 获取是否选中的回调，默认为未选中
    onSelected: () -> Unit = {}, // 选中时的回调，默认为空
) {
    val displayMode = displayModeProvider() // 获取显示模式
    val isSelected = isSelectedProvider() // 获取是否选中状态

    ListItem( // 使用 ListItem 组件
        modifier = modifier // 应用传入的修饰符
            .ifElse(isSelected, Modifier.focusOnLaunchedSaveable()) // 根据选中状态，应用焦点获取保存修饰符
            .handleKeyEvents(onSelect = onSelected), // 处理键盘事件，选中时调用 onSelected 回调
        selected = false, // 设置选中状态为 false（不实际改变 UI 选中状态）
        onClick = {}, // 点击时无动作
        headlineContent = { Text(displayMode.label) }, // 显示模式标签作为标题内容
        trailingContent = { // 设置尾部内容
            RadioButton(selected = isSelected, onClick = {}) // 显示单选按钮，选中状态由 isSelected 控制
        },
    )
}

@Preview // 标记为预览函数，供 UI 预览使用
@Composable // 该函数是一个可组合函数
private fun VideoPlayerDisplayModeItemPreview() { // 定义 VideoPlayerDisplayModeItem 组件的预览函数
    MyTVTheme { // 使用 MyTVTheme 主题
        Column( // 使用列布局
            modifier = Modifier.padding(20.dp), // 设置内边距为 20dp
            verticalArrangement = Arrangement.spacedBy(20.dp), // 设置垂直排列，每个元素间距 20dp
        ) {
            VideoPlayerDisplayModeItem( // 渲染第一个 VideoPlayerDisplayModeItem
                displayModeProvider = { VideoPlayerDisplayMode.ORIGINAL }, // 设置显示模式为原始模式
                isSelectedProvider = { true }, // 设置为选中状态
            )

            VideoPlayerDisplayModeItem( // 渲染第二个 VideoPlayerDisplayModeItem
                displayModeProvider = { VideoPlayerDisplayMode.ORIGINAL }, // 设置显示模式为原始模式
            )
        }
    }
}
