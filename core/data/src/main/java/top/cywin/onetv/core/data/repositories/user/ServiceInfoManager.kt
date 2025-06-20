package top.cywin.onetv.core.data.repositories.user

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ServiceInfoManager {
    private const val KEY_SERVICE_CACHE = "service_cache"
    private const val CACHE_DAYS = 10 // 基准天数
    private const val RANDOM_HOURS = 24 // 随机偏移小时

    suspend fun loadServiceInfo(context: Context): String {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(KEY_SERVICE_CACHE, Context.MODE_PRIVATE)
            val cachedContent = prefs.getString("content", null)
            val lastFetch = prefs.getLong("last_fetch", 0)
            val lastServerUpdate = prefs.getLong("last_updated", 0)

            // 新逻辑：增加服务端时间戳校验
            val (shouldRefresh, serverTimestamp) = checkRefreshCondition(
                lastFetch,
                lastServerUpdate,
                context
            )

            if (shouldRefresh || cachedContent == null) {
                fetchFromServer().let { response ->
                    prefs.edit {
                        putString("content", response.content)
                        putLong("last_fetch", System.currentTimeMillis())
                        putLong("last_updated", response.timestamp)
                    }
                    response.content
                }
            } else {
                cachedContent
            }
        }
    }

    // 新增加的刷新条件检查方法
    private suspend fun checkRefreshCondition(
        lastFetch: Long,
        lastServerUpdate: Long,
        context: Context
    ): Pair<Boolean, Long> {
        // 条件1：时间间隔检查（10天±24小时）
        val randomOffset = Random.nextLong(-RANDOM_HOURS * 3600000L, RANDOM_HOURS * 3600000L)
        val cacheDuration = TimeUnit.DAYS.toMillis(CACHE_DAYS.toLong()) + randomOffset
        val timeCondition = System.currentTimeMillis() > (lastFetch + cacheDuration)

        // 条件2：服务端数据更新检查
        val currentServerTime = getServerTimestamp()
        val updateCondition = currentServerTime > lastServerUpdate


        Log.d("CacheCheck", "lastFetch=${Date(lastFetch)}, cacheDuration=${cacheDuration/86400000}天")
        Log.d("CacheCheck", "需要刷新：$timeCondition || $updateCondition")

        return (timeCondition || updateCondition) to currentServerTime
    }

    // 获取服务端最新时间戳
    private suspend fun getServerTimestamp(): Long {
        return try {
            val response = fetchFromServer()
            response.timestamp
        } catch (e: Exception) {
            0L
        }
    }

    // 从服务器获取完整数据
    private suspend fun fetchFromServer(): ServiceInfoResponse {
        val url = URL("https://iptv.liubaotea.online/api/service-info")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        BufferedReader(InputStreamReader(connection.inputStream)).use {
            val json = JSONObject(it.readText())
            val content = json.getString("content")
            val timestamp = json.optLong("last_updated") // 使用 optLong 避免类型问题
            return ServiceInfoResponse(content, timestamp)
        }
    }
}

data class ServiceInfoResponse(val content: String, val timestamp: Long)