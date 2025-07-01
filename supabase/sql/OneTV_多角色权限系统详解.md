# OneTV 多角色权限系统详解

## 📋 系统概述

OneTV 多角色权限系统是一个支持用户同时拥有多种角色的权限管理系统。与传统的单角色系统不同，该系统允许一个用户同时拥有多个角色（如：超级管理员 + 管理员 + 客服），提供更灵活的权限管理。

## 🏗️ 数据库表结构

### 1. 核心表结构

#### `user_roles` 表（核心多角色表）
```sql
CREATE TABLE public.user_roles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id),
    role_type user_role_type NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,  -- NULL表示永久有效
    granted_by UUID REFERENCES auth.users(id),
    is_active BOOLEAN DEFAULT true,
    role_permissions JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, role_type)  -- 防止重复角色
);
```

**字段说明：**
- `user_id`: 用户ID，关联到 auth.users
- `role_type`: 角色类型（枚举值）
- `granted_at`: 角色授予时间
- `expires_at`: 角色过期时间（NULL = 永不过期）
- `granted_by`: 授予者ID
- `is_active`: 角色是否激活
- `role_permissions`: 角色特定权限（JSONB格式）

#### `profiles` 表（扩展字段）
```sql
-- 新增字段
ALTER TABLE public.profiles
ADD COLUMN primary_role user_role_type DEFAULT 'user';

ALTER TABLE public.profiles
ADD COLUMN global_permissions JSONB DEFAULT '{}'::jsonb;
```

**新增字段说明：**
- `primary_role`: 用户的主要角色（用于快速查询和向后兼容）
- `global_permissions`: 用户的全局权限配置

#### `role_permissions` 表（权限配置表）
```sql
CREATE TABLE public.role_permissions (
    role_name user_role_type PRIMARY KEY,
    permissions JSONB NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2. 视图和统计表

#### `user_roles_view` 视图
提供用户角色的汇总信息，包括：
- 用户基本信息
- 主要角色
- 活跃角色列表
- 非活跃角色列表
- 活跃角色数量

#### `user_roles_detail_view` 视图
提供用户角色的详细信息，包括：
- 角色授予详情
- 授予者信息
- 角色状态（是否过期、是否有效）

#### `role_statistics` 视图
提供角色统计信息：
- 每种角色的用户总数
- 活跃用户数
- 过期用户数
- 非活跃用户数

## 🎭 角色类型和权限级别

### 角色枚举类型
```sql
CREATE TYPE user_role_type AS ENUM (
    'user',           -- 普通用户 (级别1)
    'vip',            -- VIP用户 (级别2)
    'moderator',      -- 版主/协管员 (级别3)
    'support',        -- 客服 (级别4)
    'admin',          -- 管理员 (级别5)
    'super_admin'     -- 超级管理员 (级别6)
);
```

### 权限级别层次
```
超级管理员 (6) > 管理员 (5) > 客服 (4) > 版主 (3) > VIP (2) > 普通用户 (1)
```

### 默认权限配置
- **普通用户**: 基础客服对话、反馈提交、基础频道查看
- **VIP用户**: 优先客服支持、VIP频道访问
- **版主**: 对话管理、反馈审核
- **客服**: 查看所有对话、响应用户、创建私聊、关闭对话
- **管理员**: 用户管理、角色管理、查看日志
- **超级管理员**: 系统完全访问权限

## 🔧 核心函数说明

### 角色检查函数

#### `has_role(user_id, role_type)`
检查用户是否拥有指定角色
```sql
SELECT has_role('1666b530-582a-42ce-9502-2267c2a8953f', 'super_admin');
```

#### `has_any_role(user_id, role_array)`
检查用户是否拥有任意一个指定角色
```sql
SELECT has_any_role('user_id', ARRAY['admin', 'super_admin']);
```

#### `get_user_roles(user_id)`
获取用户的所有活跃角色
```sql
SELECT get_user_roles('1666b530-582a-42ce-9502-2267c2a8953f');
```

### 权限检查函数

#### `has_permission_level(user_id, required_level)`
检查用户是否达到指定权限级别
```sql
SELECT has_permission_level('user_id', 5);  -- 检查是否为管理员级别
```

#### `user_has_permission(user_id, permission_path)`
检查用户是否拥有特定权限
```sql
SELECT user_has_permission('user_id', 'support.create_conversation');
```

### 角色管理函数

#### `add_user_role(target_user_id, new_role, granted_by_user_id, expires_at, role_permissions)`
为用户添加角色
```sql
SELECT add_user_role(
    '1666b530-582a-42ce-9502-2267c2a8953f',
    'super_admin',
    null,  -- 系统授予
    null,  -- 永不过期
    '{}'::jsonb
);
```

#### `remove_user_role(target_user_id, role_to_remove, removed_by_user_id)`
移除用户角色
```sql
SELECT remove_user_role(
    'target_user_id',
    'admin',
    'operator_user_id'
);
```

## 📊 表关系图

```
auth.users
    ↓ (一对一)
