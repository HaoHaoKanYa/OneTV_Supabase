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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // 主内容区域
                    ChatStartContent(
                        onStartChat = { supportViewModel.showConversation() },
                        supportViewModel = supportViewModel
                    )

                    // 聊天窗口弹窗 - 居中显示，占据2/3屏幕
                    if (uiState.showConversation) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { /* 防止点击穿透 */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.67f)
                                    .fillMaxHeight(0.67f),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                SupportConversationScreen(
                                    viewModel = supportViewModel,
                                    onClose = { supportViewModel.hideConversation() }
                                )
                            }
                        }
                    }
                }
            }
            "submit_feedback" -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 主内容区域 - 论坛版块模式
                    FeedbackStartContent(
                        onStartFeedback = { supportViewModel.showFeedbackForm() },
                        supportViewModel = supportViewModel
                    )

                    // 反馈表单弹窗 - 居中显示，占据2/3屏幕
                    if (uiState.showFeedbackForm) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { /* 防止点击穿透 */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.67f)
                                    .fillMaxHeight(0.67f),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                FeedbackFormScreen(
                                    viewModel = supportViewModel,
                                    onClose = { supportViewModel.hideFeedbackForm() }
                                )
                            }
                        }
                    }
                }
            }
            "my_feedback" -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 主内容区域 - 论坛版块模式
                    MyFeedbackContent(
                        onViewFeedback = { supportViewModel.showFeedbackList() },
                        supportViewModel = supportViewModel
                    )

                    // 反馈列表弹窗 - 居中显示，占据2/3屏幕
                    if (uiState.showFeedbackList) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { /* 防止点击穿透 */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.67f)
                                    .fillMaxHeight(0.67f),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                FeedbackListScreen(
                                    viewModel = supportViewModel,
                                    onClose = { supportViewModel.hideFeedbackList() }
                                )
                            }
                        }
                    }

                    // 反馈详情弹窗 - 居中显示，占据2/3屏幕
                    if (uiState.showFeedbackDetail && uiState.selectedFeedback != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { /* 防止点击穿透 */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.67f)
                                    .fillMaxHeight(0.67f),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1A1A1A)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                FeedbackDetailDialog(
                                    feedback = uiState.selectedFeedback!!,
                                    onClose = { supportViewModel.hideFeedbackDetail() },
                                    onWithdraw = { feedbackId ->
                                        // 撤销反馈逻辑
                                        supportViewModel.withdrawFeedback(feedbackId)
                                        supportViewModel.hideFeedbackDetail()
                                    },
                                    onDelete = { feedbackId ->
                                        // 删除反馈逻辑
                                        supportViewModel.deleteFeedback(feedbackId)
                                        supportViewModel.hideFeedbackDetail()
                                    }
                                )
                            }
                        }
                    }
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

    // 微信聊天窗口样式布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部统计信息栏
        if (conversationStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总对话", conversationStats["total"] ?: 0)
                StatItem("进行中", conversationStats["open"] ?: 0)
                StatItem("已完成", conversationStats["closed"] ?: 0)
            }
        }

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
                                text = "点击下方按钮开始您的第一次客服对话",
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
                            onStartChat()
                        }
                    )
                }
            }
        }

        // 底部操作区域 - 移动到最下端
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 开始对话按钮
            Button(
                onClick = onStartChat,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Text(
                    text = "💬 开始新对话",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

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
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄",
                    color = Color.White,
                    fontSize = 16.sp
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

    LaunchedEffect(Unit) {
        supportViewModel.getFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getUserFeedbacks { feedbacks ->
            recentFeedbacks = feedbacks
            isLoading = false
        }
    }

    // 论坛版块样式布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部统计信息栏
        if (feedbackStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("已提交", feedbackStats["total"] ?: 0)
                StatItem("处理中", feedbackStats["reviewing"] ?: 0)
                StatItem("已解决", feedbackStats["resolved"] ?: 0)
            }
        }

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
                                text = "点击下方按钮提交您的第一个反馈",
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
                            // 这里可以添加查看反馈详情的逻辑
                        }
                    )
                }
            }
        }

        // 操作按钮区域 - 移动到最下端
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 我要反馈按钮
            Button(
                onClick = onStartFeedback,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700)
                )
            ) {
                Text(
                    text = "📝 我要反馈",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

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
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄",
                    color = Color.White,
                    fontSize = 16.sp
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
 * 反馈论坛项组件 - 论坛帖子样式
 */
@Composable
private fun FeedbackForumItem(
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
            modifier = Modifier.padding(16.dp)
        ) {
            // 帖子标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 16.sp,
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 帖子内容预览
            if (feedback.description.isNotEmpty()) {
                Text(
                    text = feedback.description.take(100) + if (feedback.description.length > 100) "..." else "",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 底部信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = feedback.getTypeText(),
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "•",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = feedback.getFormattedCreatedTime(),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // 回复数量（如果有的话）
                Text(
                    text = "💬 0",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
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

    LaunchedEffect(Unit) {
        supportViewModel.getFeedbackStats { stats ->
            feedbackStats = stats
        }
        supportViewModel.getUserFeedbacks { feedbacks ->
            myFeedbacks = feedbacks
            isLoading = false
        }
    }

    // 论坛版块样式布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部统计信息栏
        if (feedbackStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总数", feedbackStats["total"] ?: 0)
                StatItem("处理中", feedbackStats["reviewing"] ?: 0)
                StatItem("已解决", feedbackStats["resolved"] ?: 0)
            }
        }

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

        // 操作按钮区域 - 移动到最下端
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 查看全部按钮
            Button(
                onClick = onViewFeedback,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
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
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * 我的反馈论坛项组件 - 论坛帖子样式
 */
@Composable
private fun MyFeedbackForumItem(
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
            modifier = Modifier.padding(16.dp)
        ) {
            // 帖子标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedback.title,
                    color = Color.White,
                    fontSize = 16.sp,
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 帖子内容预览
            if (feedback.description.isNotEmpty()) {
                Text(
                    text = feedback.description.take(100) + if (feedback.description.length > 100) "..." else "",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 底部信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = feedback.getTypeText(),
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "•",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        text = feedback.getFormattedCreatedTime(),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // 操作按钮和回复提示
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!feedback.adminResponse.isNullOrBlank()) {
                        Text(
                            text = "💬 有回复",
                            color = Color(0xFF4285F4),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "📝",
                        color = Color(0xFFFFD700),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "🗑️",
                        color = Color.Red.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
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
 * 用户管理内容 - 用户列表模式
 */
@Composable
private fun UserManagementContent(
    onOpenUserManagement: () -> Unit,
    supportViewModel: SupportViewModel = viewModel()
) {
    var userStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var allUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        supportViewModel.getUserStats { stats ->
            userStats = stats
        }
        supportViewModel.getRecentUsers { users ->
            allUsers = users
            isLoading = false
        }
    }

    // 用户列表样式布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部统计信息栏
        if (userStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总用户", (userStats["total"] as? Int) ?: 0)
                StatItem("VIP用户", (userStats["vip"] as? Int) ?: 0)
                StatItem("管理员", (userStats["admin"] as? Int) ?: 0)
            }
        }

        // 操作按钮区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 用户管理按钮
            Button(
                onClick = onOpenUserManagement,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
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

            // 刷新按钮
            Button(
                onClick = {
                    supportViewModel.getUserStats { stats ->
                        userStats = stats
                    }
                    supportViewModel.getRecentUsers { users ->
                        allUsers = users
                    }
                },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        // 用户列表表头
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C3E50).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "序号",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.8f)
                )
                Text(
                    text = "用户名",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "用户ID",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "在线状态",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = "角色",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = "操作",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 用户列表区域
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    color = Color(0xFF1A1A1A).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            } else if (allUsers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
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
                }
            } else {
                itemsIndexed(allUsers) { index, user ->
                    UserTableRow(
                        index = index + 1,
                        user = user,
                        onClick = {
                            // 点击用户可弹出操作窗口
                        }
                    )
                }
            }
        }
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

    // 论坛版块样式布局
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部统计信息栏
        if (feedbackStats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总反馈", (feedbackStats["total"] as? Int) ?: 0)
                StatItem("待处理", (feedbackStats["submitted"] as? Int) ?: 0)
                StatItem("处理中", (feedbackStats["reviewing"] as? Int) ?: 0)
                StatItem("已解决", (feedbackStats["resolved"] as? Int) ?: 0)
            }
        }

        // 操作和筛选按钮区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态筛选按钮
            val statusOptions = listOf(
                "all" to "全部",
                "submitted" to "待处理",
                "reviewing" to "处理中",
                "resolved" to "已解决",
                "closed" to "已关闭"
            )

            statusOptions.forEach { (status, label) ->
                Button(
                    onClick = { filterStatus = status },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (filterStatus == status)
                            Color(0xFFFFD700)
                        else
                            Color(0xFF4285F4).copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = label,
                        color = if (filterStatus == status) Color.Black else Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 刷新按钮
            Button(
                onClick = {
                    supportViewModel.getAllFeedbackStats { stats ->
                        feedbackStats = stats
                    }
                    supportViewModel.getAllFeedbacks { feedbacks ->
                        feedbackList = feedbacks
                    }
                },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4).copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "🔄",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

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
                            // 处理回复反馈
                        }
                    )
                }
            }
        }
    }


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
 * 客服工作台概览
 */
@Composable
fun SupportDeskOverview(stats: Map<String, Any>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "工作台概览",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        // 统计信息
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SupportStatItem(
                    title = "待处理对话",
                    value = (stats["pending_conversations"] as? Int)?.toString() ?: "0",
                    icon = "💬",
                    color = Color(0xFFFF6B6B)
                )
            }
            item {
                SupportStatItem(
                    title = "今日处理",
                    value = (stats["today_handled"] as? Int)?.toString() ?: "0",
                    icon = "✅",
                    color = Color(0xFF4ECDC4)
                )
            }
            item {
                SupportStatItem(
                    title = "平均响应时间",
                    value = "${(stats["avg_response_time"] as? Int) ?: 0}分钟",
                    icon = "⏱️",
                    color = Color(0xFFFFD93D)
                )
            }
        }
    }
}

