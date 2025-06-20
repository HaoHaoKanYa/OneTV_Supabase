package top.cywin.onetv.core.data.entities.git

/**
 * git版本信息
 */
data class GitRelease(
    val version: String = "1.0.0",
    val downloadUrl: String = "",
    val description: String = "",
)