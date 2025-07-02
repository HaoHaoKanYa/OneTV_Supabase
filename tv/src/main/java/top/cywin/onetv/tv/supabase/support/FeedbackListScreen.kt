package top.cywin.onetv.tv.supabase.support

import android.util.Log
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

private const val TAG = "FeedbackListScreen"

/**
 * 用户反馈列表界面
 */
@Composable
fun FeedbackListScreen(
    modifier: Modifier = Modifier,
    viewModel: SupportViewModel,
    onClose: () -> Unit = {}
) {
    Log.d(TAG, "FeedbackListScreen: 初始化反馈列表界面")
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        Log.d(TAG, "FeedbackListScreen: 加载用户反馈列表")
        viewModel.loadUserFeedbackList()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的反馈",
                    color = Color.White,
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
            
            // 反馈列表
            if (uiState.feedbackState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (uiState.feedbackState.feedbackList.isEmpty()) {
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
                            fontSize = 18.sp
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
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.feedbackState.feedbackList) { feedback ->
                        FeedbackListItem(
                            feedback = feedback,
                            onClick = {
                                // 点击反馈项打开详情弹窗
                                viewModel.showFeedbackDetail(feedback)
                            }
                        )
                    }
                }
            }
            
            // 错误信息显示
            uiState.feedbackState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Red.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = error,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }
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
 * 状态标签
 */
@Composable
private fun StatusChip(status: String) {
    val (backgroundColor, textColor) = when (status) {
        UserFeedback.STATUS_SUBMITTED -> Color(0xFFFF9800).copy(alpha = 0.3f) to Color(0xFFFF9800)
        UserFeedback.STATUS_REVIEWING -> Color(0xFF2196F3).copy(alpha = 0.3f) to Color(0xFF2196F3)
        UserFeedback.STATUS_RESOLVED -> Color(0xFF4CAF50).copy(alpha = 0.3f) to Color(0xFF4CAF50)
        UserFeedback.STATUS_CLOSED -> Color(0xFF9E9E9E).copy(alpha = 0.3f) to Color(0xFF9E9E9E)
        else -> Color.Gray.copy(alpha = 0.3f) to Color.Gray
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = when (status) {
                UserFeedback.STATUS_SUBMITTED -> "已提交"
                UserFeedback.STATUS_REVIEWING -> "处理中"
                UserFeedback.STATUS_RESOLVED -> "已解决"
                UserFeedback.STATUS_CLOSED -> "已关闭"
                else -> "未知"
            },
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 类型标签
 */
@Composable
private fun TypeChip(type: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF673AB7).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = when (type) {
                UserFeedback.TYPE_BUG -> "问题报告"
                UserFeedback.TYPE_FEATURE -> "功能建议"
                UserFeedback.TYPE_COMPLAINT -> "投诉建议"
                UserFeedback.TYPE_SUGGESTION -> "改进建议"
                UserFeedback.TYPE_GENERAL -> "一般反馈"
                else -> "其他"
            },
            color = Color(0xFF673AB7),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 优先级标签
 */
@Composable
private fun PriorityChip(priority: String) {
    val (backgroundColor, textColor) = when (priority) {
        UserFeedback.PRIORITY_HIGH -> Color(0xFFF44336).copy(alpha = 0.3f) to Color(0xFFF44336)
        UserFeedback.PRIORITY_NORMAL -> Color(0xFF4CAF50).copy(alpha = 0.3f) to Color(0xFF4CAF50)
        UserFeedback.PRIORITY_LOW -> Color(0xFF9E9E9E).copy(alpha = 0.3f) to Color(0xFF9E9E9E)
        else -> Color.Gray.copy(alpha = 0.3f) to Color.Gray
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = when (priority) {
                UserFeedback.PRIORITY_HIGH -> "高"
                UserFeedback.PRIORITY_NORMAL -> "普通"
                UserFeedback.PRIORITY_LOW -> "低"
                else -> "普通"
            },
            color = textColor,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
