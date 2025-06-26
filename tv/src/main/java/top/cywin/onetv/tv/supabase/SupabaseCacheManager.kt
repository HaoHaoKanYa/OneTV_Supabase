package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager as CoreCacheManager

/**
 * Supabase缓存管理辅助类
 * 用于集中处理各种缓存的清除操作
 * 作为Core模块缓存管理器的适配层
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
        
        // 清除观看历史缓存
        SupabaseWatchHistorySessionManager.clearHistory(context)
        
        // 清除频道收藏缓存
        try {
            kotlinx.coroutines.runBlocking {
                SupabaseChannelFavoritesManager.clearCache(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除频道收藏缓存失败", e)
        }
        
        // 如果有其他缓存管理器，也在这里添加清理操作
        
        Log.d(TAG, "用户退出登录，已清除所有相关缓存")
    }
    
    /**
     * 用户退出登录时清除所有相关缓存（协程版本）
     * 在用户登出操作时调用此方法，确保下一个登录用户不会看到上一个用户的数据
     * @param context 应用上下文
     */
    suspend fun clearAllCachesOnLogoutAsync(context: Context) = withContext(Dispatchers.IO) {
        // 使用核心缓存管理器清除所有用户相关缓存
        CoreCacheManager.clearUserCaches(context)
        
        // 清除用户资料缓存
        SupabaseUserProfileInfoSessionManager.logoutCleanupAsync(context)
        
        // 清除用户设置缓存
        SupabaseUserSettingsSessionManager.logoutCleanupAsync(context)
        
        // 清除观看历史缓存
        SupabaseWatchHistorySessionManager.clearHistoryAsync(context)
        
        // 清除频道收藏缓存
        SupabaseChannelFavoritesManager.clearCache(context)
        
        // 如果有其他缓存管理器，也在这里添加清理操作
        
        Log.d(TAG, "用户退出登录，已清除所有相关缓存（异步版本）")
    }
    
    /**
     * 清除指定缓存
     * @param context 应用上下文
     * @param key 缓存键
     */
    suspend fun clearCache(context: Context, key: SupabaseCacheKey) {
        CoreCacheManager.clearCache(context, key)
    }
    
    /**
     * 清除所有缓存
     * @param context 应用上下文
     */
    suspend fun clearAllCaches(context: Context) {
        CoreCacheManager.clearAllCaches(context)
    }
    
    /**
     * 获取缓存数据
     * @param context 应用上下文
     * @param key 缓存键
     * @param defaultValue 默认值
     * @return 缓存数据，如果不存在则返回默认值
     */
    suspend fun <T : Any> getCache(context: Context, key: SupabaseCacheKey, defaultValue: T? = null): T? {
        return CoreCacheManager.getCache(context, key, defaultValue)
    }
    
    /**
     * 保存缓存数据
     * @param context 应用上下文
     * @param key 缓存键
     * @param data 要缓存的数据
     */
    suspend fun <T : Any> saveCache(context: Context, key: SupabaseCacheKey, data: T) {
        CoreCacheManager.saveCache(context, key, data)
    }
    
    /**
     * 检查缓存是否有效
     * @param context 应用上下文
     * @param key 缓存键
     * @return 如果缓存有效则返回true，否则返回false
     */
    suspend fun isValid(context: Context, key: SupabaseCacheKey): Boolean {
        return CoreCacheManager.isValid(context, key)
    }
    
    /**
     * 预热缓存
     * 在应用启动时调用此方法，预加载常用的缓存数据到内存中
     * @param context 应用上下文
     */
    suspend fun preheatCache(context: Context) {
        CoreCacheManager.preheatCache(context)
    }
    
    /**
     * 预热特定用户的缓存
     * 当用户登录后调用此方法，预加载用户相关的缓存数据
     * @param context 应用上下文
     * @param userId 用户ID
     * @param forceServer 是否强制从服务器拉取观看历史
     */
    suspend fun preheatUserCache(context: Context, userId: String, forceServer: Boolean = false) {
        CoreCacheManager.preheatUserCache(context, userId, forceServer)
    }
    
    /**
     * 获取原始缓存数据，不进行类型转换
     * 用于需要手动进行类型转换的场景
     * @param context 应用上下文
     * @param key 缓存键
     * @return 原始缓存对象，可能是任意类型
     */
    suspend fun getRawCache(context: Context, key: SupabaseCacheKey): Any? {
        return CoreCacheManager.getRawCache(context, key)
    }
    
    /**
     * 获取原始缓存数据的同步版本
     * @param context 应用上下文
     * @param key 缓存键
     * @return 原始缓存对象，可能是任意类型
     */
    fun getRawCacheSync(context: Context, key: SupabaseCacheKey): Any? {
        return kotlinx.coroutines.runBlocking {
            getRawCache(context, key)
        }
    }
    
    /**
     * 安全地将任何缓存对象转换为通用类型
     * 处理LinkedTreeMap到指定类型的转换问题
     * 
     * @param data 任何类型的数据对象
     * @param targetClass 目标类型的Class对象
     * @param defaultValue 默认值，如果转换失败则返回此值
     * @return 转换后的对象，如果转换失败则返回defaultValue
     */
    fun <T : Any> safeConvertToType(data: Any?, targetClass: Class<T>, defaultValue: T? = null): T? {
        if (data == null) return defaultValue
        
        return try {
            when {
                targetClass.isInstance(data) -> {
                    Log.d(TAG, "数据已经是${targetClass.simpleName}类型，无需转换")
                    targetClass.cast(data)
                }
                data is Map<*, *> -> {
                    Log.d(TAG, "检测到Map类型（可能是LinkedTreeMap），转换为${targetClass.simpleName}")
                    val gson = com.google.gson.Gson()
                    val json = gson.toJson(data)
                    gson.fromJson(json, targetClass)
                }
                else -> {
                    Log.w(TAG, "未知数据类型: ${data.javaClass.name}，尝试强制转换为${targetClass.simpleName}")
                    try {
                        val gson = com.google.gson.Gson()
                        val json = gson.toJson(data)
                        gson.fromJson(json, targetClass)
                    } catch (e: Exception) {
                        Log.e(TAG, "转换为${targetClass.simpleName}失败: ${e.message}", e)
                        defaultValue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "转换数据到${targetClass.simpleName}时出错: ${e.message}", e)
            defaultValue
        }
    }
} 