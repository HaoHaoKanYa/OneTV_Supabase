# OneTV 服务信息功能部署指南

本文档介绍如何在 Supabase 平台上部署和配置 OneTV 应用的服务信息功能。

## 功能概述

服务信息功能允许管理员发布应用公告、更新信息和其他重要通知，这些信息将显示在应用的设置页面中的服务信息框内。

## 部署步骤

### 1. 创建数据表

在 Supabase 的 SQL 编辑器中执行以下 SQL 语句以创建所需的表和策略：

```sql
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
```

### 2. 部署 Edge Function

1. 在 `supabase/functions` 目录下创建名为 `service-info` 的文件夹
2. 在该文件夹中创建 `index.ts` 文件，粘贴以下代码：

```typescript
// Supabase Edge Function: service-info
// 处理服务信息功能，提供获取和更新服务信息的功能
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

/**
 * 服务信息 Edge Function
 * 负责获取和更新应用服务公告
 */
serve(async (req) => {
  try {
    // 创建 Supabase 客户端
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      { global: { headers: { Authorization: req.headers.get('Authorization')! } } }
    )
    
    // 处理不同的请求方法
    const method = req.headers.get('Method') || req.method
    
    // GET 请求：获取服务信息
    if (method === 'GET') {
      // 从service_messages表获取最新消息
      const { data, error } = await supabaseClient
        .from('service_messages')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(1)
        .single()
        
      if (error) {
        console.error('获取服务信息失败:', error)
        return new Response(JSON.stringify({
          error: '获取服务信息失败',
          details: error.message
        }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        })
      }
      
      // 返回获取的服务信息
      const response = {
        content: data.message || '暂无服务信息',
        last_updated: Math.floor(Date.parse(data.updated_at) / 1000) // 转为Unix时间戳(秒)
      }
      
      return new Response(JSON.stringify(response), {
        headers: { 'Content-Type': 'application/json' }
      })
    }
    
    // POST 请求：更新服务信息（仅管理员）
    if (method === 'POST') {
      // 获取用户信息
      const { data: { user } } = await supabaseClient.auth.getUser()
      
      // 检查用户是否存在
      if (!user) {
        return new Response(JSON.stringify({ error: '未授权访问' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' }
        })
      }
      
      // 获取管理员列表 (提供一个内置的管理员列表)
      // 这是一个简单的实现，实际应用中应该从数据库获取或使用环境变量
      const adminEmails = [
        'admin@example.com', 
        Deno.env.get('ADMIN_EMAIL') || '',
        // 添加其他管理员邮箱
      ].filter(email => email !== '');
      
      // 验证用户是否为管理员
      if (!adminEmails.includes(user.email || '')) {
        return new Response(JSON.stringify({ error: '权限不足' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' }
        })
      }
      
      // 获取请求体
      const requestBody = await req.json()
      
      // 创建新的服务信息
      const { error } = await supabaseClient
        .from('service_messages')
        .insert({
          message: requestBody.content,
          updated_at: new Date().toISOString(),
          created_by: user.id
        })
      
      if (error) {
        console.error('更新服务信息失败:', error)
        return new Response(JSON.stringify({
          error: '更新服务信息失败',
          details: error.message
        }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        })
      }
      
      return new Response(JSON.stringify({ success: true }), {
        headers: { 'Content-Type': 'application/json' }
      })
    }
    
    // 处理不支持的请求方法
    return new Response(JSON.stringify({ error: '不支持的请求方法' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json' }
    })
    
  } catch (err) {
    console.error('服务信息处理失败:', err)
    return new Response(JSON.stringify({
      error: '服务器内部错误',
      details: err.message
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    })
  }
})
```

> **重要提示**: 在部署前，请修改以上代码中的 `adminEmails` 数组，将 `'admin@example.com'` 替换为实际管理员邮箱。或者，在 Supabase 项目设置中添加一个名为 `ADMIN_EMAIL` 的环境变量。

3. 使用 Supabase CLI 部署 Edge Function：

```bash
supabase functions deploy service-info --project-ref YOUR_PROJECT_REF
```

### 3. 配置管理工具

1. 在项目根目录的 `scripts` 文件夹中创建 `update_service_info.js` 文件，粘贴以下代码：

