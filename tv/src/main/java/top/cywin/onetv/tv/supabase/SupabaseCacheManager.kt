package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log

/**
 * Supabase缓存管理辅助类
 * 用于集中处理各种缓存的清除操作
 */
object SupabaseCacheManager {
    private const val TAG = "SupabaseCacheManager"
    
    /**
     * 用户退出登录时清除所有相关缓存
     * 在用户登出操作时调用此方法，确保下一个登录用户不会看到上一个用户的数据
     * @param context 应用上下文
     */
    fun clearAllCachesOnLogout(context: Context) {
        // 清除用户资料缓存
        SupabaseUserProfileInfoSessionManager.logoutCleanup(context)
        
        // 清除用户设置缓存
        SupabaseUserSettingsSessionManager.logoutCleanup(context)
        
        // 如果有其他缓存管理器，也在这里添加清理操作
        
        Log.d(TAG, "用户退出登录，已清除所有相关缓存")
    }
} 