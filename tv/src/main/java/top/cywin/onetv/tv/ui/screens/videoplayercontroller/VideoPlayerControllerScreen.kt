package top.cywin.onetv.tv.ui.screens.videoplayercontroller // 定义包路径

import androidx.compose.foundation.layout.Arrangement // 导入布局的排列方式
import androidx.compose.foundation.layout.Row // 导入 Row 布局
import androidx.compose.foundation.layout.padding // 导入设置内边距的函数
import androidx.compose.runtime.Composable // 导入 Composable 注解，用于标记可组合函数
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入 Modifier，用于修改布局属性
import androidx.compose.ui.tooling.preview.Preview // 导入预览功能
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.tv.material3.Text // 导入 Text 组件
import top.cywin.onetv.tv.ui.material.Drawer // 导入 Drawer 组件
import top.cywin.onetv.tv.ui.material.DrawerPosition // 导入 DrawerPosition，用于设置 Drawer 位置
import top.cywin.onetv.tv.ui.screens.videoplayercontroller.components.VideoPlayerControllerPositionCtrl // 导入视频播放器位置控制组件
import top.cywin.onetv.tv.ui.screens.videoplayercontroller.components.VideoPlayerControllerStateCtrl // 导入视频播放器状态控制组件
import top.cywin.onetv.tv.ui.theme.MyTVTheme // 导入 MyTVTheme 主题
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids // 导入用于预览的布局网格工具
import top.cywin.onetv.tv.ui.utils.captureBackKey // 导入捕获返回键的工具函数
import top.cywin.onetv.tv.ui.utils.focusOnLaunchedSaveable // 导入获取焦点并保存的工具函数

@Composable
fun VideoPlayerControllerScreen( // 定义视频播放器控制器屏幕
    modifier: Modifier = Modifier, // 可修改的修饰符，默认为空
    isVideoPlayerPlayingProvider: () -> Boolean = { false }, // 播放状态的提供者，默认为 false
    isVideoPlayerBufferingProvider: () -> Boolean = { false }, // 缓冲状态的提供者，默认为 false
    videoPlayerCurrentPositionProvider: () -> Long = { 0L }, // 当前播放位置的提供者，默认为 0
    videoPlayerDurationProvider: () -> Pair<Long, Long> = { 0L to 0L }, // 视频时长的提供者，默认为 (0L, 0L)
    onVideoPlayerPlay: () -> Unit = {}, // 播放回调函数，默认为空
    onVideoPlayerPause: () -> Unit = {}, // 暂停回调函数，默认为空
    onVideoPlayerSeekTo: (Long) -> Unit = {}, // 跳转位置回调函数，默认为空
    onClose: () -> Unit = {}, // 关闭回调函数，默认为空
) {
    Drawer( // 创建一个底部抽屉组件
        modifier = modifier.captureBackKey { onClose() }, // 捕获返回键事件，执行关闭回调
        onDismissRequest = onClose, // 抽屉关闭时的请求处理
        position = DrawerPosition.Bottom, // 设置抽屉的位置为底部
        header = { Text("播放控制") }, // 设置抽屉的头部文本
    ) {
        Row( // 使用 Row 布局，水平排列
            modifier = Modifier.padding(top = 10.dp), // 设置顶部内边距为 10dp
            horizontalArrangement = Arrangement.spacedBy(20.dp), // 设置元素之间的水平间距为 20dp
            verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
        ) {
            VideoPlayerControllerStateCtrl( // 渲染视频播放器状态控制组件
                modifier = Modifier.focusOnLaunchedSaveable(), // 获取焦点并保存状态
                isPlayingProvider = isVideoPlayerPlayingProvider, // 传入播放状态提供者
                isBufferingProvider = isVideoPlayerBufferingProvider, // 传入缓冲状态提供者
                onPlay = onVideoPlayerPlay, // 传入播放回调函数
                onPause = onVideoPlayerPause, // 传入暂停回调函数
            )

            VideoPlayerControllerPositionCtrl( // 渲染视频播放器位置控制组件
                currentPositionProvider = videoPlayerCurrentPositionProvider, // 传入当前播放位置提供者
                durationProvider = videoPlayerDurationProvider, // 传入时长提供者
                onSeekTo = onVideoPlayerSeekTo, // 传入跳转位置回调函数
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)") // 设置预览，设备为 Android TV 720p
@Composable
private fun VideoPlayerControllerScreenPreview() { // 定义预览函数
    MyTVTheme { // 使用 MyTVTheme 主题
        PreviewWithLayoutGrids { // 使用布局网格预览
            VideoPlayerControllerScreen( // 渲染视频播放器控制器屏幕
                videoPlayerCurrentPositionProvider = { System.currentTimeMillis() }, // 提供当前时间戳作为播放位置
                videoPlayerDurationProvider = {
                    System.currentTimeMillis() - 1000L * 60 * 60 to System.currentTimeMillis() + 1000L * 60 * 60 // 设置视频时长为1小时
                },
            )
        }
    }
}
