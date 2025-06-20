-- 创建app_configs表
CREATE TABLE IF NOT EXISTS app_configs (
    id SERIAL PRIMARY KEY,
    app_id VARCHAR(50) NOT NULL,
    project_name VARCHAR(100) NOT NULL,
    project_url VARCHAR(255) NOT NULL,
    project_id VARCHAR(100) NOT NULL,
    api_key TEXT NOT NULL,
    access_token TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(app_id, is_active) -- 确保每个应用只有一个活跃配置
);

-- 插入ONETV应用的配置数据
INSERT INTO app_configs (
    app_id, 
    project_name, 
    project_url, 
    project_id, 
    api_key, 
    access_token, 
    is_active
) VALUES (
    'onetv',
    'ONETV',
    'https://sjlmgylmcxrapwxjfzhy.supabase.co',
    'sjlmgylmcxrapwxjfzhy',
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNqbG1neWxtY3hyYXB3eGpmemh5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDk4NzQ4MTAsImV4cCI6MjA2NTQ1MDgxMH0.t5lVP4Z9sZO1xyx7yVc0kGPpCtNgX_NrjU-smi1VftY',
    'sbp_b758200fc49bd20edc98726a4d01d6588200332e',
    TRUE
) ON CONFLICT (app_id, is_active) 
DO UPDATE SET
    project_name = EXCLUDED.project_name,
    project_url = EXCLUDED.project_url,
    project_id = EXCLUDED.project_id,
    api_key = EXCLUDED.api_key,
    access_token = EXCLUDED.access_token,
    updated_at = NOW();

-- 为配置表创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_app_configs_app_id ON app_configs(app_id);
CREATE INDEX IF NOT EXISTS idx_app_configs_active ON app_configs(is_active); 