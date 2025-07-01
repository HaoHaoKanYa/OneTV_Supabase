package top.cywin.onetv.tv.supabase.support

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val TAG = "FloatingSupportBtn"

/**
 * 悬浮客服按钮组件
 * 显示在主界面右下角，提供快速访问客服功能
 */
@Composable
fun FloatingSupportButton(
    modifier: Modifier = Modifier,
    onQuickSupport: () -> Unit = {},
    onShowSupportCenter: () -> Unit = {},
    supportViewModel: SupportViewModel = viewModel()
) {
    Log.d(TAG, "FloatingSupportButton: 初始化悬浮客服按钮")
    val uiState by supportViewModel.uiState.collectAsState()
    var isFocused by remember {
        mutableStateOf(false).also {
            Log.d(TAG, "FloatingSupportButton: 初始化焦点状态 = false")
        }
    }
    var showQuickMenu by remember {
        mutableStateOf(false).also {
            Log.d(TAG, "FloatingSupportButton: 初始化快捷菜单状态 = false")
        }
    }
    var userRoles by remember {
        mutableStateOf<List<String>>(emptyList()).also {
            Log.d(TAG, "FloatingSupportButton: 初始化用户角色列表")
        }
    }
    var isAdmin by remember {
        mutableStateOf(false).also {
            Log.d(TAG, "FloatingSupportButton: 初始化管理员状态 = false")
        }
    }

    // 获取用户角色信息
    LaunchedEffect(Unit) {
        supportViewModel.getUserRoles { roles ->
            userRoles = roles
        }
        supportViewModel.checkAdminStatus { adminStatus ->
            isAdmin = adminStatus
        }
    }
    
    // 未读消息数量
    val unreadCount = uiState.conversationState.unreadCount
    
    // 按钮缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(200),
        label = "scale"
    )
    
    // 脉冲动画（当有未读消息时）
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = modifier
    ) {
        if (showQuickMenu) {
            // 快速菜单
            QuickSupportMenu(
                userRoles = userRoles,
                isAdmin = isAdmin,
                onQuickSupport = {
                    onQuickSupport()
                    showQuickMenu = false
                },
                onShowSupportCenter = {
                    onShowSupportCenter()
                    showQuickMenu = false
                },
                onDismiss = { showQuickMenu = false }
            )
        }
        
        // 主按钮
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(if (unreadCount > 0) pulseScale else scale)
                .clip(CircleShape)
                .background(
                    color = Color(0xFF4285F4).copy(alpha = 0.9f)
                )
                .border(
                    width = 2.dp,
                    color = if (isFocused) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable {
                    if (showQuickMenu) {
                        showQuickMenu = false
                    } else {
                        onQuickSupport()
                    }
                }
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        showQuickMenu = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "客服",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 未读消息徽章
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Red),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 快速客服菜单
 */
@Composable
private fun QuickSupportMenu(
    userRoles: List<String>,
    isAdmin: Boolean,
    onQuickSupport: () -> Unit,
    onShowSupportCenter: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .offset(x = (-150).dp, y = (-120).dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "客服支持",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // 显示用户角色标识
                if (isAdmin) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE91E63).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                userRoles.contains("super_admin") -> "超管"
                                userRoles.contains("admin") -> "管理"
                                userRoles.contains("support") -> "客服"
                                else -> "管理"
                            },
                            color = Color(0xFFE91E63),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp
            )

            // 快速客服按钮
            QuickMenuButton(
                text = "客服对话",
                description = "快速联系客服",
                onClick = onQuickSupport
            )

            // 客服中心按钮
            QuickMenuButton(
                text = "客服中心",
                description = "完整客服功能",
                onClick = onShowSupportCenter
            )

            // 管理员功能
            if (isAdmin) {
                QuickMenuButton(
                    text = "管理面板",
                    description = "管理员专用功能",
                    onClick = { /* TODO: 实现管理面板 */ },
                    textColor = Color(0xFFE91E63)
                )
            }

            // 关闭按钮
            QuickMenuButton(
                text = "关闭",
                description = "",
                onClick = onDismiss,
                textColor = Color.Gray
            )
        }
    }
}

/**
 * 快速菜单按钮
 */
@Composable
private fun QuickMenuButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    textColor: Color = Color.White
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color(0xFF2C3E50).copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = text,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 客服状态指示器
 * 可以显示在其他位置，表示客服连接状态
 */
@Composable
fun SupportStatusIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color.Green else Color.Red)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isConnected) "客服在线" else "客服离线",
            color = Color.Gray,
            fontSize = 10.sp
        )
    }
}

/**
 * 简化版客服按钮（用于其他界面）
 */
@Composable
fun SimpleSupportButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    text: String = "客服"
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
