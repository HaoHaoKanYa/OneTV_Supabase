# OneTV Supabase 缓存兼容性指南

## 1. 概述

本文档提供关于 OneTV Supabase 应用从传统 SharedPreferences 存储方式迁移到统一缓存管理器的兼容性指南。目前项目正在经历缓存结构的重构，本指南将帮助开发者理解迁移过程中的兼容性问题及其解决方案。

## 2. 迁移背景

### 2.1 旧存储系统

旧的缓存存储系统基于以下机制：
- 直接使用 SharedPreferences 存储
- 每个管理器维护自己的存储逻辑
- 数据格式和键名不统一
- 缺乏类型安全和有效期管理

### 2.2 新缓存管理器

新的统一缓存管理器提供以下优势：
- 统一的 API 接口
- 内存缓存和持久化存储的双重机制
- 基于枚举的缓存键管理
- 灵活的缓存策略
- 协程支持
- 类型安全

## 3. 迁移策略

### 3.1 分阶段迁移

迁移过程采用分阶段策略，主要分为三个阶段：

1. **双重存储阶段**：同时支持新旧两套存储系统，确保兼容性
2. **过渡阶段**：优先使用新存储系统，但保留对旧系统的读取能力
3. **完全迁移阶段**：完全移除旧存储系统支持，只使用新的缓存管理器

### 3.2 迁移时间表

| 组件 | 当前阶段 | 计划完成时间 |
|------|----------|------------|
| SupabaseUserProfileInfoSessionManager | 完全迁移 | 已完成 |
| SupabaseUserSettingsSessionManager | 完全迁移 | 已完成 |
| SupabaseWatchHistorySessionManager | 完全迁移 | 已完成 |
| SupabaseVipManager | 过渡阶段 | 2周内 |
| SupabaseAppConfigsManager | 过渡阶段 | 2周内 |
| SupabaseChannelFavoritesManager | 双重存储阶段 | 1个月内 |

## 4. WatchHistorySessionManager 完全迁移说明

`SupabaseWatchHistorySessionManager` 已完成向统一缓存管理器的完全迁移。本节提供迁移前后的对比和使用说明。

### 4.1 主要变更

1. **存储机制变更**：
   - 旧版本：使用 SharedPreferences 直接存储，通过用户ID前缀区分不同用户
   - 新版本：使用 `SupabaseCacheManager` 统一管理，使用 `SupabaseCacheKey.WATCH_HISTORY` 作为缓存键

2. **API 变更**：
   - 保留了原有的同步 API
   - 添加了以 `Async` 后缀命名的协程版本 API
   - 移除了 `syncToServer` 和 `syncFromServer` 方法，将在独立模块中重新实现

3. **数据结构**：
   - `WatchHistoryItem` 数据结构保持不变
   - 存储格式从自定义 JSON 转为使用标准序列化

### 4.2 使用新版本注意事项

#### 4.2.1 基本用法

```kotlin
// 初始化管理器
SupabaseWatchHistorySessionManager.initialize(context)

// 记录观看历史（同步方式）
SupabaseWatchHistorySessionManager.recordChannelWatch(
    channelName = "CCTV-1",
    channelUrl = "http://example.com/cctv1.m3u8",
    duration = 600, // 单位：秒
    context = context
)

// 记录观看历史（协程方式）
lifecycleScope.launch {
    SupabaseWatchHistorySessionManager.recordChannelWatchAsync(
        channelName = "CCTV-1",
        channelUrl = "http://example.com/cctv1.m3u8",
        duration = 600, // 单位：秒
        context = context
    )
}

// 清除历史记录
SupabaseWatchHistorySessionManager.clearHistory(context)
```

#### 4.2.2 服务器同步

新版本不再内置服务器同步功能，请使用专用的同步模块：

```kotlin
// 同步到服务器
SupabaseWatchHistorySyncService.syncToServer(context)

// 从服务器同步
SupabaseWatchHistorySyncService.syncFromServer(context)

// 双向同步（带回调）
SupabaseWatchHistorySyncService.syncWithCallback(context, object : SupabaseWatchHistorySyncService.SyncCallback {
    override fun onSyncStarted() { /* 处理同步开始 */ }
    override fun onSyncProgress(progress: Int, total: Int) { /* 处理同步进度 */ }
    override fun onSyncCompleted(successCount: Int) { /* 处理同步完成 */ }
    override fun onSyncFailed(errorMessage: String) { /* 处理同步失败 */ }
})
```

关于同步服务的详细用法，请参考 `SupabaseWatchHistorySyncServiceGuide.md` 文档。

#### 4.2.3 迁移期间的数据处理

由于完全迁移，不再支持旧存储系统，因此：

1. 首次升级后，用户的旧历史记录将无法访问
2. 建议在应用升级提示中告知用户此变化
3. 如果必须保留历史数据，请在应用升级前添加迁移代码

### 4.3 潜在问题及解决方案

#### 4.3.1 数据丢失问题

**问题**：用户升级后可能丢失观看历史记录  
**解决方案**：
- 在应用启动时检测版本变更
- 如果是首次升级到新版，执行一次性迁移代码
- 迁移代码可通过以下方式获取旧数据：

