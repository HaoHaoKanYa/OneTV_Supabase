-- OneTV 用户多角色权限系统
-- 支持一个用户同时拥有多种角色（如超级管理员+管理员+客服）

-- 1. 创建用户角色枚举类型
CREATE TYPE user_role_type AS ENUM (
    'user',           -- 普通用户
    'vip',            -- VIP用户
    'moderator',      -- 版主/协管员
    'support',        -- 客服
    'admin',          -- 管理员
    'super_admin'     -- 超级管理员
);

-- 2. 创建用户角色关联表（支持多角色）
CREATE TABLE IF NOT EXISTS public.user_roles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_type user_role_type NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE, -- NULL表示永久有效
    granted_by UUID REFERENCES auth.users(id),
    is_active BOOLEAN DEFAULT true,
    role_permissions JSONB DEFAULT '{}'::jsonb, -- 角色特定权限
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, role_type) -- 防止重复角色
);

-- 3. 为 profiles 表添加主要角色字段（向后兼容）
ALTER TABLE public.profiles
ADD COLUMN IF NOT EXISTS primary_role user_role_type DEFAULT 'user';

-- 4. 添加角色权限描述字段（全局权限）
ALTER TABLE public.profiles
ADD COLUMN IF NOT EXISTS global_permissions JSONB DEFAULT '{}'::jsonb;

-- 5. 创建索引优化角色查询
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON public.user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_type ON public.user_roles(role_type);
CREATE INDEX IF NOT EXISTS idx_user_roles_expires_at ON public.user_roles(expires_at);
CREATE INDEX IF NOT EXISTS idx_user_roles_active ON public.user_roles(is_active);
CREATE INDEX IF NOT EXISTS idx_profiles_primary_role ON public.profiles(primary_role);

-- 6. 初始化现有用户的角色（基于 is_vip 字段）
-- 为所有现有用户添加基础角色
INSERT INTO public.user_roles (user_id, role_type, granted_at, is_active)
SELECT
    userid,
    CASE
        WHEN is_vip = true THEN 'vip'::user_role_type
        ELSE 'user'::user_role_type
    END,
    created_at,
    true
FROM public.profiles
WHERE userid NOT IN (SELECT DISTINCT user_id FROM public.user_roles)
ON CONFLICT (user_id, role_type) DO NOTHING;

-- 更新 profiles 表的主要角色
UPDATE public.profiles
SET primary_role = CASE
    WHEN is_vip = true THEN 'vip'::user_role_type
    ELSE 'user'::user_role_type
END
WHERE primary_role = 'user'::user_role_type;

-- 7. 创建多角色管理函数

