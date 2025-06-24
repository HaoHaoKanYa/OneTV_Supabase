# OneTV Supabase缓存优化实现计划

## 项目背景

为了区分与旧版本 Cloudflare 的区域，并更好地维护新版本 Supabase，需要优化和统一 Supabase 相关的缓存管理机制。当前的缓存实现存在分散、重复和不一致的问题，需要通过统一的缓存管理方案来提升代码质量、性能和可维护性。

## 目录结构规范

为保持项目结构清晰，新创建的文件需遵循以下规范：

1. **Core 模块**：

   - 路径：`package top.cywin.onetv.core.data.repositories.supabase`
   - 文件名前缀：`Supabase`
2. **TV 模块**：

   - 路径：`package top.cywin.onetv.tv.supabase`
   - 文件名前缀：`Supabase`

## 已分析的核心文件

### 1. SupabaseSessionManager.kt

- **当前状态**：使用独立的 SharedPreferences 管理用户会话和数据
- **缓存策略**：VIP用户30天刷新一次，普通用户不自动刷新
- **主要问题**：缺乏内存缓存层，异步处理不完善

### 2. SettingsCategoryUser.kt

- **当前状态**：直接调用 SupabaseSessionManager 获取缓存数据
- **缓存使用**：实现多层次缓存刷新机制（首次加载、定时检查、手动刷新）
- **主要问题**：UI层包含过多缓存逻辑，缺乏响应式数据流

### 3. MainViewModel.kt

- **当前状态**：负责用户数据刷新、缓存清理和会话管理
- **关键功能**：
  - `forceRefreshUserData()`: 强制刷新用户数据并更新缓存
  - `clearAllCache()`: 清理所有缓存，包括用户数据、EPG和频道信息
  - `logout()`: 退出登录并清除所有缓存
- **主要问题**：
  - 缓存清理逻辑分散在多个方法中
  - 缺乏统一的缓存过期检查机制
  - 直接依赖 SupabaseSessionManager 而非统一缓存接口

## 优化方案

### 一、统一缓存管理器设计

创建 `SupabaseCacheManager` 作为核心缓存管理器，负责所有 Supabase 相关数据的缓存处理。

#### 核心功能：

1. 提供缓存的保存、读取、清除等基本操作
2. 支持内存缓存和持久化缓存两层存储
3. 实现缓存过期检查机制
4. 支持不同用户类型的差异化缓存策略

### 二、分层缓存结构设计

1. **内存缓存层**：

   - 使用 ConcurrentHashMap 实现高速内存缓存
   - 减少磁盘 IO 操作，提升访问性能
2. **持久化缓存层**：

   - 基于 SharedPreferences 实现持久化存储
   - 统一命名和访问方式
3. **缓存键设计**：

   - 使用枚举类型定义缓存键，避免硬编码
   - 关联缓存键与 SharedPreferences 名称

### 三、用户数据缓存策略

根据用户账号权限执行不同的缓存策略：

1. **VIP 用户**：

   - 有效期在 30 天以上：自动从服务器加载数据，保持 30 天一次
   - 有效期在 7-30 天：自动从服务器加载数据，保持 2 天一次
   - 有效期在 2-7 天：自动从服务器加载数据，保持 8 小时一次
   - 监测到 VIP 用户数据变动时立即刷新
2. **普通注册用户**：

   - 不进行被动刷新
   - 监测到用户数据变动时立即刷新
3. **游客用户**：

   - 不进行被动刷新

## 具体实现方案

### 1. 核心缓存管理器

```kotlin
/**
 * Supabase 统一缓存管理器
 * 负责管理所有 Supabase 相关数据的缓存处理
 */
object SupabaseCacheManager {
    private const val TAG = "SupabaseCacheManager"
  
    // 内存缓存
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<Any>>()
  
    // 缓存配置
    private val cacheConfigs = mapOf(
        CacheKey.SESSION to CacheConfig(expireTime = 7 * 24 * 60 * 60 * 1000L),  // 7天
        CacheKey.USER_DATA to CacheConfig(expireTime = 30 * 24 * 60 * 60 * 1000L), // 30天
        CacheKey.SERVICE_INFO to CacheConfig(expireTime = 3 * 24 * 60 * 60 * 1000L, randomOffset = 12 * 60 * 60 * 1000L) // 3天±12小时
    )
  
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
        // 实现保存逻辑
    }
  
    // 其他核心方法...
  
    /**
     * 根据用户类型获取缓存策略
     */
    fun getUserCacheStrategy(userData: SupabaseUserDataIptv?): CacheStrategy {
        if (userData == null) return CacheStrategy.DEFAULT
      
        // 根据用户类型和VIP有效期返回不同的缓存策略
        if (userData.is_vip) {
            val vipEndDate = parseVipEndDate(userData.vipend)
            val daysRemaining = calculateDaysRemaining(vipEndDate)
          
            return when {
                daysRemaining > 30 -> CacheStrategy.TimeStrategy(30 * 24 * 60 * 60 * 1000L) // 30天
                daysRemaining > 7 -> CacheStrategy.TimeStrategy(2 * 24 * 60 * 60 * 1000L)   // 2天
                daysRemaining > 2 -> CacheStrategy.TimeStrategy(8 * 60 * 60 * 1000L)        // 8小时
                else -> CacheStrategy.TimeStrategy(4 * 60 * 60 * 1000L)                     // 4小时
            }
        }
      
        // 普通用户不自动刷新
        return CacheStrategy.DEFAULT
    }
}
```

