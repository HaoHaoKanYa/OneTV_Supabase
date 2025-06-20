package top.cywin.onetv.core.data.repositories.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase在线用户响应数据模型
 * 适配Supabase Edge Functions返回的JSON结构
 * 
 * @property total 总在线人数
 * @property base 系统生成的基数 
 * @property real 真实在线用户数
 * @property updated 数据更新时间（秒级时间戳）
 * @property status 请求状态，通常为"success"
 * @property message 附加消息（可选）
 */
@Serializable
data class SupabaseOnlineUsersResponse(
    val total: Int,
    val base: Int,
    val real: Int,
    val updated: Long,
    val status: String? = "success",
    val message: String? = null
)
