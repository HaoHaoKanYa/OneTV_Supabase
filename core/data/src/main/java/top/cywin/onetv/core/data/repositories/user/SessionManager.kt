package top.cywin.onetv.core.data.repositories.user

import android.content.Context
import android.util.Log
import top.cywin.onetv.core.data.entities.iptvsource.IptvSource
import top.cywin.onetv.core.data.repositories.iptv.BaseIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.GuestIptvRepository
import top.cywin.onetv.core.data.repositories.iptv.IptvRepository
import com.google.gson.Gson // <<< 添加此导入
import top.cywin.onetv.core.data.repositories.user.SessionManager.getLastLoadedTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone



/**
 * 判断是否需要强制刷新用户数据
 *
 * 修改方案：
 * 1. 如果用户数据为空，则返回需要刷新（true）。
 * 2. 普通注册用户不进行自动刷新（退出时统一清理缓存），因此返回 false。
 * 3. 对于VIP用户：
 *    - 基础缓存有效期设置为30天（2592000000 毫秒）。
 *    - 当 (当前时间 - 上次加载时间) 超过30天 或 VIP剩余时间不足48小时时，触发刷新。
 *
 * @param context 应用上下文
 * @param userData 用户数据对象，可能为 null
 * @return 如果需要刷新则返回 true，否则返回 false
 */
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
fun shouldForceRefresh(context: Context, userData: UserDataIptv?): Boolean {
    val lastLoaded = getLastLoadedTime(context)
    val currentTime = System.currentTimeMillis()

    // 【步骤1】用户数据为空时，直接刷新
    if (userData == null) {
        Log.d("SessionManager", "[缓存检查] 用户数据为空，强制刷新")
        return true
    }

    // 【步骤2】对于普通注册用户，不做自动刷新
    if (!userData.isVIP) {
        Log.d("SessionManager", "[缓存检查] 普通用户，无需自动刷新")
        return false
    }

    // 【步骤3】对于VIP用户，设置基础缓存过期阈值为30天
    val vipBaseThreshold = 30L * 24 * 3600 * 1000 // 30天毫秒数：2592000000
    val vipBaseExpired = (currentTime - lastLoaded) > vipBaseThreshold || lastLoaded == 0L
    Log.d("SessionManager", "VIP基础缓存检查 | 使用 VIP 阈值 30天 " +
            "当前时间：${formatBeijingTime(currentTime)} " +
            "| 最后加载时间：${if (lastLoaded == 0L) "未记录" else formatBeijingTime(lastLoaded)} " +
            "| 时间差：${currentTime - lastLoaded}ms")

    // 【修改点】撤销VIP剩余有效时间的计算，不再检查VIP剩余时间是否不足48小时
    // 仅依据基础缓存过期阈值来判断
    return vipBaseExpired
}


object SessionManager {
    fun getSession(context: Context): String? {
        return context.getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("session", null)
    }

    fun getIptvRepository(context: Context, source: IptvSource): BaseIptvRepository {
        return try {
            val session = getSession(context)
            if (session.isNullOrEmpty()) {
                GuestIptvRepository(source)
            } else {
                IptvRepository(source.copy(url = source.url), session)
            }
        } catch (e: Exception) {
            Log.e("SessionManager", "获取仓库失败", e)
            throw e
        }
    }

    fun getValidSession(context: Context): String {
        return getSession(context) ?: throw Exception("用户未登录")
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("user", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun saveCachedUserData(context: Context, data: UserDataIptv) {
        val prefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val json = Gson().toJson(data)
        prefs.edit()
            .putString("cached_user_data", json)
            .commit() // 同步提交
        Log.d("SessionManager", "用户数据缓存成功｜userId=${data.userId}｜数据长度=${json.length}｜时间：${formatBeijingTime(System.currentTimeMillis())}")
    }

    fun saveLastLoadedTime(context: Context, time: Long) {
        context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_loaded_time", time)
            .apply()
        Log.d("SessionManager", "更新时间戳｜新时间：${formatBeijingTime(time)}｜时间差：${System.currentTimeMillis() - time}｜当前缓存用户：${getCachedUserData(context)?.userId ?: "空"}")
    }

    fun getCachedUserData(context: Context): UserDataIptv? {
        val prefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val data = Gson().fromJson(prefs.getString("cached_user_data", null), UserDataIptv::class.java)
        Log.d("SessionManager", "读取缓存数据: ${data?.userId ?: "空"}")
        return data
    }

    fun getLastLoadedTime(context: Context): Long {
        val prefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        return prefs.getLong("last_loaded_time", 0)
    }

    fun clearUserCache(context: Context) {
        val prefs = context.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun clearLastLoadedTime(context: Context) {
        context.getSharedPreferences("user_cache", Context.MODE_PRIVATE).edit()
            .remove("last_loaded_time")
            .apply()
    }
}

private fun formatBeijingTime(time: Long): String {
    if (time <= 0) return "未记录"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(time))
}
