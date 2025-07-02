package top.cywin.onetv.tv.supabase.support

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TAG = "FeedbackFormScreen"

/**
 * 用户反馈表单界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackFormScreen(
    modifier: Modifier = Modifier,
    viewModel: SupportViewModel,
    onClose: () -> Unit = {}
) {
    Log.d(TAG, "FeedbackFormScreen: 初始化反馈表单界面")
    val uiState by viewModel.uiState.collectAsState()

    var selectedType by remember {
        mutableStateOf(UserFeedback.TYPE_GENERAL).also {
            Log.d(TAG, "FeedbackFormScreen: 初始化反馈类型 = ${UserFeedback.TYPE_GENERAL}")
        }
    }
    var title by remember {
        mutableStateOf("").also {
            Log.d(TAG, "FeedbackFormScreen: 初始化标题字段")
        }
    }
    var description by remember {
        mutableStateOf("").also {
            Log.d(TAG, "FeedbackFormScreen: 初始化描述字段")
        }
    }
    var selectedPriority by remember { mutableStateOf(UserFeedback.PRIORITY_NORMAL) }
    
    val scrollState = rememberScrollState()
    
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
                text = "提交反馈",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    Log.d(TAG, "FeedbackFormScreen: 用户点击取消按钮")
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.7f)
                )
            ) {
                Text("取消", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 表单内容
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // 反馈类型选择
            Text(
                text = "反馈类型",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            FeedbackTypeSelector(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 优先级选择
            Text(
                text = "优先级",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            PrioritySelector(
                selectedPriority = selectedPriority,
                onPrioritySelected = { selectedPriority = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 标题输入
            Text(
                text = "问题标题",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("请简要描述问题", color = Color.Gray)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 详细描述输入
            Text(
                text = "详细描述",
                color = Color(0xFFFFD700),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text("请详细描述遇到的问题或建议", color = Color.Gray)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray
                ),
                maxLines = 5
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 错误信息显示
        uiState.feedbackState.error?.let { error ->
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

        // 提交按钮
        Button(
            onClick = {
                Log.d(TAG, "FeedbackFormScreen: 用户点击提交按钮")
                Log.d(TAG, "FeedbackFormScreen: 验证表单 - 标题: ${title.isNotBlank()}, 描述: ${description.isNotBlank()}")
                if (title.isNotBlank() && description.isNotBlank()) {
                    Log.d(TAG, "FeedbackFormScreen: 提交反馈 - 类型: $selectedType, 标题: ${title.trim()}, 优先级: $selectedPriority")
                    viewModel.submitFeedback(
                        feedbackType = selectedType,
                        title = title.trim(),
                        description = description.trim(),
                        priority = selectedPriority
                    )
                } else {
                    Log.w(TAG, "FeedbackFormScreen: 表单验证失败 - 标题或描述为空")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank() && description.isNotBlank() && !uiState.feedbackState.isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4285F4)
            )
        ) {
            if (uiState.feedbackState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("提交反馈", color = Color.White)
        }
    }
}

/**
 * 反馈类型选择器 - 单行左右排列布局
 */
@Composable
private fun FeedbackTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    val feedbackTypes = listOf(
        UserFeedback.TYPE_BUG to "问题报告",
        UserFeedback.TYPE_FEATURE to "功能建议",
        UserFeedback.TYPE_COMPLAINT to "投诉建议",
        UserFeedback.TYPE_SUGGESTION to "改进建议",
        UserFeedback.TYPE_GENERAL to "一般反馈"
    )

    // 单行显示所有5个选项
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        feedbackTypes.forEach { (type, label) ->
            FeedbackTypeItem(
                type = type,
                label = label,
                isSelected = selectedType == type,
                onSelected = {
                    Log.d(TAG, "TypeSelector: 用户选择反馈类型 = $type")
                    onTypeSelected(type)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 反馈类型单项组件 - 紧凑单行布局
 */
@Composable
private fun FeedbackTypeItem(
    type: String,
    label: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = onSelected
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelected,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFFFFD700),
                unselectedColor = Color.Gray
            ),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * 优先级选择器
 */
@Composable
private fun PrioritySelector(
    selectedPriority: String,
    onPrioritySelected: (String) -> Unit
) {
    val priorities = listOf(
        UserFeedback.PRIORITY_LOW to "低",
        UserFeedback.PRIORITY_NORMAL to "普通",
        UserFeedback.PRIORITY_HIGH to "高"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        priorities.forEach { (priority, label) ->
            Row(
                modifier = Modifier
                    .selectable(
                        selected = selectedPriority == priority,
                        onClick = {
                            Log.d(TAG, "PrioritySelector: 用户选择优先级 = $priority")
                            onPrioritySelected(priority)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPriority == priority,
                    onClick = { onPrioritySelected(priority) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFFFFD700),
                        unselectedColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
