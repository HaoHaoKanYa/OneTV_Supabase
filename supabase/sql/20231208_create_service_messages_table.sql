-- 创建服务信息表
CREATE TABLE IF NOT EXISTS service_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES auth.users(id)
);

-- 添加索引，提高查询性能
CREATE INDEX IF NOT EXISTS idx_service_messages_created_at ON service_messages(created_at);

-- 添加 RLS 策略
ALTER TABLE service_messages ENABLE ROW LEVEL SECURITY;

-- 所有用户可读
CREATE POLICY "服务信息可读" ON service_messages
    FOR SELECT USING (true);

-- 创建触发器，自动更新 updated_at
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_service_message_timestamp
BEFORE UPDATE ON service_messages
FOR EACH ROW
EXECUTE FUNCTION update_timestamp();

-- 初始化默认服务信息
INSERT INTO service_messages (message)
VALUES ('欢迎使用 OneTV 应用！这是通过新的 Supabase 服务器发布的第一条服务信息。'); 