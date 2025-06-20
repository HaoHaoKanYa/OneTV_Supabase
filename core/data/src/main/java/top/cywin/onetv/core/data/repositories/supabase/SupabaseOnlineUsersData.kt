package top.cywin.onetv.core.data.repositories.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase在线用户数据模型
 * 用于存储服务器返回的实时在线人数数据
 * 
 * @property total 总在线人数（基数+真实）
 * @property base 系统生成的基数
 * @property real 真实用户数
 * @property updated 数据更新时间戳（秒级）
 * @property fetchTime 本地获取数据的时间戳（毫秒级），仅用于客户端缓存管理
 */
@Serializable
data class SupabaseOnlineUsersData(
    val total: Int,    // 总在线人数（基数+真实）
    val base: Int,     // 系统生成的基数
    val real: Int,     // 真实用户数
    val updated: Long, // 数据更新时间戳（秒级）
    @SerialName("fetch_time")
    val fetchTime: Long = System.currentTimeMillis() // 本地获取时间（毫秒级）
)