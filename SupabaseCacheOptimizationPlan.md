# OneTV Supabase 缓存优化方案

## 当前缓存机制分析

### SupabaseSessionManager 缓存逻辑
1. 使用多个 SharedPreferences 存储不同类型的数据：
   - `supabase_user`：存储会话信息
   - `supabase_user_cache`：存储用户数据和最后加载时间
2. 缓存策略：
   - 普通用户不自动刷新
   - VIP用户30天自动刷新一次
3. 提供了完整的缓存管理功能：保存、获取、清除

### SupabaseServiceInfoManager 缓存逻辑
1. 使用单独的 SharedPreferences (`supabase_service_cache`) 存储服务信息
2. 缓存策略：
   - 基准缓存期为3天，带有±12小时随机偏移
   - 根据服务器时间戳判断是否有更新
3. 缓存内容包括：服务信息内容、上次获取时间、服务器最后更新时间

## 存在的问题

1. **缓存机制分散**：两个管理器各自实现缓存逻辑，没有统一的缓存策略
2. **代码重复**：相似的缓存读写逻辑在不同文件中重复出现
3. **缓存一致性**：缺乏统一的缓存过期和刷新机制
4. **扩展性不足**：新增需要缓存的数据时，需要重复实现类似逻辑
5. **异常处理不统一**：各自处理缓存异常，缺乏统一的错误处理策略

## 优化方案

### 一、设计统一缓存管理器 SupabaseCacheManager

创建统一的缓存管理器，负责所有 Supabase 相关数据的缓存处理，包括用户会话、用户数据和服务信息等。

### 二、缓存结构设计

1. **分层缓存设计**：
   - **内存缓存层**：提供高速访问，减少磁盘IO
   - **持久化缓存层**：基于 SharedPreferences 或 Room 数据库

2. **缓存分类**：
   - **认证缓存**：会话令牌、刷新令牌
   - **用户数据缓存**：用户信息、权限、配置
   - **服务信息缓存**：公告、配置、更新信息
   - **应用配置缓存**：全局设置、界面配置

3. **缓存策略配置**：
   - 为不同类型数据设置不同的缓存策略
   - 支持基于时间、版本和条件的缓存失效机制

### 三、核心功能设计

1. **统一接口**：
   ```kotlin
   // 保存缓存
   suspend fun <T> saveCache(key: CacheKey, data: T, strategy: CacheStrategy)
   
   // 读取缓存
   suspend fun <T> getCache(key: CacheKey, defaultValue: T? = null): T?
   
   // 检查缓存是否有效
   suspend fun isValid(key: CacheKey): Boolean
   
   // 清除缓存
   suspend fun clearCache(key: CacheKey)
   ```

2. **缓存策略**：
   - **时间策略**：基于固定时间或带随机偏移的时间
   - **版本策略**：基于数据版本号或时间戳
   - **条件策略**：基于用户类型、网络状态等条件
   - **混合策略**：组合多种策略

3. **事件通知**：
   - 缓存更新通知
   - 缓存失效通知
   - 缓存错误通知

### 四、具体实现方案

1. **基础缓存管理器**：
   ```kotlin
   object SupabaseCacheManager {
       // 内存缓存
       private val memoryCache = ConcurrentHashMap<String, CacheEntry<Any>>()
       
       // 缓存配置
       private val cacheConfigs = mapOf(
           CacheType.SESSION to CacheConfig(expireTime = 7 * 24 * 60 * 60 * 1000L),  // 7天
           CacheType.USER_DATA to CacheConfig(expireTime = 30 * 24 * 60 * 60 * 1000L), // 30天
           CacheType.SERVICE_INFO to CacheConfig(expireTime = 3 * 24 * 60 * 60 * 1000L, randomOffset = 12 * 60 * 60 * 1000L) // 3天±12小时
       )
       
       // 核心方法实现...
   }
   ```

2. **缓存键设计**：
   ```kotlin
   sealed class CacheKey(val key: String) {
       object Session : CacheKey("supabase_session")
       object UserData : CacheKey("supabase_user_data")
       object ServiceInfo : CacheKey("supabase_service_info")
       // 可扩展其他缓存键...
   }
   ```

3. **缓存条目设计**：
   ```kotlin
   data class CacheEntry<T>(
       val data: T,
       val timestamp: Long = System.currentTimeMillis(),
       val serverTimestamp: Long = 0L,
       val metadata: Map<String, Any> = emptyMap()
   )
   ```

### 五、迁移与适配方案

1. **现有代码适配**：
   - 为 SupabaseSessionManager 和 SupabaseServiceInfoManager 提供适配层
   - 保持现有接口不变，内部实现调用统一缓存管理器

2. **数据迁移**：
   - 首次使用统一缓存管理器时，迁移现有缓存数据
   - 提供向后兼容的数据访问方法

3. **渐进式迁移**：
   - 第一阶段：引入统一缓存管理器，但保留原有实现
   - 第二阶段：将现有管理器改为调用统一缓存管理器
   - 第三阶段：完全迁移到新的缓存架构

