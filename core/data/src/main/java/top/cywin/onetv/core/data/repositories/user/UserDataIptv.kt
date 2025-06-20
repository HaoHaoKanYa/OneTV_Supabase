package top.cywin.onetv.core.data.repositories.user
// SupabaseUserDataIptv.kt
data class UserDataIptv(
    val userId: String = "",
    val username: String = "", // 新增字段
    val email: String = "",
    val regTime: String = "",       // 修改为 String 类型
    val isVIP: Boolean = false,
    val accountStatus: String = "未知",
    val vipStart: String? = null,   // 修改为 String 类型
    val vipEnd: String? = null,     // 修改为 String 类型
    val lastLoginDevice: String? = null,
    val lastLoginTime: String? = null // 修改为 String 类型
)
