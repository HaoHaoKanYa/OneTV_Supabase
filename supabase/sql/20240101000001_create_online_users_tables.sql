-- Create user_sessions table to track active user sessions
CREATE TABLE IF NOT EXISTS public.user_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  device_info TEXT,
  ip_address TEXT,
  platform TEXT,
  app_version TEXT
);

-- Create index for faster querying by expiration time and user
CREATE INDEX IF NOT EXISTS idx_user_sessions_expires_at ON public.user_sessions (expires_at);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON public.user_sessions (user_id);

-- Create table for tracking online user statistics over time
CREATE TABLE IF NOT EXISTS public.online_users_stats (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  timestamp TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  total INTEGER NOT NULL,
  base INTEGER NOT NULL,
  real INTEGER NOT NULL
);

-- Create index for faster time-based queries
CREATE INDEX IF NOT EXISTS idx_online_users_stats_timestamp ON public.online_users_stats (timestamp);

-- Add RLS policies to protect the tables
ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.online_users_stats ENABLE ROW LEVEL SECURITY;

-- Only allow authenticated users to see their own sessions
CREATE POLICY "Users can view their own sessions" ON public.user_sessions
  FOR SELECT USING (auth.uid() = user_id);

-- Only allow authenticated users to see online stats (or make it public if desired)
CREATE POLICY "Anyone can view online stats" ON public.online_users_stats
  FOR SELECT USING (true);

-- Create function to create/update user sessions
CREATE OR REPLACE FUNCTION public.handle_user_session()
RETURNS TRIGGER AS $$
BEGIN
  -- Check if user already has a session from same device
  -- If exists, update it; otherwise insert new record
  INSERT INTO public.user_sessions (
    user_id, 
    device_info, 
    ip_address, 
    platform, 
    app_version,
    expires_at
  )
  VALUES (
    NEW.user_id,
    NEW.device_info,
    NEW.ip_address,
    NEW.platform,
    NEW.app_version,
    NEW.expires_at
  )
  ON CONFLICT (user_id, device_info) 
  DO UPDATE SET
    expires_at = NEW.expires_at,
    ip_address = NEW.ip_address;
    
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create scheduled function to clean expired sessions daily
CREATE OR REPLACE FUNCTION public.cleanup_expired_sessions()
RETURNS void AS $$
BEGIN
  DELETE FROM public.user_sessions 
  WHERE expires_at < NOW();
  
  -- Also clean up old stats data (keep only last 30 days)
  DELETE FROM public.online_users_stats
  WHERE timestamp < (NOW() - INTERVAL '30 days');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER; 