profiles (用户基本信息 + primary_role + global_permissions)
    ↓ (一对多)
user_roles (用户多角色关系)
    ↓ (多对一)
role_permissions (角色权限配置)
    ↓ (汇总统计)
role_statistics (角色统计视图)
```

## 🎯 实际使用场景

### 场景1：设置超级管理员
```sql
-- 方法1：使用函数（推荐）
SELECT add_user_role(
    '1666b530-582a-42ce-9502-2267c2a8953f',
    'super_admin',
    null,
    null,
    '{}'::jsonb
);

-- 方法2：直接插入
INSERT INTO user_roles (user_id, role_type, granted_at, is_active)
VALUES ('1666b530-582a-42ce-9502-2267c2a8953f', 'super_admin', now(), true);
```

### 场景2：多角色用户
```sql
-- 客服主管：同时拥有管理员、客服、版主角色
SELECT add_user_role('user_id', 'admin', 'granter_id', null, '{}');
SELECT add_user_role('user_id', 'support', 'granter_id', null, '{}');
SELECT add_user_role('user_id', 'moderator', 'granter_id', null, '{}');
```

### 场景3：临时权限
```sql
-- 给用户30天的VIP权限
SELECT add_user_role(
    'user_id',
    'vip',
    'admin_id',
    now() + interval '30 days',
    '{}'::jsonb
);
```

## 🔐 安全策略 (RLS)

系统启用了行级安全策略：

1. **用户查看权限**: 用户只能查看自己的角色
2. **管理员查看权限**: 管理员可以查看所有用户角色
3. **角色管理权限**: 只有管理员可以添加/更新角色
4. **超级管理员保护**: 超级管理员角色只能由超级管理员授予/移除

## ⚠️ 重要注意事项

1. **primary_role 字段**: 自动维护，表示用户的最高权限角色
2. **global_permissions 字段**: 用于存储用户特定的全局权限覆盖
3. **角色过期**: 系统自动检查角色过期时间
4. **权限继承**: 高级角色自动拥有低级角色的所有权限
5. **系统保护**: 防止移除最后一个超级管理员

## 🚀 快速操作指南

### 设置用户为超级管理员
```sql
-- 只需要在 user_roles 表中添加记录
INSERT INTO user_roles (user_id, role_type, granted_at, is_active)
VALUES ('1666b530-582a-42ce-9502-2267c2a8953f', 'super_admin', now(), true);
```

### 查看用户所有角色
```sql
SELECT * FROM user_roles_view 
WHERE userid = '1666b530-582a-42ce-9502-2267c2a8953f';
```

### 检查用户权限
```sql
SELECT user_has_permission(
    '1666b530-582a-42ce-9502-2267c2a8953f',
    'admin.manage_users'
);
```

这个多角色系统提供了强大而灵活的权限管理能力，支持复杂的企业级权限需求。

## 📝 详细操作说明

### profiles 表中新增字段的作用

#### `primary_role` 字段
- **作用**: 存储用户的主要角色，用于快速查询和向后兼容
- **自动维护**: 当用户获得新角色时，系统会自动更新为权限最高的角色
- **使用场景**:
  - 快速判断用户的主要身份
  - 向后兼容原有的单角色系统
  - UI界面显示用户主要角色标识

#### `global_permissions` 字段
- **作用**: 存储用户特定的全局权限覆盖配置
- **数据格式**: JSONB，例如：`{"support": {"priority": true}, "admin": {"temp_access": true}}`
- **使用场景**:
  - 为特定用户临时授予额外权限
  - 覆盖角色默认权限配置
  - 实现细粒度的权限控制

### 表关系详细说明

#### 1. profiles ↔ user_roles 关系
```sql
-- 一个用户(profiles)可以有多个角色(user_roles)
profiles.userid = user_roles.user_id (一对多)

-- 查询用户的所有角色
SELECT p.username, ur.role_type, ur.is_active
FROM profiles p
JOIN user_roles ur ON p.userid = ur.user_id
WHERE p.userid = '1666b530-582a-42ce-9502-2267c2a8953f';
```

#### 2. user_roles ↔ role_permissions 关系
```sql
-- 角色类型关联权限配置
user_roles.role_type = role_permissions.role_name (多对一)

