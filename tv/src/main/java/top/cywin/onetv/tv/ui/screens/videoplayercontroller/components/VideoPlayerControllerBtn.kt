package top.cywin.onetv.tv.ui.screens.videoplayercontroller.components

import androidx.compose.runtime.Composable  // 引入Composable注解，表示该函数是一个Composable函数
import androidx.compose.ui.Modifier  // 引入Modifier，用于修改视图的行为和外观
import androidx.compose.ui.graphics.vector.ImageVector  // 引入ImageVector，用于图标的矢量表示
import androidx.tv.material3.Icon  // 引入Icon组件，用于显示图标
import androidx.tv.material3.IconButton  // 引入IconButton组件，用于显示可点击的图标按钮
import top.cywin.onetv.tv.ui.utils.handleKeyEvents  // 引入自定义的handleKeyEvents函数，用于处理键盘事件

// 定义VideoPlayerControllerBtn函数，接收modifier、onSelect回调函数和content Composable内容作为参数
@Composable
fun VideoPlayerControllerBtn(
    modifier: Modifier = Modifier,  // 设置视图的Modifier，默认为空Modifier
    onSelect: () -> Unit = {},  // 定义一个onSelect回调函数，默认不执行任何操作
    content: @Composable () -> Unit,  // content是一个Composable函数，表示按钮的内容
) {
    IconButton(  // 使用IconButton组件创建一个可点击的图标按钮
        modifier = modifier  // 将传入的modifier应用到IconButton上
            .handleKeyEvents(onSelect = onSelect),  // 处理键盘事件，绑定onSelect回调
        onClick = {},  // 这里留空onClick事件，表示点击按钮时不执行任何操作
    ) {
        content()  // 渲染按钮的内容（通过content Composable函数传入的内容）
    }
}

// 定义另一个重载版本的VideoPlayerControllerBtn函数，传入图标和回调函数
@Composable
fun VideoPlayerControllerBtn(
    modifier: Modifier = Modifier,  // 设置视图的Modifier，默认为空Modifier
    imageVector: ImageVector,  // 接收一个ImageVector参数，表示按钮的图标
    onSelect: () -> Unit = {},  // 定义一个onSelect回调函数，默认不执行任何操作
) {
    VideoPlayerControllerBtn(  // 调用上面定义的VideoPlayerControllerBtn函数
        modifier = modifier,  // 传递modifier
        onSelect = onSelect,  // 传递onSelect回调
        content = { Icon(imageVector = imageVector, contentDescription = null) },  // 使用Icon组件显示传入的图标
    )
}
