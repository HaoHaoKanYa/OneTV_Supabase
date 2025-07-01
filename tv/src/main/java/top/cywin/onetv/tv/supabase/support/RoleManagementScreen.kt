package top.cywin.onetv.tv.supabase.support

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TAG = "RoleManagementScreen"

/**
 * 角色管理界面
 * 只有超级管理员和管理员可以访问
 */
@Composable
fun RoleManagementScreen(
    modifier: Modifier = Modifier,
    viewModel: SupportViewModel,
    onClose: () -> Unit = {}
) {
    Log.d(TAG, "RoleManagementScreen: 初始化角色管理界面")
    val uiState by viewModel.uiState.collectAsState()
    var userRoles by remember {
        mutableStateOf<List<String>>(emptyList()).also {
            Log.d(TAG, "RoleManagementScreen: 初始化用户角色列表")
        }
    }
    var canManageRoles by remember {
        mutableStateOf(false).also {
            Log.d(TAG, "RoleManagementScreen: 初始化角色管理权限 = false")
        }
    }
    var selectedUser by remember {
        mutableStateOf<String?>(null).also {
            Log.d(TAG, "RoleManagementScreen: 初始化选中用户 = null")
        }
    }
    var showAddRoleDialog by remember { mutableStateOf(false) }
    
    // 检查权限
    LaunchedEffect(Unit) {
        viewModel.getUserRoles { roles ->
            userRoles = roles
        }
        viewModel.checkPermission("admin.manage_roles") { canManage ->
            canManageRoles = canManage
        }
    }
    
    if (!canManageRoles) {
        // 权限不足
        Card(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "权限不足",
                        color = Color.Red,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "只有管理员可以管理用户角色",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("返回", color = Color.White)
                    }
                }
            }
        }
        return
    }
    
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "角色管理",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    Button(
                        onClick = { showAddRoleDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4)
                        )
                    ) {
                        Text("添加角色", color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 当前用户角色显示
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
                    Text(
                        text = "您的角色",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = userRoles.joinToString(", ") { role ->
                            when(role) {
                                "super_admin" -> "超级管理员"
                                "admin" -> "管理员"
                                "support" -> "客服"
                                "moderator" -> "版主"
                                "vip" -> "VIP用户"
                                "user" -> "普通用户"
                                else -> role
                            }
                        },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 角色管理功能区
            Text(
                text = "角色管理功能",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 功能按钮
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RoleManagementButton(
                        title = "查看所有用户角色",
                        description = "查看系统中所有用户的角色分配情况",
                        onClick = { /* TODO: 实现查看功能 */ }
                    )
                }
                
                item {
                    RoleManagementButton(
                        title = "角色统计",
                        description = "查看各角色的用户数量统计",
                        onClick = { /* TODO: 实现统计功能 */ }
                    )
                }
                
                if (userRoles.contains("super_admin")) {
                    item {
                        RoleManagementButton(
                            title = "系统角色配置",
                            description = "配置系统角色权限（仅超级管理员）",
                            onClick = { /* TODO: 实现配置功能 */ },
                            backgroundColor = Color(0xFFE91E63).copy(alpha = 0.3f),
                            borderColor = Color(0xFFE91E63)
                        )
                    }
                }
            }
        }
    }
    
    // 添加角色对话框
    if (showAddRoleDialog) {
        AddRoleDialog(
            onDismiss = { showAddRoleDialog = false },
            onConfirm = { userId, roleType ->
                // TODO: 实现添加角色功能
                showAddRoleDialog = false
            }
        )
    }
}

/**
 * 角色管理按钮
 */
@Composable
private fun RoleManagementButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    backgroundColor: Color = Color(0xFF3F51B5).copy(alpha = 0.3f),
    borderColor: Color = Color(0xFF3F51B5)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
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
 * 添加角色对话框
 */
@Composable
private fun AddRoleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("user") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加用户角色", color = Color.White)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("用户ID", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("选择角色：", color = Color.White)
                
                val roles = listOf(
                    "user" to "普通用户",
                    "vip" to "VIP用户",
                    "moderator" to "版主",
                    "support" to "客服",
                    "admin" to "管理员",
                    "super_admin" to "超级管理员"
                )
                
                roles.forEach { (role, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role }
                        )
                        Text(label, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(userId, selectedRole) },
                enabled = userId.isNotBlank()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = Color.Black.copy(alpha = 0.9f)
    )
}