-- 检查用户是否有指定角色（支持多角色）
CREATE OR REPLACE FUNCTION public.has_role(user_id UUID, required_role user_role_type)
RETURNS BOOLEAN AS $$
BEGIN
    -- 检查用户是否有指定的活跃角色
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles
        WHERE user_roles.user_id = has_role.user_id
        AND role_type = required_role
        AND is_active = true
        AND (expires_at IS NULL OR expires_at > NOW())
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 检查用户是否有任意一个指定角色
CREATE OR REPLACE FUNCTION public.has_any_role(user_id UUID, required_roles user_role_type[])
RETURNS BOOLEAN AS $$
BEGIN
    -- 检查用户是否有任意一个指定的活跃角色
    RETURN EXISTS (
        SELECT 1 FROM public.user_roles
        WHERE user_roles.user_id = has_any_role.user_id
        AND role_type = ANY(required_roles)
        AND is_active = true
        AND (expires_at IS NULL OR expires_at > NOW())
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 获取用户的所有活跃角色
CREATE OR REPLACE FUNCTION public.get_user_roles(user_id UUID)
RETURNS user_role_type[] AS $$
DECLARE
    user_roles_array user_role_type[];
BEGIN
    -- 获取用户所有活跃且未过期的角色
    SELECT ARRAY_AGG(role_type) INTO user_roles_array
    FROM public.user_roles
    WHERE user_roles.user_id = get_user_roles.user_id
    AND is_active = true
    AND (expires_at IS NULL OR expires_at > NOW());

    RETURN COALESCE(user_roles_array, ARRAY['user'::user_role_type]);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 检查用户是否有足够权限（基于角色层级）
CREATE OR REPLACE FUNCTION public.has_permission_level(user_id UUID, required_level INTEGER)
RETURNS BOOLEAN AS $$
DECLARE
    max_level INTEGER := 0;
    user_level INTEGER;
BEGIN
    -- 获取用户所有角色的最高权限级别
    SELECT MAX(
        CASE role_type
            WHEN 'user' THEN 1
            WHEN 'vip' THEN 2
            WHEN 'moderator' THEN 3
            WHEN 'support' THEN 4
            WHEN 'admin' THEN 5
            WHEN 'super_admin' THEN 6
            ELSE 0
        END
    ) INTO user_level
    FROM public.user_roles
    WHERE user_roles.user_id = has_permission_level.user_id
    AND is_active = true
    AND (expires_at IS NULL OR expires_at > NOW());

    RETURN COALESCE(user_level, 1) >= required_level;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 检查用户是否为管理员（客服及以上）
CREATE OR REPLACE FUNCTION public.is_user_admin(user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN public.has_any_role(user_id, ARRAY['support', 'admin', 'super_admin']::user_role_type[]);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 检查用户是否为客服
CREATE OR REPLACE FUNCTION public.is_user_support(user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN public.has_role(user_id, 'support'::user_role_type);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 检查用户是否为超级管理员
CREATE OR REPLACE FUNCTION public.is_super_admin(user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN public.has_role(user_id, 'super_admin'::user_role_type);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 添加角色给用户
CREATE OR REPLACE FUNCTION public.add_user_role(
    target_user_id UUID,
    new_role user_role_type,
    granted_by_user_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    role_permissions JSONB DEFAULT '{}'::jsonb
)
RETURNS BOOLEAN AS $$
BEGIN
    -- 检查授权者是否有权限
    IF NOT public.has_permission_level(granted_by_user_id, 5) THEN -- admin level
        RAISE EXCEPTION '权限不足：只有管理员可以授予角色';
    END IF;

    -- 超级管理员角色只能由超级管理员授予
    IF new_role = 'super_admin'::user_role_type AND
       NOT public.has_role(granted_by_user_id, 'super_admin'::user_role_type) THEN
        RAISE EXCEPTION '权限不足：只有超级管理员可以授予超级管理员角色';
    END IF;

    -- 插入或更新角色
    INSERT INTO public.user_roles (
        user_id, role_type, granted_at, expires_at, granted_by, is_active, role_permissions
    ) VALUES (
        target_user_id, new_role, NOW(), expires_at, granted_by_user_id, true, role_permissions
    )
    ON CONFLICT (user_id, role_type)
    DO UPDATE SET
        granted_at = NOW(),
        expires_at = EXCLUDED.expires_at,
        granted_by = EXCLUDED.granted_by,
        is_active = true,
        role_permissions = EXCLUDED.role_permissions,
        updated_at = NOW();

    -- 更新主要角色（如果新角色权限更高）
    UPDATE public.profiles
    SET primary_role = new_role
    WHERE userid = target_user_id
    AND (
        primary_role = 'user'::user_role_type OR
        (new_role = 'super_admin'::user_role_type) OR
        (new_role = 'admin'::user_role_type AND primary_role NOT IN ('super_admin')) OR
        (new_role = 'support'::user_role_type AND primary_role NOT IN ('super_admin', 'admin'))
    );

    -- 如果是VIP角色，同时更新is_vip字段
    IF new_role IN ('vip', 'moderator', 'support', 'admin', 'super_admin') THEN
        UPDATE public.profiles
        SET is_vip = true
        WHERE userid = target_user_id;
    END IF;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 移除用户角色
CREATE OR REPLACE FUNCTION public.remove_user_role(
    target_user_id UUID,
    role_to_remove user_role_type,
    removed_by_user_id UUID
)
RETURNS BOOLEAN AS $$
BEGIN
    -- 检查操作者是否有权限
    IF NOT public.has_permission_level(removed_by_user_id, 5) THEN -- admin level
        RAISE EXCEPTION '权限不足：只有管理员可以移除角色';
    END IF;

    -- 超级管理员角色只能由超级管理员移除
    IF role_to_remove = 'super_admin'::user_role_type AND
       NOT public.has_role(removed_by_user_id, 'super_admin'::user_role_type) THEN
        RAISE EXCEPTION '权限不足：只有超级管理员可以移除超级管理员角色';
    END IF;

    -- 不能移除自己的超级管理员角色（防止系统无管理员）
    IF role_to_remove = 'super_admin'::user_role_type AND target_user_id = removed_by_user_id THEN
        -- 检查是否还有其他超级管理员
        IF (SELECT COUNT(*) FROM public.user_roles
            WHERE role_type = 'super_admin'::user_role_type
            AND is_active = true
            AND user_id != target_user_id
            AND (expires_at IS NULL OR expires_at > NOW())) = 0 THEN
            RAISE EXCEPTION '不能移除最后一个超级管理员角色';
        END IF;
    END IF;

    -- 标记角色为非活跃
    UPDATE public.user_roles
    SET is_active = false, updated_at = NOW()
    WHERE user_id = target_user_id AND role_type = role_to_remove;

    -- 更新主要角色（选择剩余角色中权限最高的）
    UPDATE public.profiles
    SET primary_role = (
        SELECT COALESCE(
            (SELECT role_type FROM public.user_roles
             WHERE user_id = target_user_id
             AND is_active = true
             AND (expires_at IS NULL OR expires_at > NOW())
             ORDER BY CASE role_type
                 WHEN 'super_admin' THEN 6
                 WHEN 'admin' THEN 5
                 WHEN 'support' THEN 4
                 WHEN 'moderator' THEN 3
                 WHEN 'vip' THEN 2
                 ELSE 1
             END DESC
             LIMIT 1),
            'user'::user_role_type
        )
    )
    WHERE userid = target_user_id;

    -- 更新VIP状态
    UPDATE public.profiles
    SET is_vip = EXISTS (
        SELECT 1 FROM public.user_roles
        WHERE user_id = target_user_id
        AND role_type IN ('vip', 'moderator', 'support', 'admin', 'super_admin')
        AND is_active = true
        AND (expires_at IS NULL OR expires_at > NOW())
    )
    WHERE userid = target_user_id;

    RETURN FOUND;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 9. 更新聊天系统的管理员检查函数
DROP FUNCTION IF EXISTS is_user_admin(UUID);
CREATE OR REPLACE FUNCTION is_user_admin(user_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    -- 使用新的角色系统检查
    RETURN public.has_role(user_id, 'support'::user_role_type);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. 创建多角色管理视图
CREATE OR REPLACE VIEW user_roles_view AS
SELECT
    p.userid,
    p.username,
    p.email,
    p.primary_role,
    p.is_vip,
    p.accountstatus,
    ARRAY_AGG(
        CASE WHEN ur.is_active = true AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
        THEN ur.role_type::TEXT
        ELSE NULL END
    ) FILTER (WHERE ur.role_type IS NOT NULL) as active_roles,
    ARRAY_AGG(
        CASE WHEN ur.is_active = false OR (ur.expires_at IS NOT NULL AND ur.expires_at <= NOW())
        THEN ur.role_type::TEXT
        ELSE NULL END
    ) FILTER (WHERE ur.role_type IS NOT NULL) as inactive_roles,
    COUNT(CASE WHEN ur.is_active = true AND (ur.expires_at IS NULL OR ur.expires_at > NOW()) THEN 1 END) as active_role_count
FROM public.profiles p
LEFT JOIN public.user_roles ur ON p.userid = ur.user_id
GROUP BY p.userid, p.username, p.email, p.primary_role, p.is_vip, p.accountstatus;

-- 创建详细角色信息视图
CREATE OR REPLACE VIEW user_roles_detail_view AS
SELECT
    ur.id,
    ur.user_id,
    p.username,
    p.email,
    ur.role_type,
    ur.granted_at,
    ur.expires_at,
    ur.granted_by,
    granter.username as granted_by_username,
    ur.is_active,
    ur.role_permissions,
    CASE
        WHEN ur.expires_at IS NOT NULL AND ur.expires_at <= NOW()
        THEN true
        ELSE false
    END as is_expired,
    CASE
        WHEN ur.is_active = true AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
        THEN true
        ELSE false
    END as is_effective
FROM public.user_roles ur
JOIN public.profiles p ON ur.user_id = p.userid
LEFT JOIN public.profiles granter ON ur.granted_by = granter.userid
ORDER BY ur.user_id, ur.granted_at DESC;

-- 11. 创建默认的超级管理员账号（可选）
-- 注意：这里需要手动指定一个已存在的用户ID作为超级管理员
-- 请根据实际情况修改用户ID

-- 示例：将第一个注册的用户设为超级管理员
-- UPDATE public.profiles 
-- SET user_role = 'super_admin'::user_role_type,
--     role_granted_at = NOW()
-- WHERE userid = (SELECT userid FROM public.profiles ORDER BY created_at LIMIT 1);

-- 9. 启用RLS并创建策略
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;

-- 用户可以查看自己的角色
CREATE POLICY "用户查看自己的角色" ON public.user_roles
    FOR SELECT
    USING (user_id = auth.uid());

-- 管理员可以查看所有用户角色
CREATE POLICY "管理员查看所有角色" ON public.user_roles
    FOR SELECT
    USING (
        user_id = auth.uid() OR  -- 用户可以查看自己
        public.has_any_role(auth.uid(), ARRAY['admin', 'super_admin']::user_role_type[])  -- 管理员可以查看所有
    );

-- 管理员可以插入角色
CREATE POLICY "管理员可以添加角色" ON public.user_roles
    FOR INSERT
    WITH CHECK (
        public.has_any_role(auth.uid(), ARRAY['admin', 'super_admin']::user_role_type[])
    );

-- 管理员可以更新角色
CREATE POLICY "管理员可以更新角色" ON public.user_roles
    FOR UPDATE
    USING (
        public.has_any_role(auth.uid(), ARRAY['admin', 'super_admin']::user_role_type[])
    );

-- 更新profiles表的RLS策略
DROP POLICY IF EXISTS "管理员可以查看所有用户角色" ON public.profiles;
DROP POLICY IF EXISTS "管理员可以更新用户角色" ON public.profiles;

-- 允许管理员查看所有用户信息
CREATE POLICY "管理员可以查看所有用户信息" ON public.profiles
    FOR SELECT
    USING (
        userid = auth.uid() OR  -- 用户可以查看自己
        public.has_any_role(auth.uid(), ARRAY['admin', 'super_admin']::user_role_type[])  -- 管理员可以查看所有
    );

-- 允许管理员更新用户信息
CREATE POLICY "管理员可以更新用户信息" ON public.profiles
    FOR UPDATE
    USING (
        userid = auth.uid() OR  -- 用户可以更新自己的基本信息
        public.has_any_role(auth.uid(), ARRAY['admin', 'super_admin']::user_role_type[])  -- 管理员可以更新
    );

-- 10. 创建角色权限配置表
CREATE TABLE IF NOT EXISTS public.role_permissions (
    role_name user_role_type PRIMARY KEY,
    permissions JSONB NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 插入默认权限配置（支持多角色系统）
INSERT INTO public.role_permissions (role_name, permissions, description) VALUES
('user', '{"support": {"create_conversation": true, "send_messages": true}, "feedback": {"submit": true, "view_own": true}, "channels": {"view_basic": true}}', '普通用户权限'),
('vip', '{"support": {"create_conversation": true, "send_messages": true, "priority_support": true}, "feedback": {"submit": true, "view_own": true}, "channels": {"view_basic": true, "view_vip": true}}', 'VIP用户权限'),
('moderator', '{"support": {"create_conversation": true, "send_messages": true, "moderate_conversations": true}, "feedback": {"submit": true, "view_own": true, "moderate": true}, "channels": {"view_basic": true, "view_vip": true}}', '版主权限'),
('support', '{"support": {"view_all_conversations": true, "respond_to_users": true, "create_private_conversations": true, "close_conversations": true}, "feedback": {"view_all": true, "respond": true, "update_status": true}, "channels": {"view_all": true}, "admin": {"view_user_info": true}}', '客服权限'),
('admin', '{"support": {"full_access": true}, "feedback": {"full_access": true}, "channels": {"full_access": true}, "admin": {"manage_users": true, "manage_roles": true, "view_logs": true}}', '管理员权限'),
('super_admin', '{"support": {"full_access": true}, "feedback": {"full_access": true}, "channels": {"full_access": true}, "admin": {"full_access": true}, "system": {"full_access": true}}', '超级管理员权限')
ON CONFLICT (role_name) DO UPDATE SET
    permissions = EXCLUDED.permissions,
    description = EXCLUDED.description;

-- 11. 创建便捷函数用于检查特定权限
CREATE OR REPLACE FUNCTION public.user_has_permission(
    user_id UUID,
    permission_path TEXT -- 例如: 'support.create_conversation' 或 'admin.manage_users'
)
RETURNS BOOLEAN AS $$
DECLARE
    user_roles_array user_role_type[];
    role_item user_role_type;
    role_perms JSONB;
    path_parts TEXT[];
    result BOOLEAN := false;
BEGIN
    -- 获取用户所有活跃角色
    SELECT public.get_user_roles(user_id) INTO user_roles_array;

    -- 分割权限路径
    path_parts := string_to_array(permission_path, '.');

    -- 检查每个角色的权限
    FOREACH role_item IN ARRAY user_roles_array
    LOOP
        SELECT permissions INTO role_perms
        FROM public.role_permissions
        WHERE role_name = role_item;

        -- 检查权限路径
        IF array_length(path_parts, 1) = 2 THEN
            result := COALESCE((role_perms -> path_parts[1] ->> path_parts[2])::boolean, false);
        ELSIF array_length(path_parts, 1) = 1 THEN
            result := COALESCE((role_perms ->> path_parts[1])::boolean, false);
        END IF;

        -- 如果找到权限，立即返回
        IF result THEN
            RETURN true;
        END IF;
    END LOOP;

    RETURN false;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 12. 创建角色统计视图
CREATE OR REPLACE VIEW role_statistics AS
SELECT
    ur.role_type,
    COUNT(*) as total_users,
    COUNT(CASE WHEN ur.is_active = true AND (ur.expires_at IS NULL OR ur.expires_at > NOW()) THEN 1 END) as active_users,
    COUNT(CASE WHEN ur.expires_at IS NOT NULL AND ur.expires_at <= NOW() THEN 1 END) as expired_users,
    COUNT(CASE WHEN ur.is_active = false THEN 1 END) as inactive_users
FROM public.user_roles ur
GROUP BY ur.role_type
ORDER BY
    CASE ur.role_type
        WHEN 'super_admin' THEN 6
        WHEN 'admin' THEN 5
        WHEN 'support' THEN 4
        WHEN 'moderator' THEN 3
        WHEN 'vip' THEN 2
        ELSE 1
    END DESC;
