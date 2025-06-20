package top.cywin.onetv.tv.ui.screens.videoplayer.components

// 导入必要的库
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.cywin.onetv.tv.ui.theme.MyTVTheme

// 定义一个可组合函数，显示视频播放错误信息
@Composable
fun VideoPlayerError(
    modifier: Modifier = Modifier,  // 默认修饰符
    errorProvider: () -> String? = { null },  // 错误信息提供器，默认返回null
) {
    val error = errorProvider() ?: return  // 获取错误信息，如果为null，则不显示

    Column(
        modifier = modifier  // 传入修饰符
            .background(  // 设置背景色和形状
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),  // 设置背景色为主题中的surface颜色，并调整透明度
                shape = MaterialTheme.shapes.medium,  // 设置背景形状为中等圆角
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),  // 设置水平和垂直的内边距
        horizontalAlignment = Alignment.CenterHorizontally,  // 设置内容水平居中
    ) {
        Text(  // 显示错误标题
            text = "播放失败",  // 错误标题文本
            style = MaterialTheme.typography.titleLarge,  // 使用大标题样式
            color = MaterialTheme.colorScheme.error,  // 错误文本颜色
        )

        Text(  // 显示具体错误信息
            text = error,  // 错误信息文本
            style = MaterialTheme.typography.bodyMedium,  // 使用中等体积的正文样式
            color = LocalContentColor.current.copy(alpha = 0.8f),  // 设置文字颜色，稍微调整透明度
        )
    }
}

// 预览函数，用于展示UI效果
@Preview
@Composable
private fun VideoPlayerErrorPreview() {
    MyTVTheme {  // 使用MyTV的主题样式
        VideoPlayerError(  // 调用VideoPlayerError函数并传入错误信息
            errorProvider = { "ERROR_CODE_BEHIND_LIVE_WINDOW" }  // 错误信息为"超出直播窗口"
        )
    }
}
