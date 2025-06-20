package top.cywin.onetv.core.data.repositories.user

data class OnlineUsersData(
    val total: Int,    // 总在线人数（基数+真实）
    val base: Int,     // 系统生成的基数
    val real: Int,     // 真实用户数
    val updated: Long  // 数据更新时间戳（秒级）
)