-- 设置数据库时区为北京时间 (UTC+8)
ALTER DATABASE postgres SET timezone TO 'Asia/Shanghai';

-- 创建一个函数用于将任何时间戳转换为北京时间
CREATE OR REPLACE FUNCTION to_beijing_time(ts timestamp with time zone) 
RETURNS timestamp with time zone AS $$
BEGIN
  RETURN ts AT TIME ZONE 'Asia/Shanghai';
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 创建视图显示主要表的北京时间格式
-- 用户表
CREATE OR REPLACE VIEW profiles_beijing_time AS
SELECT
  userid,
  username,
  to_beijing_time(created_at) as created_at,
  to_beijing_time(updated_at) as updated_at,
  is_vip,
  to_beijing_time(vip_expiry) as vip_expiry,
  to_beijing_time(lastlogintime) as lastlogintime,
  email,
  lastlogindevice,
  accountstatus,
  to_beijing_time(vipstart) as vipstart,
  to_beijing_time(vipend) as vipend
FROM profiles;

-- 用户登录日志
CREATE OR REPLACE VIEW user_login_logs_beijing_time AS
SELECT
  id,
  user_id,
  to_beijing_time(login_time) as login_time,
  ip_address,
  device_info
FROM user_login_logs;

-- 观看历史
CREATE OR REPLACE VIEW watch_history_beijing_time AS
SELECT
  id,
  user_id,
  channel_name,
  channel_url,
  to_beijing_time(watch_start) as watch_start,
  to_beijing_time(watch_end) as watch_end,
  duration,
  to_beijing_time(created_at) as created_at
FROM watch_history;

-- 激活码
CREATE OR REPLACE VIEW activation_codes_beijing_time AS
SELECT
  id,
  code,
  duration_days,
  is_used,
  used_by,
  to_beijing_time(created_at) as created_at,
  to_beijing_time(used_at) as used_at
FROM activation_codes;

-- VIP交易记录
CREATE OR REPLACE VIEW vip_transactions_beijing_time AS
SELECT
  id,
  user_id,
  transaction_type,
  amount,
  code_id,
  to_beijing_time(created_at) as created_at
FROM vip_transactions;

-- 用户会话
CREATE OR REPLACE VIEW user_sessions_beijing_time AS
SELECT
  id,
  user_id,
  to_beijing_time(created_at) as created_at,
  to_beijing_time(expires_at) as expires_at,
  device_info,
  ip_address,
  platform,
  app_version
FROM user_sessions;

-- 服务消息
CREATE OR REPLACE VIEW service_messages_beijing_time AS
SELECT
  id,
  message,
  to_beijing_time(created_at) as created_at,
  to_beijing_time(updated_at) as updated_at,
  created_by
FROM service_messages;

-- 为RLS策略创建辅助函数，确保使用北京时间比较
CREATE OR REPLACE FUNCTION is_expired_beijing_time(expires_at timestamp with time zone) 
RETURNS boolean AS $$
BEGIN
  RETURN to_beijing_time(expires_at) < to_beijing_time(now());
END;
$$ LANGUAGE plpgsql STABLE;

-- 自动更新会话过期时间的函数
CREATE OR REPLACE FUNCTION update_expires_at_beijing_time() 
RETURNS TRIGGER AS $$
BEGIN
  NEW.expires_at = to_beijing_time(now() + interval '30 days');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 添加注释
COMMENT ON FUNCTION to_beijing_time IS '将时间戳转换为北京时间(UTC+8)';
COMMENT ON VIEW profiles_beijing_time IS '用户资料表的北京时间视图';
COMMENT ON VIEW user_login_logs_beijing_time IS '用户登录日志的北京时间视图';
COMMENT ON VIEW watch_history_beijing_time IS '观看历史的北京时间视图';
COMMENT ON VIEW activation_codes_beijing_time IS '激活码的北京时间视图';
COMMENT ON VIEW vip_transactions_beijing_time IS 'VIP交易记录的北京时间视图';
COMMENT ON VIEW user_sessions_beijing_time IS '用户会话的北京时间视图';
COMMENT ON VIEW service_messages_beijing_time IS '服务消息的北京时间视图'; 