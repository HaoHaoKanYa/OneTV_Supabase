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
      console.log('服务信息数据:', JSON.stringify(data, null, 2))

      const response = {
        content: data.message || '暂无服务信息',
        last_updated: Math.floor(new Date(data.updated_at).getTime() / 1000) // 转为Unix时间戳(秒)
      }

      console.log('返回响应:', JSON.stringify(response, null, 2))
      
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
      
      // 获取管理员列表 (支持从环境变量读取多个管理员)
      const defaultAdmins = ['admin@example.com']; // 默认管理员（开发环境）
      
      // 从环境变量获取管理员邮箱列表（可用逗号分隔多个邮箱）
      const envAdmins = Deno.env.get('ADMIN_EMAIL') || '';
      const envAdminsList = envAdmins.split(',').map(email => email.trim()).filter(email => email !== '');
      
      // 合并默认管理员和环境变量中的管理员
      const adminEmails = [...defaultAdmins, ...envAdminsList].filter(email => email !== '');
      
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