package top.cywin.onetv.core.data.repositories.supabase

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import top.cywin.onetv.core.data.entities.iptvsource.IptvSource
import top.cywin.onetv.core.data.repositories.iptv.BaseIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.GuestIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.IptvRepository
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager

/**
 * 判断是否需要强制刷新用户数据
 *
 * 使用缓存管理器的isValid方法替代手动判断
 *
 * @param context 应用上下文
 * @param userData 用户数据对象，可能为 null
 * @return 如果需要刷新则返回 true，否则返回 false
 */
suspend fun shouldForceRefresh(context: Context, userData: SupabaseUserDataIptv?): Boolean {
    val TAG = "SupabaseSessionManager"

    // 【步骤1】用户数据为空时，直接刷新
    if (userData == null) {
        Log.d(TAG, "[缓存检查] 用户数据为空，强制刷新")
        return true
    }

    // 【步骤2】对于普通注册用户，不做自动刷新
    if (!userData.is_vip) {
        Log.d(TAG, "[缓存检查] 普通用户，无需自动刷新")
        return false
    }

    // 【步骤3】对于VIP用户，直接使用缓存管理器的isValid方法
    val isValid = SupabaseCacheManager.isValid(context, SupabaseCacheKey.USER_DATA)
    Log.d(TAG, "[缓存检查] VIP用户，缓存是否有效: $isValid")
    
    return !isValid
}

/**
 * Supabase会话管理器
 * 负责管理用户会话、缓存和认证状态
 * 直接使用SupabaseCacheManager进行缓存操作
 */
object SupabaseSessionManager {
    private const val TAG = "SupabaseSessionManager"

    /**
     * 获取当前会话ID
     * @param context 应用上下文
     * @return 会话ID，如果未登录则返回null
     */
    suspend fun getSession(context: Context): String? = withContext(Dispatchers.IO) {
        // 首先尝试从Supabase Client获取会话
        val session = SupabaseClient.client.auth.currentSessionOrNull()?.accessToken
        if (session != null) {
            return@withContext session
        }
        
        // 如果Supabase客户端没有会话，尝试从缓存获取
        return@withContext SupabaseCacheManager.getCache(
            context = context,
            key = SupabaseCacheKey.SESSION,
            defaultValue = null
        )
    }
    
    /**
     * 同步版本的获取会话（用于不支持协程的场景）
     * @param context 应用上下文
     * @return 会话ID，如果未登录则返回null
     */
    fun getSessionSync(context: Context): String? {
        // 首先尝试从Supabase Client获取会话
        val session = SupabaseClient.client.auth.currentSessionOrNull()?.accessToken
        if (session != null) {
            return session
        }
        
        // 如果Supabase客户端没有会话，尝试从原始的SharedPreferences获取
        return context.getSharedPreferences("supabase_user", Context.MODE_PRIVATE)
            .getString("session", null)
    }

