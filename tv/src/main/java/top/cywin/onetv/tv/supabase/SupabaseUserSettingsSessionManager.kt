package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.tv.supabase.SupabaseCacheManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 用户设置缓存管理器
 * 专门用于管理SupabaseUserSettings界面的用户设置缓存
 * 使用统一的SupabaseCacheManager实现
 */
object SupabaseUserSettingsSessionManager {
    private const val TAG = "SettingsManager"
    
    // 缓存有效期：30天（以毫秒为单位）
    const val CACHE_VALIDITY_PERIOD = 30L * 24 * 60 * 60 * 1000 // 30天
    
    /**
     * 用户退出登录时清除缓存
     * 当用户退出登录时应调用此方法，确保下一个登录用户不会看到上一个用户的设置
     * @param context 应用上下文
     */
    fun logoutCleanup(context: Context) {
        Log.d(TAG, "用户退出登录，清除用户设置缓存")
    }
    
    /**
     * 用户退出登录时清除缓存（协程版本）
     * @param context 应用上下文
     */
    suspend fun logoutCleanupAsync(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_SETTINGS)
        Log.d(TAG, "用户退出登录，已清除用户设置缓存（异步版本）")
    }
    
    /**
     * 用户设置数据类
     * 根据数据库结构定义
     */
    data class UserSettings(
        val userId: String = "",
        val theme: String = "dark",
        val playerSettings: JSONObject = JSONObject(),
        val notificationEnabled: Boolean = true,
        val gender: String? = null,
        val birthDate: String? = null,
        val region: String? = null,
        val languagePreference: String = "zh-CN",
        val timezone: String = "Asia/Shanghai",
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val bio: String? = null,
        val updatedAt: String = ""
    )
    
    /**
     * 保存用户设置到缓存
     * @param context 应用上下文
     * @param settings 用户设置数据
     */
    suspend fun saveUserSettings(context: Context, settings: UserSettings) = withContext(Dispatchers.IO) {
        // 使用统一缓存管理器保存数据
        SupabaseCacheManager.saveCache(context, SupabaseCacheKey.USER_SETTINGS, settings)
        
        Log.d(TAG, "用户设置缓存成功 | userId=${settings.userId} | " +
                "主题=${settings.theme} | " +
                "通知=${settings.notificationEnabled} | " +
                "显示名=${settings.displayName ?: "未设置"} | " +
                "缓存时间：${formatTime(System.currentTimeMillis())}")
    }
    
    /**
     * 保存用户设置到缓存（同步版本，用于不支持协程的场景）
     * @param context 应用上下文
     * @param settings 用户设置数据
     */
    fun saveUserSettingsSync(context: Context, settings: UserSettings) {
        kotlinx.coroutines.runBlocking {
            saveUserSettings(context, settings)
        }
    }
    
    /**
     * 获取缓存的用户设置
     * @param context 应用上下文
     * @return 用户设置，如果缓存中没有则返回null
     */
    suspend fun getCachedUserSettings(context: Context): UserSettings? = withContext(Dispatchers.IO) {
        try {
            // 获取原始缓存数据，不进行直接类型转换
            val rawData = SupabaseCacheManager.getRawCache(context, SupabaseCacheKey.USER_SETTINGS)
            if (rawData == null) {
                Log.d(TAG, "用户设置缓存为空")
                return@withContext null
            }
            
            // 安全类型转换
            return@withContext when (rawData) {
                is UserSettings -> {
                    Log.d(TAG, "读取缓存设置: ${rawData.userId}, 数据已是UserSettings类型")
                    rawData
                }
                is Map<*, *> -> {
                    Log.d(TAG, "检测到Map类型(可能是LinkedTreeMap)，进行安全转换")
                    try {
                        val gson = Gson()
                        val json = gson.toJson(rawData)
                        val settings = gson.fromJson(json, UserSettings::class.java)
                        Log.d(TAG, "Map转换成功: ${settings.userId}")
                        settings
                    } catch (e: Exception) {
                        Log.e(TAG, "Map转换失败: ${e.message}", e)
                        null
                    }
                }
                else -> {
                    Log.w(TAG, "未知数据类型: ${rawData.javaClass.name}，尝试强制转换")
                    try {
                        val gson = Gson()
                        val json = gson.toJson(rawData)
                        val settings = gson.fromJson(json, UserSettings::class.java)
                        Log.d(TAG, "强制转换成功: ${settings.userId}")
                        settings
                    } catch (e: Exception) {
                        Log.e(TAG, "强制转换失败: ${e.message}", e)
                        // 清除可能损坏的缓存
                        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_SETTINGS)
                        Log.d(TAG, "已清除无效的用户设置缓存")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            // 捕获其他未预期的异常
            Log.e(TAG, "读取用户设置缓存失败: ${e.message}", e)
            
            // 尝试清除错误的缓存
            try {
                SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_SETTINGS)
                Log.d(TAG, "已清除无效的用户设置缓存")
            } catch (clearEx: Exception) {
                Log.e(TAG, "清除无效缓存失败", clearEx)
            }
            
            return@withContext null
        }
    }
    
    /**
     * 获取缓存的用户设置（同步版本，用于不支持协程的场景）
     * @param context 应用上下文
     * @return 用户设置，如果缓存中没有则返回null
     */
    fun getCachedUserSettingsSync(context: Context): UserSettings? {
        return kotlinx.coroutines.runBlocking {
            getCachedUserSettings(context)
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
            SupabaseCacheKey.USER_SETTINGS_LAST_LOADED, 
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
     * @param currentSettings 当前用户设置（可能来自其他来源）
     * @return 如果缓存有效则返回true，否则返回false
     */
    suspend fun isCacheValid(context: Context, currentSettings: UserSettings? = null): Boolean = withContext(Dispatchers.IO) {
        // 如果没有提供当前数据，只检查时间有效性
        if (currentSettings == null) {
            val isValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.USER_SETTINGS)
            Log.d(TAG, "缓存有效性检查（仅时间）| 结果: $isValid")
            return@withContext isValid
        }
        
        // 如果提供了当前数据，同时检查时间有效性和字段变化
        val isValid = top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseFieldTracker
            .isValidWithFieldCheck(context, SupabaseCacheKey.USER_SETTINGS, currentSettings)
        
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
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_SETTINGS)
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
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_SETTINGS)
        Log.d(TAG, "用户设置缓存已清除")
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
