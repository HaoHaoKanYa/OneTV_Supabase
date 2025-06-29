package top.cywin.onetv.tv.supabase

import kotlinx.serialization.Serializable

/**
 * 观看历史记录数据模型
 * 统一的观看历史数据格式
 */
@Serializable
data class SupabaseWatchHistoryItem(
    val id: String? = null,
    val channelName: String,
    val channelUrl: String,
    val duration: Long,
    val watchStart: String,
    val watchEnd: String? = null,
    val userId: String? = null
)

/**
 * 观看历史分页信息
 */
@Serializable
data class SupabaseWatchHistoryPagination(
    val currentPage: Int,
    val totalPages: Int,
    val pageSize: Int,
    val totalItems: Int
)

/**
 * 观看历史统计数据
 */
@Serializable
data class SupabaseWatchStatistics(
    val totalWatchTime: Long = 0,
    val totalChannels: Int = 0,
    val totalWatches: Int = 0,
    val mostWatchedChannel: String? = null,
    val mostWatchedTime: Long = 0,
    val channelStatistics: List<ChannelStatistic>? = null
) 

/**
 * 频道统计数据类
 */
@Serializable
data class ChannelStatistic(
    val channelName: String,
    val watchCount: Int,
    val totalDuration: Long
) 