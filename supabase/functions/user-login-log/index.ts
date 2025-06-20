// 用户登录日志 Edge Function user-login-log
import { serve } from 'https://deno.land/std@0.170.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.7'

interface RequestBody {
  deviceInfo: string
  ipAddress?: string
}

serve(async (req) => {
  try {
    // 创建一个服务器端的Supabase客户端
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      {
        auth: {
          autoRefreshToken: false,
          persistSession: false
        }
      }
    )

    // 获取用户授权信息
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: '未授权访问' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // 从授权头中提取令牌
    const token = authHeader.replace('Bearer ', '')
    
    // 验证令牌并获取用户信息
    const { data: { user }, error: userError } = await supabaseClient.auth.getUser(token)
    
    if (userError || !user) {
      return new Response(
        JSON.stringify({ error: '无效的令牌', details: userError?.message }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      )
    }

    // 处理不同的HTTP方法
    if (req.method === 'POST') {
      // 解析请求体
      const requestData: RequestBody = await req.json()
      
      // 验证请求体
      if (!requestData.deviceInfo) {
        return new Response(
          JSON.stringify({ error: '缺少必要参数: deviceInfo' }),
          { status: 400, headers: { 'Content-Type': 'application/json' } }
        )
      }

      // 获取客户端IP地址
      const clientIp = requestData.ipAddress || req.headers.get('x-forwarded-for') || 'unknown'
      
      // 插入登录日志
      const { data, error } = await supabaseClient
        .from('user_login_logs')
        .insert({
          user_id: user.id,
          device_info: requestData.deviceInfo,
          ip_address: clientIp
        })
        .select()
      
      if (error) {
        console.error('Error logging user login:', error)
        return new Response(
          JSON.stringify({ error: '记录登录信息失败', details: error.message }),
          { status: 500, headers: { 'Content-Type': 'application/json' } }
        )
      }
      
      // 同时更新用户资料表中的最后登录信息
      await supabaseClient
        .from('profiles')
        .update({
          lastlogintime: new Date().toISOString(),
          lastlogindevice: requestData.deviceInfo
        })
        .eq('userid', user.id)
      
      return new Response(
        JSON.stringify({ 
          message: '登录信息已记录',
          data: data?.[0] || null
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    }
    
    // 不支持的HTTP方法
    return new Response(
      JSON.stringify({ error: '不支持的HTTP方法' }),
      { status: 405, headers: { 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    console.error('Unhandled error:', error)
    return new Response(
      JSON.stringify({ error: '服务器内部错误', details: error.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    )
  }
}) 