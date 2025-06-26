package top.cywin.onetv.tv.ui.screens.settings.components

// 导入必要的Compose、UI、协程以及其他模块
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import top.cywin.onetv.core.data.repositories.user.SessionManager
import top.cywin.onetv.core.data.repositories.user.UserRepository
import top.cywin.onetv.core.data.repositories.user.UserDataIptv
import top.cywin.onetv.tv.ui.screens.main.MainViewModel
// 导入
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.cywin.onetv.tv.R
import top.cywin.onetv.tv.ui.material.CircularProgressIndicator
// 新增导入用于动画效果
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size

import androidx.compose.ui.focus.onFocusChanged

// ... 其他现有导入 ...
import androidx.compose.ui.draw.scale  // 添加这个导入用于scale修饰符

import top.cywin.onetv.core.data.repositories.user.ServiceInfoManager
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.google.gson.Gson
import kotlinx.coroutines.delay
import top.cywin.onetv.core.data.repositories.user.OnlineUsersData
import top.cywin.onetv.core.data.repositories.user.OnlineUsersSessionManager
import java.util.Calendar
// 在文件顶部导入新增的缓存策略方法
// 替换原有的错误导入
import top.cywin.onetv.core.data.repositories.user.shouldForceRefresh
// 添加 SupabaseLoginActivity 和 SupabaseUserProfileActivity 的导入
import android.content.Intent
import top.cywin.onetv.tv.supabase.SupabaseLoginActivity
import top.cywin.onetv.tv.supabase.SupabaseUserProfileActivity
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserRepository
import top.cywin.onetv.core.data.repositories.supabase.SupabaseServiceInfoManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseOnlineUsersData
import top.cywin.onetv.core.data.repositories.supabase.SupabaseOnlineUsersSessionManager
// 导入新的缓存管理器相关类
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheStrategy
// 导入SettingsCategories和ViewModel
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories
import top.cywin.onetv.tv.ui.screens.settings.SettingsViewModel


private fun formatBeijingTime(time: Long): String {
    if (time <= 0) return "未记录" // 新增判断逻辑
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(time))
}



/**
 * 主界面：显示账户信息和服务器信息
 *
 * @param onNavigateToLogin 登录/跳转回调
 * @param serverInfoHeight 服务器信息框高度（单位：dp）
 * @param serverTextColor 服务器信息文字颜色
 * @param serverTextSize 服务器信息文字大小（单位：sp）
 * @param userInfoContentPadding 用户信息框内部边距（单位：dp），用于调整上下边距
 * @param onNavigateToProfile 导航到个人中心的回调，新增参数
 */
