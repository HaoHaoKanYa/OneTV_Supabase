package top.cywin.onetv.core.data.entities.channel

import androidx.compose.runtime.Immutable

/**
 * 频道
 */
@Immutable
data class Channel(
    /**
     * 频道名称
     */
    val name: String = "",

    /**
     * 节目单名称，用于查询节目单
     */
    val epgName: String = "",

    /**
     * 播放地址
     */
    val urlList: List<String> = listOf("http://1.2.3.4"),

    /**
     * 台标
     */
    val logo: String? = null,
) {
    companion object {
        val EXAMPLE = Channel(
            name = "关注公众号【壹来了】",
            epgName = "cctv1",
            urlList = listOf(
                "https://gitee.com/vv2029/SuCai/raw/master/output.m3u8",
                "https://gitee.com/vv2029/SuCai/raw/master/output.m3u8",
            ),
            logo = "https://gitee.com/vv2029/gitee2025/raw/master/yilailogo.jpg"
        )

        val a = Channel()
    }
}