package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager as CoreCacheManager

/**
 * Supabase缓存管理辅助类
 * 用于集中处理各种缓存的清除操作
 * 作为Core模块缓存管理器的适配层
 */
object SupabaseCacheManager {
    private const val TAG = "SupabaseCacheManager"
    
    // JSON序列化工具
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }
    
    /**
     * 专门用于保存观看历史的方法
     * 统一使用List<SupabaseWatchHistoryItem>类型存储
     */
    suspend fun saveWatchHistory(context: Context, items: List<SupabaseWatchHistoryItem>) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "保存观看历史到本地存储，条数: ${items.size}")
            
            // 使用kotlinx.serialization序列化为JSON字符串
            val jsonString = json.encodeToString(items)
            
            // 保存JSON字符串到缓存
            CoreCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY, jsonString)
            
            // 记录最后更新时间
            CoreCacheManager.saveCache(context, SupabaseCacheKey.WATCH_HISTORY_LAST_LOADED, System.currentTimeMillis())
            
            Log.d(TAG, "观看历史保存成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存观看历史失败", e)
        }
    }

    /**
     * 专门用于获取观看历史的方法
     * 统一返回List<SupabaseWatchHistoryItem>类型
     */
    suspend fun getWatchHistory(context: Context): List<SupabaseWatchHistoryItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "从本地存储获取观看历史数据")
            
            // 获取JSON字符串
            val jsonString = CoreCacheManager.getCache<String>(context, SupabaseCacheKey.WATCH_HISTORY)
            
            if (jsonString == null) {
                Log.d(TAG, "观看历史缓存不存在，返回空列表")
                return@withContext emptyList<SupabaseWatchHistoryItem>()
            }
            
            // 使用kotlinx.serialization解析JSON字符串
            val items = try {
                json.decodeFromString<List<SupabaseWatchHistoryItem>>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "解析观看历史JSON失败，尝试修复", e)
                
                // 尝试修复可能存在的格式问题
                try {
                    // 检查是否是双重序列化的问题
                    if (jsonString.startsWith("\"[") && jsonString.endsWith("]\"")) {
                        // 去掉外层引号
                        val fixedJson = jsonString.substring(1, jsonString.length - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                        
                        Log.d(TAG, "尝试修复双重序列化问题")
                        json.decodeFromString<List<SupabaseWatchHistoryItem>>(fixedJson)
                    } else {
                        // 其他情况，返回空列表
                        emptyList()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "修复观看历史JSON失败，返回空列表", e2)
                    emptyList()
                }
            }
            
            Log.d(TAG, "成功获取观看历史，条数: ${items.size}")
            return@withContext items
        } catch (e: Exception) {
            Log.e(TAG, "获取观看历史失败", e)
            return@withContext emptyList<SupabaseWatchHistoryItem>()
        }
    }
    
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
} 