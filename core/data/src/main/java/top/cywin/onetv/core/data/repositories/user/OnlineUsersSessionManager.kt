// [file name]: OnlineUsersSessionManager.kt
package top.cywin.onetv.core.data.repositories.user

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class OnlineUsersSessionManager private constructor(context: Context) {
    // 严格对应JavaScript后端定义
    private val ONLINE_USERS_KEY = "ONLINE_USERS_DATA"

    private val prefs = context.getSharedPreferences("online_users_final", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var refreshJob: Job? = null
    private val CACHE_EXPIRATION = 20 * 60L // 20分钟缓存有效期
    // 新增同步状态标识
    private val isSyncing = AtomicBoolean(false)


    companion object {
        @Volatile private var instance: OnlineUsersSessionManager? = null

        fun getInstance(context: Context): OnlineUsersSessionManager {
            return instance ?: synchronized(this) {
                instance ?: OnlineUsersSessionManager(context.applicationContext).also {
                    instance = it
                    it.startBackgroundRefresh()
                }
            }
        }
    }

    // 启动后台定时刷新（严格对齐服务器正点）
    private fun startBackgroundRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                try {
                    // 1. 计算下次刷新时间（整点+30秒缓冲）
                    val nextRefresh = calculateNextRefreshTime()
                    val delayMs = nextRefresh - System.currentTimeMillis()
                    if (delayMs > 0) delay(delayMs)

                    // 2. 执行网络请求
                    Log.d("OnlineUsers", "[正点刷新] 开始请求数据...")
                    val newData = fetchFromServer()
                    saveLocalCache(newData)
                    Log.d("OnlineUsers", "[正点刷新] 成功更新：${newData.total}人")

                    // 3. 对齐下一个整点
                    delay(calculateNextInterval())
                } catch (e: Exception) {
                    Log.e("OnlineUsers", "[刷新异常] ${e.message}")
                    delay(300000) // 异常后等待5分钟重试
                }
            }
        }
    }

    // 实际网络请求方法（完全匹配JavaScript接口）
    private suspend fun fetchFromServer(): OnlineUsersData = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://iptv.liubaotea.online/api/online-users")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val rawJson = response.body?.string() ?: ""
                Log.d("OnlineUsers", "[原始响应] $rawJson")

                val json = JSONObject(rawJson)
                OnlineUsersData(
                    total = json.getInt("total"),
                    base = json.optInt("base"),    // 改为不设置默认值
                    real = json.optInt("real"),    // 改为不设置默认值
                    updated = json.getLong("updated")
                ).also {
                    Log.d("OnlineUsers",
                        """[解析结果] total=${it.total} base=${it.base} 
                    real=${it.real} updated=${it.updated}"""
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("OnlineUsers", "[请求失败] ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    // 缓存管理（严格遵循ONLINE_USERS_DATA结构）
    // OnlineUsersSessionManager.kt
    suspend fun getCachedData(forceRefresh: Boolean = false): OnlineUsersData = withContext(Dispatchers.IO) {
        // 1. 检查强制刷新标志
        if (!forceRefresh) {
            val cachedData = loadLocalCache()
            if (cachedData != null && !isCacheExpired(cachedData.updated)) {
                Log.d("OnlineUsers", "[缓存有效] 使用本地缓存 total=${cachedData.total}")
                return@withContext cachedData
            }
        }

        // 2. 进入同步流程
        return@withContext if (isSyncing.compareAndSet(false, true)) {
            try {
                Log.d("OnlineUsers", "[同步开始] 强制刷新=$forceRefresh")
                val newData = fetchFromServer().also {
                    saveLocalCache(it)
                    Log.d("OnlineUsers", "[同步完成] 新数据 total=${it.total} base=${it.base} real=${it.real}")
                }
                newData
            } catch (e: Exception) {
                Log.e("OnlineUsers", "[同步失败] ${e.message}")
                loadLocalCache() ?: OnlineUsersData(0, 0, 0, 0) // 降级返回
            } finally {
                isSyncing.set(false)
            }
        } else {
            Log.d("OnlineUsers", "[同步跳过] 已有同步进行中")
            loadLocalCache() ?: OnlineUsersData(0, 0, 0, 0)
        }
    }

    // 新增缓存有效期检查
    private fun isCacheExpired(updated: Long): Boolean {
        return (System.currentTimeMillis() / 1000 - updated) > CACHE_EXPIRATION
    }

    // 修改后的本地缓存加载
    private fun loadLocalCache(): OnlineUsersData? {
        return prefs.getString(ONLINE_USERS_KEY, null)?.let { jsonStr ->
            try {
                JSONObject(jsonStr).run {
                    OnlineUsersData(
                        getInt("total"),
                        getInt("base"),
                        getInt("real"),
                        getLong("updated")
                    )
                }
            } catch (e: Exception) {
                Log.e("OnlineUsers", "[缓存解析失败] ${e.message}")
                null
            }
        }
    }

    // OnlineUsersSessionManager.kt
    private fun saveLocalCache(data: OnlineUsersData) {
        prefs.edit {
            putString(ONLINE_USERS_KEY,
                JSONObject().apply {
                    put("total", data.total)
                    put("base", data.base)
                    put("real", data.real)
                    put("updated", data.updated)
                }.toString()
            )
            apply()
        }
        Log.d("OnlineUsers",
            """[缓存更新] 
        total=${data.total} base=${data.base} 
        real=${data.real} updated=${data.updated}"""
        )
    }

    // 时间计算工具
    private fun calculateNextRefreshTime(): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 30) // 服务器正点后30秒缓冲
        }
        return calendar.timeInMillis
    }

    private fun calculateNextInterval(): Long {
        return 60 * 60 * 1000 // 严格每小时一次
    }
}