### 2. 适配计划

#### 1) SupabaseSessionManager 适配

```kotlin
/**
 * SupabaseSessionManager 适配层
 */
object SupabaseSessionManager {
    // 使用统一缓存管理器替代直接的 SharedPreferences 操作
    suspend fun getSession(context: Context): String? {
        return SupabaseCacheManager.getCache(
            context = context,
            key = CacheKey.SESSION,
            defaultValue = null
        )
    }
  
    // 其他方法适配...
}
```

#### 2) SettingsCategoryUser 适配

```kotlin
/**
 * SettingsCategoryUser 适配
 */
@Composable
fun SettingsCategoryUser(
    onNavigateToLogin: () -> Unit,
    // 其他参数...
) {
    // 使用 Flow 获取会话信息
    val sessionFlow = remember { 
        SupabaseCacheManager.observeCache<String>(context, CacheKey.SESSION) 
    }
    val session by sessionFlow.collectAsState(initial = null)
  
    // 使用 Flow 获取用户数据
    val userDataFlow = remember { 
        SupabaseCacheManager.observeCache<SupabaseUserDataIptv>(context, CacheKey.USER_DATA) 
    }
    val userData by userDataFlow.collectAsState(initial = null)
  
    // 其余UI逻辑...
}
```

#### 3) MainViewModel 适配

```kotlin
/**
 * MainViewModel 适配
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
  
    // 退出登录
    fun logout() {
        viewModelScope.launch {
            Log.d("MainViewModel", "开始退出登录流程")
            // 使用统一缓存管理器清除所有缓存
            SupabaseCacheManager.clearAllCaches(appContext)
            Log.d("MainViewModel", "所有缓存已清除")
          
            // 重置仓库
            iptvRepo = GuestIptvRepository(source)
            Log.d("MainViewModel", "已重置为游客仓库")
        }
    }
  
    // 清理缓存
    fun clearAllCache(clearUserCache: Boolean = true, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 清除 TV 设备专用缓存
                val tvCachePath = File(appContext.externalCacheDir?.parent ?: "", "tv_sessions")
                if (tvCachePath.exists()) {
                    tvCachePath.deleteRecursively()
                    Log.d("MainViewModel", "TV专用会话缓存已清除")
                }
              
                if (clearUserCache) {
                    // 使用统一缓存管理器清除所有类型的缓存
                    Log.d("MainViewModel", "开始强制清除所有缓存")
                    SupabaseCacheManager.clearCache(appContext, CacheKey.USER_DATA)
                    SupabaseCacheManager.clearCache(appContext, CacheKey.SESSION)
                    SupabaseCacheManager.clearCache(appContext, CacheKey.SERVICE_INFO)
                    SupabaseCacheManager.clearCache(appContext, CacheKey.ONLINE_USERS)
                  
                    // 清除其他相关缓存
                    EpgList.clearCache()
                    iptvRepo.clearCache()
                    Log.d("MainViewModel", "所有缓存清除完成")
                } else {
                    // 检查缓存是否需要自动清理
                    Log.d("MainViewModel", "开始自动缓存清理检查")
                    val userData = SupabaseCacheManager.getCache<SupabaseUserDataIptv>(appContext, CacheKey.USER_DATA)
                  
                    if (userData?.is_vip == true) {
                        // 检查缓存是否有效
                        if (!SupabaseCacheManager.isValid(appContext, CacheKey.USER_DATA)) {
                            Log.d("MainViewModel", "VIP用户缓存已过期，触发自动清理")
                            SupabaseCacheManager.clearCache(appContext, CacheKey.USER_DATA)
                            EpgList.clearCache()
                            iptvRepo.clearCache()
                        } else {
                            Log.d("MainViewModel", "VIP状态有效，跳过自动清理")
                        }
                    } else {
                        Log.d("MainViewModel", "非VIP用户，无需自动清理缓存")
                    }
                }
              
                // 清除WebView缓存
                clearWebViewCacheAsync()
            }
          
            Log.d("MainViewModel", "所有缓存操作完成")
            onComplete()
            init()
        }
    }
  
    // 强制刷新用户数据
    fun forceRefreshUserData() {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "开始强制刷新用户数据流程...")
                val session = SupabaseCacheManager.getCache<String>(appContext, CacheKey.SESSION)
                Log.d("MainViewModel", "当前会话状态: ${if (session.isNullOrEmpty()) "空/未登录" else "有效会话"}")
              
                if (!session.isNullOrEmpty()) {
                    // 从服务器获取用户数据
                    Log.d("MainViewModel", "正在从服务器获取用户数据...")
                    val newUserData = withContext(Dispatchers.IO) {
                        SupabaseUserRepository().getUserData(session).also {
                            Log.d("MainViewModel", "用户数据获取成功｜用户ID: ${it.userid}｜VIP状态: ${it.is_vip}")
                        }
                    }
                  
                    // 保存到缓存，使用适合该用户的缓存策略
                    SupabaseCacheManager.saveCache(
                        context = appContext,
                        key = CacheKey.USER_DATA,
                        data = newUserData,
                        strategy = SupabaseCacheManager.getUserCacheStrategy(newUserData)
                    )
                    Log.d("MainViewModel", "用户数据已缓存｜VIP=${newUserData.is_vip}｜到期时间=${newUserData.vipend}")
                }
              
                // 重建IPTV仓库
                iptvRepo = if (session.isNullOrEmpty()) {
                    Log.w("MainViewModel", "会话已失效，回退到游客模式")
                    GuestIptvRepository(source)
                } else {
                    IptvRepository(source, session)
                }
              
                // 刷新频道和节目单
                Log.d("MainViewModel", "正在刷新频道数据...")
                refreshChannel()
                Log.d("MainViewModel", "正在刷新节目单数据...")
                refreshEpg()
              
                Log.d("MainViewModel", "强制刷新流程完成")
                _toastMessage.emit("用户数据已刷新")
            } catch (e: Exception) {
                // 处理401错误
                if (e.message?.contains("401") == true) {
                    Log.w("MainViewModel", "检测到会话过期，触发清理")
                    logout()
                    _uiState.value = MainUiState.Error("会话已过期，请重新登录")
                }
              
                Log.e("MainViewModel", "强制刷新失败", e)
                val errorMsg = when {
                    e is java.net.UnknownHostException -> "网络不可用"
                    else -> "错误: ${e.message?.take(20)}..."
                }
                _toastMessage.emit(errorMsg)
            }
        }
    }
}
```

