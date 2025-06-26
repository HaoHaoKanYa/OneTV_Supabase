/**
 * 缓存配置定义
 * 
 * 此文件定义了缓存的配置参数，包括过期时间、随机偏移和存储方式。
 * 通过不同的预设配置(VIP_30_DAYS, SERVICE_INFO等)，为不同类型的数据提供合适的缓存策略。
 * 每个配置都可以转换为对应的缓存策略(SupabaseCacheStrategy)以实际应用。
 */
package top.cywin.onetv.core.data.repositories.supabase.cache

/**
 * Supabase缓存配置类
 * 定义缓存的配置参数
 * @param expireTime 过期时间（毫秒）
 * @param randomOffset 随机偏移量（毫秒），用于避免缓存雪崩
 * @param useMemoryCache 是否使用内存缓存
 * @param usePersistentCache 是否使用持久化缓存
 */
data class SupabaseCacheConfig(
    val expireTime: Long = 24 * 60 * 60 * 1000L, // 默认缓存有效期为24小时
    val randomOffset: Long = 0, // 随机偏移量，用于避免缓存雪崩
    val useMemoryCache: Boolean = true,
    val usePersistentCache: Boolean = true
) {
    companion object {
        // 默认配置
        val DEFAULT = SupabaseCacheConfig()
        
        // VIP用户配置 - 30天
        val VIP_30_DAYS = SupabaseCacheConfig(
            expireTime = 30 * 24 * 60 * 60 * 1000L,
            randomOffset = 12 * 60 * 60 * 1000L // 12小时随机偏移
        )
        
        // VIP用户配置 - 2天
        val VIP_2_DAYS = SupabaseCacheConfig(
            expireTime = 2 * 24 * 60 * 60 * 1000L,
            randomOffset = 2 * 60 * 60 * 1000L // 2小时随机偏移
        )
        
        // VIP用户配置 - 8小时
        val VIP_8_HOURS = SupabaseCacheConfig(
            expireTime = 8 * 60 * 60 * 1000L,
            randomOffset = 30 * 60 * 1000L // 30分钟随机偏移
        )
        
        // 服务信息配置 - 3天
        val SERVICE_INFO = SupabaseCacheConfig(
            expireTime = 3 * 24 * 60 * 60 * 1000L, // 3天
            randomOffset = 12 * 60 * 60 * 1000L, // ±12小时
            useMemoryCache = true,
            usePersistentCache = true
        )
        
        // 在线用户配置 - 1小时
        val ONLINE_USERS = SupabaseCacheConfig(
            expireTime = 1 * 60 * 60 * 1000L, // 1小时
            randomOffset = 10 * 60 * 1000L, // ±10分钟
            useMemoryCache = true,
            usePersistentCache = true
        )
        
        // 无缓存配置
        val NO_CACHE = SupabaseCacheConfig(
            useMemoryCache = false,
            usePersistentCache = false
        )
        
        // 应用配置缓存配置
        val APP_CONFIGS = SupabaseCacheConfig(
            expireTime = 7 * 24 * 60 * 60 * 1000L, // 7天
            randomOffset = 24 * 60 * 60 * 1000L, // ±24小时
            useMemoryCache = true,
            usePersistentCache = true
        )
        
        // 频道收藏缓存配置
        val CHANNEL_FAVORITES = SupabaseCacheConfig(
            expireTime = 1 * 24 * 60 * 60 * 1000L, // 1天
            randomOffset = 6 * 60 * 60 * 1000L, // ±6小时
            useMemoryCache = true,
            usePersistentCache = true
        )
    }
    
    /**
     * 获取对应的缓存策略
     * @return 缓存策略对象
     */
    fun toStrategy(): SupabaseCacheStrategy {
        if (!useMemoryCache && !usePersistentCache) {
            return SupabaseCacheStrategy.NO_CACHE
        }
        
        // 使用TimeStrategy，它已经支持随机偏移
        return SupabaseCacheStrategy.TimeStrategy(expireTime, randomOffset)
    }
} 