/**
 * 客服工作台对话管理
 */
@Composable
fun SupportDeskConversations(
    conversations: List<SupportConversationDisplay>,
    onTakeOver: (String) -> Unit,
    onEndConversation: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "对话管理",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    onTakeOver = { onTakeOver(conversation.id) },
                    onEndConversation = { onEndConversation(conversation.id) }
                )
            }

            if (conversations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无待处理对话",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}



/**
 * 客服工作台反馈处理
 */
@Composable
fun SupportDeskFeedbacks(
    feedbacks: List<UserFeedback>,
    onReplyFeedback: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "反馈处理 (${feedbacks.size})",
            color = Color(0xFFFFD700),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(feedbacks) { feedback ->
                FeedbackItem(
                    feedback = feedback,
                    onReply = {
                        onReplyFeedback(feedback.id, "回复内容")
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
                            text = "暂无待处理反馈",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 对话项组件
 */
@Composable
fun ConversationItem(
    conversation: SupportConversationDisplay,
    onTakeOver: () -> Unit,
    onEndConversation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "用户: ${conversation.userId.take(8)}...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = conversation.status,
                    color = when (conversation.status) {
                        "waiting" -> Color(0xFFF39C12)
                        "active" -> Color(0xFF27AE60)
                        else -> Color.Gray
                    },
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "最后消息: ${conversation.lastMessage}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTakeOver,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("接管", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = onEndConversation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("结束", color = Color.White, fontSize = 12.sp)
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
    onReply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (feedback.status) {
                            "submitted" -> Color(0xFFFF9800).copy(alpha = 0.3f)
                            "reviewing" -> Color(0xFF2196F3).copy(alpha = 0.3f)
                            "resolved" -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                            "closed" -> Color.Gray.copy(alpha = 0.3f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (feedback.status) {
                            "submitted" -> "待处理"
                            "reviewing" -> "处理中"
                            "resolved" -> "已解决"
                            "closed" -> "已关闭"
                            else -> feedback.status
                        },
                        color = when (feedback.status) {
                            "submitted" -> Color(0xFFFF9800)
                            "reviewing" -> Color(0xFF2196F3)
                            "resolved" -> Color(0xFF4CAF50)
                            "closed" -> Color.Gray
                            else -> Color.Gray
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = feedback.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
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
}

/**
 * 客服工作台内容 - 简化版本
 */
@Composable
fun SupportDeskContent(
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

    // 简化的滚动布局
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标签页选择器
        item {
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
                                Color(0xFFFFD700)
                            else Color(0xFF4285F4).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(20.dp),
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

        // 内容区域
        item {
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
                    onReplyFeedback = { feedbackId, reply ->
                        supportViewModel.replyToFeedback(feedbackId, reply) { success ->
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
 * 反馈详情弹窗组件
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
            .padding(24.dp)
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

        // 滚动内容区域
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // 反馈基本信息
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = feedback.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "类型: ${feedback.getTypeText()}",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "状态: ${feedback.getStatusText()}",
                                color = when (feedback.status) {
                                    UserFeedback.STATUS_SUBMITTED -> Color(0xFFFFD700)
                                    UserFeedback.STATUS_REVIEWING -> Color(0xFF2196F3)
                                    UserFeedback.STATUS_RESOLVED -> Color(0xFF4CAF50)
                                    else -> Color.Gray
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "提交时间: ${feedback.getFormattedCreatedTime()}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                // 反馈内容
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C3E50).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "反馈内容",
                            color = Color(0xFFFFD700),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = feedback.description,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // 如果有回复内容，显示回复
            if (!feedback.adminResponse.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "管理员回复",
                                color = Color(0xFF4CAF50),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = feedback.adminResponse!!,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
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
            // 撤销反馈按钮（仅在未处理状态下显示）
            if (feedback.status == UserFeedback.STATUS_SUBMITTED) {
                Button(
                    onClick = { onWithdraw(feedback.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("撤销反馈", color = Color.White)
                }
            }

            // 删除反馈按钮
            Button(
                onClick = { onDelete(feedback.id) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.8f)
                )
            ) {
                Text("删除反馈", color = Color.White)
            }

            // 关闭按钮
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray.copy(alpha = 0.8f)
                )
            ) {
                Text("关闭", color = Color.White)
            }
        }
    }
}