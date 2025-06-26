/**
 * 缓存条目实现
 * 
 * 此文件定义了缓存的基本单位，包含实际缓存的数据和相关元数据。
 * 提供缓存有效性检查、剩余时间计算和时间格式化等功能。
 * 每个缓存项都有自己的创建时间和过期策略，用于管理其生命周期。
 */
package top.cywin.onetv.core.data.repositories.supabase.cache

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Supabase缓存条目
 * 包含缓存数据和相关元数据
 * @param data 缓存的数据
 * @param createTime 创建时间（毫秒时间戳）
 * @param expireTime 过期时间（毫秒）
 * @param strategy 使用的缓存策略
 */
data class SupabaseCacheEntry<T>(
    val data: T,
    val createTime: Long = System.currentTimeMillis(),
    val expireTime: Long = DEFAULT_EXPIRE_TIME,
    val strategy: SupabaseCacheStrategy = SupabaseCacheStrategy.DEFAULT
) {
    companion object {
        // 默认过期时间：24小时
        const val DEFAULT_EXPIRE_TIME = 24 * 60 * 60 * 1000L
        
        // 格式化北京时间
        fun formatBeijingTime(time: Long): String {
            if (time <= 0) return "未记录"
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.format(Date(time))
        }
    }
    
    /**
     * 检查缓存是否有效
     * @return 如果缓存有效则返回true，否则返回false
     */
    fun isValid(): Boolean {
        return when (strategy) {
            is SupabaseCacheStrategy.NEVER_EXPIRE -> true
            is SupabaseCacheStrategy.NO_CACHE -> false
            is SupabaseCacheStrategy.TimeStrategy -> {
                val currentTime = System.currentTimeMillis()
                val actualExpireTime = strategy.getActualExpireTime()
                currentTime - createTime <= actualExpireTime
            }
            else -> {
                val currentTime = System.currentTimeMillis()
                currentTime - createTime <= expireTime
            }
        }
    }
    
    /**
     * 获取缓存剩余有效时间（毫秒）
     * @return 剩余有效时间，如果已过期则返回负值
     */
    fun getRemainingTime(): Long {
        val currentTime = System.currentTimeMillis()
        val actualExpireTime = when (strategy) {
            is SupabaseCacheStrategy.TimeStrategy -> strategy.getActualExpireTime()
            else -> expireTime
        }
        return createTime + actualExpireTime - currentTime
    }
    
    /**
     * 获取格式化的创建时间
     * @return 格式化后的创建时间字符串
     */
    fun getFormattedCreateTime(): String {
        return formatBeijingTime(createTime)
    }
    
    /**
     * 获取格式化的过期时间
     * @return 格式化后的过期时间字符串
     */
    fun getFormattedExpireTime(): String {
        val actualExpireTime = when (strategy) {
            is SupabaseCacheStrategy.TimeStrategy -> strategy.getActualExpireTime()
            else -> expireTime
        }
        return formatBeijingTime(createTime + actualExpireTime)
    }
} 