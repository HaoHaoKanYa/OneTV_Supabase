package top.cywin.onetv.tv.supabase.sync

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.cywin.onetv.tv.supabase.SupabaseWatchHistorySessionManager

/**
 * 观看历史同步服务演示
 * 展示如何使用SupabaseWatchHistorySyncService同步观看历史数据
 */
object SupabaseWatchHistorySyncServiceDemo {
    
    private const val TAG = "WatchHistorySyncDemo"
    
    /**
     * 同步面板UI组件
     * 提供同步到服务器、从服务器同步和双向同步的功能
     */
    @Composable
    fun SyncPanel() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        var isSyncing by remember { mutableStateOf(false) }
        var syncMessage by remember { mutableStateOf("") }
        var syncProgress by remember { mutableStateOf(0f) }
        var pendingSyncCount by remember { mutableStateOf(0) }
        
        // 获取待同步记录数
        LaunchedEffect(Unit) {
            pendingSyncCount = if (SupabaseWatchHistorySessionManager.hasPendingChanges()) 1 else 0
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = "观看历史同步",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 待同步记录数
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "待同步记录数: ",
                    color = Color.White
                )
                
                Text(
                    text = "$pendingSyncCount",
                    color = if (pendingSyncCount > 0) Color(0xFFFF9800) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = {
                        scope.launch {
                            pendingSyncCount = if (SupabaseWatchHistorySessionManager.hasPendingChanges()) 1 else 0
                        }
                    },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text("刷新", color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 同步按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 上传到服务器
                Button(
                    onClick = {
                        // 使用应用级别的协程作用域，避免UI生命周期影响
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            isSyncing = true
                            syncMessage = "正在上传到服务器..."

                            try {
                                val count = SupabaseWatchHistorySyncService.syncToServer(context)

                                // 在主线程更新UI
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    syncMessage = "成功上传 $count 条记录到服务器"
                                    pendingSyncCount = if (SupabaseWatchHistorySessionManager.hasPendingChanges()) 1 else 0
                                    Toast.makeText(context, "上传完成: $count 条记录", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "上传失败", e)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    syncMessage = "上传失败: ${e.message}"
                                    Toast.makeText(context, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = !isSyncing && pendingSyncCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        disabledContainerColor = Color(0xFF666666)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "上传到服务器",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上传", color = Color.White)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 从服务器下载
                Button(
                    onClick = {
                        // 使用应用级别的协程作用域，避免UI生命周期影响
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            isSyncing = true
                            syncMessage = "正在从服务器下载..."

                            try {
                                val success = SupabaseWatchHistorySyncService.syncFromServer(context)

                                // 在主线程更新UI
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    syncMessage = if (success) "从服务器下载成功" else "从服务器下载失败"
                                    pendingSyncCount = if (SupabaseWatchHistorySessionManager.hasPendingChanges()) 1 else 0
                                    Toast.makeText(context, syncMessage, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "下载失败", e)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    syncMessage = "下载失败: ${e.message}"
                                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFF666666)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "从服务器下载",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("下载", color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 同步状态
            if (isSyncing) {
                LinearProgressIndicator(
                    progress = { syncProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2196F3)
                )
            }
            
            if (syncMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = syncMessage,
                    color = when {
                        syncMessage.contains("成功") -> Color(0xFF4CAF50)
                        syncMessage.contains("失败") -> Color(0xFFF44336)
                        else -> Color.White
                    },
                    fontSize = 14.sp
                )
            }
        }
    }
    
    /**
     * 在其他Activity或Fragment中使用此同步服务的示例
     */
    fun syncHistoryExample(context: Context) {
        // 1. 使用应用级别的协程作用域，避免UI生命周期影响
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // 2. 显示同步中的UI
                // ...

                // 3. 执行同步操作
                val count = SupabaseWatchHistorySyncService.syncToServer(context)

                // 4. 在主线程更新UI显示结果
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "成功同步 $count 条记录", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                // 5. 处理错误
                Log.e(TAG, "同步失败", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
} 