```javascript
#!/usr/bin/env node

/**
 * OneTV 服务信息更新工具
 * 
 * 此脚本用于通过命令行更新 Supabase 的服务信息
 * 使用方法: node update_service_info.js -m "您的服务信息内容"
 */

const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

// Supabase 配置
let SUPABASE_URL;
let SUPABASE_KEY;

try {
  // 尝试从环境变量加载配置
  SUPABASE_URL = process.env.SUPABASE_URL || '';
  SUPABASE_KEY = process.env.SUPABASE_ADMIN_KEY || '';
  
  // 如果环境变量不存在，尝试从配置文件加载
  if (!SUPABASE_URL || !SUPABASE_KEY) {
    const configPath = path.join(__dirname, 'config.json');
    if (fs.existsSync(configPath)) {
      const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      SUPABASE_URL = config.SUPABASE_URL;
      SUPABASE_KEY = config.SUPABASE_ADMIN_KEY;
    }
  }
  
  if (!SUPABASE_URL || !SUPABASE_KEY) {
    throw new Error('Supabase 配置缺失。请设置环境变量或提供配置文件。');
  }
} catch (error) {
  console.error('配置加载失败:', error.message);
  process.exit(1);
}

// 创建 Supabase 客户端
const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

// 命令行参数解析
const args = process.argv.slice(2);
let message = '';
let interactive = true;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '-m' || args[i] === '--message') {
    if (i + 1 < args.length) {
      message = args[i + 1];
      interactive = false;
    }
  }
}

// 交互式输入
async function promptForMessage() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });
  
  return new Promise(resolve => {
    rl.question('请输入服务信息内容: ', (answer) => {
      rl.close();
      resolve(answer);
    });
  });
}

// 更新服务信息
async function updateServiceInfo(content) {
  try {
    console.log('正在更新服务信息...');
    
    const { data, error } = await supabase
      .from('service_messages')
      .insert({ message: content })
      .select();
    
    if (error) {
      throw error;
    }
    
    console.log('服务信息更新成功!');
    console.log('更新时间:', new Date().toISOString());
    console.log('内容:', content);
  } catch (error) {
    console.error('更新失败:', error.message);
    process.exit(1);
  }
}

// 主函数
async function main() {
  console.log('OneTV 服务信息更新工具');
  console.log('------------------------');
  
  // 如果未通过命令行提供消息，则交互式请求输入
  if (interactive) {
    message = await promptForMessage();
  }
  
  if (!message || message.trim() === '') {
    console.error('错误: 服务信息内容不能为空!');
    process.exit(1);
  }
  
  await updateServiceInfo(message);
}

// 运行主函数
main().catch(error => {
  console.error('程序执行错误:', error);
  process.exit(1);
});
```

2. 在 `scripts` 目录中创建 `config.json` 文件（注意不要将此文件提交到版本控制系统）：

```json
{
  "SUPABASE_URL": "https://your-project-id.supabase.co",
  "SUPABASE_ADMIN_KEY": "your-supabase-admin-key"
}
```

3. 安装依赖：

```bash
npm install @supabase/supabase-js
```

4. 使工具脚本可执行（Linux/macOS）：

```bash
chmod +x scripts/update_service_info.js
```

### 4. 设置管理员邮箱

有两种方式设置允许更新服务信息的管理员账号：

1. **直接修改代码**：
   - 在 `service-info/index.ts` 文件中修改 `adminEmails` 数组，添加管理员邮箱

2. **使用环境变量**（推荐）：
   - 在 Supabase 项目控制台中，进入 "Settings" > "Function Settings"
   - 添加一个新的环境变量 `ADMIN_EMAIL`，值设为管理员邮箱
   - 如需多个管理员，可以使用逗号分隔，例如 `admin1@example.com,admin2@example.com`

## 使用方法

### 更新服务信息（使用命令行工具）

```bash
# 使用参数提供服务信息内容
node scripts/update_service_info.js -m "您的服务信息内容"

# 或者使用交互式模式
node scripts/update_service_info.js
```

### 在应用中显示服务信息

应用将自动通过 Supabase SDK 和 Edge Function 获取服务信息，并显示在设置页面的服务信息框中。

## 故障排除

1. **权限问题**：确保发布服务信息的账号邮箱已经被添加到 Edge Function 的管理员列表中。
2. **Edge Function 部署问题**：检查 Supabase 控制台中的函数日志。
3. **API 调用错误**：检查应用日志中的错误消息。

## 备注

- 服务信息会缓存在应用中，默认缓存时间为 3 天，并带有一个随机偏移（±12小时），以避免所有用户同时刷新。
- 当服务器数据更新时，缓存将在下一次应用启动时自动刷新。 