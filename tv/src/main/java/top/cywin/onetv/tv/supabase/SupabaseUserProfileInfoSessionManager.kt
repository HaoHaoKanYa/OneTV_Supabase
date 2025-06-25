package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.tv.supabase.SupabaseCacheManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 用户资料缓存管理器
 * 专门用于管理SupabaseUserProfileInfo界面的用户资料缓存
 * 使用统一的SupabaseCacheManager实现
 */
object SupabaseUserProfileInfoSessionManager {
    private const val TAG = "ProfileInfoManager"
    
    // 缓存有效期：30天（以毫秒为单位）
    private const val CACHE_VALIDITY_PERIOD = 30L * 24 * 60 * 60 * 1000 // 30天
    
    /**
     * 用户退出登录时清除缓存
     * 当用户退出登录时应调用此方法，确保下一个登录用户不会看到上一个用户的资料
     * @param context 应用上下文
     */
    fun logoutCleanup(context: Context) {
        Log.d(TAG, "用户退出登录，清除用户资料缓存")
    }
    
    /**
     * 用户退出登录时清除缓存（协程版本）
     * @param context 应用上下文
     */
    suspend fun logoutCleanupAsync(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_PROFILE)
        Log.d(TAG, "用户退出登录，已清除用户资料缓存（异步版本）")
    }
    
    /**
     * 保存用户资料到缓存
     * @param context 应用上下文
     * @param data 用户数据
     */
    suspend fun saveUserProfileData(context: Context, data: SupabaseUserDataIptv) = withContext(Dispatchers.IO) {
        // 使用统一缓存管理器保存数据
        SupabaseCacheManager.saveCache(context, SupabaseCacheKey.USER_PROFILE, data)
        
        Log.d(TAG, "用户资料缓存成功 | userId=${data.userid} | " +
                "VIP状态=${data.is_vip} | " +
                "VIP开始=${data.vipstart ?: "未设置"} | " +
                "VIP结束=${data.vipend ?: "未设置"} | " +
                "最后登录时间=${data.lastlogintime ?: "未设置"} | " +
                "最后登录设备=${data.lastlogindevice ?: "未设置"} | " +
                "缓存时间：${formatTime(System.currentTimeMillis())}")
    }
    
    /**
     * 保存用户资料到缓存（同步版本，用于不支持协程的场景）
     * @param context 应用上下文
     * @param data 用户数据
     */
    fun saveUserProfileDataSync(context: Context, data: SupabaseUserDataIptv) {
        kotlinx.coroutines.runBlocking {
            saveUserProfileData(context, data)
        }
    }
    
    /**
     * 获取缓存的用户资料
     * @param context 应用上下文
     * @return 用户资料，如果缓存中没有则返回null
     */
    suspend fun getCachedUserProfileData(context: Context): SupabaseUserDataIptv? = withContext(Dispatchers.IO) {
        val data = SupabaseCacheManager.getCache<SupabaseUserDataIptv>(context, SupabaseCacheKey.USER_PROFILE)
        Log.d(TAG, "读取缓存数据: ${data?.username ?: "空"}")
        return@withContext data
    }
    
    /**
     * 获取缓存的用户资料（同步版本，用于不支持协程的场景）
     * @param context 应用上下文
     * @return 用户资料，如果缓存中没有则返回null
     */
    fun getCachedUserProfileDataSync(context: Context): SupabaseUserDataIptv? {
        return kotlinx.coroutines.runBlocking {
            getCachedUserProfileData(context)
        }
    }
    
    /**
     * 获取最后加载时间
     * @param context 应用上下文
     * @return 最后加载时间的时间戳
     */
    suspend fun getLastLoadedTime(context: Context): Long = withContext(Dispatchers.IO) {
        return@withContext SupabaseCacheManager.getCache<Long>(
            context, 
            SupabaseCacheKey.USER_PROFILE_LAST_LOADED, 
            0L
        ) ?: 0L
    }
    
    /**
     * 获取最后加载时间（同步版本）
     * @param context 应用上下文
     * @return 最后加载时间的时间戳
     */
    fun getLastLoadedTimeSync(context: Context): Long {
        return kotlinx.coroutines.runBlocking {
            getLastLoadedTime(context)
        }
    }
    
    /**
     * 检查缓存是否有效
     * @param context 应用上下文
     * @param currentData 当前用户数据（可能来自其他来源）
     * @return 如果缓存有效则返回true，否则返回false
     */
    suspend fun isCacheValid(context: Context, currentData: SupabaseUserDataIptv? = null): Boolean = withContext(Dispatchers.IO) {
        // 如果没有提供当前数据，只检查时间有效性
        if (currentData == null) {
            val isValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.USER_PROFILE)
            Log.d(TAG, "缓存有效性检查（仅时间）| 结果: $isValid")
            return@withContext isValid
        }
        
        // 如果提供了当前数据，同时检查时间有效性和字段变化
        val isValid = top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseFieldTracker
            .isValidWithFieldCheck(context, SupabaseCacheKey.USER_PROFILE, currentData)
        
        Log.d(TAG, "缓存有效性检查（时间+字段）| 结果: $isValid")
        return@withContext isValid
    }
    
    /**
     * 检查缓存是否有效（同步版本）
     * @param context 应用上下文
     * @return 如果缓存有效则返回true，否则返回false
     */
    fun isCacheValidSync(context: Context): Boolean {
        return kotlinx.coroutines.runBlocking {
            isCacheValid(context)
        }
    }
    
    /**
     * 强制使缓存失效
     * 在用户权限变更或其他需要强制刷新的情况下调用此方法
     * @param context 应用上下文
     */
    suspend fun invalidateCache(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_PROFILE)
        Log.d(TAG, "缓存已手动失效")
    }
    
    /**
     * 强制使缓存失效（同步版本）
     * @param context 应用上下文
     */
    fun invalidateCacheSync(context: Context) {
        kotlinx.coroutines.runBlocking {
            invalidateCache(context)
        }
    }
    
    /**
     * 清除缓存
     * @param context 应用上下文
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_PROFILE)
        Log.d(TAG, "用户资料缓存已清除")
    }
    
    /**
     * 清除缓存（同步版本）
     * @param context 应用上下文
     */
    fun clearCacheSync(context: Context) {
        kotlinx.coroutines.runBlocking {
            clearCache(context)
        }
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(time: Long): String {
        if (time <= 0) return "未记录"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(time))
    }
}
