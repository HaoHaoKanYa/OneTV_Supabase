package top.cywin.onetv.core.data.utils

import top.cywin.onetv.core.data.entities.epgsource.EpgSource
import top.cywin.onetv.core.data.entities.epgsource.EpgSourceList
import top.cywin.onetv.core.data.entities.iptvsource.IptvSource
import top.cywin.onetv.core.data.entities.iptvsource.IptvSourceList
import top.cywin.onetv.core.data.repositories.user.SessionManager

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "壹来电视"

    /**
     * 应用 代码仓库（改为：关注公众号【壹来了】
     */
    const val APP_REPO = "关注公众号【壹来了】"

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://gh-proxy.com/"

    /**
     * IPTV直播源
     */


    // 确保数据结构正确
    val IPTV_SOURCE_LIST = IptvSourceList(
        sources = listOf(
            IptvSource(
                name = "移动-关注公众号【壹来了】",
                url = "dynamic:yidong" // 动态标识
            ),
            IptvSource(
                name = "电信-關注公众号【壹来了】",
                url = "dynamic:dianxin"
            ),
            IptvSource(
                name = "公共-关注公眾号【壹来了】",
                url = "dynamic:public" // 新增公共线路
            )
        )
    )
    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单来源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默认节目单 老张的EPG",
                url = "http://epg.51zmt.top:8000/e.xml",
            ),
            EpgSource(
                name = "默认节目单 回看七天",
                url = "https://e.erw.cc/all.xml",
            ),
        )
    )

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * Git最新版本信息
     */
    val GIT_RELEASE_LATEST_URL = mapOf(
        "stable" to  GITHUB_PROXY + "raw.githubusercontent.com/HaoHaoKanYa/OneTV/refs/heads/master/tv-stable.json",
        "beta" to GITHUB_PROXY + "raw.githubusercontent.com/HaoHaoKanYa/OneTV/refs/heads/master/tv-beta.json",
    )

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 界面 临时频道界面显示时间
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前后30秒
}