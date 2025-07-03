package top.cywin.onetv.tv.supabase.support

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.cywin.onetv.tv.ui.material.SimplePopup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv

private const val TAG = "SupabaseSupportCenter"

/**
 * 菜单项数据类
 */
data class MenuItem(
    val id: String,
    val title: String,
    val enabled: Boolean = true
)

/**
 * 客服支持中心组件
 * 采用左右分栏布局：左侧菜单，右侧内容区域
 * 包含客服对话、反馈管理、用户管理等功能
 */
@Composable
fun SupabaseSupportCenter(
    userData: SupabaseUserDataIptv?,
    isLoading: Boolean,
    context: Context,
    supportViewModel: SupportViewModel
) {
    val uiState by supportViewModel.uiState.collectAsState()

    // 检查用户登录状态
    val isLoggedIn = userData != null

    if (isLoading) {
        // 加载状态
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFFFD700))
        }
    } else if (!isLoggedIn) {
        // 未登录状态
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "请先登录后使用客服功能",
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "登录后您可以：\n• 与客服进行一对一对话\n• 提交问题反馈和建议\n• 查看反馈处理状态",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // 已登录状态：显示左右分栏布局
        SupportCenterLayout(
            userData = userData,
            supportViewModel = supportViewModel,
            uiState = uiState
        )
    }
}

/**
 * 左右分栏布局
 */
@Composable
private fun SupportCenterLayout(
    userData: SupabaseUserDataIptv,
    supportViewModel: SupportViewModel,
    uiState: SupportUiState
) {
    var selectedMenuItem by remember { mutableStateOf("user_info") }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // 左侧菜单
            SupportMenuPanel(
                userData = userData,
                supportViewModel = supportViewModel,
                selectedMenuItem = selectedMenuItem,
                onMenuItemSelected = { selectedMenuItem = it }
            )

            // 右侧内容区域
            SupportContentPanel(
                selectedMenuItem = selectedMenuItem,
                userData = userData,
                supportViewModel = supportViewModel,
                uiState = uiState
            )
        }

        // 用户消息提示
        if (uiState.showUserMessage) {
            UserMessageToast(
                message = uiState.userMessage,
                messageType = uiState.userMessageType,
                onDismiss = { supportViewModel.hideUserMessage() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }


    }
}

/**
 * 左侧菜单面板
 */
@Composable
private fun SupportMenuPanel(
    userData: SupabaseUserDataIptv,
    supportViewModel: SupportViewModel,
    selectedMenuItem: String,
    onMenuItemSelected: (String) -> Unit
) {
    var isAdmin by remember { mutableStateOf(false) }
    var userRoles by remember { mutableStateOf<List<String>>(emptyList()) }

    // 获取用户权限信息
    LaunchedEffect(Unit) {
        supportViewModel.checkAdminStatus { adminStatus ->
            isAdmin = adminStatus
        }
        supportViewModel.getUserRoles { roles ->
            userRoles = roles
        }
    }

    LazyColumn(
        modifier = Modifier
            .width(213.dp)  // 缩减1/3宽度：320dp * 2/3 ≈ 213dp
            .fillMaxHeight()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2C3E50).copy(alpha = 0.2f),
                        Color(0xFF1A1A1A).copy(alpha = 0.2f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                ),
                shape = RoundedCornerShape(16.dp)  // 设置所有角为圆角
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFD4AF37),
                        Color(0xFFFFD700),
                        Color(0xFFD4AF37)
                    )
                ),
                shape = RoundedCornerShape(16.dp)  // 设置所有角为圆角
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 当前用户信息
        item {
            MenuSection(
                title = "当前用户",
                items = listOf(
                    MenuItem("user_info", "用户信息", true)
                ),
                selectedItem = selectedMenuItem,
                onItemSelected = onMenuItemSelected
            )
        }

        // 客服功能
        item {
            MenuSection(
                title = "客服功能",
                items = listOf(
                    MenuItem("chat", "客服对话", true),
                    MenuItem("submit_feedback", "提交反馈", true),
                    MenuItem("my_feedback", "我的反馈", true)
                ),
                selectedItem = selectedMenuItem,
                onItemSelected = onMenuItemSelected
            )
        }

        // 管理员功能（根据权限显示）
        if (isAdmin) {
            item {
                MenuSection(
                    title = "管理员功能",
                    items = listOf(
                        MenuItem("user_management", "用户管理", userRoles.contains("admin") || userRoles.contains("super_admin")),
                        MenuItem("feedback_management", "反馈管理", userRoles.contains("admin") || userRoles.contains("super_admin")),
                        MenuItem("support_desk", "客服工作台", userRoles.contains("support") || userRoles.contains("admin") || userRoles.contains("super_admin"))
                    ),
                    selectedItem = selectedMenuItem,
                    onItemSelected = onMenuItemSelected
                )
            }
        }

        // 功能说明
        item {
            MenuSection(
                title = "帮助",
                items = listOf(
                    MenuItem("help", "功能说明", true)
                ),
                selectedItem = selectedMenuItem,
                onItemSelected = onMenuItemSelected
            )
        }
    }
}

/**
 * 菜单组
 */
