package top.cywin.onetv.core.data.repositories.supabase

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import io.github.jan.supabase.safeBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Supabase 服务信息管理器
 * 用于从 Supabase 服务器获取服务信息
 */
object SupabaseServiceInfoManager {
    private const val KEY_SERVICE_CACHE = "supabase_service_cache"
    private const val CACHE_DAYS = 3 // 基准天数比原来更短，更频繁检查更新
    private const val RANDOM_HOURS = 12 // 随机偏移小时更短

    private const val TAG = "SupabaseServiceInfo"

    /**
     * 加载服务信息
     * 优先从缓存加载，如果缓存过期则从服务器获取
     * @param context 应用上下文
     * @param forceRefresh 是否强制从服务器刷新
     * @return 服务信息内容
     */
    suspend fun loadServiceInfo(context: Context, forceRefresh: Boolean = false): String {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(KEY_SERVICE_CACHE, Context.MODE_PRIVATE)
            val cachedContent = prefs.getString("content", null)
            val lastFetch = prefs.getLong("last_fetch", 0)
            val lastServerUpdate = prefs.getLong("last_updated", 0)

            // 判断是否需要刷新数据
            val (shouldRefresh, serverTimestamp) = if (forceRefresh) {
                Log.d(TAG, "强制刷新服务信息")
                Pair(true, System.currentTimeMillis() / 1000)
            } else {
                checkRefreshCondition(lastFetch, lastServerUpdate, context)
            }

            if (shouldRefresh || cachedContent == null) {
                Log.d(TAG, "缓存过期或为空，从服务器获取")
                fetchFromServer().let { response ->
                    Log.d(TAG, "服务器响应 - 内容: ${response.content.take(50)}..., 时间戳: ${response.timestamp}")
                    prefs.edit {
                        putString("content", response.content)
                        putLong("last_fetch", System.currentTimeMillis())
                        putLong("last_updated", response.timestamp)
                    }
                    Log.d(TAG, "缓存已更新")
                    response.content
                }
            } else {
                Log.d(TAG, "使用缓存数据")
                cachedContent
            }
        }
    }

    /**
     * 检查是否需要刷新数据
     * @param lastFetch 上次从服务器获取数据的时间
     * @param lastServerUpdate 上次服务器数据更新时间
     * @param context 应用上下文
     * @return Pair<Boolean, Long> 是否需要刷新，当前服务器时间戳
     */
    private suspend fun checkRefreshCondition(
        lastFetch: Long,
        lastServerUpdate: Long,
        context: Context
    ): Pair<Boolean, Long> {
        // 条件1：时间间隔检查（3天±12小时）
        val randomOffset = Random.nextLong(-RANDOM_HOURS * 3600000L, RANDOM_HOURS * 3600000L)
        val cacheDuration = TimeUnit.DAYS.toMillis(CACHE_DAYS.toLong()) + randomOffset
        val timeCondition = System.currentTimeMillis() > (lastFetch + cacheDuration)

        // 条件2：服务端数据更新检查
        val currentServerTime = getServerTimestamp()
        val updateCondition = currentServerTime > lastServerUpdate

        Log.d(TAG, "缓存检查 - 上次获取: ${Date(lastFetch)}, 缓存时长: ${cacheDuration/3600000}小时")
        Log.d(TAG, "服务器时间戳: $currentServerTime, 本地时间戳: $lastServerUpdate")
        Log.d(TAG, "需要刷新: 时间条件=$timeCondition || 更新条件=$updateCondition")

        return (timeCondition || updateCondition) to currentServerTime
    }

    /**
     * 获取服务器时间戳
     * @return 服务器时间戳
     */
    private suspend fun getServerTimestamp(): Long {
        return try {
            val response = fetchFromServer()
            response.timestamp
        } catch (e: Exception) {
            Log.e(TAG, "获取服务器时间戳失败", e)
            0L
        }
    }

    /**
     * 从服务器获取服务信息
     * @return 包含内容和时间戳的响应对象
     */
    private suspend fun fetchFromServer(): ServiceInfoResponse {
        val apiClient = SupabaseApiClient.getInstance()
        val response = apiClient.getServiceInfo()

        val content = response["content"]?.jsonPrimitive?.content ?: "暂无服务信息"
        // 修复时间戳解析：直接获取Long值，避免字符串转换问题
        val timestamp = try {
            response["last_updated"]?.jsonPrimitive?.long ?: (System.currentTimeMillis() / 1000)
        } catch (e: Exception) {
            Log.w(TAG, "解析服务器时间戳失败，使用当前时间", e)
            System.currentTimeMillis() / 1000
        }

        Log.d(TAG, "从服务器获取服务信息 - 内容长度: ${content.length}, 时间戳: $timestamp")
        return ServiceInfoResponse(content, timestamp)
    }

    /**
     * 清除服务信息缓存
     * @param context 应用上下文
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(KEY_SERVICE_CACHE, Context.MODE_PRIVATE)
        prefs.edit {
            clear()
        }
        Log.d(TAG, "服务信息缓存已清除")
    }
}

/**
 * 服务信息响应数据类
 * @param content 服务信息内容
 * @param timestamp 时间戳（秒）
 */
data class ServiceInfoResponse(val content: String, val timestamp: Long)