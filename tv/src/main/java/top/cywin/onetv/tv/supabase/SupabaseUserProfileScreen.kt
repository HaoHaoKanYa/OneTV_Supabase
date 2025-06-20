package top.cywin.onetv.tv.supabase

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserRepository
import androidx.activity.ComponentActivity
import top.cywin.onetv.tv.supabase.SupabaseCacheManager

/**
 * 设置屏幕为横屏模式
 * 在Activity调用此函数来准备屏幕方向
 */
fun Activity.setLandscapeOrientationForProfile() {
    // 设置为传感器横屏模式，支持两种方向
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

/**
 * 恢复屏幕为默认方向
 */
fun Activity.resetOrientation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/**
 * 用户个人中心主屏幕
 * 作为设置界面第二层级的一部分
 * 
 * @param onBackPressed 返回按钮回调，在设置界面中可忽略
 */
@Composable
fun SupabaseUserProfileScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // 获取当前用户会话和数据
    val session = remember { SupabaseSessionManager.getSession(context) }
    var userData by remember { mutableStateOf<SupabaseUserDataIptv?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // 判断用户是否已登录
    val isLoggedIn = remember { session != null && session.isNotEmpty() }
    
    // 获取用户数据
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            
            // 先获取最新的用户数据（从SupabaseSessionManager缓存中）
            val sessionCachedData = SupabaseSessionManager.getCachedUserData(context)
            
            // 再尝试从专用缓存加载
            val profileCachedData = SupabaseUserProfileInfoSessionManager.getCachedUserProfileData(context)
            
            // 检查缓存是否有效（基于时间和关键字段变化）
            if (profileCachedData != null && 
                SupabaseUserProfileInfoSessionManager.isCacheValid(context, sessionCachedData)) {
                // 如果缓存有效，直接使用缓存数据
                userData = profileCachedData
                Log.d("UserProfile", "使用缓存的用户数据: ${profileCachedData.username} | 缓存有效期: 30天")
                isLoading = false
            } else {
                // 缓存无效或不存在，从服务器加载
                if (session != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val freshData = SupabaseUserRepository().getUserData(session)
                            withContext(Dispatchers.Main) {
                                userData = freshData
                                // 保存到专用缓存
                                SupabaseUserProfileInfoSessionManager.saveUserProfileData(context, freshData)
                                Log.d("UserProfile", "从服务器获取新的用户数据: ${freshData.username} | 原因: ${
                                    if (profileCachedData == null) "无缓存" 
                                    else "缓存已失效（时间或关键字段变化）"
                                }")
                            }
                        } catch (e: Exception) {
                            Log.e("UserProfile", "从服务器获取用户数据失败", e)
                            // 如果从服务器获取失败但有缓存，仍然使用缓存
                            if (profileCachedData != null) {
                                withContext(Dispatchers.Main) {
                                    userData = profileCachedData
                                    Log.d("UserProfile", "服务器获取失败，使用过期缓存数据: ${profileCachedData.username}")
                                }
                            }
                        }
                    }
                }
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("UserProfile", "加载用户数据失败", e)
            isLoading = false
        }
    }
    
    // 页面背景 - 调整透明度以适应设置界面
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1A1A).copy(alpha = 0.3f),
            Color(0xFF2C3E50).copy(alpha = 0.3f)
        )
    )
    
    // 使用更紧凑的布局以适应设置界面
    Column(
        modifier = Modifier
            .fillMaxWidth() // 不使用fillMaxSize，让它能够适应设置界面
            .background(backgroundBrush)
            .padding(8.dp) // 减小内边距
    ) {
        // 标签行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 水平菜单占满整行
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                HorizontalTabItem(
                    title = "用户资料",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                
                HorizontalTabItem(
                    title = "VIP管理",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                
                HorizontalTabItem(
                    title = "用户设置",
                    isSelected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                
                HorizontalTabItem(
                    title = "观看历史",
                    isSelected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                
                // 根据登录状态动态显示"前往登录"或"退出应用"标签
                HorizontalTabItem(
                    title = if (isLoggedIn) "退出账号" else "前往登录",
                    isSelected = selectedTab == 4,
                    onClick = { 
                        selectedTab = 4
                        if (isLoggedIn) {
                            // 退出账号逻辑
                            scope.launch {
                                try {
                                    // 获取Activity上下文
                                    val activity = context as? ComponentActivity
                                    // 清除所有用户相关的缓存
                                    SupabaseCacheManager.clearAllCachesOnLogout(context)
                                    // 调用MainViewModel的logout方法
                                    val mainViewModel = activity?.let { 
                                        ViewModelProvider(it).get(top.cywin.onetv.tv.ui.screens.main.MainViewModel::class.java) 
                                    }
                                    mainViewModel?.logout()
                                    Log.d("UserProfile", "用户已退出登录，缓存已清除")
                                    // 显示退出成功提示
                                    (context as? ComponentActivity)?.let {
                                        android.widget.Toast.makeText(context, "已退出登录", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("UserProfile", "退出登录失败", e)
                                    (context as? ComponentActivity)?.let {
                                        android.widget.Toast.makeText(context, "退出登录失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            // 前往登录逻辑
                            val intent = Intent(context, SupabaseLoginActivity::class.java).apply {
                                putExtra(SupabaseLoginActivity.EXTRA_MANUAL_LOGIN, true)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
            
            // 不需要返回按钮，因为现在是设置界面的一部分
        }
        
        // 内容区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp, // 减小边框宽度
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD4AF37),
                            Color(0xFFFFD700),
                            Color(0xFFD4AF37)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp) // 减小内边距
        ) {
            when (selectedTab) {
                0 -> SupabaseUserProfileInfo(userData, isLoading)
                1 -> SupabaseVipManager(userData, isLoading, context)
                2 -> SupabaseUserSettings(userData, isLoading, context)
                3 -> SupabaseWatchHistory(userData, isLoading, context)
                4 -> if (isLoggedIn) {
                    // 退出账号的提示界面
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "点击\"退出账号\"标签可退出当前账号",
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // 前往登录的提示界面
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "点击\"前往登录\"标签可进入登录界面",
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 水平菜单项
 */
@Composable
fun HorizontalTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) Color(0xFF2C3E50).copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFFFFD700) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFFFFD700) else Color.White
        )
    }
}

/**
 * 个人中心按钮组件
 */
@Composable
fun SupabaseProfileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .background(
                color = if (enabled) Color(0xFFD4AF37) else Color.Gray,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp)
        )
    }
} 