@Composable
private fun MenuSection(
    title: String,
    items: List<MenuItem>,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    Column {
        // 组标题
        Text(
            text = title,
            color = Color(0xFFFFD700),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 菜单项
        items.forEach { item ->
            MenuItemComponent(
                item = item,
                isSelected = selectedItem == item.id,
                onSelected = { onItemSelected(item.id) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 单个菜单项
 */
@Composable
private fun MenuItemComponent(
    item: MenuItem,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val textColor = if (item.enabled) {
        if (isSelected) Color(0xFFFFD700) else Color.White
    } else {
        Color.Gray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = if (isSelected) Color(0xFF2C3E50).copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) Color(0xFFFFD700).copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = item.enabled) { onSelected() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = item.title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 帮助内容 - 简化版本
 */
@Composable
fun HelpContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "功能说明",
            color = Color(0xFFFFD700),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // 滚动内容
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HelpSection(
                    title = "客服功能",
                    items = listOf(
                        "客服对话：与客服进行一对一实时对话",
                        "提交反馈：向我们反馈问题、建议或意见",
                        "我的反馈：查看您提交的反馈记录和处理状态"
                    )
                )
            }

            item {
                HelpSection(
                    title = "管理员功能",
                    items = listOf(
                        "用户管理：管理系统用户和权限（需要管理员权限）",
                        "反馈管理：处理和回复用户反馈（需要管理员权限）",
                        "客服工作台：客服人员专用工作界面（需要客服权限）"
                    )
                )
            }

            item {
                HelpSection(
                    title = "使用说明",
                    items = listOf(
                        "使用遥控器上下键选择左侧菜单项",
                        "按确认键进入对应功能",
                        "在对话和反馈界面中，使用返回键退出",
                        "管理员功能需要相应权限才能使用"
                    )
                )
            }
        }
    }
}

/**
 * 帮助章节 - 简化版本
 */
@Composable
fun HelpSection(title: String, items: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题
        Text(
            text = title,
            color = Color(0xFFFFD700),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        // 内容列表
        items.forEach { item ->
            Text(
                text = "• $item",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/**
 * 右侧内容面板
 */
@Composable
private fun SupportContentPanel(
    selectedMenuItem: String,
    userData: SupabaseUserDataIptv,
    supportViewModel: SupportViewModel,
    uiState: SupportUiState
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
                ),
                shape = RoundedCornerShape(16.dp)  // 设置右侧区域所有角为圆角
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFD4AF37),
                        Color(0xFFFFD700),
                        Color(0xFFD4AF37)
                    )
                ),
                shape = RoundedCornerShape(16.dp)  // 设置右侧区域所有角为圆角
            )
            .padding(8.dp)
    ) {
        when (selectedMenuItem) {
            "user_info" -> UserInfoContent(userData, supportViewModel)
            "chat" -> {
                // 主内容区域
                ChatStartContent(
                    onStartChat = {
                        // 先启动客服对话连接，然后显示对话界面
                        supportViewModel.startSupportConversation()
                        supportViewModel.showConversation()
                    },
                    supportViewModel = supportViewModel
                )
            }
            "submit_feedback" -> {
                // 主内容区域 - 论坛版块模式
                FeedbackStartContent(
                    onStartFeedback = { supportViewModel.showFeedbackForm() },
                    supportViewModel = supportViewModel
                )
            }
            "my_feedback" -> {
                // 主内容区域 - 论坛版块模式
                MyFeedbackContent(
                    onViewFeedback = { supportViewModel.showFeedbackList() },
                    supportViewModel = supportViewModel
                )
            }
            "user_management" -> {
                if (uiState.showUserManagement) {
                    UserManagementScreen(
                        viewModel = supportViewModel,
                        onClose = { supportViewModel.hideUserManagement() }
                    )
                } else {
                    UserManagementContent(
                        onOpenUserManagement = { supportViewModel.showUserManagement() },
                        supportViewModel = supportViewModel
                    )
                }
            }
            "feedback_management" -> FeedbackManagementContent(supportViewModel = supportViewModel)
            "support_desk" -> SupportDeskContent(supportViewModel = supportViewModel)
            "help" -> HelpContent()
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "功能开发中...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * 用户信息内容
 */
@Composable
private fun UserInfoContent(
    userData: SupabaseUserDataIptv,
    supportViewModel: SupportViewModel
) {
    var userRoles by remember { mutableStateOf<List<String>>(emptyList()) }
    var userStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }

    LaunchedEffect(userData) {
        supportViewModel.getUserRoles { roles ->
            userRoles = roles
        }
        // 获取用户统计信息
        supportViewModel.getUserStats { stats ->
            userStats = stats
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 用户基本信息 - 直接显示，无卡片
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("用户名", userData.username ?: "未设置")
                InfoRow("邮箱", userData.email ?: "未设置")
                InfoRow("VIP状态", if (userData.is_vip == true) "VIP用户" else "普通用户")
                InfoRow("账户状态", userData.accountstatus ?: "未知")
                InfoRow("注册时间", userData.created_at ?: "未知")
            }
        }

        // 当前角色 - 直接显示，无卡片
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (userRoles.isNotEmpty()) {
                    val roleNames = mutableListOf<String>()
                    if (userRoles.contains("super_admin")) roleNames.add("超级管理员")
                    if (userRoles.contains("admin")) roleNames.add("管理员")
                    if (userRoles.contains("support")) roleNames.add("客服")
                    if (userRoles.contains("moderator")) roleNames.add("版主")
                    if (userRoles.contains("vip")) roleNames.add("VIP用户")
                    if (roleNames.isEmpty()) roleNames.add("普通用户")

                    InfoRow("当前角色", roleNames.joinToString("、"))
                } else {
                    InfoRow("当前角色", "加载中...")
                }
            }
        }

        // 使用统计 - 直接显示，无卡片
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("观看时长", (userStats["watchTime"] as? String) ?: "统计中...")
                InfoRow("反馈提交", (userStats["feedbackCount"] as? Int)?.let { "${it}次" } ?: "0次")
                InfoRow("活跃天数", (userStats["activeDays"] as? Int)?.let { "${it}天" } ?: "0天")
            }
        }

        // 设备信息 - 直接显示，无卡片
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("设备类型", "Android TV")
                InfoRow("应用版本", (userStats["appVersion"] as? String) ?: "未知")
                InfoRow("系统版本", (userStats["systemVersion"] as? String) ?: "未知")
                InfoRow("设备型号", (userStats["deviceModel"] as? String) ?: "未知")
            }
        }

        // 快捷操作 - 改为左中右排版
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    text = "刷新用户信息",
                    onClick = {
                        supportViewModel.refreshUserInfo()
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                QuickActionButton(
                    text = "清除缓存",
                    onClick = {
                        supportViewModel.clearUserCache()
                    },
                    modifier = Modifier.weight(1f)
                )

                if (userRoles.any { it in listOf("admin", "super_admin") }) {
                    Spacer(modifier = Modifier.width(12.dp))
                    QuickActionButton(
                        text = "查看系统日志",
                        onClick = {
                            supportViewModel.viewSystemLogs()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}



/**
 * 快捷操作按钮
 */
@Composable
private fun QuickActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFFFD700).copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label：",
            color = Color.White, // 修改为亮色字体
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.LightGray, // 值保持为浅灰色以区分
            fontSize = 14.sp
        )
    }
}

/**
 * 聊天开始内容 - 微信聊天窗口样式
 */
