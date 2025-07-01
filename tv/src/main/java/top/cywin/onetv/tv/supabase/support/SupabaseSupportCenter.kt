package top.cywin.onetv.tv.supabase.support

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
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
    supportViewModel: SupportViewModel = viewModel()
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
            .width(320.dp)
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
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
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
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
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
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
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
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(24.dp)
    ) {
        when (selectedMenuItem) {
            "user_info" -> UserInfoContent(userData, supportViewModel)
            "chat" -> {
                if (uiState.showConversation) {
                    SupportConversationScreen(
                        viewModel = supportViewModel,
                        onClose = { supportViewModel.hideConversation() }
                    )
                } else {
                    ChatStartContent(
                        onStartChat = { supportViewModel.startSupportConversation() },
                        supportViewModel = supportViewModel
                    )
                }
            }
            "submit_feedback" -> {
                if (uiState.showFeedbackForm) {
                    FeedbackFormScreen(
                        viewModel = supportViewModel,
                        onClose = { supportViewModel.hideFeedbackForm() }
                    )
                } else {
                    FeedbackStartContent(
                        onStartFeedback = { supportViewModel.showFeedbackForm() },
                        supportViewModel = supportViewModel
                    )
                }
            }
            "my_feedback" -> {
                if (uiState.showFeedbackList) {
                    FeedbackListScreen(
                        viewModel = supportViewModel,
                        onClose = { supportViewModel.hideFeedbackList() }
                    )
                } else {
                    MyFeedbackContent(
                        onViewFeedback = { supportViewModel.showFeedbackList() },
                        supportViewModel = supportViewModel
                    )
                }
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
            else -> DefaultContent()
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 用户基本信息
        item {
            UserInfoCard(
                title = "用户信息",
                content = {
                    InfoRow("用户名", userData.username ?: "未设置")
                    InfoRow("邮箱", userData.email ?: "未设置")
                    InfoRow("VIP状态", if (userData.is_vip == true) "VIP用户" else "普通用户")
                    InfoRow("账户状态", userData.accountstatus ?: "未知")
                    InfoRow("注册时间", userData.created_at ?: "未知")
                }
            )
        }

        // 权限信息
        item {
            UserInfoCard(
                title = "当前角色",
                content = {
                    if (userRoles.isNotEmpty()) {
                        val roleNames = mutableListOf<String>()
                        if (userRoles.contains("super_admin")) roleNames.add("超级管理员")
                        if (userRoles.contains("admin")) roleNames.add("管理员")
                        if (userRoles.contains("support")) roleNames.add("客服")
                        if (userRoles.contains("moderator")) roleNames.add("版主")
                        if (userRoles.contains("vip")) roleNames.add("VIP用户")
                        if (roleNames.isEmpty()) roleNames.add("普通用户")

                        Text(
                            text = roleNames.joinToString("、"),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    } else {
                        Text(
                            text = "加载中...",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        }

        // 使用统计
        item {
            UserInfoCard(
                title = "使用统计",
                content = {
                    InfoRow("观看时长", (userStats["watchTime"] as? Int)?.let { "${it}小时" } ?: "统计中...")
                    InfoRow("频道收藏", (userStats["favoriteChannels"] as? Int)?.let { "${it}个" } ?: "统计中...")
                    InfoRow("反馈提交", (userStats["feedbackCount"] as? Int)?.let { "${it}次" } ?: "统计中...")
                    InfoRow("活跃天数", (userStats["activeDays"] as? Int)?.let { "${it}天" } ?: "统计中...")
                }
            )
        }

        // 设备信息
        item {
            UserInfoCard(
                title = "设备信息",
                content = {
                    InfoRow("设备类型", "Android TV")
                    InfoRow("应用版本", (userStats["appVersion"] as? String) ?: "未知")
                    InfoRow("系统版本", (userStats["systemVersion"] as? String) ?: "未知")
                    InfoRow("设备型号", (userStats["deviceModel"] as? String) ?: "未知")
                }
            )
        }

        // 快捷操作
        item {
            UserInfoCard(
                title = "快捷操作",
                content = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionButton(
                            text = "刷新用户信息",
                            onClick = {
                                supportViewModel.refreshUserInfo()
                            }
                        )

                        QuickActionButton(
                            text = "清除缓存",
                            onClick = {
                                supportViewModel.clearUserCache()
                            }
                        )

                        if (userRoles.any { it in listOf("admin", "super_admin") }) {
                            QuickActionButton(
                                text = "查看系统日志",
                                onClick = {
                                    supportViewModel.viewSystemLogs()
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * 用户信息卡片组件
 */
@Composable
private fun UserInfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
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
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2C3E50).copy(alpha = 0.2f),
                            Color(0xFF1A1A1A).copy(alpha = 0.2f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    ),
                    shape = RoundedCornerShape(16.dp)
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
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    color = Color(0xFFFFD700),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                content()
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
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
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

/**
 * 聊天开始内容
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左侧：开始对话卡片
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "客服对话",
                        color = Color(0xFFFFD700),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "与客服进行一对一实时对话\n获得专业的技术支持和服务",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // 统计信息
                    if (conversationStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            StatItem("总对话", conversationStats["total"] ?: 0)
                            StatItem("进行中", conversationStats["open"] ?: 0)
                            StatItem("已完成", conversationStats["closed"] ?: 0)
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                    Button(
                        onClick = onStartChat,
                        modifier = Modifier
                            .height(50.dp)
                            .width(160.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700)
                        )
                    ) {
                        Text(
                            text = "开始对话",
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 右侧：对话历史
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 8.dp,
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
                        ),
                        shape = RoundedCornerShape(16.dp)
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
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "对话历史",
                        color = Color(0xFFFFD700),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    } else if (conversationHistory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                                    text = "开始您的第一次客服对话",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(conversationHistory.take(10)) { conversation ->
                                ConversationHistoryItem(
                                    conversation = conversation,
                                    onClick = {
                                        // 这里可以添加点击查看历史对话的逻辑
                                        onStartChat()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 统计项组件
 */
@Composable
private fun StatItem(label: String, value: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            color = Color(0xFFFFD700),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

/**
 * 对话历史项组件
 */
@Composable
private fun ConversationHistoryItem(
    conversation: SupportConversation,
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
                    text = conversation.conversationTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                // 状态标签
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (conversation.status) {
                            "open" -> Color.Green.copy(alpha = 0.3f)
                            "closed" -> Color.Gray.copy(alpha = 0.3f)
                            "waiting" -> Color(0xFFFF9800).copy(alpha = 0.3f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = conversation.getStatusText(),
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.getFormattedLastMessageTime(),
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 反馈开始内容
 */
@Composable
private fun FeedbackStartContent(
    onStartFeedback: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var feedbackStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var recentFeedbacks by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        supportViewModel.getFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getUserFeedbacks { feedbacks ->
            recentFeedbacks = feedbacks.take(5)
            isLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左侧：提交反馈卡片
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "提交反馈",
                        color = Color(0xFFFFD700),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "向我们反馈问题、建议或意见\n帮助我们改进产品和服务",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // 反馈统计
                    if (feedbackStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            StatItem("已提交", feedbackStats["total"] ?: 0)
                            StatItem("处理中", feedbackStats["reviewing"] ?: 0)
                            StatItem("已解决", feedbackStats["resolved"] ?: 0)
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // 快速反馈按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickFeedbackButton(
                            text = "问题报告",
                            icon = "🐛",
                            onClick = {
                                // 这里可以预设反馈类型
                                onStartFeedback()
                            }
                        )
                        QuickFeedbackButton(
                            text = "功能建议",
                            icon = "💡",
                            onClick = { onStartFeedback() }
                        )
                        QuickFeedbackButton(
                            text = "一般反馈",
                            icon = "💬",
                            onClick = { onStartFeedback() }
                        )
                    }
                }
            }
        }

        // 右侧：反馈历史
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 8.dp,
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
                        ),
                        shape = RoundedCornerShape(16.dp)
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
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "反馈历史",
                        color = Color(0xFFFFD700),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    } else if (recentFeedbacks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                                    text = "提交您的第一个反馈",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentFeedbacks) { feedback ->
                                FeedbackHistoryItem(
                                    feedback = feedback,
                                    onClick = {
                                        // 这里可以添加查看反馈详情的逻辑
                                    }
                                )
                            }
                        }
                    }
                }
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
 * 反馈历史项组件
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
 * 我的反馈内容
 */
@Composable
private fun MyFeedbackContent(
    onViewFeedback: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var feedbackStats by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var recentFeedbacks by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        supportViewModel.getFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getUserFeedbacks { feedbacks ->
            recentFeedbacks = feedbacks.take(5)
            isLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左侧：我的反馈概览
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "我的反馈",
                        color = Color(0xFFFFD700),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "查看您提交的反馈记录\n跟踪处理状态和回复",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // 反馈统计
                    if (feedbackStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            StatItem("总数", feedbackStats["total"] ?: 0)
                            StatItem("处理中", feedbackStats["reviewing"] ?: 0)
                            StatItem("已解决", feedbackStats["resolved"] ?: 0)
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // 快速操作按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onViewFeedback,
                            modifier = Modifier
                                .width(200.dp)
                                .height(45.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700)
                            )
                        ) {
                            Text(
                                text = "📋 查看全部反馈",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                // 刷新反馈数据
                                supportViewModel.getFeedbackStats { stats ->
                                    feedbackStats = stats
                                }
                                supportViewModel.getUserFeedbacks { feedbacks ->
                                    recentFeedbacks = feedbacks.take(5)
                                }
                            },
                            modifier = Modifier
                                .width(200.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                            )
                        ) {
                            Text(
                                text = "🔄 刷新数据",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 右侧：最近反馈列表
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 8.dp,
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
                        ),
                        shape = RoundedCornerShape(16.dp)
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
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "最近反馈",
                        color = Color(0xFFFFD700),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    } else if (recentFeedbacks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentFeedbacks) { feedback ->
                                MyFeedbackHistoryItem(
                                    feedback = feedback,
                                    onClick = {
                                        // 点击查看详情，可以跳转到完整列表
                                        onViewFeedback()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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
 * 用户管理内容
 */
@Composable
private fun UserManagementContent(
    onOpenUserManagement: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var userStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var recentUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        supportViewModel.getUserStats { stats ->
            userStats = stats
        }
        supportViewModel.getRecentUsers { users ->
            recentUsers = users.take(5)
            isLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 左侧：用户管理概览
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "用户管理",
                        color = Color(0xFFFFD700),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "管理系统用户和权限\n查看用户信息和角色分配",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // 用户统计
                    if (userStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            StatItem("总用户", (userStats["total"] as? Int) ?: 0)
                            StatItem("VIP用户", (userStats["vip"] as? Int) ?: 0)
                            StatItem("管理员", (userStats["admin"] as? Int) ?: 0)
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // 管理操作按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onOpenUserManagement,
                            modifier = Modifier
                                .width(200.dp)
                                .height(45.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700)
                            )
                        ) {
                            Text(
                                text = "👥 用户管理",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                // 刷新用户数据
                                supportViewModel.getUserStats { stats ->
                                    userStats = stats
                                }
                                supportViewModel.getRecentUsers { users ->
                                    recentUsers = users.take(5)
                                }
                            },
                            modifier = Modifier
                                .width(200.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                            )
                        ) {
                            Text(
                                text = "🔄 刷新数据",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 右侧：最近用户列表
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .shadow(
                    elevation = 8.dp,
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
                        ),
                        shape = RoundedCornerShape(16.dp)
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
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "最近用户",
                        color = Color(0xFFFFD700),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFFD700)
                            )
                        }
                    } else if (recentUsers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无用户数据",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "系统中还没有用户",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentUsers) { user ->
                                UserListItem(
                                    user = user,
                                    onClick = {
                                        // 点击查看用户详情，可以跳转到完整管理界面
                                        onOpenUserManagement()
                                    }
                                )
                            }
                        }
                    }
                }
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
 * 反馈管理内容
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

    // 加载数据
    LaunchedEffect(Unit) {
        supportViewModel.getAllFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getAllFeedbacks { feedbacks ->
            feedbackList = feedbacks
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 左侧：统计信息和筛选
        Card(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                item {
                    Text(
                        text = "反馈管理",
                        color = Color(0xFFFFD700),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // 统计信息
                item {
                    FeedbackStatsSection(
                        title = "反馈统计",
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoRow("总反馈", (feedbackStats["total"] as? Int)?.toString() ?: "0")
                                InfoRow("待处理", (feedbackStats["submitted"] as? Int)?.toString() ?: "0")
                                InfoRow("处理中", (feedbackStats["reviewing"] as? Int)?.toString() ?: "0")
                                InfoRow("已解决", (feedbackStats["resolved"] as? Int)?.toString() ?: "0")
                                InfoRow("已关闭", (feedbackStats["closed"] as? Int)?.toString() ?: "0")
                            }
                        }
                    )
                }

                // 优先级统计
                item {
                    FeedbackStatsSection(
                        title = "优先级分布",
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoRow("高优先级", (feedbackStats["high_priority"] as? Int)?.toString() ?: "0")
                                InfoRow("普通优先级", (feedbackStats["normal_priority"] as? Int)?.toString() ?: "0")
                                InfoRow("低优先级", (feedbackStats["low_priority"] as? Int)?.toString() ?: "0")
                            }
                        }
                    )
                }

                // 筛选选项
                item {
                    FeedbackStatsSection(
                        title = "筛选选项",
                        content = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val statusOptions = listOf(
                                    "all" to "全部",
                                    "submitted" to "待处理",
                                    "reviewing" to "处理中",
                                    "resolved" to "已解决",
                                    "closed" to "已关闭"
                                )

                                statusOptions.forEach { (value, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { filterStatus = value }
                                            .background(
                                                if (filterStatus == value)
                                                    Color(0xFFD4AF37).copy(alpha = 0.2f)
                                                else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (filterStatus == value)
                                                Color(0xFFFFD700)
                                            else Color.White.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        // 右侧：反馈列表
        Card(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索反馈", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("输入关键词搜索...", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD4AF37),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                        cursorColor = Color(0xFFD4AF37)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // 反馈列表
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredFeedbacks) { feedback ->
                        FeedbackManagementItem(
                            feedback = feedback,
                            onStatusChange = { newStatus ->
                                supportViewModel.updateFeedbackStatus(feedback.id, newStatus) { success ->
                                    if (success) {
                                        // 重新加载反馈列表
                                        supportViewModel.getAllFeedbacks { feedbacks ->
                                            feedbackList = feedbacks
                                        }
                                    }
                                }
                            },
                            onReply = {
                                selectedFeedback = feedback
                                showReplyDialog = true
                            }
                        )
                    }

                    if (filteredFeedbacks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "未找到匹配的反馈" else "暂无反馈数据",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 回复对话框
    if (showReplyDialog && selectedFeedback != null) {
        AlertDialog(
            onDismissRequest = {
                showReplyDialog = false
                selectedFeedback = null
                replyText = ""
            },
            title = {
                Text(
                    text = "回复反馈",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "反馈标题: ${selectedFeedback?.title}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("回复内容", color = Color.White.copy(alpha = 0.7f)) },
                        placeholder = { Text("请输入回复内容...", color = Color.White.copy(alpha = 0.5f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD4AF37),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            cursorColor = Color(0xFFD4AF37)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (replyText.isNotBlank() && selectedFeedback != null) {
                            supportViewModel.replyToFeedback(selectedFeedback!!.id, replyText) { success ->
                                if (success) {
                                    // 重新加载反馈列表
                                    supportViewModel.getAllFeedbacks { feedbacks ->
                                        feedbackList = feedbacks
                                    }
                                    showReplyDialog = false
                                    selectedFeedback = null
                                    replyText = ""
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("发送回复", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReplyDialog = false
                        selectedFeedback = null
                        replyText = ""
                    }
                ) {
                    Text("取消", color = Color.White.copy(alpha = 0.8f))
                }
            },
            containerColor = Color(0xFF2C3E50),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * 反馈统计区域
 */
@Composable
private fun FeedbackStatsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF000000).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF34495E).copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF34495E).copy(alpha = 0.4f),
                            Color(0xFF2C3E50).copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

/**
 * 反馈管理项
 */
@Composable
private fun FeedbackManagementItem(
    feedback: UserFeedback,
    onStatusChange: (String) -> Unit,
    onReply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF000000).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF34495E).copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF34495E).copy(alpha = 0.4f),
                            Color(0xFF2C3E50).copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                // 状态标签
                val statusColor = when (feedback.status) {
                    "submitted" -> Color(0xFFE74C3C)
                    "reviewing" -> Color(0xFFF39C12)
                    "resolved" -> Color(0xFF27AE60)
                    "closed" -> Color(0xFF95A5A6)
                    else -> Color(0xFF95A5A6)
                }

                val statusText = when (feedback.status) {
                    "submitted" -> "待处理"
                    "reviewing" -> "处理中"
                    "resolved" -> "已解决"
                    "closed" -> "已关闭"
                    else -> "未知"
                }

                Box(
                    modifier = Modifier
                        .background(
                            statusColor.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            statusColor.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 反馈信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "类型: ${when(feedback.feedbackType) {
                        "bug" -> "问题反馈"
                        "feature" -> "功能建议"
                        "suggestion" -> "改进建议"
                        else -> feedback.feedbackType
                    }}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Text(
                    text = "优先级: ${when(feedback.priority) {
                        "high" -> "高"
                        "normal" -> "普通"
                        "low" -> "低"
                        else -> feedback.priority
                    }}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Text(
                    text = "用户: ${feedback.userId.take(8)}...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 反馈描述
            Text(
                text = feedback.description,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 管理员回复（如果有）
            if (!feedback.adminResponse.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF2C3E50).copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = "管理员回复: ${feedback.adminResponse}",
                        color = Color(0xFFD4AF37),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 状态更新按钮
                val statusOptions = when (feedback.status) {
                    "submitted" -> listOf("reviewing" to "开始处理", "closed" to "关闭")
                    "reviewing" -> listOf("resolved" to "标记解决", "closed" to "关闭")
                    "resolved" -> listOf("closed" to "关闭")
                    else -> emptyList()
                }

                statusOptions.forEach { (status, label) ->
                    Button(
                        onClick = { onStatusChange(status) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD4AF37).copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.Black,
                            fontSize = 12.sp
                        )
                    }
                }

                // 回复按钮
                if (feedback.status in listOf("submitted", "reviewing")) {
                    Button(
                        onClick = onReply,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3498DB).copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "回复",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 时间信息
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "创建时间: ${feedback.createdAt?.take(16)?.replace("T", " ") ?: "未知"}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 客服工作台内容
 */
@Composable
private fun SupportDeskContent(
    supportViewModel: SupportViewModel
) {
    var deskStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var pendingConversations by remember { mutableStateOf<List<SupportConversationDisplay>>(emptyList()) }
    var recentFeedbacks by remember { mutableStateOf<List<UserFeedback>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("overview") }

    // 加载数据
    LaunchedEffect(Unit) {
        supportViewModel.getSupportDeskStats { stats ->
            deskStats = stats
        }
        supportViewModel.getPendingConversations { conversations ->
            pendingConversations = conversations
        }
        supportViewModel.getRecentFeedbacks { feedbacks ->
            recentFeedbacks = feedbacks
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 顶部标题和标签页
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 800f
                        ),
                        shape = RoundedCornerShape(20.dp)
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
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Text(
                    text = "客服工作台",
                    color = Color(0xFFFFD700),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 标签页
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    Color(0xFFD4AF37)
                                else Color(0xFF34495E).copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = tabName,
                                color = if (selectedTab == tabId) Color.Black else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == tabId) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 内容区域
        when (selectedTab) {
            "overview" -> SupportDeskOverview(deskStats)
            "conversations" -> SupportDeskConversations(
                conversations = pendingConversations,
                onTakeOver = { conversationId ->
                    supportViewModel.takeOverConversation(conversationId) { success ->
                        if (success) {
                            // 重新加载对话列表
                            supportViewModel.getPendingConversations { conversations ->
                                pendingConversations = conversations
                            }
                        }
                    }
                },
                onEndConversation = { conversationId ->
                    supportViewModel.endConversation(conversationId) { success ->
                        if (success) {
                            // 重新加载对话列表
                            supportViewModel.getPendingConversations { conversations ->
                                pendingConversations = conversations
                            }
                        }
                    }
                }
            )
            "feedbacks" -> SupportDeskFeedbacks(
                feedbacks = recentFeedbacks,
                onReply = { feedbackId, response ->
                    supportViewModel.replyToFeedback(feedbackId, response) { success ->
                        if (success) {
                            // 重新加载反馈列表
                            supportViewModel.getRecentFeedbacks { feedbacks ->
                                recentFeedbacks = feedbacks
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * 客服工作台概览
 */
@Composable
private fun SupportDeskOverview(
    stats: Map<String, Any>
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 统计卡片网格
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StatCard(
                        title = "活跃对话",
                        value = (stats["active_conversations"] as? Int)?.toString() ?: "0",
                        icon = "💬",
                        color = Color(0xFF3498DB)
                    )
                }
                item {
                    StatCard(
                        title = "待处理",
                        value = (stats["pending_conversations"] as? Int)?.toString() ?: "0",
                        icon = "⏳",
                        color = Color(0xFFF39C12)
                    )
                }
                item {
                    StatCard(
                        title = "今日解决",
                        value = (stats["resolved_today"] as? Int)?.toString() ?: "0",
                        icon = "✅",
                        color = Color(0xFF27AE60)
                    )
                }
                item {
                    StatCard(
                        title = "平均响应",
                        value = stats["avg_response_time"] as? String ?: "0分钟",
                        icon = "⚡",
                        color = Color(0xFF9B59B6)
                    )
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StatCard(
                        title = "客户满意度",
                        value = "${(stats["customer_satisfaction"] as? Double)?.toString() ?: "0"}/5.0",
                        icon = "⭐",
                        color = Color(0xFFE74C3C)
                    )
                }
                item {
                    StatCard(
                        title = "在线客服",
                        value = "${(stats["online_agents"] as? Int)?.toString() ?: "0"}/${(stats["total_agents"] as? Int)?.toString() ?: "0"}",
                        icon = "👥",
                        color = Color(0xFF1ABC9C)
                    )
                }
                item {
                    StatCard(
                        title = "最近反馈",
                        value = (stats["recent_feedbacks"] as? Int)?.toString() ?: "0",
                        icon = "📝",
                        color = Color(0xFF34495E)
                    )
                }
                item {
                    StatCard(
                        title = "紧急问题",
                        value = (stats["urgent_issues"] as? Int)?.toString() ?: "0",
                        icon = "🚨",
                        color = Color(0xFFE67E22)
                    )
                }
            }
        }

        // 快速操作
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color(0xFF000000).copy(alpha = 0.2f)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF34495E).copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF34495E).copy(alpha = 0.4f),
                                    Color(0xFF2C3E50).copy(alpha = 0.3f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = "快速操作",
                        color = Color(0xFFFFD700),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionButton(
                            text = "查看待处理对话",
                            icon = "💬",
                            onClick = { /* 切换到对话管理标签 */ }
                        )
                        QuickActionButton(
                            text = "处理紧急反馈",
                            icon = "🚨",
                            onClick = { /* 切换到反馈处理标签 */ }
                        )
                        QuickActionButton(
                            text = "生成工作报告",
                            icon = "📊",
                            onClick = { /* 生成报告 */ }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 统计卡片
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(120.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF000000).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.2f),
                            color.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = icon,
                        fontSize = 20.sp
                    )
                }

                Text(
                    text = value,
                    color = color,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 快速操作按钮
 */
@Composable
private fun QuickActionButton(
    text: String,
    icon: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD4AF37).copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = icon,
                fontSize = 16.sp
            )
            Text(
                text = text,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 客服工作台对话管理
 */
@Composable
private fun SupportDeskConversations(
    conversations: List<SupportConversationDisplay>,
    onTakeOver: (String) -> Unit,
    onEndConversation: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "对话管理 (${conversations.size})",
                color = Color(0xFFFFD700),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(conversations) { conversation ->
            ConversationManagementItem(
                conversation = conversation,
                onTakeOver = { onTakeOver(conversation.id) },
                onEndConversation = { onEndConversation(conversation.id) }
            )
        }

        if (conversations.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无待处理对话",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * 对话管理项
 */
@Composable
private fun ConversationManagementItem(
    conversation: SupportConversationDisplay,
    onTakeOver: () -> Unit,
    onEndConversation: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF000000).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF34495E).copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF34495E).copy(alpha = 0.4f),
                            Color(0xFF2C3E50).copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.conversationTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                // 优先级标签
                val priorityColor = when (conversation.priority) {
                    "urgent" -> Color(0xFFE74C3C)
                    "high" -> Color(0xFFF39C12)
                    "normal" -> Color(0xFF3498DB)
                    "low" -> Color(0xFF95A5A6)
                    else -> Color(0xFF95A5A6)
                }

                val priorityText = when (conversation.priority) {
                    "urgent" -> "紧急"
                    "high" -> "高"
                    "normal" -> "普通"
                    "low" -> "低"
                    else -> "未知"
                }

                Box(
                    modifier = Modifier
                        .background(
                            priorityColor.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            priorityColor.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = priorityText,
                        color = priorityColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 对话信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "用户: ${conversation.userId.take(8)}...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Text(
                    text = "状态: ${when(conversation.status) {
                        "waiting" -> "等待中"
                        "active" -> "进行中"
                        "resolved" -> "已解决"
                        else -> conversation.status
                    }}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                if (conversation.supportId != null) {
                    Text(
                        text = "客服: ${conversation.supportId.take(8)}...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 最后消息
            Text(
                text = "最后消息: ${conversation.lastMessage}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 标签
            if (conversation.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(conversation.tags) { tag ->
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(0xFF2C3E50).copy(alpha = 0.5f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                color = Color(0xFFD4AF37),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (conversation.status) {
                    "waiting" -> {
                        Button(
                            onClick = onTakeOver,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3498DB).copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "接管对话",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    "active" -> {
                        Button(
                            onClick = onEndConversation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF27AE60).copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "结束对话",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = { /* 查看详情 */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37).copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "查看详情",
                        color = Color.Black,
                        fontSize = 12.sp
                    )
                }
            }

            // 时间信息
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "创建时间: ${conversation.createdAt?.take(16)?.replace("T", " ") ?: "未知"}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 客服工作台反馈处理
 */
@Composable
private fun SupportDeskFeedbacks(
    feedbacks: List<UserFeedback>,
    onReply: (String, String) -> Unit
) {
    var selectedFeedback by remember { mutableStateOf<UserFeedback?>(null) }
    var showReplyDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "最近反馈 (${feedbacks.size})",
                color = Color(0xFFFFD700),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(feedbacks) { feedback ->
            FeedbackQuickItem(
                feedback = feedback,
                onReply = {
                    selectedFeedback = feedback
                    showReplyDialog = true
                }
            )
        }

        if (feedbacks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无最近反馈",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // 回复对话框
    if (showReplyDialog && selectedFeedback != null) {
        AlertDialog(
            onDismissRequest = {
                showReplyDialog = false
                selectedFeedback = null
                replyText = ""
            },
            title = {
                Text(
                    text = "快速回复反馈",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "反馈标题: ${selectedFeedback?.title}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("回复内容", color = Color.White.copy(alpha = 0.7f)) },
                        placeholder = { Text("请输入回复内容...", color = Color.White.copy(alpha = 0.5f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD4AF37),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            cursorColor = Color(0xFFD4AF37)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (replyText.isNotBlank() && selectedFeedback != null) {
                            onReply(selectedFeedback!!.id, replyText)
                            showReplyDialog = false
                            selectedFeedback = null
                            replyText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("发送回复", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReplyDialog = false
                        selectedFeedback = null
                        replyText = ""
                    }
                ) {
                    Text("取消", color = Color.White.copy(alpha = 0.8f))
                }
            },
            containerColor = Color(0xFF2C3E50),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * 反馈快速处理项
 */
@Composable
private fun FeedbackQuickItem(
    feedback: UserFeedback,
    onReply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color(0xFF000000).copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF34495E).copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF34495E).copy(alpha = 0.4f),
                            Color(0xFF2C3E50).copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            // 标题和类型
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                // 类型标签
                val typeColor = when (feedback.feedbackType) {
                    "bug" -> Color(0xFFE74C3C)
                    "feature" -> Color(0xFF3498DB)
                    "suggestion" -> Color(0xFF27AE60)
                    else -> Color(0xFF95A5A6)
                }

                val typeText = when (feedback.feedbackType) {
                    "bug" -> "问题"
                    "feature" -> "功能"
                    "suggestion" -> "建议"
                    else -> feedback.feedbackType
                }

                Box(
                    modifier = Modifier
                        .background(
                            typeColor.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            typeColor.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = typeText,
                        color = typeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 反馈信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "用户: ${feedback.userId.take(8)}...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Text(
                    text = "优先级: ${when(feedback.priority) {
                        "high" -> "高"
                        "normal" -> "普通"
                        "low" -> "低"
                        else -> feedback.priority
                    }}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 反馈描述
            Text(
                text = feedback.description,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReply,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3498DB).copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "快速回复",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = { /* 查看详情 */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37).copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "查看详情",
                        color = Color.Black,
                        fontSize = 12.sp
                    )
                }
            }

            // 时间信息
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "创建时间: ${feedback.createdAt?.take(16)?.replace("T", " ") ?: "未知"}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 帮助内容
 */
@Composable
private fun HelpContent() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "功能说明",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

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

/**
 * 帮助章节
 */
@Composable
private fun HelpSection(title: String, items: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
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
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2C3E50).copy(alpha = 0.2f),
                            Color(0xFF1A1A1A).copy(alpha = 0.2f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    ),
                    shape = RoundedCornerShape(16.dp)
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
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    color = Color(0xFFFFD700),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                items.forEach { item ->
                    Text(
                        text = "• $item",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 默认内容
 */
@Composable
private fun DefaultContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF000000).copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A).copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2C3E50).copy(alpha = 0.3f),
                                Color(0xFF1A1A1A).copy(alpha = 0.2f)
                            ),
                            radius = 600f
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFFFD700).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(40.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "欢迎使用客服支持系统",
                        color = Color(0xFFFFD700),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "请选择左侧菜单项开始使用",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
