-- 迁移脚本: 将数据库时区修改为北京时间
-- 执行日期: 2023-12-31

-- 1. 设置数据库时区
ALTER DATABASE postgres SET timezone TO 'Asia/Shanghai';

-- 2. 为当前会话设置时区
SET timezone = 'Asia/Shanghai';

-- 3. 创建用于时区转换的函数
CREATE OR REPLACE FUNCTION to_beijing_time(ts timestamp with time zone) 
RETURNS timestamp with time zone AS $$
BEGIN
  RETURN ts AT TIME ZONE 'Asia/Shanghai';
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- 4. 创建便于查询的视图 (视图中的时间均为北京时间)
-- 用户资料视图
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

-- 用户登录日志视图
CREATE OR REPLACE VIEW user_login_logs_beijing_time AS
SELECT
  id,
  user_id,
  to_beijing_time(login_time) as login_time,
  ip_address,
  device_info
FROM user_login_logs;

-- 观看历史视图
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

-- 激活码视图
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

-- VIP交易记录视图
CREATE OR REPLACE VIEW vip_transactions_beijing_time AS
SELECT
  id,
  user_id,
  transaction_type,
  amount,
  code_id,
  to_beijing_time(created_at) as created_at
FROM vip_transactions;

-- 用户会话视图
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

-- 服务消息视图
CREATE OR REPLACE VIEW service_messages_beijing_time AS
SELECT
  id,
  message,
  to_beijing_time(created_at) as created_at,
  to_beijing_time(updated_at) as updated_at,
  created_by
FROM service_messages;

-- 在线用户统计视图
CREATE OR REPLACE VIEW online_users_stats_beijing_time AS
SELECT
  id,
  to_beijing_time(timestamp) as timestamp,
  total,
  base,
  real
FROM online_users_stats;

-- 应用配置视图
CREATE OR REPLACE VIEW app_configs_beijing_time AS
SELECT
  id,
  app_id,
  project_name,
  project_url,
  project_id,
  api_key,
  access_token,
  is_active,
  to_beijing_time(created_at) as created_at,
  to_beijing_time(updated_at) as updated_at
FROM app_configs;

-- 用户设置视图
CREATE OR REPLACE VIEW user_settings_beijing_time AS
SELECT
  user_id,
  theme,
  player_settings,
  notification_enabled,
  to_beijing_time(updated_at) as updated_at,
  gender,
  birth_date,
  region,
  language_preference,
  timezone,
  display_name,
  avatar_url,
  bio
FROM user_settings;

-- 5. 创建辅助函数用于时区敏感操作
-- 检查会话是否过期 (使用北京时间)
CREATE OR REPLACE FUNCTION is_expired_beijing_time(expires_at timestamp with time zone) 
RETURNS boolean AS $$
BEGIN
  RETURN to_beijing_time(expires_at) < to_beijing_time(now());
END;
$$ LANGUAGE plpgsql STABLE;

-- 自动更新会话过期时间 (使用北京时间)
CREATE OR REPLACE FUNCTION update_expires_at_beijing_time() 
RETURNS TRIGGER AS $$
BEGIN
  NEW.expires_at = to_beijing_time(now() + interval '30 days');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 6. 添加触发器
-- 创建触发器自动更新会话过期时间 (使用北京时间)
DROP TRIGGER IF EXISTS user_sessions_expires_at_trigger ON user_sessions;
CREATE TRIGGER user_sessions_expires_at_trigger
BEFORE INSERT ON user_sessions
FOR EACH ROW
EXECUTE FUNCTION update_expires_at_beijing_time();

-- 7. 添加注释
COMMENT ON FUNCTION to_beijing_time IS '将时间戳转换为北京时间(UTC+8)';
COMMENT ON VIEW profiles_beijing_time IS '用户资料表的北京时间视图';
COMMENT ON VIEW user_login_logs_beijing_time IS '用户登录日志的北京时间视图';
COMMENT ON VIEW watch_history_beijing_time IS '观看历史的北京时间视图';
COMMENT ON VIEW activation_codes_beijing_time IS '激活码的北京时间视图';
COMMENT ON VIEW vip_transactions_beijing_time IS 'VIP交易记录的北京时间视图';
COMMENT ON VIEW user_sessions_beijing_time IS '用户会话的北京时间视图';
COMMENT ON VIEW service_messages_beijing_time IS '服务消息的北京时间视图';
COMMENT ON VIEW online_users_stats_beijing_time IS '在线用户统计的北京时间视图';
COMMENT ON VIEW app_configs_beijing_time IS '应用配置的北京时间视图';
COMMENT ON VIEW user_settings_beijing_time IS '用户设置的北京时间视图';
COMMENT ON FUNCTION is_expired_beijing_time IS '使用北京时间检查会话是否过期';
COMMENT ON FUNCTION update_expires_at_beijing_time IS '使用北京时间更新会话过期时间';

-- 8. 输出确认信息
DO $$
BEGIN
  RAISE NOTICE '数据库时区已成功设置为北京时间 (Asia/Shanghai)';
  RAISE NOTICE '已创建所有支持北京时间的视图和函数';
END $$; 