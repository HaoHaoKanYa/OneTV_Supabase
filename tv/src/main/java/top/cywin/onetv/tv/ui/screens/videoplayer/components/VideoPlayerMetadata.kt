package top.cywin.onetv.tv.ui.screens.videoplayer.components

// 导入必要的库
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.cywin.onetv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.cywin.onetv.tv.ui.theme.MyTVTheme

// 定义一个可组合函数，用于显示视频播放器的元数据
@Composable
fun VideoPlayerMetadata(
    modifier: Modifier = Modifier,  // 默认修饰符
    metadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },  // 元数据提供器，默认返回空的VideoPlayer.Metadata对象
) {
    val metadata = metadataProvider()  // 获取元数据

    // 使用CompositionLocalProvider来提供局部环境数据，如文本样式和颜色
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.bodySmall,  // 设置文本样式为小号正文
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,  // 设置内容颜色为主题中的“表面颜色”
    ) {
        Column(  // 使用Column布局来垂直排列元数据内容
            modifier = modifier  // 传入修饰符
                .background(  // 设置背景色和形状
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),  // 设置背景色为主题中的surface颜色，并调整透明度
                    MaterialTheme.shapes.medium,  // 设置背景形状为中等圆角
                )
                .padding(8.dp),  // 设置内边距
            verticalArrangement = Arrangement.spacedBy(10.dp)  // 设置垂直排列之间的间距
        ) {
            // 视频信息区域
            Column {
                Text("视频", style = MaterialTheme.typography.titleMedium)  // 显示标题"视频"并设置样式为中等标题
                Column(modifier = Modifier.padding(start = 10.dp)) {  // 嵌套Column用于显示具体视频元数据
                    Text("编码: ${metadata.videoMimeType}")  // 显示视频编码类型
                    Text("解码器: ${metadata.videoDecoder}")  // 显示视频解码器
                    Text("分辨率: ${metadata.videoWidth}x${metadata.videoHeight}")  // 显示视频分辨率
                    Text("色彩: ${metadata.videoColor}")  // 显示视频色彩信息
                    Text("帧率: ${metadata.videoFrameRate}")  // 显示视频帧率
                    Text("比特率: ${metadata.videoBitrate / 1024} kbps")  // 显示视频比特率，单位为kbps
                }
            }

            // 音频信息区域
            Column {
                Text("音频", style = MaterialTheme.typography.titleMedium)  // 显示标题"音频"并设置样式为中等标题
                Column(modifier = Modifier.padding(start = 10.dp)) {  // 嵌套Column用于显示具体音频元数据
                    Text("编码: ${metadata.audioMimeType}")  // 显示音频编码类型
                    Text("解码器: ${metadata.audioDecoder}")  // 显示音频解码器
                    Text("声道数: ${metadata.audioChannels}")  // 显示音频声道数
                    Text("采样率: ${metadata.audioSampleRate} Hz")  // 显示音频采样率，单位为Hz
                }
            }
        }
    }
}

// 预览函数，用于在IDE中预览UI效果
@Preview
@Composable
private fun VideoMetadataPreview() {
    MyTVTheme {  // 使用MyTV的主题样式
        VideoPlayerMetadata(  // 调用VideoPlayerMetadata函数并传入示例元数据
            metadataProvider = {
                VideoPlayer.Metadata(  // 创建示例元数据对象
                    videoWidth = 1920,  // 视频宽度
                    videoHeight = 1080,  // 视频高度
                    videoMimeType = "video/hevc",  // 视频编码格式
                    videoColor = "BT2020/Limited range/HLG/8/8",  // 视频色彩信息
                    videoFrameRate = 25.0f,  // 视频帧率
                    videoBitrate = 10605096,  // 视频比特率
                    videoDecoder = "c2.goldfish.h264.decoder",  // 视频解码器

                    audioMimeType = "audio/mp4a-latm",  // 音频编码格式
                    audioChannels = 2,  // 音频声道数
                    audioSampleRate = 32000,  // 音频采样率
                    audioDecoder = "c2.android.aac.decoder",  // 音频解码器
                )
            }
        )
    }
}
