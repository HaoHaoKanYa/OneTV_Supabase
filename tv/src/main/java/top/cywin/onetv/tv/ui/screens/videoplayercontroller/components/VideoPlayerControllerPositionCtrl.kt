package top.cywin.onetv.tv.ui.screens.videoplayercontroller.components // 定义包路径

import androidx.annotation.IntRange // 导入 IntRange 注解
import androidx.compose.foundation.layout.Arrangement // 导入布局的排列方式
import androidx.compose.foundation.layout.Box // 导入 Box 布局
import androidx.compose.foundation.layout.Row // 导入 Row 布局
import androidx.compose.foundation.layout.height // 导入设置高度的函数
import androidx.compose.foundation.layout.padding // 导入设置内边距的函数
import androidx.compose.foundation.layout.width // 导入设置宽度的函数
import androidx.compose.material.icons.Icons // 导入图标资源
import androidx.compose.material.icons.filled.ChevronLeft // 导入左箭头图标
import androidx.compose.material.icons.filled.ChevronRight // 导入右箭头图标
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft // 导入双左箭头图标
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight // 导入双右箭头图标
import androidx.compose.runtime.Composable // 导入 Composable 注解，用于标记可组合函数
import androidx.compose.runtime.LaunchedEffect // 导入 LaunchedEffect，运行副作用操作
import androidx.compose.runtime.Stable // 导入 Stable，确保稳定性
import androidx.compose.runtime.getValue // 导入获取值的操作符
import androidx.compose.runtime.mutableStateOf // 导入创建可变状态的函数
import androidx.compose.runtime.remember // 导入 remember，记住状态
import androidx.compose.runtime.setValue // 导入设置值的操作符
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入 Modifier，用于修改布局属性
import androidx.compose.ui.tooling.preview.Preview // 导入预览功能
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.tv.material3.Text // 导入 Text 组件
import kotlinx.coroutines.FlowPreview // 导入 FlowPreview 注解
import kotlinx.coroutines.channels.Channel // 导入 Channel，用于协程的信号传递
import kotlinx.coroutines.flow.consumeAsFlow // 导入转换为 Flow
import kotlinx.coroutines.flow.debounce // 导入 debounce，防抖操作
import top.cywin.onetv.tv.ui.material.ProgressBar // 导入进度条组件
import top.cywin.onetv.tv.ui.theme.MyTVTheme // 导入主题
import java.text.SimpleDateFormat // 导入日期格式化类
import java.util.Locale // 导入 Locale 类，用于地区设置
import kotlin.math.max // 导入 max 函数，求最大值
import kotlin.math.min // 导入 min 函数，求最小值

@Composable
fun VideoPlayerControllerPositionCtrl( // 定义视频播放器控制器的位置控制
    modifier: Modifier = Modifier, // 定义可修改的修饰符，默认为空
    currentPositionProvider: () -> Long = { 0L }, // 当前播放位置的提供者，默认为 0
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L }, // 视频总时长的提供者，默认为 0
    onSeekTo: (Long) -> Unit = {}, // 快进到指定位置的回调，默认为空
) {
    var seekToPosition by remember { mutableStateOf<Long?>(null) } // 定义可变的目标位置变量

    val debounce = rememberDebounce( // 创建防抖操作
        wait = 1000L, // 设置等待时间 1 秒
        func = {
            seekToPosition?.let { nnSeekToPosition -> // 如果目标位置不为空
                val startPosition = durationProvider().first // 获取视频开始位置
                onSeekTo(nnSeekToPosition - startPosition) // 调用 onSeekTo 函数进行跳转
                seekToPosition = null // 清空目标位置
            }
        },
    )
    LaunchedEffect(seekToPosition) { // 当 seekToPosition 变化时触发副作用
        if (seekToPosition != null) debounce.active() // 如果目标位置不为空，激活防抖
    }

    fun seekForward(ms: Long) { // 定义快退函数，单位为毫秒
        val currentPosition = currentPositionProvider() // 获取当前播放位置
        val startPosition = durationProvider().first // 获取视频开始位置
        seekToPosition = max(startPosition, (seekToPosition ?: currentPosition) - ms) // 设置目标位置，确保不小于开始位置
    }

    fun seekNext(ms: Long) { // 定义快进函数，单位为毫秒
        val currentPosition = currentPositionProvider() // 获取当前播放位置
        val endPosition = durationProvider().second // 获取视频结束位置
        seekToPosition = min(
            if (endPosition <= 0L) Long.MAX_VALUE else min(endPosition, System.currentTimeMillis()), // 确保目标位置不超过结束位置
            (seekToPosition ?: currentPosition) + ms // 设置目标位置
        )
    }

    Row( // 使用 Row 布局，水平排列
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp), // 元素之间的间距为 8dp
        verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
    ) {
        VideoPlayerControllerBtn( // 渲染按钮组件
            imageVector = Icons.Default.KeyboardDoubleArrowLeft, // 设置按钮图标为双左箭头
            onSelect = { seekForward(1000L * 60 * 10) }, // 点击时快退 10 分钟
        )
        VideoPlayerControllerBtn( // 渲染按钮组件
            imageVector = Icons.Default.ChevronLeft, // 设置按钮图标为左箭头
            onSelect = { seekForward(1000L * 60 * 1) }, // 点击时快退 1 分钟
        )

        VideoPlayerControllerBtn( // 渲染按钮组件
            imageVector = Icons.Default.ChevronRight, // 设置按钮图标为右箭头
            onSelect = { seekNext(1000L * 60 * 1) }, // 点击时快进 1 分钟
        )
        VideoPlayerControllerBtn( // 渲染按钮组件
            imageVector = Icons.Default.KeyboardDoubleArrowRight, // 设置按钮图标为双右箭头
            onSelect = { seekNext(1000L * 60 * 10) }, // 点击时快进 10 分钟
        )

        VideoPlayerControllerPositionProgress( // 渲染进度条组件
            modifier = Modifier.padding(start = 10.dp), // 设置左边距为 10dp
            currentPositionProvider = { seekToPosition ?: currentPositionProvider() }, // 提供当前播放位置
            durationProvider = durationProvider, // 提供视频总时长
        )
    }
}

