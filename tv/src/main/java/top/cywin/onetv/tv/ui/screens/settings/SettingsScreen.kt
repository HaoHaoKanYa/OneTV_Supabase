package top.cywin.onetv.tv.ui.screens.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.screens.settings.components.SettingsCategoryContent
import top.cywin.onetv.tv.ui.screens.settings.components.SettingsCategoryList
import top.cywin.onetv.tv.ui.utils.captureBackKey
import top.cywin.onetv.tv.ui.utils.customBackground

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import top.cywin.onetv.tv.LoginActivity
// 导入动画相关
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap

// ... 现有导入保持不变 ...

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    onClose: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateToLogin: () -> Unit = {},
    initialCategory: SettingsCategories = SettingsCategories.USER
) {
    val childPadding = rememberChildPadding()
    // 记住当前选中的类别，初始值使用传入的initialCategory
    var currentCategory by remember { mutableStateOf(initialCategory) } 
    // 记住当前悬停的类别（光标移动到的类别）
    var hoverCategory by remember { mutableStateOf(initialCategory) }
    // 记住是否已确认选择类别
    var isConfirmed by remember { mutableStateOf(true) } // 由于有初始值，设为true
    val context = LocalContext.current

    // 创建无限动画过渡
    val infiniteTransition = rememberInfiniteTransition(label = "infiniteTransition")
    // 动画值，用于移动渐变
    val animatedOffset = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animatedOffset"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            settingsViewModel.refresh()
        }
    }

    // 定义导航到特定类别的函数
    val navigateToCategory: (SettingsCategories) -> Unit = { category ->
        currentCategory = category
        isConfirmed = true
        hoverCategory = category
    }

    // 外层Box添加流动多彩边框
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp) // 减小外边距，从16dp调整为8dp
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4285F4), // 蓝色
                        Color(0xFF34A853), // 绿色
                        Color(0xFFFBBC05), // 黄色
                        Color(0xFFEA4335), // 红色
                        Color(0xFF4285F4)  // 回到蓝色，形成循环
                    ),
                    // 使用动画值创建流动效果
                    start = Offset(animatedOffset.value % 1000f, 0f),
                    end = Offset(0f, animatedOffset.value % 1000f)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = modifier
                .captureBackKey { onClose() }
                .pointerInput(Unit) { detectTapGestures { } }
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(4.dp), // 减小内边距，从start/end=childPadding调整为4dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 上方的设置内容区域，高度自适应
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp)  // 减小底部内边距，为分隔线腾出空间
                ) {
                    // 显示当前悬停或已确认的类别内容
                    SettingsCategoryContent(
                        currentCategoryProvider = { if (isConfirmed) currentCategory else hoverCategory },
                        channelGroupListProvider = channelGroupListProvider,
                        onNavigateToLogin = {
                            context.startActivity(Intent(context, LoginActivity::class.java))
                        },
                        onNavigateToCategory = navigateToCategory // 传递导航回调
                    )
                }

                // 添加动态多彩分隔线
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 创建彩色渐变
                    val gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4285F4), // 蓝色
                            Color(0xFF34A853), // 绿色
                            Color(0xFFFBBC05), // 黄色
                            Color(0xFFEA4335), // 红色
                            Color(0xFF4285F4)  // 回到蓝色，形成循环
                        ),
                        // 使用动画值创建流动效果
                        start = Offset(animatedOffset.value % canvasWidth, 0f),
                        end = Offset(animatedOffset.value % canvasWidth + canvasWidth, 0f)
                    )

                    // 绘制线条
                    drawLine(
                        brush = gradient,
                        start = Offset(0f, canvasHeight / 2),
                        end = Offset(canvasWidth, canvasHeight / 2),
                        strokeWidth = canvasHeight,
                        cap = StrokeCap.Round
                    )
                }

                // 底部的设置类别列表，固定高度，添加底部内边距确保在屏幕内
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .padding(bottom = 8.dp) // 减小底部内边距，从16dp调整为8dp
                ) {
                    SettingsCategoryList(
                        currentCategoryProvider = { currentCategory },
                        onCategoryHover = { category ->
                            hoverCategory = category
                        },
                        onCategorySelected = navigateToCategory // 使用同一个导航函数
                    )
                }
            }
        }
    }
}