package top.cywin.onetv.tv.supabase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 用户设置缓存管理器
 * 专门用于管理SupabaseUserSettings界面的用户设置缓存
 */
object SupabaseUserSettingsSessionManager {
    private const val TAG = "SettingsManager"
    private const val PREFS_NAME = "user_settings_cache"
    private const val KEY_USER_SETTINGS = "cached_user_settings"
    private const val KEY_LAST_LOADED = "last_loaded_time"
    
    // 缓存的关键字段
    private const val KEY_THEME = "cached_theme"
    private const val KEY_PLAYER_SETTINGS = "cached_player_settings"
    private const val KEY_NOTIFICATION_ENABLED = "cached_notification_enabled"
    private const val KEY_GENDER = "cached_gender"
    private const val KEY_BIRTH_DATE = "cached_birth_date"
    private const val KEY_REGION = "cached_region"
    private const val KEY_LANGUAGE = "cached_language"
    private const val KEY_TIMEZONE = "cached_timezone"
    private const val KEY_DISPLAY_NAME = "cached_display_name"
    private const val KEY_AVATAR_URL = "cached_avatar_url"
    private const val KEY_BIO = "cached_bio"
    
    // 缓存有效期：30天（以毫秒为单位）
    const val CACHE_VALIDITY_PERIOD = 30L * 24 * 60 * 60 * 1000 // 30天
    