@Composable
fun SettingsCategoryUser(
    onNavigateToLogin: () -> Unit,
    serverTextColor: Color = Color(0xFF2196F3),
    serverTextSize: Int = 14,
    userInfoContentPadding: Int = 8,
    onNavigateToProfile: (() -> Unit)? = null // 新增可选参数
) {
    // 获取主界面ViewModel
    val mainViewModel: MainViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val context = LocalContext.current
    // 获取当前用户会话信息，直接使用SupabaseCacheManager
    var session by remember { mutableStateOf<String?>(null) }
    var userData by remember { mutableStateOf<SupabaseUserDataIptv?>(null) }
    var showImage by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 监听 Toast 消息
    LaunchedEffect(Unit) {
        mainViewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    BackHandler(enabled = showImage) {
        showImage = false
    }
    
    DonationImageDialog(
        showImage = showImage,
        onDismiss = { showImage = false }
    )
    
    // 使用标准缓存加载方式
    LaunchedEffect(Unit) {
        try {
            // 获取会话数据
            session = SupabaseCacheManager.getCache(context, SupabaseCacheKey.SESSION)
            
            // 安全获取用户数据：先获取原始数据，然后进行安全转换
            val rawUserData = SupabaseCacheManager.getRawCache(context, SupabaseCacheKey.USER_DATA)
            if (rawUserData != null) {
                // 使用安全转换方法
                userData = SupabaseCacheManager.safeConvertToUserData(rawUserData)
                Log.d("SettingsCategoryUser", "用户数据安全转换${if (userData != null) "成功: ${userData?.username}" else "失败"}")
            } else {
                Log.d("SettingsCategoryUser", "未找到用户数据缓存")
            }
            
            Log.d("SettingsCategoryUser",
                "[缓存加载] userId=${userData?.userid ?: "空"}｜" +
                        "VIP状态：${userData?.is_vip ?: false}")
        } catch (e: Exception) {
            Log.e("SettingsCategoryUser", "加载缓存数据异常: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    // 根据会话变化检测是否需要刷新数据，使用统一缓存策略
    LaunchedEffect(session) {
        // 如果有会话但没有用户数据，尝试从服务器加载
        if (session != null) {
            // 检查缓存是否有效
            val isCacheValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.USER_DATA)
            
            Log.d("SettingsCategoryUser", "缓存检查 - 是否有效：$isCacheValid")
            
            // 若缓存无效或不存在，则从服务器加载最新数据
            if (userData == null || !isCacheValid) {
                session?.let {
                    isLoading = true
                    try {
                        Log.d("SettingsCategoryUser", "开始从服务器加载用户数据...")
                        // 使用SupabaseUserRepository
                        val newData = withContext(Dispatchers.IO) {
                            SupabaseUserRepository().getUserData(it).also { data ->
                                Log.d("SettingsCategoryUser", "服务器数据获取成功，开始缓存｜userId=${data.userid}")
                                
                                // 使用SupabaseCacheManager保存用户数据
                                SupabaseCacheManager.saveCache(
                                    context = context,
                                    key = SupabaseCacheKey.USER_DATA,
                                    data = data,
                                    strategy = SupabaseCacheManager.getUserCacheStrategy(data)
                                )
                                
                                Log.d("SettingsCategoryUser", "缓存保存完成｜数据长度：${Gson().toJson(data).length}")
                            }
                        }
                        userData = newData
                    } catch (e: Exception) {
                        Log.e("SettingsCategoryUser", "数据加载异常: ${e.javaClass.simpleName} - ${e.message}", e)
                        // 尝试使用缓存数据
                        val cachedData = SupabaseCacheManager.getCache<SupabaseUserDataIptv>(context, SupabaseCacheKey.USER_DATA)
                        if (cachedData != null) {
                            userData = cachedData
                            Log.w("SettingsCategoryUser", "使用最后一次有效缓存数据：${cachedData.userid}")
                        }
                        // 401错误处理
                        if (e.message?.contains("401") == true) {
                            Log.w("SettingsCategoryUser", "检测到会话过期，触发全局清理")
                            mainViewModel.clearAllCache(true) {
                                Log.d("SettingsCategoryUser", "清理完成，开始强制刷新数据")
                                mainViewModel.forceRefreshUserData()
                            }
                        }
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                Log.d("SettingsCategoryUser", "使用本地缓存数据: ${userData?.userid}")
            }
        }
    }

    // 创建无限动画过渡效果用于分隔线
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


    // 使用水平Row布局整个界面 - 修改为左右排版
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 账户信息区域，占用60%宽度
        Box(modifier = Modifier
            .weight(0.5f)
            .fillMaxHeight()
        ) {
            if (session == null || userData == null) {
                // 未登录状态下显示登录提示界面
                UnloggedContent(onNavigateToLogin, userInfoContentPadding)
            } else {
                // 已登录状态下显示用户详细信息
                UserInfoView(
                    onLogout = {
                        // 使用SupabaseCacheManager清理所有关联数据
                        val scope = kotlinx.coroutines.MainScope()
                        scope.launch {
                            // 清除用户相关的所有缓存
                            withContext(Dispatchers.IO) {
                                SupabaseCacheManager.clearUserCaches(context)
                                Log.d("SettingsCategoryUser", "用户缓存已清除")
                            }
                            
                            // 调用ViewModel的统一退出方法
                            mainViewModel.logout()
                            
                            // 替换为直接启动SupabaseLoginActivity
                            val intent = Intent(context, SupabaseLoginActivity::class.java)
                            // 添加手动登录标志
                            intent.putExtra(SupabaseLoginActivity.EXTRA_MANUAL_LOGIN, true)
                            context.startActivity(intent)
                        }
                    },
                    // 传递导航到个人中心的回调
                    onNavigateToProfile = onNavigateToProfile
                )
            }
        }

        // 添加动态多彩分隔线
        Canvas(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
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
                start = Offset(0f, animatedOffset.value % canvasHeight),
                end = Offset(0f, animatedOffset.value % canvasHeight + canvasHeight)
            )

            // 绘制线条
            drawLine(
                brush = gradient,
                start = Offset(canvasWidth / 2, 0f),
                end = Offset(canvasWidth / 2, canvasHeight),
                strokeWidth = canvasWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // 服务器信息区域，占用40%宽度
        Box(modifier = Modifier
            .weight(0.5f)
            .fillMaxHeight()
        ) {
            // 修改后的服务器信息框，添加标题
            ServerInfoBox(
                textColor = serverTextColor,
                fontSize = serverTextSize,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 未登录状态下的账户信息提示界面
 *
 * @param onNavigateToLogin 登录按钮回调
 * @param userInfoContentPadding 内部边距参数（单位：dp）
 */
@Composable
private fun UnloggedContent(
    onNavigateToLogin: () -> Unit, userInfoContentPadding: Int,
    dividerTopMargin: Int = 50 // 添加一个参数用于控制分隔线的顶部外边距

) {
    val context = LocalContext.current
    
    // 恢复原来的用户信息卡片设计
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(400.dp), // 设置固定高度
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2C3E50).copy(alpha = 0.2f),
                            Color(0xFF1A1A1A).copy(alpha = 0.2f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD4AF37),
                            Color(0xFFFFD700),
                            Color(0xFFD4AF37)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(userInfoContentPadding.dp, 6.dp), // 设置左右和上下的边距
                verticalArrangement = Arrangement.SpaceBetween
            ){
                // 顶部：标题
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    // 标题文本居中显示
                    Text(
                        text = "账户信息",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    // 分隔线，固定在 Box 的底部
                    // **分隔线，可调整与标题的间距**
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dividerTopMargin.dp), // 通过传参控制距离
                        color = Color(0xFFD4AF37).copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }

                // 中部：未登录状态下的提示信息
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "未登录",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "请先登录以查看账户信息",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.White
                            )
                        )
                    }
                }
                // 底部：功能项"前往登录"，居中显示，标签样式
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LabelItem(
                        text = "前往登录/注册",
                        onClick = {
                            // 使用 Supabase 登录界面
                            val intent = Intent(context, SupabaseLoginActivity::class.java)
                            // 添加手动登录标志
                            intent.putExtra(SupabaseLoginActivity.EXTRA_MANUAL_LOGIN, true)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}


/**
 * 显示已登录用户信息的界面
 *
 * @param onLogout 退出登录的回调函数
 * @param onNavigateToProfile 导航到个人中心的回调函数
 */
@Composable
private fun UserInfoView(
    onLogout: () -> Unit,
    onNavigateToProfile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // 直接使用SupabaseCacheManager
    var session by remember { mutableStateOf<String?>(null) }
    var userData by remember { mutableStateOf<SupabaseUserDataIptv?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showImage by remember { mutableStateOf(false) }
    // 获取 MainViewModel
    val mainViewModel: MainViewModel = viewModel()
    
    // 监听 Toast 消息
    LaunchedEffect(Unit) {
        mainViewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    BackHandler(enabled = showImage) {
        showImage = false
    }
    
    DonationImageDialog(
        showImage = showImage,
        onDismiss = { showImage = false }
    )
    
    // 使用标准缓存加载方式
    LaunchedEffect(Unit) {
        try {
            // 获取会话数据
            session = SupabaseCacheManager.getCache(context, SupabaseCacheKey.SESSION)
            
            // 安全获取用户数据：先获取原始数据，然后进行安全转换
            val rawUserData = SupabaseCacheManager.getRawCache(context, SupabaseCacheKey.USER_DATA)
            if (rawUserData != null) {
                // 使用安全转换方法
                userData = SupabaseCacheManager.safeConvertToUserData(rawUserData)
                Log.d("SettingsCategoryUser", "用户数据安全转换${if (userData != null) "成功: ${userData?.username}" else "失败"}")
            } else {
                Log.d("SettingsCategoryUser", "未找到用户数据缓存")
            }
            
            Log.d("SettingsCategoryUser",
                "[缓存加载] userId=${userData?.userid ?: "空"}｜" +
                        "VIP状态：${userData?.is_vip ?: false}")
        } catch (e: Exception) {
            Log.e("SettingsCategoryUser", "加载缓存数据异常: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    // 根据会话变化检测是否需要刷新数据，使用统一缓存策略
    LaunchedEffect(session) {
        // 如果有会话但没有用户数据，尝试从服务器加载
        if (session != null) {
            // 检查缓存是否有效
            val isCacheValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.USER_DATA)
            
            Log.d("SettingsCategoryUser", "缓存检查 - 是否有效：$isCacheValid")
            
            // 若缓存无效或不存在，则从服务器加载最新数据
            if (userData == null || !isCacheValid) {
                session?.let {
                    isLoading = true
                    try {
                        Log.d("SettingsCategoryUser", "开始从服务器加载用户数据...")
                        // 使用SupabaseUserRepository
                        val newData = withContext(Dispatchers.IO) {
                            SupabaseUserRepository().getUserData(it).also { data ->
                                Log.d("SettingsCategoryUser", "服务器数据获取成功，开始缓存｜userId=${data.userid}")
                                
                                // 使用SupabaseCacheManager保存用户数据
                                SupabaseCacheManager.saveCache(
                                    context = context,
                                    key = SupabaseCacheKey.USER_DATA,
                                    data = data,
                                    strategy = SupabaseCacheManager.getUserCacheStrategy(data)
                                )
                                
                                Log.d("SettingsCategoryUser", "缓存保存完成｜数据长度：${Gson().toJson(data).length}")
                            }
                        }
                        userData = newData
                    } catch (e: Exception) {
                        Log.e("SettingsCategoryUser", "数据加载异常: ${e.javaClass.simpleName} - ${e.message}", e)
                        // 尝试使用缓存数据
                        val cachedData = SupabaseCacheManager.getCache<SupabaseUserDataIptv>(context, SupabaseCacheKey.USER_DATA)
                        if (cachedData != null) {
                            userData = cachedData
                            Log.w("SettingsCategoryUser", "使用最后一次有效缓存数据：${cachedData.userid}")
                        }
                        // 401错误处理
                        if (e.message?.contains("401") == true) {
                            Log.w("SettingsCategoryUser", "检测到会话过期，触发全局清理")
                            mainViewModel.clearAllCache(true) {
                                Log.d("SettingsCategoryUser", "清理完成，开始强制刷新数据")
                                mainViewModel.forceRefreshUserData()
                            }
                        }
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                Log.d("SettingsCategoryUser", "使用本地缓存数据: ${userData?.userid}")
            }
        }
    }

    // 重新添加卡片包装
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // =====【修改标注开始】=====
            // 调整外边距：上边距设置为0，与状态栏紧贴；下边距调整为2.dp，使得下边与中间分隔线距离更近
            .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 2.dp)
            // =====【修改标注结束】=====
            //.padding(8.dp)
            .height(400.dp), // 设置固定高度
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2C3E50).copy(alpha = 0.2f),
                            Color(0xFF1A1A1A).copy(alpha = 0.2f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD4AF37),
                            Color(0xFFFFD700),
                            Color(0xFFD4AF37)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 3.dp, bottom = 3.dp, start = 32.dp, end = 32.dp) //

            ) {
                // 顶部：标题
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "账户信息",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        ),
                        textAlign = TextAlign.Center
                    )
                }
                // 分隔线
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFFD4AF37).copy(alpha = 0.5f),
                    thickness = 1.dp // 分隔线的高度
                )
                // 中部：用户信息显示（左侧滚动信息）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp) // 设置每一行之间的间距
                    )
                    {
                        userData?.let { data ->
                            InfoRow("用户名", data.username)
                            InfoRow("用户ID", data.userid)
                            InfoRow("注册邮箱", data.email.orEmpty())
                            InfoRow("账户权限", if (data.is_vip) "VIP用户" else "普通注册用户")
                            InfoRow("账户状态", data.accountstatus)
                            if (data.is_vip) {
                                InfoRow("VIP生效时间", data.vipstart ?: "无记录")
                                InfoRow("VIP到期时间", data.vipend ?: "无记录")
                            }
                            InfoRow("账号注册时间", data.created_at)
                            InfoRow("最后登录时间", data.lastlogintime ?: "无记录")
                            InfoRow("最后登录设备", data.lastlogindevice ?: "无记录")
                        }
                    }
                }
                // 底部：功能标签项居中显示
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 原代码中的刷新按钮逻辑修改为：
                        LabelItem(
                            text = "刷新信息",
                            onClick = {
                                val scope = kotlinx.coroutines.MainScope()
                                scope.launch {
                                    try {
                                        Log.d("SettingsCategoryUser", "⚡ 用户手动触发刷新")
                                        isLoading = true

                                        // 使用统一方法强制刷新
                                        mainViewModel.forceRefreshUserData()
                                        
                                        // 重新获取最新数据
                                        userData = SupabaseCacheManager.getCache(context, SupabaseCacheKey.USER_DATA)
                                        
                                        isLoading = false
                                        mainViewModel.showToast("信息已刷新")
                                    } catch (e: Exception) {
                                        Log.e("SettingsCategoryUser", "❌ 刷新流程异常: ${e.message}", e)
                                        isLoading = false
                                        mainViewModel.showToast("刷新出错，请稍后重试")
                                    }
                                }
                            }
                        )
                        LabelItem(
                            text = "前往管理个人中心",
                            onClick = {
                                // 如果提供了导航回调，则使用回调导航到个人中心
                                if (onNavigateToProfile != null) {
                                    onNavigateToProfile()
                                } else {
                                    // 如果没有提供回调，则使用旧的方式启动Activity
                                    val intent = Intent(context, SupabaseUserProfileActivity::class.java)
                                    context.startActivity(intent)
                                }
                            }
                        )
                        LabelItem(
                            text = "退出登录",
                            onClick = onLogout
                        )
                    }
                }
            }
        }
    }
}
// 修改服务器信息框，添加标题和分隔线
/**
* 优化后的服务器信息框组件
*
* @param textColor 文字颜色
* @param fontSize 字体大小
* @param modifier 修饰符
* @param dividerThickness 分隔线厚度（单位：dp）新增可调参数
* @param dividerColor 分隔线颜色 新增可调参数
* @param dividerVerticalMargin 分隔线垂直外边距（单位：dp）新增可调参数
*/
@Composable
private fun ServerInfoBox(
    textColor: Color,
    fontSize: Int,
    modifier: Modifier = Modifier,
    // 新增可配置参数：分隔线厚度/颜色/边距
    dividerThickness: Int = 1,
    dividerColor: Color = Color(0xFFD4AF37).copy(alpha = 0.5f),
    dividerVerticalMargin: Int = 4
) {
    var serviceText by remember { mutableStateOf("加载中...") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 使用SupabaseOnlineUsersSessionManager
    val onlineManager = remember { SupabaseOnlineUsersSessionManager.getInstance(context) }
    // 使用SupabaseOnlineUsersData类型
    var displayData by remember { mutableStateOf<SupabaseOnlineUsersData?>(null) }
    var isLoadingData by remember { mutableStateOf(true) }
    
    // 北京时间格式化工具
    val timeFormat = remember {
        SimpleDateFormat("HH时mm分ss秒", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }
    }
    val hourFormat = remember {
        SimpleDateFormat("HH", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }
    }

    // 初始化服务信息 - 使用SupabaseCacheManager
    LaunchedEffect(Unit) {
        // 先加载缓存
        val cachedText = SupabaseCacheManager.getCache<String>(
            context, 
            SupabaseCacheKey.SERVICE_INFO, 
            "加载中..."
        ) ?: "加载中..."
        serviceText = cachedText
        Log.d("SettingsCategoryUser", "[首次加载] 使用缓存内容：${cachedText.take(20)}...")

        // 检查缓存是否需要刷新
        val isCacheValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.SERVICE_INFO)
        if (!isCacheValid) {
            // 异步加载最新内容
            isLoading = true
            try {
                val latestText = SupabaseServiceInfoManager.loadServiceInfo(context)
                if (latestText != cachedText) {
                    serviceText = latestText
                    // 更新缓存
                    SupabaseCacheManager.saveCache(
                        context = context, 
                        key = SupabaseCacheKey.SERVICE_INFO, 
                        data = latestText,
                        strategy = SupabaseCacheStrategy.TimeStrategy(
                            expireTime = 3 * 24 * 60 * 60 * 1000L, // 3天
                            randomOffset = 12 * 60 * 60 * 1000L // ±12小时
                        )
                    )
                    Log.d("SettingsCategoryUser", "[后台更新] 内容已刷新")
                }
            } finally {
                isLoading = false
            }
        }
    }

    // 在线人数加载 - 使用SupabaseCacheManager
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // 先尝试获取原始缓存数据，然后进行安全转换
                val rawCachedData = SupabaseCacheManager.getRawCache(context, SupabaseCacheKey.ONLINE_USERS)
                if (rawCachedData != null) {
                    // 安全转换为SupabaseOnlineUsersData
                    val safeData = SupabaseCacheManager.safeConvertToType<SupabaseOnlineUsersData>(
                        rawCachedData,
                        SupabaseOnlineUsersData::class.java
                    )
                    if (safeData != null) {
                        displayData = safeData
                        Log.d("SettingsCategoryUser", "[在线用户] 安全转换成功，使用缓存数据: ${safeData.total}人")
                    } else {
                        Log.d("SettingsCategoryUser", "[在线用户] 缓存数据转换失败，将重新获取")
                        // 清除可能损坏的缓存
                        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.ONLINE_USERS)
                    }
                }
                
                // 检查缓存是否需要刷新
                val isCacheValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.ONLINE_USERS)
                if (!isCacheValid || displayData == null) {
                    try {
                        // 获取最新数据 - 修改为使用getCachedData方法
                        val freshData = onlineManager.getCachedData(true)
                        if (freshData != null) {
                            // 更新显示
                            displayData = freshData
                            // 更新缓存
                            SupabaseCacheManager.saveCache(
                                context = context,
                                key = SupabaseCacheKey.ONLINE_USERS,
                                data = freshData,
                                strategy = SupabaseCacheStrategy.TimeStrategy(
                                    expireTime = 1 * 60 * 60 * 1000L, // 1小时
                                    randomOffset = 10 * 60 * 1000L // ±10分钟
                                )
                            )
                            Log.d("SettingsCategoryUser", "[在线用户] 数据已刷新: ${freshData.total}人")
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsCategoryUser", "获取在线用户数据失败: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsCategoryUser", "在线用户数据处理异常: ${e.message}", e)
            } finally {
                // 延迟短暂时间显示加载动画
                delay(800)
                isLoadingData = false
            }
        }
    }

    // 定时刷新逻辑 - 使用协程的周期性任务
    DisposableEffect(Unit) {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        
        // 周期性检查缓存是否过期，而不是强制按时间刷新
        val job = scope.launch {
            while (true) {
                try {
                    // 每10分钟检查一次
                    delay(10 * 60 * 1000L)
                    
                    // 检查在线用户缓存
                    if (!SupabaseCacheManager.isValid(context, SupabaseCacheKey.ONLINE_USERS)) {
                        Log.d("SettingsCategoryUser", "[定时检查] 在线用户缓存过期，刷新数据")
                        
                        try {
                            val freshData = onlineManager.getCachedData(true)
                            if (freshData != null) {
                                displayData = freshData
                                // 更新缓存
                                SupabaseCacheManager.saveCache(
                                    context = context,
                                    key = SupabaseCacheKey.ONLINE_USERS,
                                    data = freshData,
                                    strategy = SupabaseCacheStrategy.TimeStrategy(
                                        expireTime = 1 * 60 * 60 * 1000L,
                                        randomOffset = 10 * 60 * 1000L
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsCategoryUser", "刷新在线用户数据失败", e)
                        }
                    }
                    
                    // 检查服务信息缓存
                    if (!SupabaseCacheManager.isValid(context, SupabaseCacheKey.SERVICE_INFO)) {
                        Log.d("SettingsCategoryUser", "[定时检查] 服务信息缓存过期，刷新数据")
                        
                        try {
                            val latestText = SupabaseServiceInfoManager.loadServiceInfo(context)
                            serviceText = latestText
                            // 更新缓存
                            SupabaseCacheManager.saveCache(
                                context = context,
                                key = SupabaseCacheKey.SERVICE_INFO,
                                data = latestText,
                                strategy = SupabaseCacheStrategy.TimeStrategy(
                                    expireTime = 3 * 24 * 60 * 60 * 1000L,
                                    randomOffset = 12 * 60 * 60 * 1000L
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("SettingsCategoryUser", "刷新服务信息失败", e)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d("SettingsCategoryUser", "定时检查任务被取消")
                        break
                    }
                    Log.e("SettingsCategoryUser", "定时检查任务异常", e)
                    delay(30000) // 错误后延迟30秒再试
                }
            }
        }
        
        onDispose {
            job.cancel()
            Log.d("SettingsCategoryUser", "取消定时刷新任务")
        }
    }

    Card(
        modifier = modifier
            //.padding(8.dp)
            // =====【修改标注开始】=====
            // 调整外边距：上边距设置为0，与状态栏紧贴；下边距设置为2.dp，使其与下方内容紧靠
            .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 2.dp)
            // =====【修改标注结束】=====
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF000000).copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2C3E50).copy(alpha = 0.2f),
                            Color(0xFF1A1A1A).copy(alpha = 0.2f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD4AF37),
                            Color(0xFFFFD700),
                            Color(0xFFD4AF37)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp), // 确保没有底部间距
                verticalArrangement = Arrangement.SpaceBetween // 将内容推到顶部和底部
            ) {
                // 顶部内容
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // 占据剩余空间
                    verticalArrangement = Arrangement.Top
                ) {
                    /**************** 标题优化部分 ****************/
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "服务信息",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            ),
                            modifier = Modifier
                                .align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    }

                    /**************** 可配置分隔线 ****************/
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = dividerVerticalMargin.dp,
                                horizontal = 0.dp
                            ),
                        color = dividerColor,
                        thickness = dividerThickness.dp
                    )

                    /**************** 内容区域调整 ****************/
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(16.dp)
                        )
                    } else {
                        AnimatedVisibility(
                            visible = !isLoadingData,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                // 原有服务信息显示
                                Text(
                                    text = serviceText,
                                    color = textColor,
                                    fontSize = fontSize.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 底部信息（固定在底部）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 0.dp), // 确保底部没有间距
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // 最后更新
                    Text(
                        text = "最后更新：${getLastUpdated(context)}",
                        color = textColor.copy(alpha = 0.8f),
                        fontSize = (fontSize - 2).sp,
                        modifier = Modifier
                            .padding(bottom = 0.dp) // 强制设置为0间距
                            .align(Alignment.Start) // 左对齐避免留空
                    )

                    // 当前时间和正点实时注册在线
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp, bottom = 0.dp), // 消除行内间距
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom // 内容贴底
                    ) {
                        // 当前时间显示（带秒数高亮）
                        var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(1000)
                                currentTime = LocalDateTime.now()
                            }
                        }

                        val timeText = buildAnnotatedString {
                            // 修改时间格式，添加星期显示（EEEE 表示完整中文星期）
                            val fullTimeWithWeek = currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA))
                            val dateTimeParts = fullTimeWithWeek.split(" ") // 分割为 [日期, 时间, 星期]
                            val timeParts = dateTimeParts[1].split(":")     // 分割时间为 [时, 分, 秒]

                            // 构建带红色秒数和星期显示的时间字符串
                            append("当前时间：${dateTimeParts[0]} ${timeParts[0]}:${timeParts[1]}:")
                            withStyle(SpanStyle(color = Color.Red)) {
                                append(timeParts[2])
                            }
                            append(" ${dateTimeParts[2]}") // 添加星期部分
                        }

                        Text(
                            text = timeText,
                            color = textColor.copy(alpha = 1f),
                            fontSize = (fontSize - 2).sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 0.dp), // 消除文本底部间距
                            textAlign = TextAlign.Start
                        )

                        // 正点人数显示（强制贴边）
                        Text(
                            text = "${hourFormat.format(Date((displayData?.updated ?: 0) * 1000))}时正点实时注册在线：${
                                formatNumber(displayData?.total ?: 0)
                            }人",
                            color = textColor.copy(alpha = 0.8f),
                            fontSize = (fontSize - 2).sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 0.dp), // 消除文本底部间距
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }

    // 加载状态指示器
    if (isLoadingData) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFFD700),
                strokeWidth = 2.dp,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

