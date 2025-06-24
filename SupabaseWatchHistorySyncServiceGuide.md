# 观看历史同步服务使用指南

## 1. 概述

`SupabaseWatchHistorySyncService` 是一个专门负责观看历史服务器同步功能的模块，它完全从 `SupabaseWatchHistorySessionManager` 中分离出来，作为独立的服务存在。这种分离使得缓存管理和同步功能各司其职，降低了代码耦合度，提高了可维护性。

## 2. 功能特点

- **完全分离的同步模块**：不再与缓存管理耦合
- **支持批量和单条同步**：优先使用批量同步，失败时自动回退到单条同步
- **双向同步**：支持上传到服务器和从服务器下载
- **同步状态回调**：提供完整的同步状态监听机制
- **ID映射管理**：自动维护本地ID和服务器ID的映射关系
- **自动通知缓存管理器**：同步完成后自动通知缓存管理器重新加载数据

## 3. 基本用法

### 3.1 导入服务

```kotlin
import top.cywin.onetv.tv.supabase.sync.SupabaseWatchHistorySyncService
```

### 3.2 同步到服务器

将本地观看历史同步到服务器，适用于有新记录需要上传的场景。

```kotlin
// 在协程中调用
lifecycleScope.launch {
    try {
        val syncCount = SupabaseWatchHistorySyncService.syncToServer(context)
        Toast.makeText(context, "成功同步 $syncCount 条记录到服务器", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("Sync", "同步失败", e)
        Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

### 3.3 从服务器同步

从服务器获取观看历史并合并到本地，适用于首次加载或强制刷新本地数据的场景。

```kotlin
// 在协程中调用
lifecycleScope.launch {
    try {
        val success = SupabaseWatchHistorySyncService.syncFromServer(context)
        if (success) {
            Toast.makeText(context, "从服务器同步成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "从服务器同步失败", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("Sync", "同步失败", e)
        Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

### 3.4 使用回调进行双向同步

使用回调接口监听同步过程，适用于需要显示进度或详细状态的场景。

```kotlin
// 在协程中调用
lifecycleScope.launch {
    try {
        SupabaseWatchHistorySyncService.syncWithCallback(
            context,
            object : SupabaseWatchHistorySyncService.SyncCallback {
                override fun onSyncStarted() {
                    // 同步开始
                    progressBar.visibility = View.VISIBLE
                    statusText.text = "同步开始..."
                }
                
                override fun onSyncProgress(progress: Int, total: Int) {
                    // 同步进度更新
                    progressBar.progress = (progress * 100 / total)
                    statusText.text = "同步进度: $progress/$total"
                }
                
                override fun onSyncCompleted(successCount: Int) {
                    // 同步完成
                    progressBar.visibility = View.GONE
                    statusText.text = "同步完成，成功同步 $successCount 条记录"
                }
                
                override fun onSyncFailed(errorMessage: String) {
                    // 同步失败
                    progressBar.visibility = View.GONE
                    statusText.text = "同步失败: $errorMessage"
                }
            }
        )
    } catch (e: Exception) {
        Log.e("Sync", "同步失败", e)
    }
}
```

### 3.5 获取待同步记录数

获取本地需要同步到服务器的记录数量，适用于显示同步状态或判断是否需要同步的场景。

```kotlin
// 在协程中调用
lifecycleScope.launch {
    val pendingCount = SupabaseWatchHistorySyncService.getPendingSyncCount(context)
    if (pendingCount > 0) {
        syncButton.visibility = View.VISIBLE
        syncButton.text = "同步($pendingCount)"
    } else {
        syncButton.visibility = View.GONE
    }
}
```

## 4. 集成到应用中

### 4.1 添加到设置页面

在设置页面中添加观看历史同步选项，让用户可以手动触发同步。

```kotlin
@Composable
fun SettingsCategoryWatchHistory() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSyncCount by remember { mutableStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // 获取待同步记录数
    LaunchedEffect(Unit) {
        pendingSyncCount = SupabaseWatchHistorySyncService.getPendingSyncCount(context)
    }
    
    // 设置项UI
    SettingItem(
        title = "观看历史同步",
        description = if (pendingSyncCount > 0) "有 $pendingSyncCount 条记录待同步" else "所有记录已同步",
        icon = Icons.Default.Sync,
        onClick = {
            if (!isSyncing) {
                scope.launch {
                    isSyncing = true
                    try {
                        val count = SupabaseWatchHistorySyncService.syncToServer(context)
                        Toast.makeText(context, "成功同步 $count 条记录", Toast.LENGTH_SHORT).show()
                        pendingSyncCount = SupabaseWatchHistorySyncService.getPendingSyncCount(context)
                    } catch (e: Exception) {
                        Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSyncing = false
                    }
                }
            }
        },
        trailing = {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    )
}
```

### 4.2 定期自动同步

在应用后台定期执行同步，确保数据及时更新。

```kotlin
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // 检查网络连接
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            if (network == null) {
                Log.d(TAG, "网络不可用，跳过同步")
                return Result.retry()
            }
            
            // 检查用户登录状态
            val userId = SupabaseSessionManager.getCachedUserData(applicationContext)?.userid
            if (userId == null) {
                Log.d(TAG, "用户未登录，跳过同步")
                return Result.success()
            }
            
            // 执行同步
            val syncCount = SupabaseWatchHistorySyncService.syncToServer(applicationContext)
            Log.d(TAG, "后台同步完成，同步了 $syncCount 条记录")
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "后台同步失败", e)
            Result.failure()
        }
    }
    
    companion object {
        private const val TAG = "SyncWorker"
        
        /**
         * 安排定期同步任务
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                6, TimeUnit.HOURS, // 每6小时同步一次
                1, TimeUnit.HOURS  // 灵活时间窗口1小时
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "watch_history_sync",
                ExistingPeriodicWorkPolicy.REPLACE,
                syncRequest
            )
            
            Log.d(TAG, "已安排观看历史定期同步任务")
        }
    }
}
```

## 5. 最佳实践

### 5.1 何时使用同步功能

- **观看结束时**：用户观看结束后，将新记录上传到服务器
- **应用启动时**：应用启动时，检查并同步未上传的记录
- **网络状态变化**：网络恢复连接时，尝试同步待上传的记录
- **切换账号后**：用户登录或切换账号后，从服务器同步该账号的历史记录
- **定期后台同步**：使用 WorkManager 定期在后台执行同步操作

### 5.2 错误处理

- **网络错误**：捕获网络异常，稍后重试
- **服务器错误**：记录错误信息，必要时向用户展示提示
- **冲突处理**：合并本地和服务器数据时，优先保留较新的记录

### 5.3 性能优化

- **批量同步**：尽量使用批量同步而非单条同步，减少网络请求次数
- **适当频率**：避免过于频繁的同步操作，考虑用户行为和电池消耗
- **网络条件**：在Wi-Fi连接时执行更频繁的同步，在移动网络时减少同步频率

## 6. 故障排查

### 6.1 常见问题

1. **同步失败，返回401错误**
   - 原因：用户会话已过期或无效
   - 解决：重新登录，刷新会话令牌

2. **无法从服务器获取数据**
   - 原因：网络连接问题或服务器接口变更
   - 解决：检查网络状态，确认API端点是否可用

3. **本地数据与服务器不一致**
   - 原因：同步过程中断或数据合并冲突
   - 解决：强制执行完整双向同步，解决冲突

### 6.2 日志分析

服务中包含详细的日志记录，可通过以下过滤器查看相关日志：

```
adb logcat -s WatchHistorySyncService
```

关键日志信息包括：
- 同步开始/完成状态
- 待同步记录数量
- 服务器响应和错误信息
- ID映射更新情况

## 7. 总结

`SupabaseWatchHistorySyncService` 提供了完整的观看历史同步解决方案，它独立于缓存管理器，专注于数据同步功能。通过合理使用这个服务，可以确保用户的观看历史记录在多设备间保持同步，同时保证数据的一致性和完整性。

无论是在设置页面提供手动同步选项，还是在应用后台自动执行同步，这个服务都能提供稳定可靠的同步功能，增强用户体验。 