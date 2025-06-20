package top.cywin.onetv.core.data.repositories.supabase
// SupabaseUserDataIptv.kt

/**
 * Supabase用户数据模型
 * 从Supabase服务获取的用户信息数据结构
 * 
 * @property userid 用户ID
 * @property username 用户名
 * @property email 电子邮箱
 * @property created_at 注册时间
 * @property updated_at 更新时间
 * @property is_vip 是否为VIP用户
 * @property accountstatus 账户状态
 * @property vipstart VIP开始时间
 * @property vipend VIP结束时间
 * @property lastlogindevice 最后登录设备
 * @property lastlogintime 最后登录时间
 * @property vip_expiry VIP过期时间(旧字段，保留兼容性)
 */
data class SupabaseUserDataIptv(
    val userid: String = "",
    val username: String = "",
    val email: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val is_vip: Boolean = false,
    val accountstatus: String = "未知",
    val vipstart: String? = null,
    val vipend: String? = null,
    val lastlogindevice: String? = null,
    val lastlogintime: String? = null,
    val vip_expiry: String? = null
)