-- 查询用户的所有权限
SELECT ur.role_type, rp.permissions
FROM user_roles ur
JOIN role_permissions rp ON ur.role_type = rp.role_name
WHERE ur.user_id = '1666b530-582a-42ce-9502-2267c2a8953f'
AND ur.is_active = true;
```

#### 3. 自动同步机制
当在 `user_roles` 表中添加/修改角色时，系统会自动：

1. **更新 primary_role**: 选择权限最高的活跃角色
2. **更新 is_vip**: 如果拥有VIP及以上角色，设置为true
3. **更新统计**: role_statistics 视图自动反映最新数据
4. **更新视图**: user_roles_view 和 user_roles_detail_view 自动更新

### 权限检查流程

#### 1. 基础角色检查
```sql
-- 检查是否为超级管理员
SELECT has_role('user_id', 'super_admin');

-- 检查是否为管理员（任意管理角色）
SELECT has_any_role('user_id', ARRAY['support', 'admin', 'super_admin']);
```

#### 2. 权限级别检查
```sql
-- 检查是否达到管理员级别（级别5）
SELECT has_permission_level('user_id', 5);

-- 检查是否达到客服级别（级别4）
SELECT has_permission_level('user_id', 4);
```

#### 3. 具体权限检查
```sql
-- 检查是否可以管理用户
SELECT user_has_permission('user_id', 'admin.manage_users');

-- 检查是否可以创建客服对话
SELECT user_has_permission('user_id', 'support.create_conversation');
```

### 常见操作示例

#### 1. 为用户设置超级管理员
```sql
-- 推荐方法：使用系统函数
SELECT add_user_role(
    '1666b530-582a-42ce-9502-2267c2a8953f',  -- 用户ID
    'super_admin',                            -- 角色类型
    null,                                     -- 授予者（null=系统）
    null,                                     -- 过期时间（null=永不过期）
    '{}'::jsonb                              -- 角色权限
);

-- 验证设置
SELECT * FROM user_roles_view
WHERE userid = '1666b530-582a-42ce-9502-2267c2a8953f';
```

#### 2. 批量设置多角色
```sql
-- 设置客服主管（拥有多个角色）
SELECT add_user_role('user_id', 'admin', 'granter_id', null, '{}');
SELECT add_user_role('user_id', 'support', 'granter_id', null, '{}');
SELECT add_user_role('user_id', 'moderator', 'granter_id', null, '{}');

-- 查看结果
SELECT active_roles FROM user_roles_view WHERE userid = 'user_id';
-- 结果: ["admin", "support", "moderator"]
```

#### 3. 临时权限管理
```sql
-- 给用户30天的VIP权限
SELECT add_user_role(
    'user_id',
    'vip',
    'admin_id',
    now() + interval '30 days',  -- 30天后过期
    '{"priority_support": true}'::jsonb
);

-- 查看即将过期的角色
SELECT * FROM user_roles_detail_view
WHERE expires_at IS NOT NULL
AND expires_at <= now() + interval '7 days';
```

#### 4. 权限验证示例
```sql
-- 在应用中验证用户权限
SELECT
    has_role(auth.uid(), 'super_admin') as is_super_admin,
    has_any_role(auth.uid(), ARRAY['admin', 'super_admin']) as is_admin,
    user_has_permission(auth.uid(), 'support.view_all_conversations') as can_view_all_support,
    get_user_roles(auth.uid()) as user_roles;
```

### 数据一致性保证

#### 1. 约束和触发器
- **UNIQUE约束**: 防止同一用户拥有重复的相同角色
- **外键约束**: 确保用户ID和授予者ID的有效性
- **自动更新**: profiles表的相关字段自动同步

#### 2. 事务安全
所有角色管理操作都在事务中执行，确保数据一致性：
```sql
BEGIN;
-- 角色操作
SELECT add_user_role(...);
-- 其他相关操作
COMMIT;
```

### 性能优化

#### 1. 索引策略
```sql
-- 已创建的性能优化索引
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_type ON user_roles(role_type);
CREATE INDEX idx_user_roles_expires_at ON user_roles(expires_at);
CREATE INDEX idx_user_roles_active ON user_roles(is_active);
CREATE INDEX idx_profiles_primary_role ON profiles(primary_role);
```

#### 2. 查询优化建议
- 使用视图进行复杂查询
- 利用函数进行权限检查
- 避免直接JOIN多表，使用预定义的视图

这个系统设计确保了数据的一致性、安全性和高性能，同时提供了灵活的多角色权限管理能力。
