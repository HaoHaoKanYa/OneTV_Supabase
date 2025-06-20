package top.cywin.onetv.tv.supabase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 用户资料缓存管理器
 * 专门用于管理SupabaseUserProfileInfo界面的用户资料缓存
 */
object SupabaseUserProfileInfoSessionManager {
    private const val TAG = "ProfileInfoManager"
    private const val PREFS_NAME = "profile_info_cache"
    private const val KEY_USER_DATA = "cached_profile_data"
    private const val KEY_LAST_LOADED = "last_loaded_time"
    private const val KEY_VIP_STATUS = "cached_vip_status" // 保存用户上次的VIP状态
    private const val KEY_LAST_LOGIN_TIME = "cached_last_login_time" // 保存用户上次的登录时间
    private const val KEY_LAST_LOGIN_DEVICE = "cached_last_login_device" // 保存用户上次的登录设备
    private const val KEY_VIP_START = "cached_vip_start" // 保存用户VIP开始时间
    private const val KEY_VIP_END = "cached_vip_end" // 保存用户VIP结束时间
    
    // 缓存有效期：30天（以毫秒为单位）
    private const val CACHE_VALIDITY_PERIOD = 30L * 24 * 60 * 60 * 1000 // 30天
    
    /**
     * 用户退出登录时清除缓存
     * 当用户退出登录时应调用此方法，确保下一个登录用户不会看到上一个用户的资料
     * @param context 应用上下文
     */
    fun logoutCleanup(context: Context) {
        clearCache(context)
        Log.d(TAG, "用户退出登录，已清除用户资料缓存")
    }
    
    /**
     * 保存用户资料到缓存
     * @param context 应用上下文
     * @param data 用户数据
     */
    fun saveUserProfileData(context: Context, data: SupabaseUserDataIptv) {
        val prefs = getSharedPreferences(context)
        val json = Gson().toJson(data)
        prefs.edit()
            .putString(KEY_USER_DATA, json)
            .apply()
        
        // 保存关键字段，用于后续检测状态变化
        prefs.edit()
            .putBoolean(KEY_VIP_STATUS, data.is_vip)
            .putString(KEY_LAST_LOGIN_TIME, data.lastlogintime ?: "")
            .putString(KEY_LAST_LOGIN_DEVICE, data.lastlogindevice ?: "")
            .putString(KEY_VIP_START, data.vipstart ?: "")
            .putString(KEY_VIP_END, data.vipend ?: "")
            .apply()
        
        // 更新最后加载时间
        saveLastLoadedTime(context, System.currentTimeMillis())
        
        Log.d(TAG, "用户资料缓存成功 | userId=${data.userid} | " +
                "VIP状态=${data.is_vip} | " +
                "VIP开始=${data.vipstart ?: "未设置"} | " +
                "VIP结束=${data.vipend ?: "未设置"} | " +
                "最后登录时间=${data.lastlogintime ?: "未设置"} | " +
                "最后登录设备=${data.lastlogindevice ?: "未设置"} | " +
                "缓存时间：${formatTime(System.currentTimeMillis())}")
    }
    
    /**
     * 获取缓存的用户资料
     * @param context 应用上下文
     * @return 用户资料，如果缓存中没有则返回null
     */
    fun getCachedUserProfileData(context: Context): SupabaseUserDataIptv? {
        val prefs = getSharedPreferences(context)
        val dataJson = prefs.getString(KEY_USER_DATA, null)
        val data = dataJson?.let { 
            try {
                Gson().fromJson(it, SupabaseUserDataIptv::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "解析缓存数据失败", e)
                null
            }
        }
        Log.d(TAG, "读取缓存数据: ${data?.username ?: "空"}")
        return data
    }
    
    /**
     * 保存最后加载时间
     * @param context 应用上下文
     * @param time 时间戳
     */
    private fun saveLastLoadedTime(context: Context, time: Long) {
        getSharedPreferences(context).edit()
            .putLong(KEY_LAST_LOADED, time)
            .apply()
        Log.d(TAG, "更新缓存时间戳 | 新时间：${formatTime(time)}")
    }
    
    /**
     * 获取最后加载时间
     * @param context 应用上下文
     * @return 最后加载时间的时间戳
     */
    fun getLastLoadedTime(context: Context): Long {
        return getSharedPreferences(context).getLong(KEY_LAST_LOADED, 0)
    }
    