// 获取最后更新时间 - 修改为使用SupabaseCacheManager
private fun getLastUpdated(context: Context): String {
    val lastUpdated: Long
    
    try {
        // 在非协程上下文中安全调用
        val prefs = context.getSharedPreferences("supabase_cache", Context.MODE_PRIVATE)
        lastUpdated = prefs.getLong("${SupabaseCacheKey.SERVICE_INFO_LAST_LOADED.name}_timestamp", 0L)
    } catch (e: Exception) {
        Log.e("SettingsCategoryUser", "获取最后更新时间失败", e)
        return "暂无记录"
    }
    
    return if (lastUpdated > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }.format(Date(lastUpdated))
    } else {
        "暂无记录"
    }
}

// 保留原有辅助函数
private fun calculateNextHourTime(): Long {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8")).apply {
        timeInMillis = System.currentTimeMillis()
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 30)
    }
    return calendar.timeInMillis
}
//数据转换万计函数
private fun formatNumber(number: Int): String {
    return if (number >= 10000) {
        "%.1f万".format(number / 10000.0)
    } else {
        number.toString()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
            //.padding(vertical = 4.dp),//固定行距
             horizontalArrangement = Arrangement.SpaceBetween//两端对齐
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700)
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White
            )
        )
    }
}
@Composable
private fun DonationImageDialog(
    showImage: Boolean,
    onDismiss: () -> Unit
) {
    if (showImage) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.nice_live),
                    contentDescription = "赞赏图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.32f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        }
    }
}

