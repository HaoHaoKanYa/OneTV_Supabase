package top.cywin.onetv.tv.ui.screens.classicchannel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import kotlin.math.max

// 其他项目中已有的导入
import top.cywin.onetv.core.data.entities.channel.Channel
import top.cywin.onetv.core.data.entities.channel.ChannelGroup
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelGroupIdx
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.cywin.onetv.core.data.entities.channel.ChannelList
import top.cywin.onetv.core.data.entities.epg.EpgList
import top.cywin.onetv.core.data.entities.epg.EpgList.Companion.match
import top.cywin.onetv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.cywin.onetv.core.data.entities.epg.EpgProgramme
import top.cywin.onetv.core.data.entities.epg.EpgProgrammeReserveList
import top.cywin.onetv.tv.ui.material.Visible
import top.cywin.onetv.tv.ui.screens.channel.ChannelScreenTopRight
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelInfo
import top.cywin.onetv.tv.ui.screens.channel.components.ChannelUrlPanel
import top.cywin.onetv.tv.ui.screens.classicchannel.components.ClassicChannelGroupItemList
import top.cywin.onetv.tv.ui.screens.classicchannel.components.ClassicChannelItemList
import top.cywin.onetv.tv.ui.screens.classicchannel.components.ClassicEpgItemList
import top.cywin.onetv.tv.ui.screens.components.rememberScreenAutoCloseState
import top.cywin.onetv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.cywin.onetv.tv.ui.theme.MyTVTheme

import top.cywin.onetv.tv.ui.screens.quickop.components.QuickOpBtn
import top.cywin.onetv.tv.ui.screens.guide.components.GuideTvRemoteKeys
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.handleKeyEvents
import top.cywin.onetv.tv.R // 确保R文件路径正确
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories
import androidx.compose.ui.platform.LocalContext