### 六、优化效果预期

1. **代码质量提升**：
   - 减少重复代码约 40%
   - 提高代码可维护性和可测试性

2. **性能提升**：
   - 内存缓存减少约 30% 的磁盘 IO
   - 统一的缓存策略减少不必要的网络请求

3. **用户体验改善**：
   - 更一致的数据刷新体验
   - 减少加载等待时间

4. **开发效率提升**：
   - 新功能开发时可直接使用统一缓存机制
   - 减少缓存相关 bug 的发生

### 七、实施路线图

1. **阶段一：设计与准备（1-2天）**
   - 详细设计统一缓存管理器架构
   - 确定缓存策略和接口规范

2. **阶段二：核心实现（2-3天）**
   - 实现基础缓存管理器
   - 实现缓存策略和缓存条目

3. **阶段三：适配与迁移（2-3天）**
   - 为现有管理器提供适配层
   - 实现数据迁移功能

4. **阶段四：测试与优化（1-2天）**
   - 单元测试和集成测试
   - 性能测试和优化

5. **阶段五：完全迁移（1-2天）**
   - 将所有缓存操作迁移到统一缓存管理器
   - 移除冗余代码

## 总结

通过实施统一缓存管理机制，可以显著提升项目的代码质量、性能和可维护性。该方案在保留现有业务逻辑的前提下，通过分层设计和统一接口，解决了当前缓存机制分散、重复和不一致的问题，为未来功能扩展提供了更好的基础架构支持。

## 代码示例

### SupabaseCacheManager 核心实现示例

```kotlin
/**
 * Supabase 统一缓存管理器
 * 负责管理所有 Supabase 相关数据的缓存处理
 */
object SupabaseCacheManager {
    private const val TAG = "SupabaseCacheManager"
    
    // 内存缓存
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<Any>>()
    
    /**
     * 保存缓存数据
     * @param context 应用上下文
     * @param key 缓存键
     * @param data 要缓存的数据
     * @param strategy 缓存策略
     */
    suspend fun <T : Any> saveCache(
        context: Context,
        key: CacheKey,
        data: T,
        strategy: CacheStrategy = CacheStrategy.DEFAULT
    ) = withContext(Dispatchers.IO) {
        try {
            // 保存到内存缓存
            val entry = CacheEntry(
                data = data,
                timestamp = System.currentTimeMillis(),
                strategy = strategy
            )
            memoryCache[key.key] = entry as CacheEntry<Any>
            
            // 保存到持久化存储
            val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
            val json = Gson().toJson(data)
            prefs.edit()
                .putString("${key.key}_data", json)
                .putLong("${key.key}_timestamp", entry.timestamp)
                .putString("${key.key}_strategy", Gson().toJson(strategy))
                .commit()
            
            Log.d(TAG, "缓存保存成功: $key")
        } catch (e: Exception) {
            Log.e(TAG, "缓存保存失败: $key", e)
            throw CacheException("保存缓存失败", e)
        }
    }
    
    /**
     * 获取缓存数据
     * @param context 应用上下文
     * @param key 缓存键
     * @param defaultValue 默认值
     * @return 缓存数据或默认值
     */
    suspend fun <T : Any> getCache(
        context: Context,
        key: CacheKey,
        defaultValue: T? = null
    ): T? = withContext(Dispatchers.IO) {
        try {
            // 先从内存缓存获取
            @Suppress("UNCHECKED_CAST")
            val memoryEntry = memoryCache[key.key] as? CacheEntry<T>
            if (memoryEntry != null && !isCacheExpired(memoryEntry)) {
                Log.d(TAG, "从内存缓存获取: $key")
                return@withContext memoryEntry.data
            }
            
            // 从持久化存储获取
            val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
            val json = prefs.getString("${key.key}_data", null) ?: return@withContext defaultValue
            val timestamp = prefs.getLong("${key.key}_timestamp", 0)
            val strategyJson = prefs.getString("${key.key}_strategy", null)
            val strategy = strategyJson?.let { 
                Gson().fromJson(it, CacheStrategy::class.java) 
            } ?: CacheStrategy.DEFAULT
            
            // 创建缓存条目
            val entry = CacheEntry(
                data = Gson().fromJson(json, getTypeForKey<T>(key)),
                timestamp = timestamp,
                strategy = strategy
            )
            
            // 检查缓存是否过期
            if (isCacheExpired(entry)) {
                Log.d(TAG, "缓存已过期: $key")
                return@withContext defaultValue
            }
            
            // 更新内存缓存
            memoryCache[key.key] = entry as CacheEntry<Any>
            
            Log.d(TAG, "从持久化存储获取: $key")
            return@withContext entry.data
        } catch (e: Exception) {
            Log.e(TAG, "获取缓存失败: $key", e)
            return@withContext defaultValue
        }
    }
    
    /**
     * 检查缓存是否过期
     * @param entry 缓存条目
     * @return 是否过期
     */
    private fun isCacheExpired(entry: CacheEntry<*>): Boolean {
        val currentTime = System.currentTimeMillis()
        val expirationTime = when (val strategy = entry.strategy) {
            is CacheStrategy.TimeStrategy -> {
                val randomOffset = if (strategy.randomOffsetMs > 0) {
                    Random.nextLong(-strategy.randomOffsetMs, strategy.randomOffsetMs)
                } else 0
                entry.timestamp + strategy.expirationMs + randomOffset
            }
            is CacheStrategy.VersionStrategy -> {
                // 版本策略依赖于外部提供的版本比较
                return false
            }
            is CacheStrategy.ConditionalStrategy -> {
                // 条件策略依赖于外部提供的条件判断
                return strategy.condition()
            }
            else -> Long.MAX_VALUE
        }
        return currentTime > expirationTime
    }
    
    /**
     * 清除指定键的缓存
     * @param context 应用上下文
     * @param key 缓存键
     */
    suspend fun clearCache(context: Context, key: CacheKey) = withContext(Dispatchers.IO) {
        try {
            // 清除内存缓存
            memoryCache.remove(key.key)
            
            // 清除持久化存储
            val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("${key.key}_data")
                .remove("${key.key}_timestamp")
                .remove("${key.key}_strategy")
                .apply()
            
            Log.d(TAG, "缓存已清除: $key")
        } catch (e: Exception) {
            Log.e(TAG, "清除缓存失败: $key", e)
        }
    }
    
    /**
     * 清除所有缓存
     * @param context 应用上下文
     */
    suspend fun clearAllCaches(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 清除内存缓存
            memoryCache.clear()
            
            // 清除所有持久化存储
            CacheKey.values().forEach { key ->
                val prefs = context.getSharedPreferences(key.prefsName, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            }
            
            Log.d(TAG, "所有缓存已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除所有缓存失败", e)
        }
    }
    
    /**
     * 获取指定键的类型
     * @param key 缓存键
     * @return 类型
     */
    private inline fun <reified T> getTypeForKey(key: CacheKey): Type {
        return object : TypeToken<T>() {}.type
    }
}

/**
 * 缓存键
 * @param key 键名
 * @param prefsName SharedPreferences 名称
 */
enum class CacheKey(val key: String, val prefsName: String) {
    SESSION("session", "supabase_cache"),
    USER_DATA("user_data", "supabase_cache"),
    SERVICE_INFO("service_info", "supabase_cache")
}

/**
 * 缓存条目
 * @param data 数据
 * @param timestamp 时间戳
 * @param strategy 缓存策略
 */
data class CacheEntry<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis(),
    val strategy: CacheStrategy = CacheStrategy.DEFAULT
)

/**
 * 缓存策略
 */
sealed class CacheStrategy {
    /**
     * 默认策略（永不过期）
     */
    object DEFAULT : CacheStrategy()
    
    /**
     * 时间策略
     * @param expirationMs 过期时间（毫秒）
     * @param randomOffsetMs 随机偏移（毫秒）
     */
    data class TimeStrategy(
        val expirationMs: Long,
        val randomOffsetMs: Long = 0
    ) : CacheStrategy()
    
    /**
     * 版本策略
     * @param version 版本号
     */
    data class VersionStrategy(
        val version: String
    ) : CacheStrategy()
    
    /**
     * 条件策略
     * @param condition 条件函数
     */
    data class ConditionalStrategy(
        val condition: () -> Boolean
    ) : CacheStrategy()
}

/**
 * 缓存异常
 */
class CacheException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

### 适配现有管理器示例

```kotlin
/**
 * SupabaseSessionManager 适配层
 */
