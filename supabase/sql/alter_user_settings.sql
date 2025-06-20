-- 为user_settings表添加个人资料字段
ALTER TABLE public.user_settings
  ADD COLUMN IF NOT EXISTS gender text,
  ADD COLUMN IF NOT EXISTS birth_date text,
  ADD COLUMN IF NOT EXISTS region text,
  ADD COLUMN IF NOT EXISTS language_preference text DEFAULT 'zh-CN'::text,
  ADD COLUMN IF NOT EXISTS timezone text DEFAULT 'Asia/Shanghai'::text,
  ADD COLUMN IF NOT EXISTS display_name text,
  ADD COLUMN IF NOT EXISTS avatar_url text,
  ADD COLUMN IF NOT EXISTS bio text; 