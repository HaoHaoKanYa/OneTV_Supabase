/**
 * 缓存策略定义
 * 
 * 此文件定义了不同的缓存策略，控制缓存的过期行为。
 * 通过密封类实现策略模式，支持默认策略、基于时间的策略、永不过期和不缓存等多种方式。
 * 基于时间的策略支持随机偏移，有助于防止缓存雪崩问题。
 */
package top.cywin.onetv.core.data.repositories.supabase.cache

/**
 * Supabase缓存策略基类
 * 定义不同的缓存行为和过期策略
 */
sealed class SupabaseCacheStrategy {
    /**
     * 默认缓存策略
     * 使用系统默认的缓存过期时间
     */
    object DEFAULT : SupabaseCacheStrategy()

    /**
     * 基于时间的缓存策略
     * @param expireTime 过期时间（毫秒）
     * @param randomOffset 随机偏移量（毫秒），用于避免缓存雪崩
     */
    data class TimeStrategy(
        val expireTime: Long,
        val randomOffset: Long = 0L
    ) : SupabaseCacheStrategy() {
        /**
         * 计算实际过期时间
         * @return 实际过期时间（毫秒）
         */
        fun getActualExpireTime(): Long {
            if (randomOffset <= 0) return expireTime
            val offset = if (randomOffset > 0) (Math.random() * randomOffset * 2 - randomOffset).toLong() else 0
            return expireTime + offset
        }
    }

    /**
     * 永不过期的缓存策略
     */
    object NEVER_EXPIRE : SupabaseCacheStrategy()

    /**
     * 不缓存策略
     * 每次都从网络获取最新数据
     */
    object NO_CACHE : SupabaseCacheStrategy()
} 