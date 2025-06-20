# 数据库时区设置为北京时间

本文档描述了如何将 Supabase 数据库的时区设置为北京时间（UTC+8），以及如何在应用程序中使用北京时间视图。

## 时区变更概述

为了使数据库中的所有时间戳显示为北京时间，我们实施了以下更改：

1. 将数据库时区设置为 'Asia/Shanghai'（UTC+8）
2. 创建时间转换函数 `to_beijing_time()`
3. 为所有包含时间戳的表创建北京时间视图
4. 添加辅助函数和触发器以确保时区敏感操作正确处理

## 应用迁移脚本

要应用这些更改，请按照以下步骤操作：

1. 确保您有对数据库的管理员访问权限
2. 在 Supabase 控制台中打开 SQL 编辑器
3. 运行迁移脚本 `migrations/20231231_timezone_migration.sql`

```sql
-- 1. 使用 Supabase SQL 编辑器执行此脚本
-- 2. 确认迁移完成
```

## 在应用程序中使用北京时间视图

### 视图列表

以下是可用的北京时间视图：

| 表名 | 北京时间视图 |
|-----|-------------|
| profiles | profiles_beijing_time |
| user_login_logs | user_login_logs_beijing_time |
| watch_history | watch_history_beijing_time |
| activation_codes | activation_codes_beijing_time |
| vip_transactions | vip_transactions_beijing_time |
| user_sessions | user_sessions_beijing_time |
| service_messages | service_messages_beijing_time |
| online_users_stats | online_users_stats_beijing_time |
| app_configs | app_configs_beijing_time |
| user_settings | user_settings_beijing_time |

### 使用方法

#### 在 SQL 查询中

```sql
-- 示例：查询用户资料（北京时间）
SELECT * FROM profiles_beijing_time WHERE userid = '用户ID';

-- 示例：查询观看历史（北京时间）
SELECT * FROM watch_history_beijing_time 
WHERE user_id = '用户ID' 
ORDER BY watch_start DESC;
```

#### 在 Supabase Edge Functions 中

```javascript
// 示例：获取用户资料（北京时间）
const { data, error } = await supabase
  .from('profiles_beijing_time')
  .select('*')
  .eq('userid', userId)
  .single();
```

#### 在应用程序中

对于移动应用程序和 Web 应用程序，只需将原始表名更改为相应的北京时间视图名称：

```kotlin
// 原来的代码
val response = supabase.from("profiles").select().eq("userid", userId).single()

// 修改后的代码（使用北京时间）
val response = supabase.from("profiles_beijing_time").select().eq("userid", userId).single()
```

## 时区转换函数

如果需要在查询中手动转换时间戳，可以使用以下函数：

```sql
-- 将任何时间戳转换为北京时间
SELECT to_beijing_time(your_timestamp_column) FROM your_table;
```

## 注意事项

1. 视图是只读的，不能直接更新。如果需要更新数据，请使用原始表。
2. 确保所有新的应用程序代码都使用北京时间视图。
3. 如果添加了新的表，请记得创建相应的北京时间视图。 