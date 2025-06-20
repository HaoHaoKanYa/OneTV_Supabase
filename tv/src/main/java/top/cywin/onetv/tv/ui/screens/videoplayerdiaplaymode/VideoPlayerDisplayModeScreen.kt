package top.cywin.onetv.tv.ui.screens.videoplayerdiaplaymode // 引入该文件所在包

import androidx.compose.foundation.layout.width // 引入设置宽度的工具
import androidx.compose.runtime.Composable // 引入可组合函数
import androidx.compose.ui.Modifier // 引入Modifier工具类
import androidx.compose.ui.tooling.preview.Preview // 引入预览功能
import androidx.compose.ui.unit.dp // 引入dp单位
import androidx.tv.material3.Text // 引入文本组件
import kotlinx.collections.immutable.toPersistentList // 引入转换为持久化列表的函数
import top.cywin.onetv.tv.ui.material.Drawer // 引入Drawer组件
import top.cywin.onetv.tv.ui.material.DrawerPosition // 引入Drawer位置
import top.cywin.onetv.tv.ui.screens.components.rememberScreenAutoCloseState // 引入屏幕自动关闭状态管理
import top.cywin.onetv.tv.ui.screens.videoplayer.VideoPlayerDisplayMode // 引入视频播放器显示模式
import top.cywin.onetv.tv.ui.screens.videoplayerdiaplaymode.components.VideoPlayerDisplayModeItemList // 引入显示模式列表组件
import top.cywin.onetv.tv.ui.theme.MyTVTheme // 引入主题
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids // 引入带布局网格的预览
import top.cywin.onetv.tv.ui.utils.captureBackKey // 引入捕获返回键事件的工具

@Composable // 表明该函数是一个可组合函数
fun VideoPlayerDisplayModeScreen( // 定义 VideoPlayerDisplayModeScreen 可组合函数
    modifier: Modifier = Modifier, // 修饰符，默认为空
    currentDisplayModeProvider: () -> VideoPlayerDisplayMode = { VideoPlayerDisplayMode.ORIGINAL }, // 获取当前显示模式的回调，默认为原始模式
    onDisplayModeChanged: (VideoPlayerDisplayMode) -> Unit = {}, // 显示模式更改时的回调，默认为空
    onApplyToGlobal: (() -> Unit)? = null, // 应用到全局的回调，默认为null
    onClose: () -> Unit = {}, // 关闭回调，默认为空
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose) // 记住屏幕自动关闭状态，设置超时触发关闭回调

    Drawer( // 使用Drawer组件
        modifier = modifier.captureBackKey { onClose() }, // 捕获返回键事件并调用关闭回调
        onDismissRequest = onClose, // 设置取消请求时的回调
        position = DrawerPosition.End, // 设置Drawer的位置为右侧
        header = { Text("显示模式") }, // 设置Drawer的标题为“显示模式”
    ) {
        VideoPlayerDisplayModeItemList( // 渲染VideoPlayerDisplayModeItemList组件
            modifier = Modifier.width(268.dp), // 设置组件宽度为268dp
            displayModeListProvider = { VideoPlayerDisplayMode.entries.toPersistentList() }, // 提供显示模式条目的回调，转换为持久化列表
            currentDisplayModeProvider = currentDisplayModeProvider, // 设置当前显示模式的回调
            onSelected = onDisplayModeChanged, // 选中模式时调用onDisplayModeChanged回调
            onApplyToGlobal = onApplyToGlobal, // 设置应用到全局的回调
            onUserAction = { screenAutoCloseState.active() }, // 用户操作时激活屏幕自动关闭
        )
    }
}

@Preview(device = "id:Android TV (720p)") // 在Android TV 720p设备上进行预览
@Composable // 该函数是一个可组合函数
private fun VideoPlayerDisplayModeScreenPreview() { // 定义 VideoPlayerDisplayModeScreen 组件的预览函数
    MyTVTheme { // 使用 MyTVTheme 主题
        PreviewWithLayoutGrids { // 使用布局网格进行预览
            VideoPlayerDisplayModeScreen() // 渲染 VideoPlayerDisplayModeScreen 组件
        }
    }
}
