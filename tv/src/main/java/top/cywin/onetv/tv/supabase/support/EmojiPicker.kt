package top.cywin.onetv.tv.supabase.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 表情选择器组件
 * 类似微信的表情选择界面
 */
@Composable
fun EmojiPickerDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .width(400.dp)
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
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
                            text = "选择表情",
                            color = Color(0xFFFFD700),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.Gray
                            )
                        ) {
                            Text("关闭", fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 表情网格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(getEmojiList()) { emoji ->
                            EmojiItem(
                                emoji = emoji,
                                onClick = {
                                    onEmojiSelected(emoji)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个表情项
 */
@Composable
private fun EmojiItem(
    emoji: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = Color.Gray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp
        )
    }
}

/**
 * 获取表情列表
 * 包含常用的表情符号
 */
private fun getEmojiList(): List<String> {
    return listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
        "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
        "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
        "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
        "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨",
        "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥",
        "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
        "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
        "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑",
        "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻",
        "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸",
        "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "👋",
        "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "✌️", "🤞",
        "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇",
        "☝️", "👍", "👎", "👊", "✊", "🤛", "🤜", "👏",
        "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
        "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃",
        "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
        "👄", "💋", "🩸", "👶", "🧒", "👦", "👧", "🧑",
        "👱", "👨", "🧔", "👩", "🧓", "👴", "👵", "🙍",
        "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇", "🤦",
        "🤷", "👮", "🕵️", "💂", "🥷", "👷", "🤴", "👸",
        "👳", "👲", "🧕", "🤵", "👰", "🤰", "🤱", "👼"
    )
}