    /**
     * 保存会话到本地存储
     * @param context 应用上下文
     * @param sessionToken 会话令牌
     */
    suspend fun saveSession(context: Context, sessionToken: String) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.saveCache(
            context = context,
            key = SupabaseCacheKey.SESSION,
            data = sessionToken
        )
        Log.d(TAG, "会话已保存到本地存储")
    }
    
    /**
     * 同步版本的保存会话（用于不支持协程的场景）
     * @param context 应用上下文
     * @param sessionToken 会话令牌
     */
    fun saveSessionSync(context: Context, sessionToken: String) {
        context.getSharedPreferences("supabase_user", Context.MODE_PRIVATE)
            .edit()
            .putString("session", sessionToken)
            .apply()
        Log.d(TAG, "会话已保存到本地存储（同步版本）")
    }

    /**
     * 获取IPTV仓库实例
     * @param context 应用上下文
     * @param source IPTV源配置
     * @return IPTV仓库实例
     */
    suspend fun getIptvRepository(context: Context, source: IptvSource): BaseIptvRepository = withContext(Dispatchers.IO) {
        return@withContext try {
            val session = getSession(context)
            if (session.isNullOrEmpty()) {
                GuestIptvRepository(source)
            } else {
                IptvRepository(source.copy(), session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取仓库失败", e)
            throw e
        }
    }

    /**
     * 获取有效的会话ID，如果未登录则抛出异常
     * @param context 应用上下文
     * @return 会话ID
     * @throws Exception 如果用户未登录
     */
    suspend fun getValidSession(context: Context): String = withContext(Dispatchers.IO) {
        return@withContext getSession(context) ?: throw Exception("用户未登录")
    }

    /**
     * 清除用户会话
     * @param context 应用上下文
     */
    suspend fun clearSession(context: Context) = withContext(Dispatchers.IO) {
        // 清除缓存中的会话
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.SESSION)
            
        // 尝试从Supabase注销（在非协程中安全地执行）
        try {
            runBlocking {
                try {
                    SupabaseClient.client.auth.signOut()
                    Log.d(TAG, "已从Supabase服务注销")
                } catch (e: RestException) {
                    Log.w(TAG, "Supabase注销失败，但本地会话已清除: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "注销时发生未知异常", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行Supabase注销时发生错误", e)
        }
    }
    
    /**
     * 同步版本的清除会话（用于不支持协程的场景）
     * @param context 应用上下文
     */
    fun clearSessionSync(context: Context) {
        // 清除本地存储的会话
        context.getSharedPreferences("supabase_user", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
            
        // 尝试从Supabase注销（在非协程中安全地执行）
        try {
            runBlocking {
                try {
                    SupabaseClient.client.auth.signOut()
                    Log.d(TAG, "已从Supabase服务注销")
                } catch (e: RestException) {
                    Log.w(TAG, "Supabase注销失败，但本地会话已清除: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "注销时发生未知异常", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行Supabase注销时发生错误", e)
        }
    }

    /**
     * 保存用户数据到缓存
     * @param context 应用上下文
     * @param data 用户数据
     */
    suspend fun saveCachedUserData(context: Context, data: SupabaseUserDataIptv) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.saveCache(
            context = context,
            key = SupabaseCacheKey.USER_DATA,
            data = data,
            strategy = SupabaseCacheManager.getUserCacheStrategy(data)
        )
        Log.d(TAG, "用户数据缓存成功｜userId=${data.userid}｜VIP=${data.is_vip}｜时间：${System.currentTimeMillis()}")
    }
    
    /**
     * 同步版本的保存用户数据（用于不支持协程的场景）
     * @param context 应用上下文
     * @param data 用户数据
     */
    fun saveCachedUserDataSync(context: Context, data: SupabaseUserDataIptv) {
        val prefs = context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE)
        val json = Gson().toJson(data)
        prefs.edit()
            .putString("cached_user_data", json)
            .commit() // 同步提交确保数据写入
        Log.d(TAG, "用户数据缓存成功｜userId=${data.userid}｜数据长度=${json.length}｜时间：${System.currentTimeMillis()}")
    }

    /**
     * 保存最后加载时间
     * @param context 应用上下文
     * @param time 时间戳
     */
    suspend fun saveLastLoadedTime(context: Context, time: Long) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.saveCache(
            context = context,
            key = SupabaseCacheKey.LAST_LOADED_TIME,
            data = time
        )
        Log.d(TAG, "更新时间戳｜新时间：$time｜时间差：${System.currentTimeMillis() - time}")
    }
    
    /**
     * 同步版本的保存最后加载时间（用于不支持协程的场景）
     * @param context 应用上下文
     * @param time 时间戳
     */
    fun saveLastLoadedTimeSync(context: Context, time: Long) {
        context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_loaded_time", time)
            .apply()
        Log.d(TAG, "更新时间戳｜新时间：$time｜时间差：${System.currentTimeMillis() - time}")
    }

    /**
     * 获取缓存的用户数据
     * @param context 应用上下文
     * @return 用户数据，如果缓存中没有则返回null
     */
    suspend fun getCachedUserData(context: Context): SupabaseUserDataIptv? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE)
            val dataJson = prefs.getString("cached_user_data", null)
            
            if (dataJson.isNullOrEmpty()) {
                Log.d(TAG, "缓存中没有用户数据")
                return@withContext null
            }
            
            try {
                // 直接使用指定类型进行反序列化，避免类型转换错误
                val data = Gson().fromJson(dataJson, SupabaseUserDataIptv::class.java)
                Log.d(TAG, "读取缓存数据成功: ${data.userid}")
                return@withContext data
            } catch (e: Exception) {
                // 如果反序列化失败，尝试清除缓存并返回null
                Log.e(TAG, "用户数据反序列化失败，将清除缓存: ${e.message}", e)
                clearUserCacheSync(context)
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取缓存用户数据时发生错误", e)
            return@withContext null
        }
    }
    
    /**
     * 同步版本的获取缓存用户数据（用于不支持协程的场景）
     * @param context 应用上下文
     * @return 用户数据，如果缓存中没有则返回null
     */
    fun getCachedUserDataSync(context: Context): SupabaseUserDataIptv? {
        try {
            val prefs = context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE)
            val dataJson = prefs.getString("cached_user_data", null)
            
            if (dataJson.isNullOrEmpty()) {
                Log.d(TAG, "缓存中没有用户数据（同步版本）")
                return null
            }
            
            try {
                // 直接使用指定类型进行反序列化，避免类型转换错误
                val data = Gson().fromJson(dataJson, SupabaseUserDataIptv::class.java)
                Log.d(TAG, "读取缓存数据成功（同步版本）: ${data.userid}")
                return data
            } catch (e: Exception) {
                // 如果反序列化失败，尝试清除缓存并返回null
                Log.e(TAG, "用户数据反序列化失败（同步版本），将清除缓存: ${e.message}", e)
                clearUserCacheSync(context)
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取缓存用户数据时发生错误（同步版本）", e)
            return null
        }
    }

    /**
     * 获取最后加载时间
     * @param context 应用上下文
     * @return 最后加载时间的时间戳
     */
    suspend fun getLastLoadedTime(context: Context): Long = withContext(Dispatchers.IO) {
        return@withContext SupabaseCacheManager.getCache<Long>(
            context = context,
            key = SupabaseCacheKey.LAST_LOADED_TIME,
            defaultValue = 0L
        ) ?: 0L
    }
    
    /**
     * 同步版本的获取最后加载时间（用于不支持协程的场景）
     * @param context 应用上下文
     * @return 最后加载时间的时间戳
     */
    fun getLastLoadedTimeSync(context: Context): Long {
        val prefs = context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE)
        return prefs.getLong("last_loaded_time", 0)
    }

    /**
     * 清除用户数据缓存
     * @param context 应用上下文
     */
    suspend fun clearUserCache(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.USER_DATA)
        Log.d(TAG, "用户缓存已清除")
    }
    
    /**
     * 同步版本的清除用户数据缓存（用于不支持协程的场景）
     * @param context 应用上下文
     */
    fun clearUserCacheSync(context: Context) {
        val prefs = context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "用户缓存已清除（同步版本）")
    }

    /**
     * 清除最后加载时间
     * @param context 应用上下文
     */
    suspend fun clearLastLoadedTime(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.LAST_LOADED_TIME)
        Log.d(TAG, "最后加载时间已清除")
    }
    
    /**
     * 同步版本的清除最后加载时间（用于不支持协程的场景）
     * @param context 应用上下文
     */
    fun clearLastLoadedTimeSync(context: Context) {
        context.getSharedPreferences("supabase_user_cache", Context.MODE_PRIVATE).edit()
            .remove("last_loaded_time")
            .apply()
        Log.d(TAG, "最后加载时间已清除（同步版本）")
    }
}

/**
 * 格式化北京时间
 * @param time 时间戳（毫秒）
 * @return 格式化后的时间字符串
 */
private fun formatBeijingTime(time: Long): String {
    if (time <= 0) return "未记录"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(time))
}
