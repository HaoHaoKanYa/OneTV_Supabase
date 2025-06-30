package top.cywin.onetv.tv.supabase.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 应用退出时的同步管理器
 * 确保应用退出时只执行一次同步操作，避免重复同步
 */
object SupabaseAppExitSyncManager {
    private const val TAG = "AppExitSyncManager"
    
    // 退出同步锁
    private val exitSyncLock = Mutex()
    private var hasExitSynced = false
    private var isExitSyncing = false
    
    /**
     * 执行应用退出时的同步操作
     * 确保只执行一次
     */
    suspend fun performExitSync(context: Context): Int {
        return exitSyncLock.withLock {
            if (hasExitSynced) {
                Log.d(TAG, "本次会话已完成同步，跳过重复请求")
                return@withLock 0
            }

            if (isExitSyncing) {
                Log.d(TAG, "同步正在进行中，跳过重复请求")
                return@withLock 0
            }

            isExitSyncing = true
            try {
                Log.d(TAG, "开始执行应用退出同步")

                // 执行同步操作
                val syncCount = SupabaseWatchHistorySyncService.syncToServer(context)

                if (syncCount > 0) {
                    Log.d(TAG, "应用退出同步完成，成功同步 $syncCount 条记录")
                } else {
                    Log.d(TAG, "应用退出同步完成，无记录需要同步")
                }

                hasExitSynced = true
                return@withLock syncCount
            } catch (e: Exception) {
                Log.e(TAG, "应用退出同步失败: ${e.message}", e)
                return@withLock 0
            } finally {
                isExitSyncing = false
            }
        }
    }
    
    /**
     * 重置同步状态
     * 用于应用重新启动时
     */
    fun resetSyncState() {
        hasExitSynced = false
        isExitSyncing = false
        Log.d(TAG, "同步状态已重置")
    }

    /**
     * 检查是否已完成退出同步
     */
    fun hasCompletedExitSync(): Boolean {
        return hasExitSynced
    }

    /**
     * 检查是否正在进行同步
     */
    fun isSyncInProgress(): Boolean {
        return isExitSyncing
    }
}
