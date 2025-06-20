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

/**
 * 判断是否需要强制刷新用户数据
 *
 * 修改方案：
 * 1. 如果用户数据为空，则返回需要刷新（true）。
 * 2. 普通注册用户不进行自动刷新（退出时统一清理缓存），因此返回 false。
 * 3. 对于VIP用户：
 *    - 基础缓存有效期设置为30天（2592000000 毫秒）。
 *    - 当 (当前时间 - 上次加载时间) 超过30天，则触发刷新。
 *
 * @param context 应用上下文
 * @param userData 用户数据对象，可能为 null
 * @return 如果需要刷新则返回 true，否则返回 false
 */
fun shouldForceRefresh(context: Context, userData: SupabaseUserDataIptv?): Boolean {
    val lastLoaded = SupabaseSessionManager.getLastLoadedTime(context)
    val currentTime = System.currentTimeMillis()
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

    // 【步骤3】对于VIP用户，设置基础缓存过期阈值为30天
    val vipBaseThreshold = 30L * 24 * 3600 * 1000 // 30天毫秒数：2592000000
    val vipBaseExpired = (currentTime - lastLoaded) > vipBaseThreshold || lastLoaded == 0L
    Log.d(TAG, "VIP基础缓存检查 | 使用 VIP 阈值 30天 " +
            "当前时间：${formatBeijingTime(currentTime)} " +
            "| 最后加载时间：${if (lastLoaded == 0L) "未记录" else formatBeijingTime(lastLoaded)} " +
            "| 时间差：${currentTime - lastLoaded}ms")

    // 仅依据基础缓存过期阈值来判断
    return vipBaseExpired
}

/**
 * Supabase会话管理器
 * 负责管理用户会话、缓存和认证状态
 */
object SupabaseSessionManager {
    private const val TAG = "SupabaseSessionManager"
    private const val PREFS_USER = "supabase_user"
    private const val PREFS_USER_CACHE = "supabase_user_cache"
    private const val KEY_SESSION = "session"
    private const val KEY_USER_DATA = "cached_user_data"
    private const val KEY_LAST_LOADED = "last_loaded_time"

    /**
     * 获取当前会话ID
     * @param context 应用上下文
     * @return 会话ID，如果未登录则返回null
     */
    fun getSession(context: Context): String? {
        // 首先尝试从Supabase Client获取会话
        val session = SupabaseClient.client.auth.currentSessionOrNull()?.accessToken
        if (session != null) {
            return session
        }
        
        // 如果Supabase客户端没有会话，尝试从SharedPreferences获取
        return context.getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null)
    }

    /**
     * 保存会话到本地存储
     * @param context 应用上下文
     * @param sessionToken 会话令牌
     */
    fun saveSession(context: Context, sessionToken: String) {
        context.getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION, sessionToken)
            .apply()
        Log.d(TAG, "会话已保存到本地存储")
    }

    /**
     * 获取IPTV仓库实例
     * @param context 应用上下文
     * @param source IPTV源配置
     * @return IPTV仓库实例
     */
    fun getIptvRepository(context: Context, source: IptvSource): BaseIptvRepository {
        return try {
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
    fun getValidSession(context: Context): String {
        return getSession(context) ?: throw Exception("用户未登录")
    }

    /**
     * 清除用户会话
     * @param context 应用上下文
     */
    fun clearSession(context: Context) {
        // 清除本地存储的会话
        context.getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)
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
    fun saveCachedUserData(context: Context, data: SupabaseUserDataIptv) {
        val prefs = context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE)
        val json = Gson().toJson(data)
        prefs.edit()
            .putString(KEY_USER_DATA, json)
            .commit() // 同步提交确保数据写入
        Log.d(TAG, "用户数据缓存成功｜userId=${data.userid}｜数据长度=${json.length}｜时间：${formatBeijingTime(System.currentTimeMillis())}")
    }

    /**
     * 保存最后加载时间
     * @param context 应用上下文
     * @param time 时间戳
     */
    fun saveLastLoadedTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_LOADED, time)
            .apply()
        Log.d(TAG, "更新时间戳｜新时间：${formatBeijingTime(time)}｜时间差：${System.currentTimeMillis() - time}｜当前缓存用户：${getCachedUserData(context)?.userid ?: "空"}")
    }

    /**
     * 获取缓存的用户数据
     * @param context 应用上下文
     * @return 用户数据，如果缓存中没有则返回null
     */
    fun getCachedUserData(context: Context): SupabaseUserDataIptv? {
        val prefs = context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE)
        val dataJson = prefs.getString(KEY_USER_DATA, null)
        val data = dataJson?.let { Gson().fromJson(it, SupabaseUserDataIptv::class.java) }
        Log.d(TAG, "读取缓存数据: ${data?.userid ?: "空"}")
        return data
    }

    /**
     * 获取最后加载时间
     * @param context 应用上下文
     * @return 最后加载时间的时间戳
     */
    fun getLastLoadedTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_LOADED, 0)
    }

    /**
     * 清除用户数据缓存
     * @param context 应用上下文
     */
    fun clearUserCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "用户缓存已清除")
    }

    /**
     * 清除最后加载时间
     * @param context 应用上下文
     */
    fun clearLastLoadedTime(context: Context) {
        context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_LOADED)
            .apply()
        Log.d(TAG, "最后加载时间已清除")
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
