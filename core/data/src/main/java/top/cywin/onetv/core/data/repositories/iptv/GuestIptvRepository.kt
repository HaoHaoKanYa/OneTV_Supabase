package top.cywin.onetv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.core.data.entities.iptvsource.IptvSource
import top.cywin.onetv.core.data.network.await
import top.cywin.onetv.core.data.repositories.FileCacheRepository
import top.cywin.onetv.core.data.repositories.iptv.parser.IptvParser
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import top.cywin.onetv.core.data.utils.Logger

/**
 * 游客模式的直播源数据获取（不需要 token）
 */
class GuestIptvRepository(
    private val source: IptvSource
) : FileCacheRepository(
    if (source.isLocal) source.url
    else "iptv-${source.url.hashCode().toUInt().toString(16)}.txt",
    source.isLocal
), BaseIptvRepository {

    private val log = Logger.create(javaClass.simpleName)
    private val supabaseApi = SupabaseApiClient.getInstance() // 使用单例Supabase API客户端

    // 获取直播源数据
    private suspend fun fetchSource(sourceUrl: String): String {
        log.d("游客模式：获取远程直播源: $source")

        // 检查是否是 dynamic: 前缀的 URL，如果是则使用Supabase获取
        if (sourceUrl.startsWith("dynamic:")) {
            val ispType = when (sourceUrl) {
                "dynamic:yidong" -> "yidong"
                "dynamic:dianxin" -> "dianxin"
                "dynamic:public" -> "public"
                else -> throw IllegalArgumentException("Invalid dynamic URL: $sourceUrl")
            }
            
            try {
                // 使用Supabase API获取IPTV频道
                log.d("游客模式：使用Supabase获取IPTV频道: ispType=$ispType")
                return supabaseApi.getIptvChannels(ispType)
            } catch (e: Exception) {
                log.e("游客模式：从Supabase获取直播源失败", e)
                
                // 添加更详细的错误日志
                val errorDetails = when (e) {
                    is io.github.jan.supabase.exceptions.HttpRequestException -> {
                        "HTTP错误: ${e.message}\n" +
                        "尝试连接的URL: ${e.message?.substringAfter("to ")?.substringBefore(" (")}\n" +
                        "请确认Supabase客户端已正确初始化，环境变量已正确设置"
                    }
                    else -> "错误类型: ${e.javaClass.simpleName}, 消息: ${e.message}"
                }
                log.e("游客模式：错误详情 - $errorDetails")
                
                throw e
            }
        } else {
            // 对于非动态URL，仍然使用原来的逻辑
            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .build()

            val request = Request.Builder()
                .url(sourceUrl)
                .build()

            try {
                val response = client.newCall(request).await()
                if (!response.isSuccessful) {
                    throw Exception("${response.code}: ${response.message}")
                }
                val rawData = withContext(Dispatchers.IO) {
                    response.body?.string().orEmpty()
                }
                log.d("游客模式：原始 M3U 内容:\n$rawData")
                return rawData
            } catch (ex: Exception) {
                log.e("游客模式：获取直播源失败", ex)
                throw Exception("获取直播源失败，请检查网络连接", ex)
            }
        }
    }

    /**
     * 获取直播源分组列表（仅包含游客权限频道）
     */
    override suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList {
        try {
            val sourceData = getOrRefresh(
                if (source.isLocal) Long.MAX_VALUE else cacheTime
            ) {
                fetchSource(source.url)
            }
            val parser = IptvParser.instances.first { it.isSupport(source.url, sourceData) }
            val startTime = System.currentTimeMillis()
            val groupList = parser.parse(sourceData)
            val timeUsed = System.currentTimeMillis() - startTime
            log.i(
                "游客模式：解析直播源（${source.name}）完成：${groupList.size} 个分组, " +
                        "${groupList.sumOf { it.channelList.size }} 个频道, " +
                        "${groupList.sumOf { it.channelList.sumOf { ch -> ch.urlList.size } }} 条线路, " +
                        "耗时：${timeUsed}ms"
            )
            return groupList
        } catch (ex: Exception) {
            log.e("游客模式：获取直播源失败", ex)
            throw ex
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        super.clearCache()
    }
}
