package top.cywin.onetv.core.data.entities.iptvsource

import kotlinx.serialization.Serializable
import top.cywin.onetv.core.data.utils.Globals

/**
 *  直播源
 */
@Serializable
data class IptvSource(
    /**
     * 名称
     */
    val name: String = "",

    /**
     * 链接
     */
    val url: String = "",

    /**
     * 是否本地
     */
    val isLocal: Boolean = false,
) {
    companion object {
        fun IptvSource.needExternalStoragePermission(): Boolean {
            return this.isLocal && !this.url.startsWith(Globals.cacheDir.path)
        }
    }
}
