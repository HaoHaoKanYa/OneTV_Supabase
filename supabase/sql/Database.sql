-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

-- 设置数据库时区为北京时间 (UTC+8)
SET timezone = 'Asia/Shanghai';

-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.activation_codes (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  code text NOT NULL UNIQUE,
  duration_days integer NOT NULL,
  is_used boolean DEFAULT false,
  used_by uuid,
  created_at timestamp with time zone DEFAULT now(),
  used_at timestamp with time zone,
  CONSTRAINT activation_codes_pkey PRIMARY KEY (id),
  CONSTRAINT activation_codes_used_by_fkey FOREIGN KEY (used_by) REFERENCES auth.users(id)
);
CREATE TABLE public.app_configs (
  id integer NOT NULL DEFAULT nextval('app_configs_id_seq'::regclass),
  app_id character varying NOT NULL,
  project_name character varying NOT NULL,
  project_url character varying NOT NULL,
  project_id character varying NOT NULL,
  api_key text NOT NULL,
  access_token text,
  is_active boolean DEFAULT true,
  created_at timestamp with time zone DEFAULT now(),
  updated_at timestamp with time zone DEFAULT now(),
  CONSTRAINT app_configs_pkey PRIMARY KEY (id)
);
CREATE TABLE public.channel_favorites (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  channel_name text NOT NULL,
  channel_url text NOT NULL,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT channel_favorites_pkey PRIMARY KEY (id),
  CONSTRAINT channel_favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);
CREATE TABLE public.online_users_stats (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  timestamp timestamp with time zone NOT NULL DEFAULT now(),
  total integer NOT NULL,
  base integer NOT NULL,
  real integer NOT NULL,
  CONSTRAINT online_users_stats_pkey PRIMARY KEY (id)
);
CREATE TABLE public.profiles (
  userid uuid NOT NULL,
  username text NOT NULL UNIQUE,
  created_at timestamp with time zone DEFAULT now(),
  updated_at timestamp with time zone DEFAULT now(),
  is_vip boolean DEFAULT false,
  vip_expiry timestamp with time zone,
  lastlogintime timestamp with time zone,
  email text,
  lastlogindevice text,
  accountstatus text DEFAULT 'active'::text,
  vipstart timestamp with time zone,
  vipend timestamp with time zone,
  CONSTRAINT profiles_pkey PRIMARY KEY (userid),
  CONSTRAINT profiles_userid_fkey FOREIGN KEY (userid) REFERENCES auth.users(id)
);
CREATE TABLE public.service_messages (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  message text NOT NULL,
  created_at timestamp with time zone DEFAULT now(),
  updated_at timestamp with time zone DEFAULT now(),
  created_by uuid,
  CONSTRAINT service_messages_pkey PRIMARY KEY (id),
  CONSTRAINT service_messages_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id)
);
CREATE TABLE public.user_login_logs (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid,
  login_time timestamp with time zone DEFAULT now(),
  ip_address text,
  device_info text,
  CONSTRAINT user_login_logs_pkey PRIMARY KEY (id),
  CONSTRAINT user_login_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);
CREATE TABLE public.user_sessions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  expires_at timestamp with time zone NOT NULL,
  device_info text,
  ip_address text,
  platform text,
  app_version text,
  CONSTRAINT user_sessions_pkey PRIMARY KEY (id),
  CONSTRAINT user_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);
CREATE TABLE public.user_settings (
  user_id uuid NOT NULL,
  theme text DEFAULT 'dark'::text,
  player_settings jsonb DEFAULT '{}'::jsonb,
  notification_enabled boolean DEFAULT true,
  updated_at timestamp with time zone DEFAULT now(),
  gender text,
  birth_date text,
  region text,
  language_preference text DEFAULT 'zh-CN'::text,
  timezone text DEFAULT 'Asia/Shanghai'::text,
  display_name text,
  avatar_url text,
  bio text,
  CONSTRAINT user_settings_pkey PRIMARY KEY (user_id),
  CONSTRAINT user_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);
CREATE TABLE public.vip_transactions (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  transaction_type text NOT NULL,
  amount integer,
  code_id uuid,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT vip_transactions_pkey PRIMARY KEY (id),
  CONSTRAINT vip_transactions_code_id_fkey FOREIGN KEY (code_id) REFERENCES public.activation_codes(id),
  CONSTRAINT vip_transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);
CREATE TABLE public.watch_history (
  id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  channel_name text NOT NULL,
  channel_url text NOT NULL,
  watch_start timestamp with time zone DEFAULT now(),
  watch_end timestamp with time zone,
  duration integer,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT watch_history_pkey PRIMARY KEY (id),
  CONSTRAINT watch_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id)
);