/**
 * 个人中心按钮组件，采用动态流光背景和圆角效果，并根据用户类型显示不同的 VIP 图标
 * 
 * @param onClick 点击事件回调
 * @param modifier Modifier修饰符
 * @param buttonHeight 按钮高度
 * @param userType 用户类型：0=游客，1=普通注册会员，2=VIP会员
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PersonalCenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: Dp = 56.dp,
    userType: Int = 0 // 默认为游客
) {
    // 无限动画实现流光效果
    val infiniteTransition = rememberInfiniteTransition()
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // VIP标签脉动动画
    val vipPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f, // 减小动画幅度，避免文字换行
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // 定义多彩渐变颜色
    val gradientColors = listOf(
        Color(0xFF4CAF50),
        Color(0xFFFFC107),
        Color(0xFFE91E63),
        Color(0xFF03A9F4),
        Color(0xFF9C27B0)
    )

    // 根据动画偏移构建动态背景 Brush
    val dynamicBrush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(x = animatedOffset, y = 0f),
        end = Offset(x = animatedOffset + 300f, y = 300f)
    )
    
    // 根据用户类型决定VIP标签的颜色
    val vipColor = when (userType) {
        2 -> Color(0xFFFFD700) // VIP 用户 - 金色
        1 -> Color(0xFFC0C0C0) // 普通会员 - 银色
        else -> Color(0xFF9E9E9E) // 游客 - 暗淡色
    }
    
    // 光晕效果颜色和强度根据用户类型变化
    val glowColor = when (userType) {
        2 -> Color(0xFFFFD700).copy(alpha = 0.6f) // VIP 用户 - 金色光晕
        1 -> Color(0xFFC0C0C0).copy(alpha = 0.4f) // 普通会员 - 银色光晕
        else -> Color(0xFF9E9E9E).copy(alpha = 0.2f) // 游客 - 几乎没有光晕
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .focusable(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .background(dynamicBrush)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .width(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用彩色"VIP"文本替代图标
            Box(
                modifier = Modifier.size(32.dp), // 减小VIP图标区域尺寸
                contentAlignment = Alignment.Center
            ) {
                // 底层光晕效果（只对VIP和普通会员显示）
                if (userType > 0) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowColor, Color.Transparent),
                                    center = Offset.Unspecified,
                                    radius = 30f
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
                
                // VIP用户文本动画缩放效果
                val textModifier = if (userType == 2) {
                    // 计算基于脉动动画的大小变化
                    val size = (22 * vipPulse).sp
                    Modifier
                } else {
                    Modifier
                }
                
                // 使用固定宽度的容器确保文字不会因脉动导致换行
                Box(
                    modifier = Modifier.width(32.dp).height(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 恢复使用整体VIP文本，取消跳动效果
                    if (userType > 0) {
                        // 阴影层
                        Text(
                            text = "VIP",
                            fontSize = 14.sp, // 减小字体大小
                            fontWeight = FontWeight.Black,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.offset(1.dp, 1.dp),
                            color = Color.Black.copy(alpha = 0.5f)
                        )
                    }
                    
                    // VIP文本主体
                    Text(
                        text = "VIP",
                        fontSize = 14.sp, // 减小字体大小
                        fontWeight = FontWeight.Black,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center
                        ),
                        color = vipColor
                    )
                }
                
                // 如果是VIP用户，添加额外的装饰效果（移除了皇冠图标）
                if (userType == 2) {
                    // 闪亮星星效果
                    Box(modifier = Modifier
                        .size(6.dp)
                        .offset(8.dp, (-9).dp)
                        .background(Color(0xFFFFD700), shape = RoundedCornerShape(6.dp))
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "个人中心",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp // 减小字体大小
                )
            )
        }
    }
}

val ClassicPanelScreenFavoriteChannelGroup = ChannelGroup(name = "我的收藏")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClassicChannelScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    currentChannelProvider: () -> Channel = { Channel() },
    currentChannelUrlIdxProvider: () -> Int = { 0 },
    showChannelLogoProvider: () -> Boolean = { false },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    epgListProvider: () -> EpgList = { EpgList() },
    epgProgrammeReserveListProvider: () -> EpgProgrammeReserveList = { EpgProgrammeReserveList() },
    showEpgProgrammeProgressProvider: () -> Boolean = { false },
    onEpgProgrammePlayback: (Channel, EpgProgramme) -> Unit = { _, _ -> },
    onEpgProgrammeReserve: (Channel, EpgProgramme) -> Unit = { _, _ -> },
    isInTimeShiftProvider: () -> Boolean = { false },
    supportPlaybackProvider: (Channel) -> Boolean = { false },
    currentPlaybackEpgProgrammeProvider: () -> EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },
    channelFavoriteEnabledProvider: () -> Boolean = { false },
    channelFavoriteListVisibleProvider: () -> Boolean = { false },
    onChannelFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onClose: () -> Unit = {},
    onShowMoreSettings: () -> Unit = {},
    onNavigateToSettingsCategory: ((SettingsCategories) -> Unit)? = null,
    // 多线路相关参数
    showChannelUrlListProvider: () -> Boolean = { false },
    onChannelUrlSelected: (String) -> Unit = {},
) {
    val screenAutoCloseState = rememberScreenAutoCloseState(onTimeout = onClose)
    val channelGroupList = channelGroupListProvider()
    val channelFavoriteListVisible = remember { channelFavoriteListVisibleProvider() }
    val context = LocalContext.current

    // 获取用户类型
    var userType by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val userData = withContext(Dispatchers.IO) {
            SupabaseSessionManager.getCachedUserData(context)
        }
        userType = if (userData != null) {
            if (userData.is_vip) 2 // VIP用户
            else 1 // 普通注册会员
        } else {
            0 // 游客
        }
    }

    var focusedChannelGroup by remember {
        mutableStateOf(
            if (channelFavoriteListVisible)
                ClassicPanelScreenFavoriteChannelGroup
            else
                channelGroupList[max(0, channelGroupList.channelGroupIdx(currentChannelProvider()))]
        )
    }
    var focusedChannel by remember { mutableStateOf(currentChannelProvider()) }
    var epgListVisible by remember { mutableStateOf(false) }

    var groupWidth by remember { mutableIntStateOf(0) }
    val offsetXPx by animateIntAsState(
        targetValue = if (epgListVisible) -groupWidth else 0,
        animationSpec = tween(),
        label = ""
    )

    ClassicChannelScreenWrapper(
        modifier = modifier.offset { IntOffset(x = offsetXPx, y = 0) },
        onClose = onClose,
    ) {
        Row {
            // 左侧区域：包含个人中心按钮与频道组列表
            Column {
                PersonalCenterButton(
                    onClick = { 
                        // 修改为导航到个人中心设置项
                        onNavigateToSettingsCategory?.invoke(SettingsCategories.PROFILE) 
                            ?: onShowMoreSettings()
                    },
                    modifier = if (groupWidth > 0)
                        Modifier.width(with(LocalDensity.current) { groupWidth.toDp() })
                    else Modifier,
                    userType = userType // 传递用户类型
                )
                ClassicChannelGroupItemList(
                    modifier = Modifier
                        .onSizeChanged { groupWidth = it.width }
                        .clip(MaterialTheme.shapes.medium),
                    channelGroupListProvider = {
                        if (channelFavoriteEnabledProvider())
                            ChannelGroupList(listOf(ClassicPanelScreenFavoriteChannelGroup) + channelGroupList)
                        else
                            channelGroupList
                    },
                    initialChannelGroupProvider = {
                        if (channelFavoriteListVisible)
                            ClassicPanelScreenFavoriteChannelGroup
                        else
                            channelGroupList.find { it.channelList.contains(currentChannelProvider()) }
                                ?: ChannelGroup()
                    },
                    onChannelGroupFocused = {
                        focusedChannelGroup = it
                        onChannelFavoriteListVisibleChange(it == ClassicPanelScreenFavoriteChannelGroup)
                    },
                    onUserAction = { screenAutoCloseState.active() }
                )
            }
            // 右侧区域：频道列表（贴合左侧区域，左上角圆角）
            ClassicChannelItemList(
                modifier =
                    Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .focusProperties {
                        exit = {
                            if (epgListVisible && it == FocusDirection.Left) {
                                epgListVisible = false
                                FocusRequester.Cancel
                            } else if (!epgListVisible && it == FocusDirection.Right) {
                                epgListVisible = true
                                FocusRequester.Cancel
                            } else {
                                FocusRequester.Default
                            }
                        }
                    },
                channelGroupProvider = { focusedChannelGroup },
                channelListProvider = {
                    if (focusedChannelGroup == ClassicPanelScreenFavoriteChannelGroup)
                        favoriteChannelListProvider()
                    else
                        focusedChannelGroup.channelList
                },
                epgListProvider = epgListProvider,
                initialChannelProvider = currentChannelProvider,
                onChannelSelected = onChannelSelected,
                onChannelFavoriteToggle = onChannelFavoriteToggle,
                onChannelFocused = { channel -> focusedChannel = channel },
                showEpgProgrammeProgressProvider = showEpgProgrammeProgressProvider,
                onUserAction = { screenAutoCloseState.active() },
                inFavoriteModeProvider = { focusedChannelGroup == ClassicPanelScreenFavoriteChannelGroup },
                showChannelLogoProvider = showChannelLogoProvider
            )

            // 多线路组件 - 放在频道列表和EPG之间
            if (showChannelUrlListProvider()) {
                ChannelUrlPanel(
                    modifier = Modifier.width(320.dp),
                    channelProvider = currentChannelProvider,
                    currentUrlProvider = { currentChannelProvider().urlList[currentChannelUrlIdxProvider()] },
                    onUrlSelected = onChannelUrlSelected,
                    onUserAction = { screenAutoCloseState.active() },
                )
            }

            Visible({ epgListVisible }) {
                ClassicEpgItemList(
                    epgProvider = { epgListProvider().match(focusedChannel) },
                    epgProgrammeReserveListProvider = {
                        EpgProgrammeReserveList(
                            epgProgrammeReserveListProvider().filter { it.channel == focusedChannel.name }
                        )
                    },
                    supportPlaybackProvider = { supportPlaybackProvider(focusedChannel) },
                    currentPlaybackEpgProgrammeProvider = currentPlaybackEpgProgrammeProvider,
                    onEpgProgrammePlayback = { onEpgProgrammePlayback(focusedChannel, it) },
                    onEpgProgrammeReserve = { onEpgProgrammeReserve(focusedChannel, it) },
                    onUserAction = { screenAutoCloseState.active() }
                )
            }
            Visible({ !epgListVisible }) {
                ClassicPanelScreenShowEpgTip(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(0.2f))//向右查看节目单透明度设置
                        .padding(horizontal = 4.dp)
                        .focusable(),
                    onTap = { epgListVisible = true },
                )
            }
        }
    }

    ChannelScreenTopRight(
        channelNumberProvider = {
            (channelGroupListProvider().channelIdx(currentChannelProvider()) + 1)
                .toString()
                .padStart(2, '0')
        },
    )
     //右下角频道显示
    Visible({ !epgListVisible }) {
        Box(Modifier.fillMaxSize()) {
            ChannelInfo(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.5f)
                    .padding(24.dp)
                    .background(
                        Color.Black.copy(alpha = 0f),  // 改为透明黑色
                        MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                channelProvider = currentChannelProvider,
                channelUrlIdxProvider = currentChannelUrlIdxProvider,
                recentEpgProgrammeProvider = {
                    epgListProvider().recentProgramme(currentChannelProvider())
                },
                isInTimeShiftProvider = isInTimeShiftProvider,
                currentPlaybackEpgProgrammeProvider = currentPlaybackEpgProgrammeProvider,
                videoPlayerMetadataProvider = videoPlayerMetadataProvider,
                dense = true,
            )
        }
    }
}

@Composable
private fun ClassicChannelScreenWrapper(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
    ) {
        Box(
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(24.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            content()
        }
    }
}

@Composable
private fun ClassicPanelScreenShowEpgTip(
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        "向右查看节目单".map {
            Text(text = it.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
fun ClassicChannelScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            ClassicChannelScreen(
                channelGroupListProvider = { ChannelGroupList.EXAMPLE },
                currentChannelProvider = { ChannelGroupList.EXAMPLE.first().channelList.first() },
                epgListProvider = { EpgList.example(ChannelGroupList.EXAMPLE.channelList) },
                showEpgProgrammeProgressProvider = { true },
                onShowMoreSettings = {},
                onNavigateToSettingsCategory = {}
            )
        }
    }
}