@Composable
private fun VideoPlayerControllerPositionProgress( // 定义进度条控制器组件
    modifier: Modifier = Modifier, // 修饰符，默认为空
    currentPositionProvider: () -> Long = { 0L }, // 当前播放位置的提供者，默认为 0
    durationProvider: () -> Pair<Long, Long> = { 0L to 0L }, // 视频总时长的提供者，默认为 0
) {
    val currentPosition = currentPositionProvider() // 获取当前播放位置
    val duration = durationProvider() // 获取视频总时长
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // 格式化时间为 HH:mm:ss

    Row( // 使用 Row 布局，水平排列
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp), // 元素之间的间距为 8dp
        verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
    ) {
        Text( // 渲染开始时间文本
            text = timeFormat.format(duration.first),
        )

        ProgressBar( // 渲染进度条
            process = (currentPosition - duration.first) / (duration.second - duration.first).toFloat(), // 计算进度比例
            modifier = Modifier
                .weight(1f) // 占据剩余空间
                .height(6.dp), // 设置高度为 6dp
        )

        Text( // 渲染当前播放时间和总时长文本
            text = "${timeFormat.format(currentPosition)} / ${timeFormat.format(duration.second)}",
        )
    }
}

@Stable
class Debounce internal constructor( // 定义防抖类
    @IntRange(from = 0) private val wait: Long, // 防抖等待时间
    private val func: () -> Unit = {}, // 防抖回调函数
) {
    fun active() { // 激活防抖
        channel.trySend(wait)
    }

    private val channel = Channel<Long>(Channel.CONFLATED) // 定义信号通道

    @OptIn(FlowPreview::class)
    suspend fun observe() { // 观察通道，执行防抖操作
        channel.consumeAsFlow().debounce { it }.collect {
            func()
        }
    }
}

@Composable
fun rememberDebounce( // 创建并记住防抖对象
    @IntRange(from = 0) wait: Long, // 防抖等待时间
    func: () -> Unit = {}, // 防抖回调函数
) = remember { Debounce(wait = wait, func = func) }.also { // 使用 remember 保持对象
    LaunchedEffect(it) { it.observe() } // 启动协程观察防抖效果
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun VideoPlayerControllerPositionCtrlPreview() { // 定义预览函数
    MyTVTheme { // 使用 MyTVTheme 主题
        Box(modifier = Modifier.width(600.dp)) { // 使用 Box 布局并设置宽度为 600dp
            VideoPlayerControllerPositionCtrl( // 渲染 VideoPlayerControllerPositionCtrl 组件
                currentPositionProvider = { System.currentTimeMillis() }, // 提供当前时间作为播放进度
                durationProvider = {
                    System.currentTimeMillis() - 1000L * 60 * 60 to System.currentTimeMillis() + 1000L * 60 * 60 // 提供 1 小时的总时长
                },
            )
        }
    }
}
