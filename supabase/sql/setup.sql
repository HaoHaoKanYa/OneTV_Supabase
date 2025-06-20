-- Supabase数据库设置脚本
-- 用于创建IPTV应用所需的表和函数

-- 启用UUID扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 创建profiles表（如果不存在）
CREATE TABLE IF NOT EXISTS public.profiles (
  userid UUID PRIMARY KEY REFERENCES auth.users(id),
  username TEXT UNIQUE,
  email TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  is_vip BOOLEAN DEFAULT false,
  vip_expiry TIMESTAMP WITH TIME ZONE, -- 旧字段，保留兼容
  vipstart TIMESTAMP WITH TIME ZONE,
  vipend TIMESTAMP WITH TIME ZONE,
  lastlogintime TIMESTAMP WITH TIME ZONE,
  lastlogindevice TEXT,
  accountstatus TEXT DEFAULT 'active'
);

-- 创建激活码表
CREATE TABLE IF NOT EXISTS public.activation_codes (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  code TEXT UNIQUE NOT NULL,
  duration_days INTEGER NOT NULL,
  is_used BOOLEAN DEFAULT false,
  used_by UUID REFERENCES auth.users(id),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  used_at TIMESTAMP WITH TIME ZONE
);

-- 创建VIP交易记录表
CREATE TABLE IF NOT EXISTS public.vip_transactions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES auth.users(id) NOT NULL,
  transaction_type TEXT NOT NULL, -- 'activation', 'renewal'
  amount INTEGER, -- 月数
  code_id UUID REFERENCES public.activation_codes(id),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 创建用户登录日志表
CREATE TABLE IF NOT EXISTS public.user_login_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES auth.users(id) NOT NULL,
  login_time TIMESTAMP WITH TIME ZONE DEFAULT now(),
  device_info TEXT,
  ip_address TEXT
);

-- 创建频道收藏表
CREATE TABLE IF NOT EXISTS public.channel_favorites (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES auth.users(id) NOT NULL,
  channel_name TEXT NOT NULL,
  channel_url TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(user_id, channel_url)
);

-- 创建用户设置表
CREATE TABLE IF NOT EXISTS public.user_settings (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id),
  theme TEXT DEFAULT 'dark',
  player_settings JSONB DEFAULT '{}'::jsonb,
  notification_enabled BOOLEAN DEFAULT true,
  
  -- 新增用户个人资料字段
  gender TEXT,
  birth_date DATE,
  region TEXT,
  language_preference TEXT DEFAULT 'zh-CN',
  timezone TEXT DEFAULT 'Asia/Shanghai',
  display_name TEXT,
  avatar_url TEXT,
  bio TEXT,
  
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 创建观看历史表
CREATE TABLE IF NOT EXISTS public.watch_history (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES auth.users(id) NOT NULL,
  channel_name TEXT NOT NULL,
  channel_url TEXT NOT NULL,
  watch_start TIMESTAMP WITH TIME ZONE DEFAULT now(),
  watch_end TIMESTAMP WITH TIME ZONE,
  duration INTEGER, -- 观看时长（秒）
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 为观看历史表添加索引
CREATE INDEX IF NOT EXISTS watch_history_user_id_idx ON public.watch_history(user_id);
CREATE INDEX IF NOT EXISTS watch_history_channel_url_idx ON public.watch_history(channel_url);
CREATE INDEX IF NOT EXISTS watch_history_watch_start_idx ON public.watch_history(watch_start);

-- 观看历史表策略
ALTER TABLE public.watch_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY "允许用户查看自己的观看历史" ON public.watch_history
  FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "允许用户添加自己的观看历史" ON public.watch_history
  FOR INSERT
  WITH CHECK (auth.uid() = user_id);

-- 创建观看历史统计函数
CREATE OR REPLACE FUNCTION public.get_watch_statistics(p_user_id UUID, p_start_date TIMESTAMP WITH TIME ZONE, p_end_date TIMESTAMP WITH TIME ZONE)
RETURNS TABLE (
  total_duration BIGINT,
  channel_name TEXT,
  watch_count BIGINT,
  total_channel_duration BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    SUM(COALESCE(wh.duration, 0)) AS total_duration,
    wh.channel_name,
    COUNT(*) AS watch_count,
    SUM(COALESCE(wh.duration, 0)) AS total_channel_duration
  FROM 
    public.watch_history wh
  WHERE 
    wh.user_id = p_user_id
    AND wh.watch_start >= p_start_date
    AND wh.watch_start <= p_end_date
  GROUP BY 
    wh.channel_name
  ORDER BY 
    total_channel_duration DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 创建VIP激活存储过程
CREATE OR REPLACE FUNCTION public.activate_vip(
  p_user_id UUID,
  p_code_id UUID,
  p_vip_start TIMESTAMP WITH TIME ZONE,
  p_vip_end TIMESTAMP WITH TIME ZONE
) RETURNS VOID AS $$
BEGIN
  -- 更新用户VIP状态
  UPDATE public.profiles
  SET is_vip = true,
      vipstart = p_vip_start,
      vipend = p_vip_end,
      updated_at = now()
  WHERE userid = p_user_id;
  
  -- 标记激活码为已使用
  UPDATE public.activation_codes
  SET is_used = true,
      used_by = p_user_id,
      used_at = now()
  WHERE id = p_code_id;
  
  -- 记录交易
  INSERT INTO public.vip_transactions (
    user_id,
    transaction_type,
    code_id,
    created_at
  ) VALUES (
    p_user_id,
    'activation',
    p_code_id,
    now()
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 创建检查用户名是否可用的函数
CREATE OR REPLACE FUNCTION public.check_username_available(username TEXT)
RETURNS BOOLEAN AS $$
BEGIN
  RETURN NOT EXISTS (
    SELECT 1 FROM public.profiles WHERE profiles.username = check_username_available.username
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 创建用户注册触发器函数
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (userid, username, email)
  VALUES (NEW.id, NEW.raw_user_meta_data->>'username', NEW.email);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 创建用户登录触发器函数
CREATE OR REPLACE FUNCTION public.handle_user_login()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE public.profiles
  SET lastlogintime = NOW()
  WHERE userid = NEW.user_id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 创建新用户触发器
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 创建用户登录触发器
DROP TRIGGER IF EXISTS on_auth_user_login ON auth.sessions;
CREATE TRIGGER on_auth_user_login
  AFTER INSERT ON auth.sessions
  FOR EACH ROW EXECUTE FUNCTION public.handle_user_login();

-- 设置行级安全策略

-- profiles表策略
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "允许用户查看自己的资料" ON public.profiles
  FOR SELECT
  USING (auth.uid() = userid);

CREATE POLICY "允许用户更新自己的资料" ON public.profiles
  FOR UPDATE
  USING (auth.uid() = userid);

-- channel_favorites表策略
ALTER TABLE public.channel_favorites ENABLE ROW LEVEL SECURITY;

CREATE POLICY "允许用户查看自己的收藏" ON public.channel_favorites
  FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "允许用户添加收藏" ON public.channel_favorites
  FOR INSERT
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY "允许用户删除自己的收藏" ON public.channel_favorites
  FOR DELETE
  USING (auth.uid() = user_id);

-- user_settings表策略
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "允许用户查看自己的设置" ON public.user_settings
  FOR SELECT
  USING (auth.uid() = user_id);

CREATE POLICY "允许用户更新自己的设置" ON public.user_settings
  FOR UPDATE
  USING (auth.uid() = user_id);

CREATE POLICY "允许用户插入自己的设置" ON public.user_settings
  FOR INSERT
  WITH CHECK (auth.uid() = user_id); 