## 实施路线图

### 阶段一：设计与准备（1-2天）

- 详细设计统一缓存管理器架构
- 确定缓存策略和接口规范
- 创建必要的数据类和枚举

### 阶段二：核心实现（2-3天）

- 实现 SupabaseCacheManager 核心功能
- 实现缓存策略和缓存条目
- 编写单元测试验证功能

### 阶段三：适配与迁移（2-3天）

- 为现有管理器提供适配层
- 实现数据迁移功能
- 确保向后兼容性

### 阶段四：测试与优化（1-2天）

- 进行集成测试
- 性能测试和优化
- 修复发现的问题

### 阶段五：完全迁移（1-2天）

- 将所有缓存操作迁移到统一缓存管理器
- 移除冗余代码
- 文档更新和最终测试

## 预期效果

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

## MainViewModel 适配重点

作为应用的核心状态管理组件，MainViewModel 的缓存操作适配尤为重要，重点包括：

1. **退出登录流程**：

   - 使用 SupabaseCacheManager.clearAllCaches 替代多个清理调用
   - 保留现有日志输出，确保调试信息完整
2. **缓存清理机制**：

   - 分离用户数据缓存和应用缓存的清理逻辑
   - 为 VIP 用户实现基于过期时间的自动清理
   - 保留对 WebView 缓存等特殊缓存的清理逻辑
3. **用户数据刷新**：

   - 使用 SupabaseCacheManager 获取和保存用户数据
   - 根据用户类型应用不同的缓存策略
   - 保留现有的错误处理和日志机制
4. **会话管理**：

   - 使用 SupabaseCacheManager 获取会话信息
   - 在会话过期时正确处理 401 错误
   - 确保仓库切换逻辑与现有行为一致

## 总结

通过实施统一缓存管理机制，可以显著提升项目的代码质量、性能和可维护性。该方案在保留现有业务逻辑的前提下，通过分层设计和统一接口，解决了当前缓存机制分散、重复和不一致的问题，为未来功能扩展提供了更好的基础架构支持。

MainViewModel 作为应用的核心状态管理组件，其缓存操作的适配尤为重要。通过将直接的 SupabaseSessionManager 调用替换为统一的 SupabaseCacheManager 接口，可以实现更一致的缓存管理，并简化缓存相关的业务逻辑。
