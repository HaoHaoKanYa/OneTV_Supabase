# OneTV Supabase 缓存管理器实现指南

## 1. 统一缓存管理器简介

我们已经实现了 `SupabaseCacheManager` 作为统一的缓存管理器，用于集中管理所有 Supabase 相关数据的缓存。本文档旨在帮助开发人员快速了解如何使用新的缓存管理器，并提供从旧缓存机制迁移到新缓存机制的指南。

### 1.1 核心优势

- **统一管理**：所有缓存操作通过一个中心化的管理器处理
- **多层缓存**：支持内存缓存和持久化存储两层缓存机制
- **协程支持**：所有操作都支持协程，提供更好的异步处理能力
- **类型安全**：使用泛型方法确保类型安全
- **一致的缓存策略**：针对不同类型的数据提供统一的缓存策略

## 2. 使用指南

### 2.1 基本操作

#### 读取缓存数据

```kotlin
// 协程版本
suspend fun getUserData(context: Context): UserData? {
    return SupabaseCacheManager.getCache(
        context = context,
        key = SupabaseCacheKey.USER_DATA,
        defaultValue = null
    )
}

// 同步版本（内部使用 runBlocking）
fun getUserDataSync(context: Context): UserData? {
    return runBlocking {
        SupabaseCacheManager.getCache(
            context = context,
            key = SupabaseCacheKey.USER_DATA,
            defaultValue = null
        )
    }
}
```

#### 保存缓存数据

```kotlin
// 协程版本
suspend fun saveUserData(context: Context, userData: UserData) {
    SupabaseCacheManager.saveCache(
        context = context,
        key = SupabaseCacheKey.USER_DATA,
        data = userData
    )
}

// 同步版本
fun saveUserDataSync(context: Context, userData: UserData) {
    runBlocking {
        SupabaseCacheManager.saveCache(
            context = context,
            key = SupabaseCacheKey.USER_DATA,
            data = userData
        )
    }
}
```

#### 清除缓存

```kotlin
// 清除特定缓存
suspend fun clearUserData(context: Context) {
    SupabaseCacheManager.clearCache(
        context = context,
        key = SupabaseCacheKey.USER_DATA
    )
}

// 清除所有缓存
suspend fun clearAllCaches(context: Context) {
    SupabaseCacheManager.clearAllCaches(context)
}

// 仅清除用户相关缓存
suspend fun clearUserCaches(context: Context) {
    SupabaseCacheManager.clearUserCaches(context)
}
```

#### 检查缓存有效性

```kotlin
suspend fun isUserDataValid(context: Context): Boolean {
    return SupabaseCacheManager.isValid(
        context = context,
        key = SupabaseCacheKey.USER_DATA
    )
}
```

### 2.2 缓存键说明

所有缓存键都定义在 `SupabaseCacheKey` 枚举类中：

```kotlin
enum class SupabaseCacheKey(val prefsName: String, val keyName: String) {
    // 会话相关
    SESSION("supabase_user", "session"),
    
    // 用户数据相关
    USER_DATA("supabase_user_cache", "cached_user_data"),
    USER_PROFILE("profile_info_cache", "cached_profile_data"),
    USER_SETTINGS("user_settings_cache", "cached_user_settings"),
    
    // 服务信息相关
    SERVICE_INFO("service_info_cache", "cached_service_info"),
    
    // 在线用户相关
    ONLINE_USERS("online_users_cache", "cached_online_users"),
    
    // 观看历史相关
    WATCH_HISTORY("watch_history_cache", "cached_watch_history"),
    
    // 时间戳相关
    LAST_LOADED_TIME("supabase_user_cache", "last_loaded_time"),
    USER_PROFILE_LAST_LOADED("profile_info_cache", "last_loaded_time"),
    USER_SETTINGS_LAST_LOADED("user_settings_cache", "last_loaded_time"),
    SERVICE_INFO_LAST_LOADED("service_info_cache", "last_loaded_time"),
    ONLINE_USERS_LAST_LOADED("online_users_cache", "last_loaded_time"),
    WATCH_HISTORY_LAST_LOADED("watch_history_cache", "last_loaded_time")
}
```

## 3. 迁移指南

### 3.1 迁移步骤

对于现有的缓存管理类，按照以下步骤进行迁移：

1. **导入必要的包**

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
```

2. **替换直接的 SharedPreferences 操作**

旧代码：
```kotlin
private const val PREFS_NAME = "user_settings_cache"
private const val KEY_USER_SETTINGS = "cached_user_settings"

private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

fun saveUserSettings(context: Context, settings: UserSettings) {
    val prefs = getSharedPreferences(context)
    val json = Gson().toJson(settings)
    prefs.edit()
        .putString(KEY_USER_SETTINGS, json)
        .apply()
}
```

新代码：
```kotlin
suspend fun saveUserSettings(context: Context, settings: UserSettings) = withContext(Dispatchers.IO) {
    SupabaseCacheManager.saveCache(
        context = context,
        key = SupabaseCacheKey.USER_SETTINGS,
        data = settings
    )
}

