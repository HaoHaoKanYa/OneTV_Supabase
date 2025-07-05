package top.cywin.onetv.tv.supabase.support

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val TAG = "SupportConversation"

/**
 * 客服对话界面
 * 1对1客服聊天功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportConversationScreen(
    modifier: Modifier = Modifier,
    viewModel: SupportViewModel,
    onClose: () -> Unit = {}
) {
    Log.d(TAG, "SupportConversationScreen: 初始化客服对话界面")
    val uiState by viewModel.uiState.collectAsState()
    val currentMessage by viewModel.currentMessage.collectAsState()
    val currentUserInfo by viewModel.currentUserInfo.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 用户角色和权限状态
    var userRoles by remember {
        mutableStateOf<List<String>>(emptyList()).also {
            Log.d(TAG, "SupportConversationScreen: 初始化用户角色列表")
        }
    }
    var canViewAllConversations by remember {
        mutableStateOf(false).also {
            Log.d(TAG, "SupportConversationScreen: 初始化查看所有对话权限 = false")
        }
    }
    var isAdmin by remember {
        mutableStateOf(false).also {
            Log.d(TAG, "SupportConversationScreen: 初始化管理员状态 = false")
        }
    }
    var currentUserId by remember { mutableStateOf<String?>(null) }

    // 获取管理员信息（如果对话中有管理员参与）
    var adminUserInfo by remember { mutableStateOf<UserProfile?>(null) }

    // 获取用户权限和当前用户ID
    LaunchedEffect(Unit) {
        viewModel.getUserRoles { roles ->
            userRoles = roles
        }
        viewModel.canViewAllConversations { canView ->
            canViewAllConversations = canView
        }
        viewModel.checkAdminStatus { adminStatus ->
            isAdmin = adminStatus
        }
        // 获取当前用户ID
        viewModel.getCurrentUserId { userId ->
            currentUserId = userId
            Log.d(TAG, "SupportConversationScreen: 当前用户ID = $userId")
        }
    }

    // 当对话信息变化时，获取管理员信息
    LaunchedEffect(uiState.conversationState.conversation?.supportId) {
        val supportId = uiState.conversationState.conversation?.supportId
        if (supportId != null) {
            viewModel.getUserInfoById(supportId) { userInfo ->
                adminUserInfo = userInfo
                Log.d(TAG, "获取管理员信息: ${userInfo?.username}")
            }
        } else {
            adminUserInfo = null
        }
    }

    // 当有新消息时自动滚动到底部
    LaunchedEffect(uiState.conversationState.messages.size) {
        if (uiState.conversationState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.conversationState.messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // 标题栏
        ConversationHeader(
            conversation = uiState.conversationState.conversation,
            isConnected = uiState.conversationState.isConnected,
            isLoading = uiState.conversationState.isLoading,
            error = uiState.conversationState.error,
            userRoles = userRoles,
            isAdmin = isAdmin,
            currentUserInfo = currentUserInfo,
            adminUserInfo = adminUserInfo,
            onClose = onClose
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 消息列表
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.conversationState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (uiState.conversationState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "👋 欢迎使用客服支持",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请描述您遇到的问题，我们会尽快为您解答",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.conversationState.messages) { message ->
                        MessageItem(message = message, currentUserId = currentUserId)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 错误信息显示
        uiState.conversationState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = error,
                    color = Color.Red,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 输入框
        MessageInputField(
            message = currentMessage,
            onMessageChange = viewModel::updateCurrentMessage,
            onSendMessage = viewModel::sendMessage,
            enabled = uiState.conversationState.isConnected && !uiState.conversationState.isLoading
        )
    }
}

/**
 * 对话标题栏
 */
