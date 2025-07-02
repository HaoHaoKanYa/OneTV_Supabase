-- 添加用户反馈删除策略
-- 这个脚本修复了user_feedback表缺少DELETE策略的问题

-- 用户可以删除自己的反馈
CREATE POLICY "用户删除自己的反馈" ON public.user_feedback
    FOR DELETE USING (user_id = auth.uid());

-- 管理员可以删除任何反馈
CREATE POLICY "管理员删除反馈" ON public.user_feedback
    FOR DELETE USING (
        public.has_any_role(auth.uid(), ARRAY['support', 'admin', 'super_admin']::user_role_type[])
    );
