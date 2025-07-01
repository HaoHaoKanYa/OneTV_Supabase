-- OneTV 简化聊天系统
-- 只保留 1对1客服聊天 和 用户反馈功能

-- 删除旧的聊天表（如果存在）
DROP TABLE IF EXISTS public.chat_participants CASCADE;
DROP TABLE IF EXISTS public.chat_messages CASCADE;
DROP TABLE IF EXISTS public.chat_rooms CASCADE;

-- 1. 客服对话表（1对1私聊）
CREATE TABLE public.support_conversations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    support_id UUID REFERENCES auth.users(id) ON DELETE SET NULL, -- 分配的客服ID
    conversation_title VARCHAR(255) DEFAULT '客服对话',
    status VARCHAR(50) DEFAULT 'open', -- open(进行中), closed(已关闭), waiting(等待客服)
    priority VARCHAR(20) DEFAULT 'normal', -- low, normal, high, urgent
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    closed_at TIMESTAMP WITH TIME ZONE,
    last_message_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. 对话消息表
CREATE TABLE public.support_messages (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES public.support_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    message_text TEXT NOT NULL,
    message_type VARCHAR(50) DEFAULT 'text', -- text, image, file, system
    is_from_support BOOLEAN DEFAULT false, -- 是否来自客服
    read_at TIMESTAMP WITH TIME ZONE, -- 消息已读时间
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. 用户反馈表
CREATE TABLE public.user_feedback (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    feedback_type VARCHAR(50) DEFAULT 'general', -- bug, feature, complaint, suggestion, general
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'submitted', -- submitted, reviewing, resolved, closed
    priority VARCHAR(20) DEFAULT 'normal', -- low, normal, high
    admin_response TEXT, -- 管理员回复
    admin_id UUID REFERENCES auth.users(id), -- 处理的管理员
    device_info JSONB, -- 设备信息
    app_version VARCHAR(50), -- 应用版本
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- 4. 创建索引优化查询
CREATE INDEX idx_support_conversations_user_id ON public.support_conversations(user_id);
CREATE INDEX idx_support_conversations_support_id ON public.support_conversations(support_id);
CREATE INDEX idx_support_conversations_status ON public.support_conversations(status);
CREATE INDEX idx_support_conversations_last_message_at ON public.support_conversations(last_message_at DESC);

CREATE INDEX idx_support_messages_conversation_id ON public.support_messages(conversation_id);
CREATE INDEX idx_support_messages_sender_id ON public.support_messages(sender_id);
CREATE INDEX idx_support_messages_created_at ON public.support_messages(created_at DESC);

CREATE INDEX idx_user_feedback_user_id ON public.user_feedback(user_id);
CREATE INDEX idx_user_feedback_status ON public.user_feedback(status);
CREATE INDEX idx_user_feedback_feedback_type ON public.user_feedback(feedback_type);
CREATE INDEX idx_user_feedback_created_at ON public.user_feedback(created_at DESC);

-- 5. 启用RLS
ALTER TABLE public.support_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_feedback ENABLE ROW LEVEL SECURITY;

-- 6. RLS策略 - 客服对话
-- 用户只能看到自己的对话
CREATE POLICY "用户查看自己的客服对话" ON public.support_conversations
    FOR SELECT USING (user_id = auth.uid());

-- 客服可以看到分配给自己的对话
CREATE POLICY "客服查看分配的对话" ON public.support_conversations
    FOR SELECT USING (
        support_id = auth.uid() OR
        public.has_any_role(auth.uid(), ARRAY['support', 'admin', 'super_admin']::user_role_type[])
    );

-- 用户可以创建新对话
CREATE POLICY "用户创建客服对话" ON public.support_conversations
    FOR INSERT WITH CHECK (user_id = auth.uid());

-- 客服可以更新对话状态
CREATE POLICY "客服更新对话状态" ON public.support_conversations
    FOR UPDATE USING (
        support_id = auth.uid() OR
        public.has_any_role(auth.uid(), ARRAY['support', 'admin', 'super_admin']::user_role_type[])
    );

-- 7. RLS策略 - 对话消息
-- 用户和客服都可以查看对话中的消息
CREATE POLICY "查看对话消息" ON public.support_messages
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.support_conversations sc
            WHERE sc.id = conversation_id
            AND (sc.user_id = auth.uid() OR sc.support_id = auth.uid())
        ) OR
        public.has_any_role(auth.uid(), ARRAY['support', 'admin', 'super_admin']::user_role_type[])
    );

-- 参与对话的用户可以发送消息
CREATE POLICY "发送对话消息" ON public.support_messages
    FOR INSERT WITH CHECK (
        sender_id = auth.uid() AND
        EXISTS (
            SELECT 1 FROM public.support_conversations sc
            WHERE sc.id = conversation_id 
            AND (sc.user_id = auth.uid() OR sc.support_id = auth.uid())
        )
    );