```kotlin
// 一次性迁移代码示例
private fun migrateWatchHistoryIfNeeded(context: Context) {
    val hasPerformedMigration = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean("has_migrated_watch_history", false)
        
    if (!hasPerformedMigration) {
        val userId = SupabaseSessionManager.getCachedUserDataSync(context)?.userid
        val prefName = if (userId != null) {
            "watch_history_prefs_$userId"
        } else {
            "watch_history_prefs"
        }
        
        val oldPrefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val historyJson = oldPrefs.getString("watch_history_data", null)
        
        if (!historyJson.isNullOrBlank()) {
            // 保存到新缓存系统
            kotlinx.coroutines.runBlocking {
                SupabaseCacheManager.saveCache(
                    context, 
                    SupabaseCacheKey.WATCH_HISTORY, 
                    historyJson
                )
            }
            Log.d("Migration", "已迁移观看历史数据")
        }
        
        // 标记已完成迁移
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("has_migrated_watch_history", true)
            .apply()
    }
}
```

#### 4.3.2 性能问题

**问题**：统一缓存管理器引入的序列化可能影响性能  
**解决方案**：
- 减少序列化/反序列化次数
- 使用内存缓存减少磁盘读写
- 避免在主线程执行缓存操作

## 5. 面向开发者的迁移指南

### 5.1 从双重存储迁移到完全迁移

如果您正在将组件从双重存储阶段迁移到完全迁移阶段，请按以下步骤操作：

1. **移除旧存储相关代码**：
   - 删除所有 SharedPreferences 相关常量和方法
   - 移除双重存储的读写逻辑

2. **简化现有方法**：
   - 移除检查旧存储的条件判断
   - 直接使用 SupabaseCacheManager 进行读写操作

3. **更新注释和文档**：
   - 更新类和方法注释
   - 在 CHANGELOG.md 中记录变更

4. **考虑添加一次性迁移代码**：
   - 如果数据连续性很重要，添加一次性迁移逻辑
   - 在应用启动时执行迁移

### 5.2 测试建议

迁移后应进行以下测试：

1. **基本功能测试**：
   - 验证数据正确保存和读取
   - 验证缓存过期和刷新机制

2. **升级测试**：
   - 测试从旧版本升级到新版本
   - 验证现有数据是否正确迁移

3. **边缘情况测试**：
   - 无网络环境
   - 低存储空间
   - 应用被系统强制终止后重启

4. **性能测试**：
   - 大量数据存储和读取性能
   - 内存占用
   - 电池消耗

## 6. 常见问题解答

### 6.1 为什么要进行缓存系统重构？
重构缓存系统旨在解决代码重复、类型安全和可维护性问题。统一的缓存管理器提供了更高的抽象层次，使代码更简洁、更可靠。

### 6.2 迁移会影响用户体验吗？
对于大多数组件，迁移过程对用户是透明的。但对于已完全迁移的组件（如 WatchHistorySessionManager），用户可能会失去历史数据。我们建议在应用更新说明中告知用户。

### 6.3 我应该什么时候完全移除旧存储支持？
建议在以下条件满足时移除旧存储支持：
- 应用已发布至少 2 个版本，使用双重存储阶段
- 用户升级路径已经过充分测试
- 旧版本的用户比例低于 10%

### 6.4 如何处理迁移过程中的崩溃？
在迁移代码中使用 try-catch 捕获异常，确保即使迁移失败，应用也能继续运行。记录异常并上报分析，以便在后续版本中修复。

## 7. 附录

### 7.1 迁移检查清单

使用以下检查清单评估组件是否已准备好完全迁移：

- [ ] 双重存储已在生产环境运行至少 2 个版本
- [ ] 没有收到与双重存储相关的崩溃报告
- [ ] 旧版本用户比例低于 10%
- [ ] 已准备数据迁移策略（如果需要）
- [ ] 已更新所有依赖该组件的代码
- [ ] 已准备回退计划

### 7.2 推荐的缓存键命名约定

为确保缓存键的一致性，请遵循以下命名约定：

- 用全大写下划线分隔的命名方式
- 使用有意义的功能前缀
- 区分不同数据类型的键
- 为时间戳使用 `_LAST_LOADED` 后缀

示例：
```kotlin
enum class SupabaseCacheKey {
    USER_PROFILE,                // 用户资料
    USER_PROFILE_LAST_LOADED,    // 用户资料最后加载时间
    USER_SETTINGS,               // 用户设置
    USER_SETTINGS_LAST_LOADED,   // 用户设置最后加载时间
    WATCH_HISTORY,               // 观看历史
    WATCH_HISTORY_LAST_LOADED,   // 观看历史最后加载时间
}
```

## 5. 新版本注意事项

### 5.1 观看历史同步服务分离

在新版本中，为了更好地分离关注点，观看历史的同步功能已经从 `SupabaseWatchHistorySessionManager` 移动到专用的同步服务类 `SupabaseWatchHistorySyncService` 中。这个类位于 `top.cywin.onetv.tv.supabase.sync` 包中。

使用方法示例：

```kotlin
// 同步到服务器
SupabaseWatchHistorySyncService.syncToServer(context)

// 从服务器同步
SupabaseWatchHistorySyncService.syncFromServer(context)

// 双向同步（带回调）
SupabaseWatchHistorySyncService.syncWithCallback(context, object : SupabaseWatchHistorySyncService.SyncCallback {
    override fun onSyncStarted() { /* 处理同步开始 */ }
    override fun onSyncProgress(progress: Int, total: Int) { /* 处理同步进度 */ }
    override fun onSyncCompleted(successCount: Int) { /* 处理同步完成 */ }
    override fun onSyncFailed(errorMessage: String) { /* 处理同步失败 */ }
})
```

关于同步服务的详细用法，请参考 `SupabaseWatchHistorySyncServiceGuide.md` 文档。 