@Composable
private fun ConversationHeader(
    conversation: SupportConversation?,
    isConnected: Boolean,
    isLoading: Boolean,
    error: String?,
    userRoles: List<String>,
    isAdmin: Boolean,
    currentUserInfo: UserProfile?,
    adminUserInfo: UserProfile?,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        // 当前用户是管理员，显示与客服用户的对话
                        isAdmin -> "与用户：${currentUserInfo?.username ?: "用户"}对话中"
                        // 当前用户是客服，检查是否有管理员参与对话
                        conversation?.supportId != null -> {
                            // 修复：确保显示管理员的username而不是userid
                            val adminName = adminUserInfo?.username ?: "管理员"
                            "与超管：${adminName}对话中"
                        }
                        // 普通客服对话
                        else -> "与客服对话中"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // 显示管理员标识
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE91E63).copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "管理员",
                            color = Color(0xFFE91E63),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isLoading -> Color.Yellow
                                error != null -> Color.Red
                                !isConnected -> Color.Red
                                conversation?.status == "waiting" -> Color(0xFFFF9800) // Orange
                                conversation?.status == "open" -> Color.Green
                                else -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        isLoading -> "连接中..."
                        error != null -> "连接失败"
                        !isConnected -> "未连接"
                        conversation?.status == "waiting" -> "等待客服"
                        conversation?.status == "open" -> "客服在线"
                        else -> "未知状态"
                    },
                    color = when {
                        isLoading -> Color.Yellow
                        error != null -> Color.Red
                        !isConnected -> Color.Red
                        conversation?.status == "waiting" -> Color(0xFFFF9800)
                        conversation?.status == "open" -> Color.Green
                        else -> Color.Gray
                    },
                    fontSize = 12.sp
                )

                // 显示用户角色
                if (userRoles.isNotEmpty() && userRoles != listOf("user")) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${userRoles.joinToString(", ") {
                            when(it) {
                                "super_admin" -> "超管"
                                "admin" -> "管理"
                                "support" -> "客服"
                                "moderator" -> "版主"
                                "vip" -> "VIP"
                                else -> it
                            }
                        }}",
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red.copy(alpha = 0.7f)
            )
        ) {
            Text("关闭", color = Color.White)
        }
    }
}

/**
 * 消息项 - 微信式左右对话布局，基于发送者ID正确识别身份
 */
@Composable
private fun MessageItem(
    message: SupportMessage,
    currentUserId: String? = null
) {
    // 正确的身份判断：比较发送者ID和当前用户ID
    val isFromCurrentUser = message.senderId == currentUserId
    val isFromSupport = message.isFromSupport

    // 微信式布局：当前用户消息在右侧，其他人消息在左侧
    val alignment = if (isFromCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isFromCurrentUser)
        Color(0xFF4CAF50).copy(alpha = 0.8f) else Color(0xFF2C3E50).copy(alpha = 0.8f)
    val textColor = Color.White

    // 根据实际身份显示发送者信息
    val senderText = when {
        isFromCurrentUser -> "我"
        isFromSupport -> "客服"
        else -> "用户"
    }
    val senderIcon = when {
        isFromCurrentUser -> "👤"
        isFromSupport -> "👨‍💼"
        else -> "👤"
    }
    val senderColor = if (isFromSupport) Color(0xFF4ECDC4) else Color(0xFFFFD700)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
            ),
            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 发送者信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = senderIcon,
                        fontSize = 12.sp
                    )
                    Text(
                        text = senderText,
                        color = senderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 消息内容
                Text(
                    text = message.messageText,
                    color = textColor,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 时间显示
                Text(
                    text = message.getFormattedTime(),
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                // 已读状态（仅显示用户发送的消息）
                if (!isFromSupport && message.isRead()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已读",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * 消息输入框
 */
@Composable
private fun MessageInputField(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    enabled: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    var showEmojiPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = if (enabled) "输入消息..." else "连接中...",
                    color = Color.Gray
                )
            },
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray
            ),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.width(8.dp))

        // 表情按钮
        IconButton(
            onClick = { showEmojiPicker = true },
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Text(
                text = "😊",
                fontSize = 20.sp,
                color = if (enabled) Color.White else Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 美化的发送按钮
        Button(
            onClick = onSendMessage,
            enabled = enabled && message.trim().isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled && message.trim().isNotEmpty())
                    Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "发送",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // 表情选择器对话框
    EmojiPickerDialog(
        visible = showEmojiPicker,
        onDismiss = { showEmojiPicker = false },
        onEmojiSelected = { emoji ->
            onMessageChange(message + emoji)
        }
    )
}
