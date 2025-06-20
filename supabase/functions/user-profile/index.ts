// Supabase Edge Function: user-profile
// 处理用户资料请求，提供获取和更新用户资料的功能
import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.42.0'

// 处理请求
serve(async (req) => {
  try {
    // 检查请求方法
    if (req.method === 'OPTIONS') {
      return new Response('ok', { headers: corsHeaders })
    }
    
    // 创建Supabase客户端
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )
    
    // 从请求头获取授权信息
    const authHeader = req.headers.get('Authorization')
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return new Response(
        JSON.stringify({ error: '未授权' }),
        { status: 401, headers: corsHeaders }
      )
    }
    
    const token = authHeader.substring(7)
    
    // 验证用户Token
    const { data: { user }, error } = await supabaseAdmin.auth.getUser(token)
    
    if (error || !user) {
      return new Response(
        JSON.stringify({ error: '无效的认证令牌' }),
        { status: 401, headers: corsHeaders }
      )
    }
    
    // 根据请求方法处理
    if (req.method === 'GET') {
      // 获取用户资料
      const { data: profile, error: profileError } = await supabaseAdmin
        .from('profiles')
        .select('*')
        .eq('userid', user.id)
        .single()
      
      if (profileError) {
        return new Response(
          JSON.stringify({ error: '获取用户资料失败' }),
          { status: 500, headers: corsHeaders }
        )
      }
      
      // 返回用户资料
      return new Response(
        JSON.stringify({
          id: profile.userid,
          email: user.email,
          username: profile.username,
          is_vip: profile.is_vip,
          vip_expiry: profile.vipend,
          created_at: profile.created_at,
          last_login: profile.lastlogintime,
          device_info: profile.lastlogindevice,
          account_status: profile.accountstatus
        }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    } else if (req.method === 'POST' || req.method === 'PUT') {
      // 更新用户资料
      const requestData = await req.json()
      
      // 只允许更新特定字段
      const allowedFields = ['username', 'avatar_url']
      const updateData = {}
      
      Object.keys(requestData).forEach(key => {
        if (allowedFields.includes(key)) {
          updateData[key] = requestData[key]
        }
      })
      
      if (Object.keys(updateData).length === 0) {
        return new Response(
          JSON.stringify({ error: '没有可更新的字段' }),
          { status: 400, headers: corsHeaders }
        )
      }
      
      // 更新用户资料
      const { error: updateError } = await supabaseAdmin
        .from('profiles')
        .update({
          ...updateData,
          updated_at: new Date().toISOString()
        })
        .eq('userid', user.id)
      
      if (updateError) {
        return new Response(
          JSON.stringify({ error: '更新用户资料失败' }),
          { status: 500, headers: corsHeaders }
        )
      }
      
      return new Response(
        JSON.stringify({ message: '用户资料更新成功' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    } else {
      return new Response(
        JSON.stringify({ error: '不支持的请求方法' }),
        { status: 405, headers: corsHeaders }
      )
    }
  } catch (error) {
    // 捕获未预期的异常
    console.error('处理用户资料请求失败:', error)
    return new Response(
      JSON.stringify({ 
        error: '服务器内部错误', 
        details: error.message
      }),
      { status: 500, headers: corsHeaders }
    )
  }
})

// CORS 头配置
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
} 