@Composable
private fun ChatStartContent(
    onStartChat: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var conversationHistory by remember { mutableStateOf<List<SupportConversation>>(emptyList()) }
    var conversationStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        supportViewModel.getUserConversations { conversations ->
            conversationHistory = conversations
            isLoading = false
        }
        supportViewModel.getConversationStats { stats ->
            conversationStats = stats
        }
    }

    // 左右分栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {

            // 聊天历史区域 - 类似微信聊天记录
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                } else if (conversationHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无对话记录",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "点击右侧按钮开始您的第一次客服对话",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(conversationHistory) { conversation ->
                        ConversationHistoryItem(
                            conversation = conversation,
                            onClick = {
                                // 先启动客服对话连接，然后显示对话界面
                                supportViewModel.startSupportConversation()
                                onStartChat()
                            }
                        )
                    }
                }
            }
        }


        // 分隔线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧操作区域 - 垂直排列
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计信息区域 - 固定字段，只加载数据
            ConversationStatsPanel(conversationStats)

            Spacer(modifier = Modifier.height(8.dp))

            // 刷新按钮
            Button(
                onClick = {
                    supportViewModel.getUserConversations { conversations ->
                        conversationHistory = conversations
                    }
                    supportViewModel.getConversationStats { stats ->
                        conversationStats = stats
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄 刷新",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 开始新对话按钮
            Button(
                onClick = onStartChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Text(
                    text = "💬 开始新对话",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 统计项组件 - 横向布局，数据显示在标签右侧
 */
@Composable
private fun StatItem(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Text(
            text = value.toString(),
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 垂直统计项组件 - 用于右侧垂直排列
 */
@Composable
private fun StatItemVertical(label: String, value: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = value.toString(),
            color = Color(0xFFFFD700),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 水平统计项组件 - 用于左右排版
 */
@Composable
private fun StatItemHorizontal(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Text(
            text = value.toString(),
            color = Color(0xFFFFD700),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 二列统计组件 - 用于反馈管理的特殊布局
 */
@Composable
private fun StatItemTwoColumn(
    label1: String, value1: Int,
    label2: String, value2: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左列
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "$label1 $value1",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }

        // 右列
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$label2 $value2",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 固定字段统计组件 - 客服对话专用
 */
@Composable
private fun ConversationStatsPanel(stats: Map<String, Any>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatItemHorizontal("总对话", (stats["total"] as? Int) ?: 0)
        StatItemHorizontal("进行中", (stats["open"] as? Int) ?: 0)
        StatItemHorizontal("已完成", (stats["closed"] as? Int) ?: 0)
    }
}

/**
 * 固定字段统计组件 - 提交反馈专用
 */
@Composable
private fun SubmitFeedbackStatsPanel(stats: Map<String, Any>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatItemHorizontal("已提交", (stats["total"] as? Int) ?: 0)
        StatItemHorizontal("处理中", (stats["reviewing"] as? Int) ?: 0)
        StatItemHorizontal("已解决", (stats["resolved"] as? Int) ?: 0)
    }
}

/**
 * 固定字段统计组件 - 我的反馈专用
 */
@Composable
private fun MyFeedbackStatsPanel(stats: Map<String, Any>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatItemHorizontal("总数", (stats["total"] as? Int) ?: 0)
        StatItemHorizontal("处理中", (stats["reviewing"] as? Int) ?: 0)
        StatItemHorizontal("已解决", (stats["resolved"] as? Int) ?: 0)
    }
}

/**
 * 固定字段统计组件 - 用户管理专用
 */
@Composable
private fun UserManagementStatsPanel(stats: Map<String, Any>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatItemHorizontal("总用户", (stats["total"] as? Int) ?: 0)
        StatItemHorizontal("VIP用户", (stats["vip"] as? Int) ?: 0)
        StatItemHorizontal("管理员", (stats["admin"] as? Int) ?: 0)
    }
}

/**
 * 固定字段统计组件 - 反馈管理专用（二列布局）
 */
@Composable
private fun FeedbackManagementStatsPanel(stats: Map<String, Any>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatItemTwoColumn(
            "总反馈", (stats["total"] as? Int) ?: 0,
            "处理中", (stats["reviewing"] as? Int) ?: 0
        )
        StatItemTwoColumn(
            "待处理", (stats["submitted"] as? Int) ?: 0,
            "已解决", (stats["resolved"] as? Int) ?: 0
        )
    }
}

/**
 * 对话历史项组件 - 论坛帖子样式单行显示
 */
@Composable
private fun ConversationHistoryItem(
    conversation: SupportConversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：对话标题
        Text(
            text = conversation.conversationTitle,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 中间：时间
        Text(
            text = conversation.getFormattedLastMessageTime(),
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.widthIn(min = 80.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧：状态
        Text(
            text = conversation.getStatusText(),
            color = when (conversation.status) {
                "open" -> Color.Green
                "closed" -> Color.Gray
                "waiting" -> Color(0xFFFF9800)
                else -> Color.Gray
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 50.dp),
            textAlign = TextAlign.End
        )
    }

    // 分隔线
    Divider(
        color = Color.Gray.copy(alpha = 0.2f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

/**
 * 反馈开始内容 - 论坛版块模式
 */
@Composable
private fun FeedbackStartContent(
    onStartFeedback: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var feedbackStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var recentFeedbacks by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val uiState by supportViewModel.uiState.collectAsState()

    // 初始加载数据
    LaunchedEffect(Unit) {
        supportViewModel.getFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getUserFeedbacks { feedbacks ->
            recentFeedbacks = feedbacks
            isLoading = false
        }
    }

    // 监听反馈刷新触发器，当删除反馈后自动刷新数据
    LaunchedEffect(uiState.feedbackRefreshTrigger) {
        if (uiState.feedbackRefreshTrigger > 0) {
            Log.d("FeedbackStartContent", "检测到反馈数据变化，刷新反馈列表")
            isLoading = true
            supportViewModel.getFeedbackStats { stats ->
                feedbackStats = stats
            }
            supportViewModel.getUserFeedbacks { feedbacks ->
                recentFeedbacks = feedbacks
                isLoading = false
            }
        }
    }

    // 左右分栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {

            // 反馈帖子列表区域 - 论坛样式
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                } else if (recentFeedbacks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无反馈记录",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "点击右侧按钮提交您的第一个反馈",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(recentFeedbacks) { feedback ->
                        FeedbackForumItem(
                            feedback = feedback,
                            onClick = {
                                // 点击反馈标题显示详情弹窗
                                supportViewModel.showFeedbackDetail(feedback)
                            }
                        )
                    }
                }
            }
        }


        // 分隔线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧操作区域 - 垂直排列
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计信息区域 - 固定字段，只加载数据
            SubmitFeedbackStatsPanel(feedbackStats)

            Spacer(modifier = Modifier.height(8.dp))

            // 刷新按钮
            Button(
                onClick = {
                    supportViewModel.getFeedbackStats { stats ->
                        feedbackStats = stats
                    }
                    supportViewModel.getUserFeedbacks { feedbacks ->
                        recentFeedbacks = feedbacks
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄 刷新",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 我要反馈按钮
            Button(
                onClick = onStartFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Text(
                    text = "📝 我要反馈",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 快速反馈按钮
 */
@Composable
private fun QuickFeedbackButton(
    text: String,
    icon: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(40.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
        )
    ) {
        Text(
            text = "$icon $text",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 反馈论坛项组件 - 简洁单行论坛帖子样式（无卡片设计）
 */
@Composable
private fun FeedbackForumItem(
    feedback: UserFeedback,
    onClick: () -> Unit
) {
    Column {
        // 单行显示：标题-类型-状态-时间-回复数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题（占据主要空间）
            Text(
                text = feedback.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 类型
            Text(
                text = feedback.getTypeText(),
                color = Color(0xFF4285F4),
                fontSize = 12.sp,
                modifier = Modifier.weight(0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 状态
            Text(
                text = feedback.getStatusText(),
                color = when (feedback.status) {
                    UserFeedback.STATUS_SUBMITTED -> Color(0xFFFF9800)
                    UserFeedback.STATUS_REVIEWING -> Color(0xFF2196F3)
                    UserFeedback.STATUS_RESOLVED -> Color(0xFF4CAF50)
                    UserFeedback.STATUS_CLOSED -> Color(0xFF9E9E9E)
                    UserFeedback.STATUS_WITHDRAWN -> Color(0xFF9E9E9E)
                    else -> Color.Gray
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 提交时间
            Text(
                text = feedback.getFormattedCreatedTime(),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.weight(0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 回复数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "💬",
                    fontSize = 12.sp
                )
                Text(
                    text = if (!feedback.adminResponse.isNullOrBlank()) "1" else "0",
                    color = if (!feedback.adminResponse.isNullOrBlank()) Color(0xFF4285F4) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 分隔线
        Divider(
            color = Color.Gray.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * 反馈历史项组件 - 保留用于其他地方
 */
@Composable
private fun FeedbackHistoryItem(
    feedback: UserFeedback,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                // 状态标签
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (feedback.status) {
                            "resolved" -> Color.Green.copy(alpha = 0.3f)
                            "reviewing" -> Color.Blue.copy(alpha = 0.3f)
                            "submitted" -> Color(0xFFFF9800).copy(alpha = 0.3f)
                            "closed" -> Color.Gray.copy(alpha = 0.3f)
                            "withdrawn" -> Color.Gray.copy(alpha = 0.3f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = feedback.getStatusText(),
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = feedback.getTypeText(),
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp
                )
                Text(
                    text = feedback.getFormattedCreatedTime(),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * 我的反馈内容 - 论坛版块模式
 */
@Composable
private fun MyFeedbackContent(
    onViewFeedback: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var feedbackStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var myFeedbacks by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val uiState by supportViewModel.uiState.collectAsState()

    // 初始加载数据
    LaunchedEffect(Unit) {
        supportViewModel.getFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getUserFeedbacks { feedbacks ->
            myFeedbacks = feedbacks
            isLoading = false
        }
    }

    // 监听反馈刷新触发器，当删除反馈后自动刷新数据
    LaunchedEffect(uiState.feedbackRefreshTrigger) {
        if (uiState.feedbackRefreshTrigger > 0) {
            Log.d("MyFeedbackContent", "检测到反馈数据变化，刷新我的反馈列表")
            isLoading = true
            supportViewModel.getFeedbackStats { stats ->
                feedbackStats = stats
            }
            supportViewModel.getUserFeedbacks { feedbacks ->
                myFeedbacks = feedbacks
                isLoading = false
            }
        }
    }

    // 左右分栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {

        // 我的反馈帖子列表区域 - 论坛样式
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    }
                } else if (myFeedbacks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无反馈记录",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "您还没有提交过任何反馈",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(myFeedbacks) { feedback ->
                        MyFeedbackForumItem(
                            feedback = feedback,
                            onClick = {
                                // 点击帖子显示详情弹窗
                                supportViewModel.showFeedbackDetail(feedback)
                            }
                        )
                    }
                }
            }
        }


        // 分隔线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧操作区域 - 垂直排列
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计信息区域 - 固定字段，只加载数据
            MyFeedbackStatsPanel(feedbackStats)

            Spacer(modifier = Modifier.height(8.dp))

            // 刷新按钮
            Button(
                onClick = {
                    supportViewModel.getFeedbackStats { stats ->
                        feedbackStats = stats
                    }
                    supportViewModel.getUserFeedbacks { feedbacks ->
                        myFeedbacks = feedbacks
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄 刷新",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 查看全部反馈按钮
            Button(
                onClick = onViewFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Text(
                    text = "📋 查看全部反馈",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 我的反馈论坛项组件 - 简洁单行论坛帖子样式（无卡片设计）
 */
@Composable
private fun MyFeedbackForumItem(
    feedback: UserFeedback,
    onClick: () -> Unit
) {
    Column {
        // 单行显示：标题-类型-状态-时间-回复数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题（占据主要空间）
            Text(
                text = feedback.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 类型
            Text(
                text = feedback.getTypeText(),
                color = Color(0xFF4285F4),
                fontSize = 12.sp,
                modifier = Modifier.weight(0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 状态
            Text(
                text = feedback.getStatusText(),
                color = when (feedback.status) {
                    UserFeedback.STATUS_SUBMITTED -> Color(0xFFFF9800)
                    UserFeedback.STATUS_REVIEWING -> Color(0xFF2196F3)
                    UserFeedback.STATUS_RESOLVED -> Color(0xFF4CAF50)
                    UserFeedback.STATUS_CLOSED -> Color(0xFF9E9E9E)
                    UserFeedback.STATUS_WITHDRAWN -> Color(0xFF9E9E9E)
                    else -> Color.Gray
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 提交时间
            Text(
                text = feedback.getFormattedCreatedTime(),
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.weight(0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 回复数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "💬",
                    fontSize = 12.sp
                )
                Text(
                    text = if (!feedback.adminResponse.isNullOrBlank()) "1" else "0",
                    color = if (!feedback.adminResponse.isNullOrBlank()) Color(0xFF4285F4) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 分隔线
        Divider(
            color = Color.Gray.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * 我的反馈历史项组件
 */
@Composable
private fun MyFeedbackHistoryItem(
    feedback: UserFeedback,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 状态标签
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (feedback.status) {
                            "resolved" -> Color.Green.copy(alpha = 0.3f)
                            "reviewing" -> Color.Blue.copy(alpha = 0.3f)
                            "submitted" -> Color(0xFFFF9800).copy(alpha = 0.3f)
                            "closed" -> Color.Gray.copy(alpha = 0.3f)
                            "withdrawn" -> Color.Gray.copy(alpha = 0.3f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = feedback.getStatusText(),
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 类型和时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = feedback.getTypeText(),
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp
                )
                Text(
                    text = feedback.getFormattedCreatedTime(),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // 如果有管理员回复，显示提示
            if (!feedback.adminResponse.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💬",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "有新回复",
                        color = Color(0xFF4285F4),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 用户管理内容 - 优化版本
 */
@Composable
private fun UserManagementContent(
    onOpenUserManagement: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var userStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var allUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }

    // 加载数据的函数
    val loadData = {
        isLoading = true
        supportViewModel.getGlobalUserStats { stats ->
            userStats = stats
        }
        supportViewModel.getAllUsers { users ->
            allUsers = users
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // 过滤用户列表
    val filteredUsers = remember(allUsers, searchQuery) {
        if (searchQuery.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.username?.contains(searchQuery, ignoreCase = true) == true ||
                user.email?.contains(searchQuery, ignoreCase = true) == true ||
                user.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 左右分栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {

            // 用户列表区域
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // 用户列表表头 - 紧凑设计
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF2C3E50).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp), // 减少内边距
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "用户名",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp, // 减小字体
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = "邮箱",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(2.5f)
                        )
                        Text(
                            text = "角色",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = "操作",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (filteredUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "暂无用户数据" else "未找到匹配用户",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(filteredUsers) { user ->
                    CompactUserRow(
                        user = user,
                        onRoleChange = { newRole ->
                            supportViewModel.updateUserRole(user.id, newRole) { success ->
                                if (success) {
                                    loadData() // 重新加载数据
                                }
                            }
                        }
                    )
                }
            }
            }
        }

        // 分隔线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧操作区域 - 垂直排列
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计信息区域 - 固定字段，只加载数据
            UserManagementStatsPanel(userStats)

            Spacer(modifier = Modifier.height(8.dp))

            // 刷新按钮
            Button(
                onClick = { loadData() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄 刷新",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 搜索按钮
            Button(
                onClick = { showSearchField = !showSearchField },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34A853).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔍 搜索",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // 搜索框
            if (showSearchField) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "搜索用户...",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD700),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 10.sp),
                    shape = RoundedCornerShape(17.dp)
                )
            }
        }
    }
}

/**
 * 紧凑用户行组件 - 优化版本
 */
@Composable
private fun CompactUserRow(
    user: UserProfile,
    onRoleChange: (String) -> Unit
) {
    var showRoleMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF2C3E50).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp), // 最小化内边距
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 用户名
        Text(
            text = user.username ?: "未知用户",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 邮箱
        Text(
            text = user.email ?: "无邮箱",
            color = Color.White, // 修改为亮色
            fontSize = 11.sp,
            modifier = Modifier.weight(2.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 角色显示
        Text(
            text = getUserRoleDisplayName(user.roles),
            color = getUserRoleColor(user.roles),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Center
        )

        // 设置图标和下拉菜单
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { showRoleMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "⚙️",
                    fontSize = 12.sp
                )
            }

            // 角色管理下拉菜单
            DropdownMenu(
                expanded = showRoleMenu,
                onDismissRequest = { showRoleMenu = false },
                modifier = Modifier.background(Color(0xFF2C3E50))
            ) {
                val currentRoles = user.roles
                val roleOptions = listOf(
                    "super_admin" to "设为超级管理员",
                    "admin" to "设为管理员",
                    "support" to "设为客服",
                    "moderator" to "设为版主",
                    "vip" to "设为VIP用户",
                    "user" to "设为普通用户"
                )

                roleOptions.forEach { (roleType, displayName) ->
                    val isCurrentRole = currentRoles.contains(roleType)
                    val menuText = if (isCurrentRole && roleType != "user") {
                        "取消${displayName.substring(2)}" // 去掉"设为"前缀
                    } else {
                        displayName
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = menuText,
                                color = if (isCurrentRole) Color(0xFFFF6B6B) else Color.White,
                                fontSize = 12.sp
                            )
                        },
                        onClick = {
                            showRoleMenu = false
                            if (isCurrentRole && roleType != "user") {
                                // 取消当前角色，设为普通用户
                                onRoleChange("user")
                            } else {
                                // 设置新角色
                                onRoleChange(roleType)
                            }
                        }
                    )
                }

                // 删除用户选项
                Divider(color = Color.Gray)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "删除用户",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    },
                    onClick = {
                        showRoleMenu = false
                        // TODO: 实现删除用户功能
                    }
                )
            }
        }
    }
}

/**
 * 获取用户角色显示名称
 */
private fun getUserRoleDisplayName(roles: List<String>): String {
    return when {
        roles.contains("super_admin") -> "超管"
        roles.contains("admin") -> "管理"
        roles.contains("support") -> "客服"
        roles.contains("moderator") -> "版主"
        roles.contains("vip") -> "VIP"
        else -> "普通"
    }
}

/**
 * 获取用户角色颜色
 */
private fun getUserRoleColor(roles: List<String>): Color {
    return when {
        roles.contains("super_admin") -> Color.Red
        roles.contains("admin") -> Color.Blue
        roles.contains("support") -> Color.Green
        roles.contains("moderator") -> Color(0xFFFF9800)
        roles.contains("vip") -> Color(0xFFFFD700)
        else -> Color.Gray
    }
}

/**
 * 用户表格行组件 - 表格样式
 */
@Composable
private fun UserTableRow(
    index: Int,
    user: UserProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Text(
                text = index.toString(),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.Center
            )

            // 用户名
            Text(
                text = user.username ?: "未知用户",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 用户ID
            Text(
                text = user.id.take(8) + "...",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.weight(2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 在线状态
            Row(
                modifier = Modifier.weight(1.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = Color.Green, // 简化为固定颜色
                            shape = RoundedCornerShape(50)
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "在线", // 简化为固定状态
                    color = Color.Green,
                    fontSize = 12.sp
                )
            }

            // 角色
            Row(
                modifier = Modifier.weight(1.5f),
                horizontalArrangement = Arrangement.Center
            ) {
                if (user.roles.isNotEmpty()) {
                    Text(
                        text = when (user.roles.first()) {
                            "super_admin" -> "超管"
                            "admin" -> "管理"
                            "support" -> "客服"
                            "moderator" -> "版主"
                            "vip" -> "VIP"
                            else -> "普通"
                        },
                        color = when (user.roles.first()) {
                            "super_admin" -> Color.Red
                            "admin" -> Color.Blue
                            "support" -> Color.Green
                            "moderator" -> Color(0xFFFF9800)
                            "vip" -> Color(0xFFFFD700)
                            else -> Color.Gray
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "普通",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⚙️",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 用户列表项组件
 */
@Composable
private fun UserListItem(
    user: UserProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.username ?: "未知用户",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // VIP状态标签
                if (user.isVip) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFD700).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "VIP",
                            color = Color(0xFFFFD700),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 邮箱和注册时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = user.email ?: "无邮箱",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.getFormattedCreatedTime(),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // 用户角色
            if (user.roles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    user.roles.take(3).forEach { role ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (role) {
                                    "super_admin" -> Color.Red.copy(alpha = 0.3f)
                                    "admin" -> Color.Blue.copy(alpha = 0.3f)
                                    "support" -> Color.Green.copy(alpha = 0.3f)
                                    "moderator" -> Color(0xFFFF9800).copy(alpha = 0.3f)
                                    else -> Color.Gray.copy(alpha = 0.3f)
                                }
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = when (role) {
                                    "super_admin" -> "超管"
                                    "admin" -> "管理"
                                    "support" -> "客服"
                                    "moderator" -> "版主"
                                    "vip" -> "VIP"
                                    else -> role
                                },
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用户管理详细界面
 */
@Composable
private fun UserManagementScreen(
    viewModel: SupportViewModel,
    onClose: () -> Unit
) {
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        viewModel.getAllUsers { userList ->
            users = userList
            isLoading = false
        }
    }

    // 过滤用户
    val filteredUsers = users.filter { user ->
        val matchesSearch = searchQuery.isEmpty() ||
            user.username?.contains(searchQuery, ignoreCase = true) == true ||
            user.email?.contains(searchQuery, ignoreCase = true) == true

        val matchesRole = selectedRole == "all" || user.roles.contains(selectedRole)

        matchesSearch && matchesRole
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "用户管理",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.7f)
                )
            ) {
                Text("关闭", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 搜索和过滤栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜索用户", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray
                )
            )

            // 角色过滤
            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        text = when (selectedRole) {
                            "all" -> "全部角色"
                            "super_admin" -> "超级管理员"
                            "admin" -> "管理员"
                            "support" -> "客服"
                            "moderator" -> "版主"
                            "vip" -> "VIP用户"
                            else -> selectedRole
                        },
                        color = Color.White
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listOf(
                        "all" to "全部角色",
                        "super_admin" to "超级管理员",
                        "admin" to "管理员",
                        "support" to "客服",
                        "moderator" to "版主",
                        "vip" to "VIP用户"
                    ).forEach { (role, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedRole = role
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 用户列表
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFD700))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredUsers) { user ->
                        UserManagementItem(
                            user = user,
                            onRoleChange = { newRole ->
                                viewModel.updateUserRole(user.id, newRole) { success ->
                                    if (success) {
                                        // 重新加载用户列表
                                        viewModel.getAllUsers { userList ->
                                            users = userList
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 用户管理项组件
 */
@Composable
private fun UserManagementItem(
    user: UserProfile,
    onRoleChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 用户基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.username ?: "未知用户",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.email ?: "无邮箱",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }

                // VIP状态
                if (user.isVip) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFD700).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "VIP",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 用户详细信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "注册时间: ${user.getFormattedCreatedTime()}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "ID: ${user.id.take(8)}...",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 当前角色显示
            Text(
                text = "当前角色:",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(user.roles) { role ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (role) {
                                "super_admin" -> Color.Red.copy(alpha = 0.4f)
                                "admin" -> Color.Blue.copy(alpha = 0.4f)
                                "support" -> Color.Green.copy(alpha = 0.4f)
                                "moderator" -> Color(0xFFFF9800).copy(alpha = 0.4f)
                                "vip" -> Color(0xFFFFD700).copy(alpha = 0.4f)
                                else -> Color.Gray.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (role) {
                                "super_admin" -> "超级管理员"
                                "admin" -> "管理员"
                                "support" -> "客服"
                                "moderator" -> "版主"
                                "vip" -> "VIP"
                                else -> role
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 角色管理按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val availableRoles = listOf(
                    "vip" to "VIP",
                    "moderator" to "版主",
                    "support" to "客服",
                    "admin" to "管理员"
                )

                availableRoles.forEach { (role, label) ->
                    val hasRole = user.roles.contains(role)
                    Button(
                        onClick = { onRoleChange(role) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasRole) {
                                Color.Red.copy(alpha = 0.7f)
                            } else {
                                Color(0xFF4285F4).copy(alpha = 0.7f)
                            }
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (hasRole) "移除$label" else "添加$label",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 反馈管理内容 - 论坛版块模式
 */
@Composable
private fun FeedbackManagementContent(
    supportViewModel: SupportViewModel
) {
    var feedbackStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var feedbackList by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var selectedFeedback by remember { mutableStateOf<UserFeedback?>(null) }
    var showReplyDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }

    val uiState by supportViewModel.uiState.collectAsState()

    // 初始加载数据
    LaunchedEffect(Unit) {
        supportViewModel.getAllFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getAllFeedbacks { feedbacks ->
            feedbackList = feedbacks
        }
    }

    // 监听反馈刷新触发器，当删除反馈后自动刷新数据
    LaunchedEffect(uiState.feedbackRefreshTrigger) {
        if (uiState.feedbackRefreshTrigger > 0) {
            Log.d("FeedbackMgmtContent", "检测到反馈数据变化，刷新反馈管理列表")
            supportViewModel.getAllFeedbackStats { stats ->
                feedbackStats = stats
            }
            supportViewModel.getAllFeedbacks { feedbacks ->
                feedbackList = feedbacks
            }
        }
    }

    // 过滤反馈列表
    val filteredFeedbacks = remember(feedbackList, filterStatus, searchQuery) {
        feedbackList.filter { feedback ->
            val matchesStatus = filterStatus == "all" || feedback.status == filterStatus
            val matchesSearch = searchQuery.isEmpty() ||
                feedback.title.contains(searchQuery, ignoreCase = true) ||
                feedback.description.contains(searchQuery, ignoreCase = true) ||
                feedback.feedbackType.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesSearch
        }
    }

    // 左右分栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {



            // 反馈帖子列表区域 - 论坛样式
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (filteredFeedbacks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无反馈记录",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "没有符合条件的反馈",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredFeedbacks) { feedback ->
                    FeedbackItem(
                        feedback = feedback,
                        onReply = {
                            // 使用ViewModel显示管理员回复弹窗
                            supportViewModel.showAdminReplyDialog(feedback)
                        },
                        onClick = {
                            // 使用ViewModel显示反馈详情弹窗，将在FullScreenDialogs中显示
                            supportViewModel.showFeedbackDetail(feedback)
                        }
                    )
                }
            }
            }
        }

        // 分隔线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧操作区域 - 垂直排列
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计信息区域 - 固定字段，只加载数据
            FeedbackManagementStatsPanel(feedbackStats)

            Spacer(modifier = Modifier.height(8.dp))

            // 筛选按钮组 - 改为二列排版
            val statusOptions = listOf(
                listOf("all" to "全部", "resolved" to "已解决"),
                listOf("submitted" to "待处理", "closed" to "已关闭"),
                listOf("reviewing" to "处理中", "refresh" to "刷新")
            )

            statusOptions.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowOptions.forEach { (status, label) ->
                        Button(
                            onClick = {
                                if (status == "refresh") {
                                    supportViewModel.getAllFeedbackStats { stats ->
                                        feedbackStats = stats
                                    }
                                    supportViewModel.getAllFeedbacks { feedbacks ->
                                        feedbackList = feedbacks
                                    }
                                } else {
                                    filterStatus = status
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (status == "refresh") {
                                    Color(0xFF4285F4).copy(alpha = 0.8f)
                                } else if (filterStatus == status) {
                                    Color(0xFFFFD700)
                                } else {
                                    Color(0xFF4285F4).copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Text(
                                text = if (status == "refresh") "🔄" else label,
                                color = if (status == "refresh" || filterStatus != status) Color.White else Color.Black,
                                fontSize = 10.sp,
                                fontWeight = if (filterStatus == status) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // 注意：反馈详情弹窗和管理员回复弹窗已移至FullScreenDialogs组件中统一管理，占整个应用屏幕95%
}
















/**
 * 统计项组件 - 简化版本
 */
@Composable
fun SupportStatItem(
    title: String,
    value: String,
    icon: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}


/**
 * 客服工作台内容
 */
@Composable
fun SupportDeskContent(
    supportViewModel: SupportViewModel
) {
    var deskStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var pendingConversations by remember { mutableStateOf<List<SupportConversationDisplay>>(emptyList()) }
    var recentFeedbacks by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("overview") }

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 数据加载
    LaunchedEffect(Unit) {
        try {
            supportViewModel.getSupportDeskStats { stats ->
                deskStats = stats
            }
            supportViewModel.getPendingConversations { conversations ->
                pendingConversations = conversations
            }
            supportViewModel.getRecentFeedbacks { feedbacks ->
                recentFeedbacks = feedbacks
                isLoading = false
            }
        } catch (e: Exception) {
            loadError = "加载数据失败: ${e.message}"
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFFFD700))
        }
        return
    }

    if (loadError != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = loadError!!,
                color = Color.Red,
                fontSize = 16.sp
            )
        }
        return
    }

    // 左右分栏布局
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 左侧主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (selectedTab) {
                "overview" -> SupportDeskOverview(deskStats)
                "conversations" -> ConversationManagement(pendingConversations)
                "feedbacks" -> FeedbackProcessing(recentFeedbacks)
            }
        }

        // 分隔线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧操作区域 - 垂直居中三个按钮
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tabs = listOf(
                "overview" to "概览",
                "conversations" to "对话管理",
                "feedbacks" to "反馈处理"
            )

            tabs.forEach { (tabId, tabName) ->
                Button(
                    onClick = { selectedTab = tabId },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == tabId)
                            Color(0xFFFFD700)
                        else Color(0xFF4285F4).copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Text(
                        text = tabName,
                        color = if (selectedTab == tabId) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == tabId) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }

                // 添加按钮间距
                if (tabId != "feedbacks") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 反馈项组件
 */
@Composable
fun FeedbackItem(
    feedback: UserFeedback,
    onReply: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick?.invoke() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feedback.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = feedback.getFormattedCreatedTime(),
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "💬",
                    fontSize = 14.sp
                )
                Text(
                    text = if (!feedback.adminResponse.isNullOrBlank()) "1" else "0",
                    color = if (!feedback.adminResponse.isNullOrBlank()) Color(0xFF4285F4) else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onReply,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("回复", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/**
 * 客服工作台概览组件
 */
@Composable
private fun SupportDeskOverview(stats: Map<String, Any>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "工作台概览",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (stats.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val statsToShow = listOf(
                    "active_conversations" to "活跃对话",
                    "pending_conversations" to "待处理对话",
                    "resolved_today" to "今日已解决",
                    "online_agents" to "在线客服",
                    "total_agents" to "总客服数",
                    "customer_satisfaction" to "满意度"
                )

                statsToShow.forEach { (key, label) ->
                    val value = stats[key]
                    if (value != null) {
                        item {
                            SupportStatItem(
                                title = label,
                                value = when (key) {
                                    "customer_satisfaction" -> "${value}★"
                                    else -> value.toString()
                                },
                                icon = when (key) {
                                    "active_conversations" -> "💬"
                                    "pending_conversations" -> "⏳"
                                    "resolved_today" -> "✅"
                                    "online_agents" -> "👥"
                                    "total_agents" -> "👤"
                                    "customer_satisfaction" -> "⭐"
                                    else -> "📊"
                                },
                                color = when (key) {
                                    "pending_conversations" -> Color(0xFFFF6B6B)
                                    "resolved_today" -> Color(0xFF4ECDC4)
                                    "online_agents" -> Color(0xFF45B7D1)
                                    else -> Color(0xFFFFD700)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "暂无统计数据",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * 对话管理组件
 */
@Composable
private fun ConversationManagement(conversations: List<SupportConversationDisplay>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "待处理对话 (${conversations.size})",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (conversations.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations) { conversation ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = conversation.conversationTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when(conversation.priority) {
                                        "urgent" -> "紧急"
                                        "high" -> "高"
                                        "normal" -> "普通"
                                        "low" -> "低"
                                        else -> "普通"
                                    },
                                    color = when(conversation.priority) {
                                        "urgent" -> Color.Red
                                        "high" -> Color(0xFFFF9800)
                                        else -> Color.Gray
                                    },
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = conversation.lastMessage,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "暂无待处理对话",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * 反馈处理组件
 */
@Composable
private fun FeedbackProcessing(feedbacks: List<UserFeedback>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "最近反馈 (${feedbacks.size})",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (feedbacks.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(feedbacks) { feedback ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = feedback.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when(feedback.status) {
                                        "submitted" -> "待处理"
                                        "reviewing" -> "处理中"
                                        "resolved" -> "已解决"
                                        "closed" -> "已关闭"
                                        "withdrawn" -> "已撤回"
                                        else -> "未知"
                                    },
                                    color = when(feedback.status) {
                                        "submitted" -> Color(0xFFFF9800)
                                        "reviewing" -> Color(0xFF4285F4)
                                        "resolved" -> Color.Green
                                        else -> Color.Gray
                                    },
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = feedback.feedbackType,
                                color = Color(0xFFFFD700),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "暂无最近反馈",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * 用户消息提示组件
 */
@Composable
private fun UserMessageToast(
    message: String,
    messageType: UserMessageType,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (messageType) {
        UserMessageType.SUCCESS -> Color(0xFF4CAF50)
        UserMessageType.ERROR -> Color(0xFFF44336)
        UserMessageType.WARNING -> Color(0xFFFF9800)
        UserMessageType.INFO -> Color(0xFF2196F3)
    }

    Card(
        modifier = modifier
            .padding(16.dp)
            .clickable { onDismiss() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}

/**
 * 反馈详情弹窗组件 - 优化小屏幕空间利用
 */
@Composable
fun FeedbackDetailDialog(
    feedback: UserFeedback,
    onClose: () -> Unit,
    onWithdraw: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "反馈详情",
                color = Color(0xFFFFD700),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.7f)
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Text("关闭", color = Color.White, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 滚动内容区域
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // 反馈基本信息 - 单行显示标题、类型、状态、提交时间
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    // 标题
                    Text(
                        text = feedback.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 类型、状态、提交时间在一行显示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = feedback.getTypeText(),
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = feedback.getStatusText(),
                            color = when (feedback.status) {
                                UserFeedback.STATUS_SUBMITTED -> Color(0xFFFFD700)
                                UserFeedback.STATUS_REVIEWING -> Color(0xFF2196F3)
                                UserFeedback.STATUS_RESOLVED -> Color(0xFF4CAF50)
                                UserFeedback.STATUS_CLOSED -> Color(0xFF9E9E9E)
                                UserFeedback.STATUS_WITHDRAWN -> Color(0xFF9E9E9E)
                                else -> Color.Gray
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = feedback.getFormattedCreatedTime(),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // 分割线
                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                // 反馈内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "反馈内容",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = feedback.description,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }

                // 分割线
                if (!feedback.adminResponse.isNullOrBlank()) {
                    Divider(
                        color = Color.Gray.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            // 如果有回复内容，显示回复
            if (!feedback.adminResponse.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "管理员回复",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = feedback.adminResponse!!,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // 底部操作按钮 - 紧凑布局
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 撤销反馈按钮（仅在未处理状态下显示）
            if (feedback.status == UserFeedback.STATUS_SUBMITTED) {
                Button(
                    onClick = { onWithdraw(feedback.id) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("撤销", color = Color.White, fontSize = 12.sp)
                }
            }

            // 删除反馈按钮
            Button(
                onClick = { onDelete(feedback.id) },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("删除", color = Color.White, fontSize = 12.sp)
            }

            // 关闭按钮
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("关闭", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/**
 * 反馈详情弹窗 - 占据85%屏幕
 */
@Composable
fun FeedbackDetailDialog(
    feedback: UserFeedback,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp), // 7.5%的边距 (15%/2)
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "反馈详情",
                            color = Color(0xFFFFD700),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(
                                text = "✕",
                                color = Color.Gray,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 反馈内容区域
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 基本信息
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoRow("标题", feedback.title)
                                InfoRow("类型", feedback.feedbackType)
                                InfoRow("状态", feedback.status)
                                InfoRow("提交时间", feedback.getFormattedCreatedTime())
                                InfoRow("用户ID", feedback.userId)
                            }
                        }

                        // 反馈内容
                        item {
                            Column {
                                Text(
                                    text = "反馈内容",
                                    color = Color(0xFFFFD700),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text(
                                        text = feedback.description,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        // 管理员回复
                        if (!feedback.adminResponse.isNullOrBlank()) {
                            item {
                                Column {
                                    Text(
                                        text = "管理员回复",
                                        color = Color(0xFFFFD700),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF4285F4).copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Text(
                                            text = feedback.adminResponse!!,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 底部操作按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 回复按钮
                        Button(
                            onClick = onReply,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4285F4)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("回复", color = Color.White, fontSize = 14.sp)
                        }

                        // 删除按钮
                        Button(
                            onClick = { onDelete(feedback.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("删除", color = Color.White, fontSize = 14.sp)
                        }

                        // 关闭按钮
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("关闭", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 管理员回复弹窗内容
 */
@Composable
fun AdminReplyDialogContent(
    feedback: UserFeedback,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // 标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "回复反馈",
                color = Color(0xFFFFD700),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Text(
                    text = "✕",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 反馈信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feedback.description,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 回复输入框
        Text(
            text = "回复内容",
            color = Color(0xFFFFD700),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = replyText,
            onValueChange = onReplyTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    "请输入回复内容...",
                    color = Color.Gray
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFD700),
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("取消", color = Color.White, fontSize = 14.sp)
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4)
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = replyText.isNotBlank()
            ) {
                Text("确认回复", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

/**
 * 全屏弹窗组件 - 占整个应用屏幕95%
 * 使用全局弹窗系统，真正占据整个应用屏幕
 */
@Composable
fun SupportFullScreenDialogs(
    uiState: SupportUiState,
    supportViewModel: SupportViewModel
) {
    // 聊天窗口弹窗 - 使用全局弹窗系统，真正占据整个应用屏幕95%，居中显示
    SimplePopup(
        visibleProvider = { uiState.showConversation },
        onDismissRequest = { supportViewModel.hideConversation() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.95f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                SupportConversationScreen(
                    viewModel = supportViewModel,
                    onClose = { supportViewModel.hideConversation() }
                )
            }
        }
    }

    // 反馈表单弹窗 - 使用全局弹窗系统，真正占据整个应用屏幕95%，居中显示
    SimplePopup(
        visibleProvider = { uiState.showFeedbackForm },
        onDismissRequest = { supportViewModel.hideFeedbackForm() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.95f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                FeedbackFormScreen(
                    viewModel = supportViewModel,
                    onClose = { supportViewModel.hideFeedbackForm() }
                )
            }
        }
    }

    // 反馈详情弹窗 - 使用全局弹窗系统，真正占据整个应用屏幕95%，居中显示
    SimplePopup(
        visibleProvider = { uiState.showFeedbackDetail },
        onDismissRequest = { supportViewModel.hideFeedbackDetail() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.95f)
                    .background(
                        color = Color(0xFF1A1A1A).copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.Gray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                uiState.selectedFeedback?.let { feedback ->
                    FeedbackDetailDialog(
                        feedback = feedback,
                        onClose = { supportViewModel.hideFeedbackDetail() },
                        onWithdraw = { feedbackId ->
                            supportViewModel.withdrawFeedback(feedbackId)
                            // ViewModel会自动关闭弹窗并刷新列表
                        },
                        onDelete = { feedbackId ->
                            supportViewModel.deleteFeedback(feedbackId)
                            // ViewModel会自动关闭弹窗并刷新列表
                        }
                    )
                }
            }
        }
    }

    // 反馈列表弹窗 - 使用全局弹窗系统，真正占据整个应用屏幕95%，居中显示
    SimplePopup(
        visibleProvider = { uiState.showFeedbackList },
        onDismissRequest = { supportViewModel.hideFeedbackList() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.95f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                FeedbackListScreen(
                    viewModel = supportViewModel,
                    onClose = { supportViewModel.hideFeedbackList() }
                )
            }
        }
    }

    // 管理员回复弹窗 - 使用全局弹窗系统，真正占据整个应用屏幕95%，居中显示
    SimplePopup(
        visibleProvider = { uiState.showAdminReplyDialog },
        onDismissRequest = { supportViewModel.hideAdminReplyDialog() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(0.95f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                uiState.selectedFeedback?.let { feedback ->
                    AdminReplyDialogContent(
                        feedback = feedback,
                        replyText = uiState.adminReplyText,
                        onReplyTextChange = { supportViewModel.updateAdminReplyText(it) },
                        onConfirm = {
                            supportViewModel.replyToFeedback(
                                feedback.id,
                                uiState.adminReplyText
                            ) { success ->
                                if (success) {
                                    supportViewModel.hideAdminReplyDialog()
                                    // 触发反馈数据刷新
                                    supportViewModel.triggerFeedbackRefresh()
                                }
                            }
                        },
                        onDismiss = { supportViewModel.hideAdminReplyDialog() }
                    )
                }
            }
        }
    }
}