object SupabaseSessionManagerAdapter {
    private const val TAG = "SessionManagerAdapter"
    
    /**
     * 获取当前会话ID
     * @param context 应用上下文
     * @return 会话ID，如果未登录则返回null
     */
    suspend fun getSession(context: Context): String? {
        // 首先尝试从Supabase Client获取会话
        val session = SupabaseClient.client.auth.currentSessionOrNull()?.accessToken
        if (session != null) {
            return session
        }
        
        // 如果Supabase客户端没有会话，尝试从缓存获取
        return SupabaseCacheManager.getCache(
            context = context,
            key = CacheKey.SESSION,
            defaultValue = null
        )
    }
    
    /**
     * 保存会话到缓存
     * @param context 应用上下文
     * @param sessionToken 会话令牌
     */
    suspend fun saveSession(context: Context, sessionToken: String) {
        SupabaseCacheManager.saveCache(
            context = context,
            key = CacheKey.SESSION,
            data = sessionToken,
            strategy = CacheStrategy.TimeStrategy(
                expirationMs = 7 * 24 * 60 * 60 * 1000L // 7天
            )
        )
        Log.d(TAG, "会话已保存到缓存")
    }
    
    // 其他方法适配...
}
```

## 下一步行动计划

1. 创建 `SupabaseCacheManager` 核心类
2. 定义缓存策略和缓存键
3. 实现内存缓存和持久化缓存层
4. 为现有管理器创建适配层
5. 编写单元测试和集成测试
6. 进行性能测试和优化
7. 完成迁移和部署 