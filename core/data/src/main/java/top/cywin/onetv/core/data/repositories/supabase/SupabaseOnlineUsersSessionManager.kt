// [file name]: OnlineUsersSessionManager.kt
package top.cywin.onetv.core.data.repositories.supabase

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Supabase在线用户会话管理器
 * 负责从Supabase服务获取和缓存在线用户数据
 */
class SupabaseOnlineUsersSessionManager private constructor(context: Context) {
    // 缓存键名
    private val ONLINE_USERS_KEY = "SUPABASE_ONLINE_USERS_DATA"
    
    // 本地配置存储
    private val prefs = context.getSharedPreferences("supabase_online_users_cache", Context.MODE_PRIVATE)
    
    // Supabase API客户端
    private val apiClient = SupabaseApiClient.getInstance()
    
    // 协程作用域和任务
    private val scope = CoroutineScope(Dispatchers.Default)
    private var refreshJob: Job? = null
    
    // 缓存过期时间（20分钟）
    private val CACHE_EXPIRATION = 20 * 60L
    
    // 同步状态标识
    private val isSyncing = AtomicBoolean(false)
    
    // 日志标签
    private val TAG = "SupabaseOnlineUsers"

    companion object {
        @Volatile private var instance: SupabaseOnlineUsersSessionManager? = null

        /**
         * 获取实例（单例模式）
         */
        fun getInstance(context: Context): SupabaseOnlineUsersSessionManager {
            return instance ?: synchronized(this) {
                instance ?: SupabaseOnlineUsersSessionManager(context.applicationContext).also {
                    instance = it
                    it.startBackgroundRefresh()
                }
            }
        }
    }

    /**
     * 启动后台定时刷新（严格对齐服务器正点）
     */
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
                    Log.d(TAG, "[正点刷新] 开始请求数据...")
                    val newData = fetchFromServer()
                    saveLocalCache(newData)
                    Log.d(TAG, "[正点刷新] 成功更新：${newData.total}人")

                    // 3. 对齐下一个整点
                    delay(calculateNextInterval())
                } catch (e: Exception) {
                    Log.e(TAG, "[刷新异常] ${e.message}")
                    delay(300000) // 异常后等待5分钟重试
                }
            }
        }
    }

    /**
     * 从服务器获取在线用户数据
     * 使用SupabaseApiClient调用Edge Function
     */
    private suspend fun fetchFromServer(): SupabaseOnlineUsersData = withContext(Dispatchers.IO) {
        try {
            // 调用Edge Function获取数据
            val response = apiClient.getOnlineUsers()
            
            Log.d(TAG, "[原始响应] $response")
            
            // 转换为数据模型
            return@withContext SupabaseOnlineUsersData(
                total = response.total,
                base = response.base,
                real = response.real,
                updated = response.updated
            ).also {
                Log.d(TAG, "[解析结果] total=${it.total} base=${it.base} real=${it.real} updated=${it.updated}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[请求失败] ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /**
     * 获取缓存的数据
     * @param forceRefresh 是否强制刷新
     */
    suspend fun getCachedData(forceRefresh: Boolean = false): SupabaseOnlineUsersData = withContext(Dispatchers.IO) {
        // 1. 检查强制刷新标志
        if (!forceRefresh) {
            val cachedData = loadLocalCache()
            if (cachedData != null && !isCacheExpired(cachedData.updated)) {
                Log.d(TAG, "[缓存有效] 使用本地缓存 total=${cachedData.total}")
                return@withContext cachedData
            }
        }

        // 2. 进入同步流程
        return@withContext if (isSyncing.compareAndSet(false, true)) {
            try {
                Log.d(TAG, "[同步开始] 强制刷新=$forceRefresh")
                val newData = fetchFromServer().also {
                    saveLocalCache(it)
                    Log.d(TAG, "[同步完成] 新数据 total=${it.total} base=${it.base} real=${it.real}")
                }
                newData
            } catch (e: Exception) {
                Log.e(TAG, "[同步失败] ${e.message}")
                loadLocalCache() ?: SupabaseOnlineUsersData(0, 0, 0, 0) // 降级返回
            } finally {
                isSyncing.set(false)
            }
        } else {
            Log.d(TAG, "[同步跳过] 已有同步进行中")
            loadLocalCache() ?: SupabaseOnlineUsersData(0, 0, 0, 0)
        }
    }

    /**
     * 检查缓存是否过期
     */
    private fun isCacheExpired(updated: Long): Boolean {
        return (System.currentTimeMillis() / 1000 - updated) > CACHE_EXPIRATION
    }

    /**
     * 加载本地缓存
     */
    private fun loadLocalCache(): SupabaseOnlineUsersData? {
        return prefs.getString(ONLINE_USERS_KEY, null)?.let { jsonStr ->
            try {
                JSONObject(jsonStr).run {
                    SupabaseOnlineUsersData(
                        getInt("total"),
                        getInt("base"),
                        getInt("real"),
                        getLong("updated"),
                        getLong("fetchTime")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "[缓存解析失败] ${e.message}")
                null
            }
        }
    }

    /**
     * 保存本地缓存
     */
    private fun saveLocalCache(data: SupabaseOnlineUsersData) {
        // 1. 保存传统JSON格式
        prefs.edit {
            putString(ONLINE_USERS_KEY,
                JSONObject().apply {
                    put("total", data.total)
                    put("base", data.base)
                    put("real", data.real)
                    put("updated", data.updated)
                    put("fetchTime", data.fetchTime)
                }.toString()
            )
            apply()
        }
        
        // 2. 保存到缓存
        try {
            // 获取应用上下文
            val appContext = SupabaseClient.getAppContext()
            if (appContext != null) {
                // 使用scope.launch确保在IO线程执行
                scope.launch(Dispatchers.IO) {
                    try {
                        // 现在我们可以直接导入SupabaseCacheManager和SupabaseCacheKey
                        // 然后就直接调用saveCache方法，不需要反射
                        Log.d(TAG, "[统一缓存] 在线用户数据同步中...")
                        
                        // 我们只保存传统JSON格式，不再尝试直接保存对象
                        val gson = Gson()
                        val jsonString = gson.toJson(data)
                        
                        // 将JSON字符串保存到ONLINE_USERS_RAW键
                        prefs.edit {
                            putString("ONLINE_USERS_RAW", jsonString)
                            apply()
                        }
                        
                        Log.d(TAG, "[统一缓存] 在线用户数据已保存")
                    } catch (e: Exception) {
                        Log.e(TAG, "[统一缓存] 保存失败: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[统一缓存] 初始化失败: ${e.message}", e)
        }
        
        Log.d(TAG, "[缓存更新] total=${data.total} base=${data.base} real=${data.real} updated=${data.updated}")
    }

    /**
     * 计算下次刷新时间（下一个整点+30秒缓冲）
     */
    private fun calculateNextRefreshTime(): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 30) // 服务器正点后30秒缓冲
        }
        return calendar.timeInMillis
    }

    /**
     * 计算下次刷新间隔（一小时）
     */
    private fun calculateNextInterval(): Long {
        return 60 * 60 * 1000 // 严格每小时一次
    }
}

