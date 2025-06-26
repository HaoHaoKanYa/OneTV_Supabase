/**
 * 字段变化跟踪器
 * 
 * 此文件实现了数据字段变化的跟踪功能，超越了基于时间的缓存策略。
 * 通过对比当前数据和缓存数据的关键字段，即使缓存未过期，也能在重要数据变化时触发更新。
 * 支持用户资料、设置等复杂对象的智能比较，提高缓存系统的准确性和响应性。
 */
package top.cywin.onetv.core.data.repositories.supabase.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv

/**
 * Supabase字段跟踪器
 * 用于检测重要字段的变化，确保原有的刷新机制可以继续工作。
 */
object SupabaseFieldTracker {
    private const val TAG = "SupabaseFieldTracker"
    
    /**
     * 跟踪用户资料关键字段的变化
     * @param context 应用上下文
     * @param currentData 当前用户数据
     * @return 如果有字段变化则返回true，否则返回false
     */
    suspend fun trackUserProfileChanges(
        context: Context,
        currentData: SupabaseUserDataIptv
    ): Boolean = withContext(Dispatchers.IO) {
        // 获取缓存的用户数据
        val cachedData = SupabaseCacheManager.getCache<SupabaseUserDataIptv>(
            context, 
            SupabaseCacheKey.USER_PROFILE
        )
        
        // 如果缓存为空，则没有变化
        if (cachedData == null) {
            Log.d(TAG, "缓存用户资料为空，无法检测变化")
            return@withContext false
        }
        
        // 检查关键字段变化
        val isVipStatusChanged = cachedData.is_vip != currentData.is_vip
        val isVipStartChanged = cachedData.vipstart != currentData.vipstart
        val isVipEndChanged = cachedData.vipend != currentData.vipend
        val isLastLoginTimeChanged = cachedData.lastlogintime != currentData.lastlogintime
        val isLastLoginDeviceChanged = cachedData.lastlogindevice != currentData.lastlogindevice
        
        // 记录变化日志
        if (isVipStatusChanged) {
            Log.d(TAG, "VIP状态已变化 | 缓存状态：${cachedData.is_vip} | 当前状态：${currentData.is_vip}")
        }
        if (isVipStartChanged) {
            Log.d(TAG, "VIP开始时间已变化 | 缓存时间：${cachedData.vipstart ?: "未设置"} | 当前时间：${currentData.vipstart ?: "未设置"}")
        }
        if (isVipEndChanged) {
            Log.d(TAG, "VIP结束时间已变化 | 缓存时间：${cachedData.vipend ?: "未设置"} | 当前时间：${currentData.vipend ?: "未设置"}")
        }
        if (isLastLoginTimeChanged) {
            Log.d(TAG, "最后登录时间已变化 | 缓存时间：${cachedData.lastlogintime ?: "未设置"} | 当前时间：${currentData.lastlogintime ?: "未设置"}")
        }
        if (isLastLoginDeviceChanged) {
            Log.d(TAG, "最后登录设备已变化 | 缓存设备：${cachedData.lastlogindevice ?: "未设置"} | 当前设备：${currentData.lastlogindevice ?: "未设置"}")
        }
        
        // 判断是否有变化
        val hasChanges = isVipStatusChanged || 
                         isVipStartChanged || 
                         isVipEndChanged ||
                         isLastLoginTimeChanged || 
                         isLastLoginDeviceChanged
        
        if (hasChanges) {
            Log.d(TAG, "检测到用户资料关键字段变化，需要刷新")
        } else {
            Log.d(TAG, "用户资料关键字段无变化，无需刷新")
        }
        
        return@withContext hasChanges
    }
    
    /**
     * 检查用户设置关键字段变化
     * @param context 应用上下文
     * @param currentSettings 当前用户设置
     * @param cachedSettings 缓存的用户设置
     * @return 如果有字段变化则返回true，否则返回false
     */
    suspend fun <T : Any> trackUserSettingsChanges(
        context: Context,
        currentSettings: T,
        cachedSettings: T
    ): Boolean = withContext(Dispatchers.IO) {
        if (cachedSettings == null) {
            Log.d(TAG, "缓存用户设置为空，无法检测变化")
            return@withContext false
        }
        
        // 使用反射比较关键字段
        try {
            val fields = currentSettings.javaClass.declaredFields
            var hasChanges = false
            
            for (field in fields) {
                field.isAccessible = true
                val cachedValue = field.get(cachedSettings)
                val currentValue = field.get(currentSettings)
                
                if (cachedValue != currentValue) {
                    Log.d(TAG, "字段 ${field.name} 已变化: $cachedValue -> $currentValue")
                    hasChanges = true
                }
            }
            
            if (hasChanges) {
                Log.d(TAG, "检测到用户设置关键字段变化，需要刷新")
            } else {
                Log.d(TAG, "用户设置关键字段无变化，无需刷新")
            }
            
            return@withContext hasChanges
        } catch (e: Exception) {
            Log.e(TAG, "检查字段变化时发生错误", e)
            return@withContext false
        }
    }
    
    /**
     * 检查缓存是否有效并检测字段变化
     * @param context 应用上下文
     * @param key 缓存键
     * @param currentData 当前数据（可能来自其他来源）
     * @return 如果缓存有效且字段未变化则返回true，否则返回false
     */
    suspend fun <T : Any> isValidWithFieldCheck(
        context: Context,
        key: SupabaseCacheKey,
        currentData: T
    ): Boolean = withContext(Dispatchers.IO) {
        // 首先检查时间有效性
        val isTimeValid = SupabaseCacheManager.isValid(context, key)
        if (!isTimeValid) {
            Log.d(TAG, "缓存已过期，无需检查字段变化")
            return@withContext false
        }
        
        // 根据缓存类型检查字段变化
        when {
            key == SupabaseCacheKey.USER_PROFILE && currentData is SupabaseUserDataIptv -> {
                val hasChanges = trackUserProfileChanges(context, currentData)
                return@withContext !hasChanges
            }
            key == SupabaseCacheKey.USER_SETTINGS -> {
                val cachedData = SupabaseCacheManager.getCache<T>(context, key)
                if (cachedData != null) {
                    val hasChanges = trackUserSettingsChanges(context, currentData, cachedData)
                    return@withContext !hasChanges
                }
            }
        }
        
        return@withContext isTimeValid
    }
} 