package top.cywin.onetv.core.data.repositories.supabase.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope

/**
 * Supabase统一缓存管理器
 * 负责管理所有Supabase相关数据的缓存处理
 */
object SupabaseCacheManager {
    private const val TAG = "SupabaseCacheManager"
    
    // 内存缓存的最大条目数
    private const val MAX_MEMORY_CACHE_SIZE = 100
    
    // 最后访问时间跟踪
    private val lastAccessTime = ConcurrentHashMap<String, Long>()
    
    // 使用LRU策略的内存缓存
    private val memoryCache = object : LinkedHashMap<String, SupabaseCacheEntry<Any>>(MAX_MEMORY_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SupabaseCacheEntry<Any>>): Boolean {
            return size > MAX_MEMORY_CACHE_SIZE
        }
    }
    
    // 缓存数据流
    private val cacheFlows = ConcurrentHashMap<String, MutableStateFlow<Any?>>()
    
    // 缓存预热状态
    private val preheatedKeys = mutableSetOf<String>()
    
    // 缓存清理调度器
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    
    // 缓存配置
    private val cacheConfigs = mapOf(
        SupabaseCacheKey.SESSION to SupabaseCacheConfig(expireTime = 7 * 24 * 60 * 60 * 1000L),  // 7天
        SupabaseCacheKey.USER_DATA to SupabaseCacheConfig(expireTime = 30 * 24 * 60 * 60 * 1000L), // 30天
        SupabaseCacheKey.SERVICE_INFO to SupabaseCacheConfig.SERVICE_INFO,
        SupabaseCacheKey.ONLINE_USERS to SupabaseCacheConfig.ONLINE_USERS,
        SupabaseCacheKey.USER_PROFILE to SupabaseCacheConfig(expireTime = 7 * 24 * 60 * 60 * 1000L), // 7天
        SupabaseCacheKey.USER_SETTINGS to SupabaseCacheConfig(expireTime = 7 * 24 * 60 * 60 * 1000L), // 7天
        SupabaseCacheKey.WATCH_HISTORY to SupabaseCacheConfig(expireTime = 30 * 24 * 60 * 60 * 1000L) // 30天
    )
    
    // 需要预热的缓存键
    private val preheatingKeys = listOf(
        SupabaseCacheKey.SESSION,
        SupabaseCacheKey.USER_DATA,
        SupabaseCacheKey.USER_PROFILE,
        SupabaseCacheKey.USER_SETTINGS,
        SupabaseCacheKey.SERVICE_INFO
    )
    
    init {
        // 启动定期清理过期缓存的任务
        scheduler.scheduleAtFixedRate({
            try {
                cleanExpiredCache()
            } catch (e: Exception) {
                Log.e(TAG, "清理过期缓存失败", e)
            }
        }, 30, 30, TimeUnit.MINUTES)
    }
    
    /**
     * 保存缓存数据
     * @param context 应用上下文
     * @param key 缓存键
     * @param data 要缓存的数据
     * @param strategy 缓存策略
     */
    suspend fun <T : Any> saveCache(
        context: Context,
        key: SupabaseCacheKey,
        data: T,
        strategy: SupabaseCacheStrategy = SupabaseCacheStrategy.DEFAULT
    ) = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(key)
        
        // 保存到内存缓存
        @Suppress("UNCHECKED_CAST")
        val entry = SupabaseCacheEntry(
            data = data,
            createTime = System.currentTimeMillis(),
            strategy = strategy
        )
        
        synchronized(memoryCache) {
            memoryCache[cacheKey] = entry as SupabaseCacheEntry<Any>
        }
        
        // 更新最后访问时间
        lastAccessTime[cacheKey] = System.currentTimeMillis()
        
        // 保存到持久化存储
        val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
        try {
            val json = Gson().toJson(data)
            prefs.edit()
                .putString(key.keyName, json)
                .putLong("${key.keyName}_time", entry.createTime)
                .apply()
            
            Log.d(TAG, "缓存保存成功 | 键：${key.name} | 时间：${entry.getFormattedCreateTime()}")
        } catch (e: Exception) {
            Log.e(TAG, "缓存保存失败 | 键：${key.name}", e)
        }
        
        // 更新数据流
        updateCacheFlow(cacheKey, data)
    }
    
    /**
     * 获取缓存数据
     * @param context 应用上下文
     * @param key 缓存键
     * @param defaultValue 默认值，如果缓存不存在则返回此值
     * @return 缓存数据，如果缓存不存在则返回默认值
     */
    suspend fun <T : Any> getCache(
        context: Context,
        key: SupabaseCacheKey,
        defaultValue: T? = null
    ): T? = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(key)
        
        // 更新最后访问时间
        lastAccessTime[cacheKey] = System.currentTimeMillis()
        
        // 首先尝试从内存缓存获取
        @Suppress("UNCHECKED_CAST")
        val memoryEntry = synchronized(memoryCache) { 
            memoryCache[cacheKey] as? SupabaseCacheEntry<T> 
        }
        
        if (memoryEntry != null && memoryEntry.isValid()) {
            Log.d(TAG, "从内存缓存获取数据 | 键：${key.name} | 创建时间：${memoryEntry.getFormattedCreateTime()}")
            return@withContext memoryEntry.data
        }
        
        // 如果内存缓存不存在或已过期，尝试从持久化存储获取
        val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
        val json = prefs.getString(key.keyName, null)
        val createTime = prefs.getLong("${key.keyName}_time", 0)
        
        if (json != null) {
            try {
                // 特殊处理USER_DATA类型，这是导致类型转换错误的主要原因
                if (key == SupabaseCacheKey.USER_DATA) {
                    try {
                        Log.d(TAG, "处理USER_DATA类型，使用明确类型反序列化")
                        // 使用明确的类型反序列化，避免LinkedTreeMap转换问题
                        val userData = Gson().fromJson(json, SupabaseUserDataIptv::class.java)
                        
                        // 检查数据有效性
                        val config = cacheConfigs[key] ?: SupabaseCacheConfig.DEFAULT
                        val strategy = config.toStrategy()
                        val entry = SupabaseCacheEntry(
                            data = userData as Any,
                            createTime = createTime,
                            strategy = strategy
                        )
                        
                        if (entry.isValid()) {
                            // 更新内存缓存
                            synchronized(memoryCache) {
                                memoryCache[cacheKey] = entry
                            }
                            Log.d(TAG, "USER_DATA反序列化成功 | 用户ID: ${userData.userid}")
                            return@withContext userData as T
                        } else {
                            Log.d(TAG, "USER_DATA缓存已过期")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "USER_DATA反序列化失败: ${e.message}", e)
                        // 清除无效缓存
                        prefs.edit()
                            .remove(key.keyName)
                            .remove("${key.keyName}_time")
                            .apply()
                    }
                    return@withContext defaultValue
                }
                
                // 特殊处理WATCH_HISTORY类型，修复类型不匹配问题
                if (key == SupabaseCacheKey.WATCH_HISTORY && json.startsWith("[")) {
                    try {
                        Log.d(TAG, "处理WATCH_HISTORY类型，检测到数组JSON格式")
                        // 检测到是JSON数组格式，使用Type Token处理
                        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                        val watchHistoryData = Gson().fromJson<Any>(json, type)
                        
                        // 检查数据有效性
                        val config = cacheConfigs[key] ?: SupabaseCacheConfig.DEFAULT
                        val strategy = config.toStrategy()
                        val entry = SupabaseCacheEntry(
                            data = watchHistoryData,
                            createTime = createTime,
                            strategy = strategy
                        )
                        
                        if (entry.isValid()) {
                            // 更新内存缓存
                            synchronized(memoryCache) {
                                memoryCache[cacheKey] = entry as SupabaseCacheEntry<Any>
                            }
                            Log.d(TAG, "WATCH_HISTORY反序列化成功 | 是数组格式")
                            return@withContext watchHistoryData as T
                        } else {
                            Log.d(TAG, "WATCH_HISTORY缓存已过期")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "WATCH_HISTORY反序列化失败: ${e.message}", e)
                        // 清除无效缓存
                        prefs.edit()
                            .remove(key.keyName)
                            .remove("${key.keyName}_time")
                            .apply()
                    }
                    return@withContext defaultValue
                }
                
                // 处理其他类型
                val data: T = when {
                    // 处理原始类型
                    defaultValue is Long? -> {
                        Log.d(TAG, "使用原始类型反序列化 | 键：${key.name} | 类型：Long")
                        Gson().fromJson(json, Long::class.java) as T
                    }
                    defaultValue is Int? -> {
                        Log.d(TAG, "使用原始类型反序列化 | 键：${key.name} | 类型：Int")
                        Gson().fromJson(json, Int::class.java) as T
                    }
                    defaultValue is Boolean? -> {
                        Log.d(TAG, "使用原始类型反序列化 | 键：${key.name} | 类型：Boolean")
                        Gson().fromJson(json, Boolean::class.java) as T
                    }
                    defaultValue is String? -> {
                        Log.d(TAG, "使用原始类型反序列化 | 键：${key.name} | 类型：String")
                        Gson().fromJson(json, String::class.java) as T
                    }
                    // 其他复杂类型，使用 TypeToken 处理泛型
                    else -> {
                        Log.d(TAG, "使用泛型反序列化 | 键：${key.name}")
                        val type = object : TypeToken<T>() {}.type
                        Gson().fromJson(json, type)
                    }
                }
                
                // 检查数据是否有效
                val config = cacheConfigs[key] ?: SupabaseCacheConfig.DEFAULT
                val strategy = config.toStrategy()
                val entry = SupabaseCacheEntry(
                    data = data,
                    createTime = createTime,
                    strategy = strategy
                )
                
                if (entry.isValid()) {
                    // 更新内存缓存
                    synchronized(memoryCache) {
                        memoryCache[cacheKey] = entry as SupabaseCacheEntry<Any>
                    }
                    Log.d(TAG, "从持久化存储获取数据 | 键：${key.name} | 创建时间：${entry.getFormattedCreateTime()}")
                    return@withContext data
                } else {
                    Log.d(TAG, "持久化缓存已过期 | 键：${key.name} | 创建时间：${SupabaseCacheEntry.formatBeijingTime(createTime)}")
                }
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "解析缓存数据失败 | 键：${key.name}", e)
            } catch (e: ClassCastException) {
                Log.e(TAG, "缓存数据类型不匹配 | 键：${key.name} | 错误：${e.message}", e)
                // 清除无效缓存
                prefs.edit()
                    .remove(key.keyName)
                    .remove("${key.keyName}_time")
                    .apply()
            }
        }
        
        Log.d(TAG, "缓存不存在或已过期 | 键：${key.name}")
        return@withContext defaultValue
    }
    
    /**
     * 观察缓存数据变化
     * @param context 应用上下文
     * @param key 缓存键
     * @return 数据流
     */
    fun <T : Any> observeCache(context: Context, key: SupabaseCacheKey): Flow<T?> {
        val cacheKey = getCacheKey(key)
        
        // 如果数据流不存在，创建一个新的
        if (!cacheFlows.containsKey(cacheKey)) {
            cacheFlows[cacheKey] = MutableStateFlow<Any?>(null)
            
            // 异步加载初始数据
            GlobalScope.launch(Dispatchers.IO) {
                val data = getCache<Any>(context, key)
                updateCacheFlow(cacheKey, data)
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return cacheFlows[cacheKey]!!.map { it as? T }
    }
    
    /**
     * 清除指定缓存
     * @param context 应用上下文
     * @param key 缓存键
     */
    suspend fun clearCache(context: Context, key: SupabaseCacheKey) = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(key)
        
        // 清除内存缓存
        synchronized(memoryCache) {
            memoryCache.remove(cacheKey)
        }
        
        // 清除最后访问时间
        lastAccessTime.remove(cacheKey)
        
        // 清除预热状态
        preheatedKeys.remove(cacheKey)
        
        // 清除持久化存储
        val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(key.keyName)
            .remove("${key.keyName}_time")
            .apply()
        
        // 更新数据流
        updateCacheFlow(cacheKey, null)
        
        Log.d(TAG, "缓存已清除 | 键：${key.name}")
    }
    
    /**
     * 清除所有缓存
     * @param context 应用上下文
     */
    suspend fun clearAllCaches(context: Context) = withContext(Dispatchers.IO) {
        // 清除所有内存缓存
        synchronized(memoryCache) {
            memoryCache.clear()
        }
        
        // 清除所有最后访问时间
        lastAccessTime.clear()
        
        // 清除所有预热状态
        preheatedKeys.clear()
        
        // 清除所有持久化存储
        val processedPrefs = mutableSetOf<String>()
        
        SupabaseCacheKey.values().forEach { key ->
            // 避免重复清除同一个SharedPreferences
            if (!processedPrefs.contains(key.prefsName)) {
                val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                processedPrefs.add(key.prefsName)
            }
        }
        
        // 更新所有数据流
        cacheFlows.keys.forEach { key ->
            updateCacheFlow(key, null)
        }
        
        Log.d(TAG, "所有缓存已清除")
    }
    
    /**
     * 清除所有用户相关的缓存
     * @param context 应用上下文
     */
    suspend fun clearUserCaches(context: Context) = withContext(Dispatchers.IO) {
        SupabaseCacheKey.getUserRelatedKeys().forEach { key ->
            clearCache(context, key)
        }
        Log.d(TAG, "所有用户相关缓存已清除")
    }
    
    /**
     * 检查缓存是否有效
     * @param context 应用上下文
     * @param key 缓存键
     * @return 如果缓存有效则返回true，否则返回false
     */
    suspend fun isValid(context: Context, key: SupabaseCacheKey): Boolean = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(key)
        
        // 首先检查内存缓存
        val memoryEntry = synchronized(memoryCache) { memoryCache[cacheKey] }
        if (memoryEntry != null) {
            return@withContext memoryEntry.isValid()
        }
        
        // 如果内存缓存不存在，检查持久化存储
        val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
        val createTime = prefs.getLong("${key.keyName}_time", 0)
        
        if (createTime > 0) {
            val config = cacheConfigs[key] ?: SupabaseCacheConfig.DEFAULT
            val strategy = config.toStrategy()
            val entry = SupabaseCacheEntry(
                data = Any(),
                createTime = createTime,
                strategy = strategy
            )
            return@withContext entry.isValid()
        }
        
        return@withContext false
    }
    
    /**
     * 根据用户类型获取缓存策略
     * @param userData 用户数据
     * @return 适合该用户的缓存策略
     */
    fun getUserCacheStrategy(userData: SupabaseUserDataIptv?): SupabaseCacheStrategy {
        if (userData == null) return SupabaseCacheStrategy.DEFAULT
        
        // 根据用户类型和VIP有效期返回不同的缓存策略
        if (userData.is_vip) {
            val vipEndDate = parseVipEndDate(userData.vipend)
            val daysRemaining = calculateDaysRemaining(vipEndDate)
            
            return when {
                daysRemaining > 30 -> SupabaseCacheStrategy.TimeStrategy(30 * 24 * 60 * 60 * 1000L) // 30天
                daysRemaining > 7 -> SupabaseCacheStrategy.TimeStrategy(2 * 24 * 60 * 60 * 1000L)   // 2天
                daysRemaining > 2 -> SupabaseCacheStrategy.TimeStrategy(8 * 60 * 60 * 1000L)        // 8小时
                else -> SupabaseCacheStrategy.TimeStrategy(4 * 60 * 60 * 1000L)                     // 4小时
            }
        }
        
        // 普通用户不自动刷新
        return SupabaseCacheStrategy.DEFAULT
    }
    
    /**
     * 解析VIP结束日期
     * @param vipEndStr VIP结束日期字符串
     * @return 结束日期的时间戳，如果解析失败则返回0
     */
    private fun parseVipEndDate(vipEndStr: String?): Long {
        if (vipEndStr.isNullOrEmpty()) return 0
        
        return try {
            // 尝试解析日期格式，例如："2023-12-31"
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            format.parse(vipEndStr)?.time ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "解析VIP结束日期失败: $vipEndStr", e)
            0
        }
    }
    
    /**
     * 计算剩余天数
     * @param vipEndTimestamp VIP结束时间戳
     * @return 剩余天数，如果时间戳无效则返回0
     */
    private fun calculateDaysRemaining(vipEndTimestamp: Long): Int {
        if (vipEndTimestamp <= 0) return 0
        
        val currentTime = System.currentTimeMillis()
        val timeRemaining = vipEndTimestamp - currentTime
        
        if (timeRemaining <= 0) return 0
        
        // 转换为天数
        return (timeRemaining / (24 * 60 * 60 * 1000)).toInt()
    }
    
    /**
     * 获取完整的缓存键
     * @param key 缓存键枚举
     * @return 完整的缓存键字符串
     */
    private fun getCacheKey(key: SupabaseCacheKey): String {
        return "${key.prefsName}_${key.keyName}"
    }
    
    /**
     * 更新缓存数据流
     * @param cacheKey 缓存键
     * @param data 新数据
     */
    private fun updateCacheFlow(cacheKey: String, data: Any?) {
        val flow = cacheFlows.getOrPut(cacheKey) { MutableStateFlow<Any?>(null) }
        flow.tryEmit(data)
    }

    /**
     * 清理过期的内存缓存
     * 这是一个内部方法，由调度器定期调用
     */
    private fun cleanExpiredCache() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<String>()
        
        synchronized(memoryCache) {
            for ((key, entry) in memoryCache) {
                // 检查缓存是否过期
                if (!entry.isValid()) {
                    keysToRemove.add(key)
                    continue
                }
                
                // 检查是否30分钟未访问（低频访问缓存）
                val lastAccess = lastAccessTime[key] ?: 0
                if (now - lastAccess > 30 * 60 * 1000) {
                    keysToRemove.add(key)
                }
            }
            
            // 移除过期或长时间未访问的缓存
            keysToRemove.forEach { key ->
                memoryCache.remove(key)
                Log.d(TAG, "缓存自动清理 | 键：$key")
            }
        }
    }
    
    /**
     * 预热缓存
     * 在应用启动时调用此方法，预加载常用的缓存数据到内存中
     * @param context 应用上下文
     */
    suspend fun preheatCache(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始预热缓存...")
        
        try {
            // 添加超时保护
            val result = withTimeoutOrNull(5000) { // 5秒超时
                // 使用安全的方式遍历预热键
                val safeKeys = preheatingKeys.toList() // 创建副本避免并发修改
                
                for (key in safeKeys) {
                    val cacheKey = getCacheKey(key)
                    
                    // 跳过已预热的缓存
                    if (preheatedKeys.contains(cacheKey)) {
                        continue
                    }
                    
                    try {
                        // 尝试从持久化存储加载数据到内存
                        val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
                        val json = prefs.getString(key.keyName, null)
                        val createTime = prefs.getLong("${key.keyName}_time", 0)
                        
                        if (json != null) {
                            try {
                                val data = Gson().fromJson(json, Any::class.java)
                                
                                // 检查数据是否有效
                                val config = cacheConfigs[key] ?: SupabaseCacheConfig.DEFAULT
                                val strategy = config.toStrategy()
                                val entry = SupabaseCacheEntry(
                                    data = data,
                                    createTime = createTime,
                                    strategy = strategy
                                )
                                
                                if (entry.isValid()) {
                                    // 加载到内存缓存
                                    synchronized(memoryCache) {
                                        memoryCache[cacheKey] = entry
                                    }
                                    lastAccessTime[cacheKey] = System.currentTimeMillis()
                                    
                                    // 更新数据流
                                    updateCacheFlow(cacheKey, data)
                                    
                                    // 标记为已预热
                                    preheatedKeys.add(cacheKey)
                                    
                                    Log.d(TAG, "缓存预热成功 | 键：${key.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "缓存预热解析失败 | 键：${key.name}", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "缓存预热异常 | 键：${key.name}", e)
                    }
                    
                    // 添加短暂延迟，避免CPU占用过高
                    kotlinx.coroutines.delay(50)
                }
                
                true // 成功完成
            }
            
            if (result == null) {
                Log.w(TAG, "缓存预热超时，强制结束")
            }
            
            Log.d(TAG, "缓存预热完成 | 共预热 ${preheatedKeys.size} 个缓存")
        } catch (e: Exception) {
            // 捕获所有异常，确保不会导致应用崩溃
            Log.e(TAG, "缓存预热过程中发生异常", e)
        }
    }
    
    /**
     * 预热特定用户的缓存
     * 当用户登录后调用此方法，预加载用户相关的缓存数据
     * @param context 应用上下文
     * @param userId 用户ID
     */
    suspend fun preheatUserCache(context: Context, userId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始预热用户缓存 | 用户ID：$userId")
        
        val userKeys = SupabaseCacheKey.getUserRelatedKeys()
        for (key in userKeys) {
            try {
                getCache<Any>(context, key) // 触发加载到内存缓存
            } catch (e: Exception) {
                Log.e(TAG, "用户缓存预热失败 | 键：${key.name}", e)
            }
        }
        
        Log.d(TAG, "用户缓存预热完成")
    }
} 