    /**
     * 检查缓存是否有效
     * 
     * 缓存在以下情况下无效：
     * 1. 缓存时间超过有效期（30天）
     * 2. 以下任一关键字段发生变化：
     *    - VIP状态
     *    - VIP开始时间
     *    - VIP结束时间
     *    - 最后登录时间
     *    - 最后登录设备
     * 
     * @param context 应用上下文
     * @param currentData 当前用户数据（可能来自其他来源）
     * @return 如果缓存有效则返回true，否则返回false
     */
    fun isCacheValid(context: Context, currentData: SupabaseUserDataIptv? = null): Boolean {
        val prefs = getSharedPreferences(context)
        val lastLoaded = getLastLoadedTime(context)
        if (lastLoaded == 0L) return false
        
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastLoaded
        
        // 检查时间是否过期（30天）
        val isTimeValid = timeDiff <= CACHE_VALIDITY_PERIOD
        
        // 如果没有提供当前数据，只检查时间有效性
        if (currentData == null) {
            return isTimeValid
        }
        
        // 检查关键字段是否变化
        val cachedVipStatus = prefs.getBoolean(KEY_VIP_STATUS, false)
        val cachedLastLoginTime = prefs.getString(KEY_LAST_LOGIN_TIME, "") ?: ""
        val cachedLastLoginDevice = prefs.getString(KEY_LAST_LOGIN_DEVICE, "") ?: ""
        val cachedVipStart = prefs.getString(KEY_VIP_START, "") ?: ""
        val cachedVipEnd = prefs.getString(KEY_VIP_END, "") ?: ""
        
        val currentVipStatus = currentData.is_vip
        val currentLastLoginTime = currentData.lastlogintime ?: ""
        val currentLastLoginDevice = currentData.lastlogindevice ?: ""
        val currentVipStart = currentData.vipstart ?: ""
        val currentVipEnd = currentData.vipend ?: ""
        
        val isVipStatusUnchanged = (cachedVipStatus == currentVipStatus)
        val isLastLoginTimeUnchanged = (cachedLastLoginTime == currentLastLoginTime)
        val isLastLoginDeviceUnchanged = (cachedLastLoginDevice == currentLastLoginDevice)
        val isVipStartUnchanged = (cachedVipStart == currentVipStart)
        val isVipEndUnchanged = (cachedVipEnd == currentVipEnd)
        
        // 所有关键字段都未变化，且时间未过期，则缓存有效
        val isValid = isTimeValid && 
                      isVipStatusUnchanged && 
                      isLastLoginTimeUnchanged && 
                      isLastLoginDeviceUnchanged &&
                      isVipStartUnchanged &&
                      isVipEndUnchanged
        
        // 记录详细日志，便于调试
        if (!isVipStatusUnchanged) {
            Log.d(TAG, "VIP状态已变化 | 缓存状态：$cachedVipStatus | 当前状态：$currentVipStatus")
        }
        if (!isVipStartUnchanged) {
            Log.d(TAG, "VIP开始时间已变化 | 缓存时间：$cachedVipStart | 当前时间：$currentVipStart")
        }
        if (!isVipEndUnchanged) {
            Log.d(TAG, "VIP结束时间已变化 | 缓存时间：$cachedVipEnd | 当前时间：$currentVipEnd")
        }
        if (!isLastLoginTimeUnchanged) {
            Log.d(TAG, "最后登录时间已变化 | 缓存时间：$cachedLastLoginTime | 当前时间：$currentLastLoginTime")
        }
        if (!isLastLoginDeviceUnchanged) {
            Log.d(TAG, "最后登录设备已变化 | 缓存设备：$cachedLastLoginDevice | 当前设备：$currentLastLoginDevice")
        }
        
        Log.d(TAG, "缓存有效性检查 | " +
                "当前时间：${formatTime(currentTime)} | " +
                "最后加载时间：${formatTime(lastLoaded)} | " +
                "时间差：${timeDiff}ms | " +
                "有效期：${CACHE_VALIDITY_PERIOD}ms | " +
                "时间是否有效：$isTimeValid | " +
                "VIP状态是否未变：$isVipStatusUnchanged | " +
                "VIP开始时间是否未变：$isVipStartUnchanged | " +
                "VIP结束时间是否未变：$isVipEndUnchanged | " +
                "最后登录时间是否未变：$isLastLoginTimeUnchanged | " +
                "最后登录设备是否未变：$isLastLoginDeviceUnchanged | " +
                "最终是否有效：$isValid")
        
        return isValid
    }
    
    /**
     * 强制使缓存失效
     * 在用户权限变更或其他需要强制刷新的情况下调用此方法
     * @param context 应用上下文
     */
    fun invalidateCache(context: Context) {
        saveLastLoadedTime(context, 0) // 将时间戳设置为0，使缓存立即失效
        Log.d(TAG, "缓存已手动失效")
    }
    
    /**
     * 清除缓存
     * @param context 应用上下文
     */
    fun clearCache(context: Context) {
        getSharedPreferences(context).edit().clear().apply()
        Log.d(TAG, "用户资料缓存已清除")
    }
    
    /**
     * 获取SharedPreferences实例
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
