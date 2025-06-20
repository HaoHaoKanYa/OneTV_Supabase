package top.cywin.onetv.tv.ui.screens.videoplayercontroller.components // 定义包路径

import androidx.compose.foundation.layout.Arrangement // 导入布局的排列方式
import androidx.compose.foundation.layout.Row // 导入 Row 布局
import androidx.compose.foundation.layout.size // 导入设置大小的函数
import androidx.compose.material.icons.Icons // 导入图标资源
import androidx.compose.material.icons.filled.Pause // 导入暂停图标
import androidx.compose.material.icons.filled.PlayArrow // 导入播放箭头图标
import androidx.compose.runtime.Composable // 导入 Composable 注解，用于标记可组合函数
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入 Modifier，用于修改布局属性
import androidx.compose.ui.graphics.Color // 导入颜色类
import androidx.compose.ui.tooling.preview.Preview // 导入预览功能
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.tv.material3.Icon // 导入图标组件
import androidx.tv.material3.LocalContentColor // 导入当前内容颜色
import top.cywin.onetv.tv.ui.material.CircularProgressIndicator // 导入圆形进度指示器组件
import top.cywin.onetv.tv.ui.theme.MyTVTheme // 导入主题

@Composable
fun VideoPlayerControllerStateCtrl( // 定义视频播放器控制器的状态控制
    modifier: Modifier = Modifier, // 可修改的修饰符，默认为空
    isPlayingProvider: () -> Boolean = { false }, // 播放状态的提供者，默认为 false
    isBufferingProvider: () -> Boolean = { false }, // 缓冲状态的提供者，默认为 false
    onPlay: () -> Unit = {}, // 播放回调函数，默认为空
    onPause: () -> Unit = {}, // 暂停回调函数，默认为空
) {
    val isPlaying = isPlayingProvider() // 获取当前播放状态
    val isBuffering = isBufferingProvider() // 获取当前缓冲状态

    VideoPlayerControllerBtn( // 渲染控制按钮
        modifier = modifier, // 设置修饰符
        onSelect = { // 按钮点击时的处理
            if (!isBuffering) { // 如果当前没有缓冲
                if (isPlaying) onPause() // 如果当前正在播放，执行暂停回调
                else onPlay() // 否则执行播放回调
            }
        },
    ) {
        if (isBuffering) { // 如果正在缓冲
            CircularProgressIndicator( // 显示圆形进度指示器
                modifier = Modifier.size(20.dp), // 设置大小为 20dp
                strokeWidth = 3.dp, // 设置进度条宽度为 3dp
                color = LocalContentColor.current, // 使用当前内容颜色
                trackColor = Color.Transparent, // 设置轨迹颜色为透明
            )
        } else { // 如果不在缓冲状态
            Icon( // 显示播放/暂停图标
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, // 如果正在播放则显示暂停图标，否则显示播放箭头
                contentDescription = null, // 不设置内容描述
            )
        }
    }
}

@Preview // 定义预览
@Composable
private fun VideoPlayerControllerStateCtrlPreview() { // 定义预览函数
    MyTVTheme { // 使用 MyTVTheme 主题

        Row( // 使用 Row 布局，水平排列
            horizontalArrangement = Arrangement.spacedBy(8.dp), // 设置元素之间的间距为 8dp
            verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
        ) {
            VideoPlayerControllerStateCtrl( // 渲染控制器，未播放
                isPlayingProvider = { false },
            )

            VideoPlayerControllerStateCtrl( // 渲染控制器，正在播放
                isPlayingProvider = { true },
            )

            VideoPlayerControllerStateCtrl( // 渲染控制器，正在缓冲
                isBufferingProvider = { true },
            )
        }
    }
}
