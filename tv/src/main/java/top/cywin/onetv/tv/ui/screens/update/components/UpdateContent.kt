package top.cywin.onetv.tv.ui.screens.update.components  // 导入所需的包

import androidx.compose.foundation.layout.Arrangement  // 导入布局控制器，控制排列方式
import androidx.compose.foundation.layout.Column  // 导入垂直排列布局
import androidx.compose.foundation.layout.Row  // 导入水平排列布局
import androidx.compose.foundation.layout.fillMaxSize  // 导入填充满整个父容器的布局
import androidx.compose.foundation.layout.padding  // 导入控制内边距的布局
import androidx.compose.foundation.layout.width  // 导入设置宽度的布局
import androidx.compose.foundation.lazy.LazyColumn  // 导入懒加载的垂直列表
import androidx.compose.runtime.Composable  // 导入可组合函数（Compose函数）
import androidx.compose.ui.Modifier  // 导入修饰符，用来修改组件的外观或行为
import androidx.compose.ui.tooling.preview.Preview  // 导入预览工具
import androidx.compose.ui.unit.dp  // 导入定义尺寸的单位dp
import androidx.tv.material3.MaterialTheme  // 导入 TV 风格的 Material 设计主题
import androidx.tv.material3.Text  // 导入文本组件
import androidx.tv.material3.WideButton  // 导入宽大的按钮组件
import top.cywin.onetv.core.data.entities.git.GitRelease  // 导入GitRelease数据实体类
import top.cywin.onetv.tv.ui.theme.MyTVTheme  // 导入应用的主题
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids  // 导入带布局网格的预览工具
import top.cywin.onetv.tv.ui.utils.customBackground  // 导入自定义背景的工具函数
import top.cywin.onetv.tv.ui.utils.focusOnLaunched  // 导入设置焦点的工具函数
import top.cywin.onetv.tv.ui.utils.handleKeyEvents  // 导入处理按键事件的工具函数

@Composable  // 标记该函数为Compose函数
fun UpdateContent(  // 定义更新内容的可组合函数
    modifier: Modifier = Modifier,  // 修饰符，用来修改布局外观
    onDismissRequest: () -> Unit = {},  // 点击忽略按钮时的回调
    releaseProvider: () -> GitRelease = { GitRelease() },  // 获取 GitRelease 数据的提供者
    isUpdateAvailableProvider: () -> Boolean = { false },  // 检查是否有更新的提供者
    onUpdateAndInstall: () -> Unit = {},  // 点击更新按钮后的回调
) {
    val release = releaseProvider()  // 获取当前的 GitRelease 信息

    Row(  // 使用 Row 布局将内容水平排列
        modifier = modifier  // 设置布局修饰符
            .fillMaxSize()  // 填充满整个父容器
            .customBackground()  // 设置自定义背景
            .padding(horizontal = 130.dp, vertical = 88.dp),  // 设置内边距
        horizontalArrangement = Arrangement.SpaceBetween,  // 水平排列时，子组件之间的间距
    ) {
        Column(  // 使用 Column 布局将内容垂直排列
            modifier = Modifier.width(340.dp),  // 设置 Column 宽度
            verticalArrangement = Arrangement.spacedBy(16.dp),  // 垂直排列时，子组件之间的间距
        ) {
            Text(  // 显示当前版本号
                "最新版本: v${release.version}",  // 显示版本号
                style = MaterialTheme.typography.headlineMedium  // 使用主题中的标题样式
            )

            LazyColumn {  // 使用 LazyColumn 显示可滚动的列表
                item {  // 列表项
                    Text(release.description, style = MaterialTheme.typography.bodyLarge)  // 显示更新日志
                }
            }
        }

        if (isUpdateAvailableProvider()) {  // 如果有可用更新
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  // 垂直排列更新操作按钮
                WideButton(  // 立即更新按钮
                    modifier = Modifier
                        .focusOnLaunched()  // 聚焦此按钮
                        .handleKeyEvents(onSelect = onUpdateAndInstall),  // 处理按键事件
                    onClick = { },  // 按钮点击事件
                    title = { Text("立即更新") },  // 按钮文本
                )

                WideButton(  // 忽略按钮
                    modifier = Modifier.handleKeyEvents(onSelect = onDismissRequest),  // 处理按键事件
                    onClick = { },  // 按钮点击事件
                    title = { Text("忽略") },  // 按钮文本
                )
            }
        } else {  // 如果没有可用更新
            WideButton(  // 当前为最新版本按钮
                modifier = Modifier
                    .focusOnLaunched()  // 聚焦此按钮
                    .handleKeyEvents(onSelect = onDismissRequest),  // 处理按键事件
                onClick = { },  // 按钮点击事件
                title = { Text("当前为最新版本") },  // 按钮文本
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")  // 预览，设置为720p电视设备
@Composable  // 标记为 Compose 函数
private fun UpdateDialogPreview() {  // 预览用的更新对话框
    MyTVTheme {  // 使用 MyTV 主题
        PreviewWithLayoutGrids {  // 使用布局网格进行预览
            UpdateContent(  // 显示 UpdateContent 组件
                releaseProvider = {  // 提供一个模拟的 GitRelease 数据
                    GitRelease(
                        version = "1.0.0",  // 版本号
                        downloadUrl = "",  // 下载链接为空
                        description = "更新日志".repeat(100),  // 更新日志重复100次
                    )
                },
            )
        }
    }
}