// 修复CardButton组件
@Composable
private fun CardButton(
    text: String,
    onClick: () -> Unit,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // 添加动画效果
    val infiniteTransition = rememberInfiniteTransition(label = "buttonAnimation")
    val pulseAnimation = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnimation"
    )

    // 边框动画
    val borderAnimation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderAnimation"
    )

    Card(
        modifier = modifier
            .width(100.dp) // 增加宽度
            .height(40.dp) // 增加高度
            .scale(if (isFocused) pulseAnimation.value else 1f) // 添加脉动动画
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { state -> isFocused = state.isFocused }, // 修复这里的语法
        shape = RoundedCornerShape(16.dp), // 增大圆角
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) containerColor.copy(alpha = 0.9f) else containerColor.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 12.dp else 4.dp // 增加高亮时的阴影
        )
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (isFocused) {
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.5f),
                                containerColor.copy(alpha = 0.8f)
                            ),
                            radius = 200f
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                containerColor,
                                containerColor.copy(alpha = 0.8f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // 添加选中时的光晕效果
            if (isFocused) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 绘制旋转的边框光效
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth / 2, canvasHeight / 2),
                            radius = canvasWidth.coerceAtLeast(canvasHeight)
                        ),
                        radius = canvasWidth.coerceAtLeast(canvasHeight) / 2,
                        center = Offset(canvasWidth / 2, canvasHeight / 2)
                    )
                }
            }

            // 文本内容
            Text(
                text = text,
                color = contentColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isFocused) 18.sp else 16.sp, // 增大字体
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
/**
 * LabelItem 函数用于显示一个可点击的标签，
 * 当光标悬停（获得焦点）时，标签背景变为不透明白色，文字颜色变为黑色；未获得焦点时背景透明，文字颜色为白色。
 *
 * @param text 显示的文本内容
 * @param onClick 点击事件的回调函数
 * @param modifier 修饰符参数，默认值为 Modifier
 * @param textStyle 文本样式，默认使用 MaterialTheme.typography.bodyMedium，并将文字颜色设为白色
 */
@Composable
private fun LabelItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
) {
    // 使用 remember 保存焦点状态
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            // 点击时执行 onClick 回调
            .clickable { onClick() }
            // 监听焦点变化，更新 isFocused 状态
            .onFocusChanged { isFocused = it.isFocused }
            // 设置背景颜色：
            // 当获得焦点时，背景为白色且完全不透明 (alpha = 1f)；
            // 当未获得焦点时，背景为白色但完全透明 (alpha = 0f)。
            .background(
                color = if (isFocused) Color.White.copy(alpha = 1f) else Color.White.copy(alpha = 0f),
                shape = RoundedCornerShape(4.dp)  // 四个角为圆角
            )
            // 内边距设置
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // 根据焦点状态设置文本颜色：
        // 获得焦点时文本颜色为黑色；未获得焦点时文本颜色为白色
        Text(
            text = text,
            style = textStyle.copy(
                color = if (isFocused) Color.Black else Color.White
            ),
            textAlign = TextAlign.Center
        )
    }
}