    /**
     * 用户退出登录时清除缓存
     * 当用户退出登录时应调用此方法，确保下一个登录用户不会看到上一个用户的设置
     * @param context 应用上下文
     */
    fun logoutCleanup(context: Context) {
        clearCache(context)
        Log.d(TAG, "用户退出登录，已清除用户设置缓存")
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
    fun saveUserSettings(context: Context, settings: UserSettings) {
        val prefs = getSharedPreferences(context)
        val json = Gson().toJson(settings)
        prefs.edit()
            .putString(KEY_USER_SETTINGS, json)
            .apply()
        
        // 保存关键字段，用于后续检测状态变化
        prefs.edit()
            .putString(KEY_THEME, settings.theme)
            .putString(KEY_PLAYER_SETTINGS, settings.playerSettings.toString())
            .putBoolean(KEY_NOTIFICATION_ENABLED, settings.notificationEnabled)
            .putString(KEY_GENDER, settings.gender ?: "")
            .putString(KEY_BIRTH_DATE, settings.birthDate ?: "")
            .putString(KEY_REGION, settings.region ?: "")
            .putString(KEY_LANGUAGE, settings.languagePreference)
            .putString(KEY_TIMEZONE, settings.timezone)
            .putString(KEY_DISPLAY_NAME, settings.displayName ?: "")
            .putString(KEY_AVATAR_URL, settings.avatarUrl ?: "")
            .putString(KEY_BIO, settings.bio ?: "")
            .apply()
        
        // 更新最后加载时间
        saveLastLoadedTime(context, System.currentTimeMillis())
        
        Log.d(TAG, "用户设置缓存成功 | userId=${settings.userId} | " +
                "主题=${settings.theme} | " +
                "通知=${settings.notificationEnabled} | " +
                "显示名=${settings.displayName ?: "未设置"} | " +
                "缓存时间：${formatTime(System.currentTimeMillis())}")
    }
    
    /**
     * 获取缓存的用户设置
     * @param context 应用上下文
     * @return 用户设置，如果缓存中没有则返回null
     */
    fun getCachedUserSettings(context: Context): UserSettings? {
        val prefs = getSharedPreferences(context)
        val dataJson = prefs.getString(KEY_USER_SETTINGS, null)
        val settings = dataJson?.let { 
            try {
                Gson().fromJson(it, UserSettings::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "解析缓存数据失败", e)
                null
            }
        }
        Log.d(TAG, "读取缓存设置: ${settings?.userId ?: "空"}")
        return settings
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
     *    - 主题
     *    - 播放器设置
     *    - 通知状态
     *    - 性别
     *    - 出生日期
     *    - 地区
     *    - 语言偏好
     *    - 时区
     *    - 显示名称
     *    - 头像URL
     *    - 个人简介
     * 
     * @param context 应用上下文
     * @param currentSettings 当前用户设置（可能来自其他来源）
     * @return 如果缓存有效则返回true，否则返回false
     */
    fun isCacheValid(context: Context, currentSettings: UserSettings? = null): Boolean {
        val prefs = getSharedPreferences(context)
        val lastLoaded = getLastLoadedTime(context)
        if (lastLoaded == 0L) return false
        
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastLoaded
        
        // 检查时间是否过期（30天）
        val isTimeValid = timeDiff <= CACHE_VALIDITY_PERIOD
        
        // 如果没有提供当前数据，只检查时间有效性
        if (currentSettings == null) {
            return isTimeValid
        }
        
        // 检查关键字段是否变化
        val cachedTheme = prefs.getString(KEY_THEME, "dark") ?: "dark"
        val cachedPlayerSettings = prefs.getString(KEY_PLAYER_SETTINGS, "{}") ?: "{}"
        val cachedNotificationEnabled = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        val cachedGender = prefs.getString(KEY_GENDER, "") ?: ""
        val cachedBirthDate = prefs.getString(KEY_BIRTH_DATE, "") ?: ""
        val cachedRegion = prefs.getString(KEY_REGION, "") ?: ""
        val cachedLanguage = prefs.getString(KEY_LANGUAGE, "zh-CN") ?: "zh-CN"
        val cachedTimezone = prefs.getString(KEY_TIMEZONE, "Asia/Shanghai") ?: "Asia/Shanghai"
        val cachedDisplayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        val cachedAvatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: ""
        val cachedBio = prefs.getString(KEY_BIO, "") ?: ""
        
        val currentTheme = currentSettings.theme
        val currentPlayerSettings = currentSettings.playerSettings.toString()
        val currentNotificationEnabled = currentSettings.notificationEnabled
        val currentGender = currentSettings.gender ?: ""
        val currentBirthDate = currentSettings.birthDate ?: ""
        val currentRegion = currentSettings.region ?: ""
        val currentLanguage = currentSettings.languagePreference
        val currentTimezone = currentSettings.timezone
        val currentDisplayName = currentSettings.displayName ?: ""
        val currentAvatarUrl = currentSettings.avatarUrl ?: ""
        val currentBio = currentSettings.bio ?: ""
        
        // 检查各个字段是否变化
        val isThemeUnchanged = (cachedTheme == currentTheme)
        val isPlayerSettingsUnchanged = (cachedPlayerSettings == currentPlayerSettings)
        val isNotificationEnabledUnchanged = (cachedNotificationEnabled == currentNotificationEnabled)
        val isGenderUnchanged = (cachedGender == currentGender)
        val isBirthDateUnchanged = (cachedBirthDate == currentBirthDate)
        val isRegionUnchanged = (cachedRegion == currentRegion)
        val isLanguageUnchanged = (cachedLanguage == currentLanguage)
        val isTimezoneUnchanged = (cachedTimezone == currentTimezone)
        val isDisplayNameUnchanged = (cachedDisplayName == currentDisplayName)
        val isAvatarUrlUnchanged = (cachedAvatarUrl == currentAvatarUrl)
        val isBioUnchanged = (cachedBio == currentBio)
        
        // 所有关键字段都未变化，且时间未过期，则缓存有效
        val isValid = isTimeValid && 
                      isThemeUnchanged && 
                      isPlayerSettingsUnchanged && 
                      isNotificationEnabledUnchanged &&
                      isGenderUnchanged &&
                      isBirthDateUnchanged &&
                      isRegionUnchanged &&
                      isLanguageUnchanged &&
                      isTimezoneUnchanged &&
                      isDisplayNameUnchanged &&
                      isAvatarUrlUnchanged &&
                      isBioUnchanged
        
        // 记录详细日志，便于调试
        if (!isThemeUnchanged) {
            Log.d(TAG, "主题已变化 | 缓存主题：$cachedTheme | 当前主题：$currentTheme")
        }
        if (!isPlayerSettingsUnchanged) {
            Log.d(TAG, "播放器设置已变化 | 缓存设置：$cachedPlayerSettings | 当前设置：$currentPlayerSettings")
        }
        if (!isNotificationEnabledUnchanged) {
            Log.d(TAG, "通知状态已变化 | 缓存状态：$cachedNotificationEnabled | 当前状态：$currentNotificationEnabled")
        }
        if (!isDisplayNameUnchanged) {
            Log.d(TAG, "显示名称已变化 | 缓存名称：$cachedDisplayName | 当前名称：$currentDisplayName")
        }
        if (!isAvatarUrlUnchanged) {
            Log.d(TAG, "头像URL已变化 | 缓存URL：$cachedAvatarUrl | 当前URL：$currentAvatarUrl")
        }
        
        Log.d(TAG, "缓存有效性检查 | " +
                "当前时间：${formatTime(currentTime)} | " +
                "最后加载时间：${formatTime(lastLoaded)} | " +
                "时间差：${timeDiff}ms | " +
                "有效期：${CACHE_VALIDITY_PERIOD}ms | " +
                "时间是否有效：$isTimeValid | " +
                "主题是否未变：$isThemeUnchanged | " +
                "播放器设置是否未变：$isPlayerSettingsUnchanged | " +
                "通知状态是否未变：$isNotificationEnabledUnchanged | " +
                "显示名称是否未变：$isDisplayNameUnchanged | " +
                "最终是否有效：$isValid")
        
        return isValid
    }
    
    /**
     * 强制使缓存失效
     * 在用户设置变更或其他需要强制刷新的情况下调用此方法
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
        Log.d(TAG, "用户设置缓存已清除")
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
