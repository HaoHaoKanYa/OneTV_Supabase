package top.cywin.onetv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
 * 直播源数据获取
 */
class IptvRepository(
    private val source: IptvSource,
    private val sessionId: String
) : FileCacheRepository(
    if (source.isLocal) source.url
    else "iptv-${source.url.hashCode().toUInt().toString(16)}.txt",
    source.isLocal,
), BaseIptvRepository {
    private val log = Logger.create(javaClass.simpleName)
    private val supabaseApi = SupabaseApiClient.getInstance() // 添加Supabase API客户端

    // ------------ 核心逻辑 ------------
    override suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList {
        try {
            val sourceData = getOrRefresh(if (source.isLocal) Long.MAX_VALUE else cacheTime) {
                fetchSource(source.url)
            }
            val parser = IptvParser.instances.first { it.isSupport(source.url, sourceData) }
            val startTime = System.currentTimeMillis()
            val groupList = parser.parse(sourceData)
            log.i(
                listOf(
                    "解析直播源（${source.name}）完成：${groupList.size}个分组",
                    "${groupList.sumOf { it.channelList.size }}个频道",
                    "${groupList.sumOf { it.channelList.sumOf { channel -> channel.urlList.size } }}条线路",
                    "耗时：${System.currentTimeMillis() - startTime}ms"
                ).joinToString()
            )
            return groupList
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }

    private suspend fun fetchSource(sourceUrl: String): String {
        log.d("获取远程直播源: $source")
        
        // 检查是否是动态URL（以dynamic:开头）
        if (sourceUrl.startsWith("dynamic:")) {
            val ispType = when (sourceUrl) {
                "dynamic:yidong" -> "yidong"
                "dynamic:dianxin" -> "dianxin"
                "dynamic:public" -> "public"
                else -> throw IllegalArgumentException("无效的动态URL: $sourceUrl")
            }
            
            try {
                // 使用Supabase API获取IPTV频道（带授权）
                log.d("使用Supabase获取IPTV频道: ispType=$ispType")
                return supabaseApi.getIptvChannels(ispType)
            } catch (e: Exception) {
                log.e("从Supabase获取直播源失败", e)
                
                // 添加更详细的错误日志
                val errorDetails = when (e) {
                    is io.github.jan.supabase.exceptions.HttpRequestException -> {
                        "HTTP错误: ${e.message}\n" +
                        "尝试连接的URL: ${e.message?.substringAfter("to ")?.substringBefore(" (")}\n" +
                        "请确认Supabase客户端已正确初始化，环境变量已正确设置"
                    }
                    else -> "错误类型: ${e.javaClass.simpleName}, 消息: ${e.message}"
                }
                log.e("错误详情 - $errorDetails")
                
                throw e
            }
        } else {
            // 对于普通URL，保持原有逻辑
            val client = OkHttpClient()
            val requestBuilder = Request.Builder().url(sourceUrl)
            requestBuilder.header("Authorization", "Bearer $sessionId")

            try {
                val response = client.newCall(requestBuilder.build()).await()
                if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")
                val rawData = withContext(Dispatchers.IO) { response.body?.string() ?: "" }
                log.d("原始M3U内容:\n$rawData")
                return rawData
            } catch (ex: Exception) {
                log.e("获取直播源失败", ex)
                throw Exception("获取直播源失败，请检查网络连接", ex)
            }
        }
    }

    override suspend fun clearCache() {
        if (source.isLocal) return
        super.clearCache()
    }
}

interface BaseIptvRepository {
    suspend fun getChannelGroupList(cacheTime: Long): ChannelGroupList
    suspend fun clearCache()
}