-- 8. RLS策略 - 用户反馈
-- 用户只能看到自己的反馈
CREATE POLICY "用户查看自己的反馈" ON public.user_feedback
    FOR SELECT USING (user_id = auth.uid());

-- 管理员可以查看所有反馈
CREATE POLICY "管理员查看所有反馈" ON public.user_feedback
    FOR SELECT USING (
        public.has_any_role(auth.uid(), ARRAY['support', 'admin', 'super_admin']::user_role_type[])
    );

-- 用户可以提交反馈
CREATE POLICY "用户提交反馈" ON public.user_feedback
    FOR INSERT WITH CHECK (user_id = auth.uid());

-- 管理员可以更新反馈状态
CREATE POLICY "管理员更新反馈" ON public.user_feedback
    FOR UPDATE USING (
        public.has_any_role(auth.uid(), ARRAY['support', 'admin', 'super_admin']::user_role_type[])
    );

-- 9. 创建自动分配客服的函数
CREATE OR REPLACE FUNCTION auto_assign_support()
RETURNS TRIGGER AS $$
DECLARE
    available_support_id UUID;
BEGIN
    -- 查找在线的客服（负载最少的）
    SELECT p.userid INTO available_support_id
    FROM public.profiles p
    WHERE EXISTS (
        SELECT 1 FROM public.user_roles ur
        WHERE ur.user_id = p.userid
        AND ur.role_type IN ('support', 'admin', 'super_admin')
        AND ur.is_active = true
        AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
    )
    AND p.accountstatus = 'active'
    ORDER BY (
        SELECT COUNT(*)
        FROM public.support_conversations sc
        WHERE sc.support_id = p.userid
        AND sc.status = 'open'
    ) ASC
    LIMIT 1;
    
    -- 如果找到可用客服，自动分配
    IF available_support_id IS NOT NULL THEN
        NEW.support_id := available_support_id;
        NEW.status := 'open';
    ELSE
        NEW.status := 'waiting';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
CREATE TRIGGER trigger_auto_assign_support
    BEFORE INSERT ON public.support_conversations
    FOR EACH ROW
    EXECUTE FUNCTION auto_assign_support();

-- 10. 更新时间戳触发器
CREATE OR REPLACE FUNCTION update_conversation_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    -- 更新对话的最后消息时间
    UPDATE public.support_conversations 
    SET 
        last_message_at = NOW(),
        updated_at = NOW()
    WHERE id = NEW.conversation_id;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_conversation_timestamp
    AFTER INSERT ON public.support_messages
    FOR EACH ROW
    EXECUTE FUNCTION update_conversation_timestamp();

-- 11. 创建便捷查询函数

-- 获取用户的活跃对话
CREATE OR REPLACE FUNCTION get_user_active_conversation(p_user_id UUID)
RETURNS TABLE(
    conversation_id UUID,
    support_username VARCHAR,
    status VARCHAR,
    last_message_at TIMESTAMP WITH TIME ZONE,
    unread_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        sc.id as conversation_id,
        COALESCE(sp.username, '等待分配客服') as support_username,
        sc.status,
        sc.last_message_at,
        (SELECT COUNT(*) 
         FROM public.support_messages sm 
         WHERE sm.conversation_id = sc.id 
         AND sm.is_from_support = true 
         AND sm.read_at IS NULL) as unread_count
    FROM public.support_conversations sc
    LEFT JOIN public.profiles sp ON sc.support_id = sp.userid
    WHERE sc.user_id = p_user_id 
    AND sc.status IN ('open', 'waiting')
    ORDER BY sc.last_message_at DESC
    LIMIT 1;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 获取客服的待处理对话列表
CREATE OR REPLACE FUNCTION get_support_conversations(p_support_id UUID)
RETURNS TABLE(
    conversation_id UUID,
    user_username VARCHAR,
    status VARCHAR,
    priority VARCHAR,
    last_message_at TIMESTAMP WITH TIME ZONE,
    unread_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        sc.id as conversation_id,
        u.username as user_username,
        sc.status,
        sc.priority,
        sc.last_message_at,
        (SELECT COUNT(*) 
         FROM public.support_messages sm 
         WHERE sm.conversation_id = sc.id 
         AND sm.is_from_support = false 
         AND sm.read_at IS NULL) as unread_count
    FROM public.support_conversations sc
    JOIN public.profiles u ON sc.user_id = u.userid
    WHERE sc.support_id = p_support_id 
    AND sc.status = 'open'
    ORDER BY sc.priority DESC, sc.last_message_at ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