// 提供向后兼容的同步方法
fun saveUserSettingsSync(context: Context, settings: UserSettings) {
    runBlocking { saveUserSettings(context, settings) }
}
```

3. **替换缓存检查机制**

旧代码：
```kotlin
fun isCacheValid(context: Context): Boolean {
    val lastLoaded = getLastLoadedTime(context)
    if (lastLoaded == 0L) return false
    
    val currentTime = System.currentTimeMillis()
    val timeDiff = currentTime - lastLoaded
    
    // 检查时间是否过期
    return timeDiff <= CACHE_VALIDITY_PERIOD
}
```

新代码：
```kotlin
suspend fun isCacheValid(context: Context): Boolean = withContext(Dispatchers.IO) {
    return@withContext SupabaseCacheManager.isValid(
        context = context,
        key = SupabaseCacheKey.USER_SETTINGS
    )
}

// 提供向后兼容的同步方法
fun isCacheValidSync(context: Context): Boolean {
    return runBlocking { isCacheValid(context) }
}
```

4. **替换缓存清理方法**

旧代码：
```kotlin
fun clearCache(context: Context) {
    getSharedPreferences(context).edit().clear().apply()
}
```

新代码：
```kotlin
suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
    SupabaseCacheManager.clearCache(
        context = context,
        key = SupabaseCacheKey.USER_SETTINGS
    )
}

// 提供向后兼容的同步方法
fun clearCacheSync(context: Context) {
    runBlocking { clearCache(context) }
}
```

### 3.2 迁移注意事项

1. **保持兼容性**：为每个协程方法提供同步版本，确保现有代码平滑过渡
2. **错误处理**：在协程方法中合理处理异常，避免应用崩溃
3. **缓存键映射**：确保正确映射旧的缓存键到新的 `SupabaseCacheKey` 枚举值
4. **IO操作**：所有涉及存储的操作都应该使用 `Dispatchers.IO` 上下文

### 3.3 常见迁移问题

#### 1. UI组件调用协程方法

在UI组件（如Composable函数）中调用协程方法时，需要使用适当的协程作用域：

```kotlin
val coroutineScope = rememberCoroutineScope()

Button(onClick = {
    coroutineScope.launch {
        sessionManager.clearCache(context)
    }
}) {
    Text("清除缓存")
}
```

#### 2. ViewModel中使用缓存管理器

在ViewModel中使用缓存管理器时，应使用viewModelScope：

```kotlin
class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    fun loadUserData() {
        viewModelScope.launch {
            val userData = SupabaseCacheManager.getCache<UserData>(
                context = context,
                key = SupabaseCacheKey.USER_DATA
            )
            // 处理数据...
        }
    }
}
```

#### 3. 处理旧版本应用的迁移

如果应用已经发布，且用户设备上存在旧版本的缓存数据，可以实现数据迁移：

```kotlin
suspend fun migrateFromOldCache(context: Context) = withContext(Dispatchers.IO) {
    // 检查是否存在旧缓存
    val oldPrefs = context.getSharedPreferences("old_prefs_name", Context.MODE_PRIVATE)
    val oldData = oldPrefs.getString("old_key", null)
    
    if (!oldData.isNullOrEmpty()) {
        try {
            // 解析旧数据
            val data = Gson().fromJson(oldData, UserData::class.java)
            
            // 保存到新缓存系统
            SupabaseCacheManager.saveCache(
                context = context,
                key = SupabaseCacheKey.USER_DATA,
                data = data
            )
            
            // 清除旧缓存
            oldPrefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e("Migration", "迁移旧缓存失败", e)
        }
    }
}
```

## 4. 下一步计划

### 4.1 短期计划（1-2周）

1. **完成所有缓存管理器的迁移**
   - SupabaseVipManager
   - SupabaseAppConfigsManager
   - SupabaseChannelFavoritesManager

2. **优化缓存性能**
   - 实现更高效的内存缓存淘汰策略
   - 添加缓存预热机制，提高冷启动性能

3. **完善单元测试**
   - 为所有缓存操作编写单元测试
   - 测试边缘情况和错误处理

### 4.2 中期计划（2-4周）

1. **实现缓存监控机制**
   - 添加缓存命中率统计
   - 实现缓存使用情况分析

2. **增强缓存安全性**
   - 为敏感数据添加加密支持
   - 实现安全擦除机制

3. **添加缓存迁移工具**
   - 开发数据迁移助手
   - 提供缓存版本控制机制

### 4.3 长期计划（1-3个月）

1. **引入响应式缓存机制**
   - 使用Flow API提供响应式数据流
   - 实现数据变更通知机制

2. **缓存压缩与优化**
   - 实现大型数据的压缩存储
   - 优化二进制数据的存储方式

3. **分布式缓存策略**
   - 研究多设备间缓存同步
   - 实现差异化同步策略

## 5. 问题反馈与支持

如果在使用统一缓存管理器过程中遇到任何问题，或有任何改进建议，请通过以下方式联系我们：

- 在项目仓库提交Issue
- 发送邮件至开发团队
- 在项目Slack频道讨论

我们将持续完善缓存管理机制，确保它能够满足项目需求，并提供更好的性能和用户体验。 