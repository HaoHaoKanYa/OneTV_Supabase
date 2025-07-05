package top.cywin.onetv.tv.supabase.support

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 反馈列表界面
 * 显示用户提交的反馈列表，支持查看详情、撤回和删除操作
 */
@Composable
fun FeedbackListScreen(
    viewModel: SupportViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // 只在首次加载时获取数据，避免不停刷新
    LaunchedEffect(key1 = "feedback_list_initial_load") {
        if (uiState.feedbackState.feedbackList.isEmpty()) {
            Log.d("FeedbackListScreen", "首次加载反馈列表")
            viewModel.loadUserFeedbackList()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的反馈",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 返回按钮
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text("返回", color = Color.White, fontSize = 12.sp)
                    }

                    // 刷新按钮
                    Button(
                        onClick = { viewModel.loadUserFeedbackList() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text("刷新", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            
            // 反馈列表
            when {
                uiState.feedbackState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                
                uiState.feedbackState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "加载失败",
                                color = Color.Red,
                                fontSize = 16.sp
                            )
                            Text(
                                text = uiState.feedbackState.error ?: "未知错误",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = { viewModel.loadUserFeedbackList() },
                                modifier = Modifier.padding(top = 16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF333333)
                                )
                            ) {
                                Text("重试", color = Color.White)
                            }
                        }
                    }
                }
                
                uiState.feedbackState.feedbackList.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无反馈记录",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.feedbackState.feedbackList) { feedback ->
                            FeedbackListItem(
                                feedback = feedback,
                                onClick = {
                                    Log.d("FeedbackListScreen", "点击反馈项: ${feedback.id}")
                                    viewModel.showFeedbackDetail(feedback)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 注意：反馈详情弹窗已移至FullScreenDialogs组件中统一管理，占整个应用屏幕85%
    }
}

/**
 * 反馈列表项 - 简洁单行论坛帖子样式（无卡片设计）
 */
@Composable
private fun FeedbackListItem(
    feedback: UserFeedback,
    onClick: () -> Unit = {}
) {
    Column {
        // 单行显示：标题-类型-状态-时间-回复数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题（占主要空间）
            Text(
                text = feedback.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 类型标签
            FeedbackTypeChip(feedback.feedbackType)

            // 状态标签
            FeedbackStatusChip(feedback.status)

            // 时间
            Text(
                text = formatDateTime(feedback.createdAt),
                color = Color.Gray,
                fontSize = 11.sp
            )

            // 回复数（如果有）
            if ((feedback.adminResponse?.isNotEmpty() == true)) {
                Text(
                    text = "已回复",
                    color = Color(0xFF4CAF50),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(
                            Color(0xFF4CAF50).copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
 * 反馈类型标签
 */
@Composable
private fun FeedbackTypeChip(type: String) {
    val (color, text) = when (type) {
        // 中文类型
        "问题报告" -> Color(0xFFFF5722) to "问题"
        "功能建议" -> Color(0xFF2196F3) to "建议"
        "投诉建议" -> Color(0xFFFF9800) to "投诉"
        "改进建议" -> Color(0xFF9C27B0) to "改进"
        "一般反馈" -> Color(0xFF607D8B) to "反馈"
        // 英文类型映射到中文
        "bug" -> Color(0xFFFF5722) to "问题"
        "feature" -> Color(0xFF2196F3) to "建议"
        "complaint" -> Color(0xFFFF9800) to "投诉"
        "improvement" -> Color(0xFF9C27B0) to "改进"
        "general" -> Color(0xFF607D8B) to "反馈"
        else -> Color.Gray to type
    }
    
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 反馈状态标签
 */
@Composable
private fun FeedbackStatusChip(status: String) {
    val (color, text) = when (status) {
        "submitted" -> Color(0xFFFF9800) to "已提交"
        "reviewing" -> Color(0xFF2196F3) to "处理中"
        "resolved" -> Color(0xFF4CAF50) to "已解决"
        "closed" -> Color(0xFF607D8B) to "已关闭"
        "withdrawn" -> Color(0xFF9E9E9E) to "已撤回"
        else -> Color.Gray to status
    }

    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 格式化日期时间
 */
private fun formatDateTime(dateTimeString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateTimeString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        // 如果解析失败，尝试其他格式
        try {
            val inputFormat2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat2.parse(dateTimeString)
            outputFormat.format(date ?: Date())
        } catch (e2: Exception) {
            // 如果都失败了，返回原始字符串的前10个字符
            dateTimeString.take(10)
        }
    }
}
