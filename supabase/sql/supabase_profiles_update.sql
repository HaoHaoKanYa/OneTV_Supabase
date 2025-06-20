-- 修改profiles表结构

-- 1. 重命名id列为userId
ALTER TABLE public.profiles RENAME COLUMN id TO userId;

-- 2. 添加email列
ALTER TABLE public.profiles ADD COLUMN email TEXT;

-- 3. 重命名last_login为lastLoginTime
ALTER TABLE public.profiles RENAME COLUMN last_login TO lastLoginTime;

-- 4. 添加新字段
ALTER TABLE public.profiles ADD COLUMN lastLoginDevice TEXT;
ALTER TABLE public.profiles ADD COLUMN accountStatus TEXT DEFAULT 'active';
ALTER TABLE public.profiles ADD COLUMN vipStart TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.profiles ADD COLUMN vipEnd TIMESTAMP WITH TIME ZONE;

-- 5. 更新外键引用
ALTER TABLE public.profiles DROP CONSTRAINT profiles_pkey;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_pkey PRIMARY KEY (userId);
ALTER TABLE public.profiles DROP CONSTRAINT profiles_id_fkey;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_userId_fkey FOREIGN KEY (userId) REFERENCES auth.users(id);

-- 6. 更新RLS策略
DROP POLICY IF EXISTS "允许用户查看自己的资料" ON public.profiles;
CREATE POLICY "允许用户查看自己的资料" ON public.profiles
  FOR SELECT
  USING (auth.uid() = userId);

DROP POLICY IF EXISTS "允许用户更新自己的资料" ON public.profiles;
CREATE POLICY "允许用户更新自己的资料" ON public.profiles
  FOR UPDATE
  USING (auth.uid() = userId);

-- 7. 更新触发器函数
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (userId, username, email)
  VALUES (NEW.id, NEW.raw_user_meta_data->>'username', NEW.email);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. 更新登录触发器函数
CREATE OR REPLACE FUNCTION public.handle_user_login()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE public.profiles
  SET lastLoginTime = NOW()
  WHERE userId = NEW.user_id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 9. 更新user_login_logs表的外键引用
ALTER TABLE public.user_login_logs DROP CONSTRAINT user_login_logs_user_id_fkey;
ALTER TABLE public.user_login_logs ADD CONSTRAINT user_login_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id);

-- 10. 更新check_username_available函数
CREATE OR REPLACE FUNCTION public.check_username_available(username TEXT)
RETURNS BOOLEAN AS $$
BEGIN
  RETURN NOT EXISTS (
    SELECT 1 FROM public.profiles WHERE profiles.username = check_username_available.username
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER; 