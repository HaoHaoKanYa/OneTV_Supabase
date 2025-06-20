package top.cywin.onetv.tv.ui.screens.videoplayer

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import top.cywin.onetv.tv.ui.material.Visible
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.screens.videoplayer.components.VideoPlayerError
import top.cywin.onetv.tv.ui.screens.videoplayer.components.VideoPlayerMetadata
import top.cywin.onetv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids

@Composable
fun VideoPlayerScreen(
    modifier: Modifier = Modifier, // 修饰符，允许外部自定义样式
    state: VideoPlayerState = rememberVideoPlayerState(), // 播放器状态，记住播放器状态
    showMetadataProvider: () -> Boolean = { false }, // 显示元数据的提供者，默认不显示
) {
    val context = LocalContext.current // 获取当前的上下文

    Box(
        modifier = modifier
            .fillMaxSize() // 填充满整个父容器
            .background(Color.Black), // 背景颜色为黑色
    ) {
        // 根据播放器的显示模式设置不同的布局修饰符
        val displayModeModifier = when (state.displayMode) {
            VideoPlayerDisplayMode.ORIGINAL -> Modifier.aspectRatio(state.aspectRatio) // 原始显示模式，保持宽高比
            VideoPlayerDisplayMode.FILL -> Modifier.fillMaxSize() // 填充整个容器
            VideoPlayerDisplayMode.CROP -> Modifier
                .fillMaxWidth() // 宽度填充，保持纵横比
                .aspectRatio(state.aspectRatio)
            VideoPlayerDisplayMode.FOUR_THREE -> Modifier.aspectRatio(4f / 3) // 4:3 比例
            VideoPlayerDisplayMode.SIXTEEN_NINE -> Modifier.aspectRatio(16f / 9) // 16:9 比例
            VideoPlayerDisplayMode.WIDE -> Modifier.aspectRatio(2.35f / 1) // 宽屏比例
        }

        // 使用 AndroidView 组件，将 SurfaceView 嵌入 Jetpack Compose 中
        AndroidView(
            modifier = Modifier
                .align(Alignment.Center) // 在父容器中居中显示
                .then(displayModeModifier), // 添加根据显示模式计算的修饰符
            factory = { SurfaceView(context) }, // 创建一个 SurfaceView 实例
            update = { state.setVideoSurfaceView(it) }, // 更新 SurfaceView，绑定到播放器状态
        )
    }

    // 在播放器界面上层显示错误或元数据信息
    VideoPlayerScreenCover(
        showMetadataProvider = showMetadataProvider,
        metadataProvider = state::metadata,
        errorProvider = state::error,
    )
}

@Composable
private fun VideoPlayerScreenCover(
    modifier: Modifier = Modifier, // 修饰符
    showMetadataProvider: () -> Boolean = { false }, // 是否显示元数据
    metadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() }, // 元数据提供者
    errorProvider: () -> String? = { null }, // 错误信息提供者
) {
    val childPadding = rememberChildPadding() // 获取子元素的内边距

    Box(modifier = modifier.fillMaxSize()) {
        // 如果需要显示元数据，则显示 VideoPlayerMetadata 组件
        Visible(showMetadataProvider) {
            VideoPlayerMetadata(
                modifier = Modifier.padding(start = childPadding.start, top = childPadding.top), // 设置元数据的内边距
                metadataProvider = metadataProvider, // 元数据提供者
            )
        }

        // 显示错误信息组件
        VideoPlayerError(
            modifier = Modifier.align(Alignment.Center), // 在中心位置显示错误信息
            errorProvider = errorProvider, // 错误信息提供者
        )
    }
}

@Preview(device = "id:Android TV (720p)") // 预览组件，显示在 720p Android TV 设备上
@Composable
private fun VideoPlayerScreenCoverPreview() {
    MyTVTheme { // 使用自定义主题
        PreviewWithLayoutGrids { // 显示布局网格
            VideoPlayerScreenCover(
                showMetadataProvider = { true }, // 强制显示元数据
                metadataProvider = { VideoPlayer.Metadata() }, // 提供默认的元数据
                errorProvider = { "ERROR_CODE_BEHIND_LIVE_WINDOW" } // 提供错误信息
            )
        }
    }
}
