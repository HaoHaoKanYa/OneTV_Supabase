// Supabase Edge Function: vip-management
// 处理VIP会员管理，提供查询、激活和续费VIP的功能
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
    
    // 获取请求路径
    const url = new URL(req.url)
    const path = url.pathname.split('/').pop()
    
    // 根据路径处理不同的VIP操作
    if (path === 'status') {
      // 获取VIP状态
      return await handleVipStatus(supabaseAdmin, user.id)
    } else if (path === 'activate') {
      // 激活VIP
      return await handleVipActivation(supabaseAdmin, user.id, req)
    } else if (path === 'renew') {
      // 续费VIP
      return await handleVipRenewal(supabaseAdmin, user.id, req)
    } else {
      return new Response(
        JSON.stringify({ error: '无效的请求路径' }),
        { status: 404, headers: corsHeaders }
      )
    }
  } catch (error) {
    // 捕获未预期的异常
    console.error('处理VIP请求失败:', error)
    return new Response(
      JSON.stringify({ 
        error: '服务器内部错误', 
        details: error.message
      }),
      { status: 500, headers: corsHeaders }
    )
  }
})

// 处理VIP状态查询
async function handleVipStatus(supabase, userId) {
  // 查询用户资料
  const { data: profile, error } = await supabase
    .from('profiles')
    .select('is_vip, vipstart, vipend')
    .eq('userid', userId)
    .single()
  
  if (error) {
    return new Response(
      JSON.stringify({ error: '获取VIP状态失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  // 检查VIP是否过期
  const now = new Date()
  const vipEnd = profile.vipend ? new Date(profile.vipend) : null
  const isActive = profile.is_vip && vipEnd && vipEnd > now
  
  return new Response(
    JSON.stringify({
      is_vip: isActive,
      vip_start: profile.vipstart,
      vip_end: profile.vipend,
      days_remaining: vipEnd ? Math.ceil((vipEnd.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)) : 0
    }),
    { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
  )
}

// 处理VIP激活
async function handleVipActivation(supabase, userId, req) {
  // 获取激活码
  const { activationCode } = await req.json()
  
  if (!activationCode) {
    return new Response(
      JSON.stringify({ error: '缺少激活码' }),
      { status: 400, headers: corsHeaders }
    )
  }
  
  // 查询激活码
  const { data: codeData, error: codeError } = await supabase
    .from('activation_codes')
    .select('*')
    .eq('code', activationCode)
    .eq('is_used', false)
    .single()
  
  if (codeError || !codeData) {
    return new Response(
      JSON.stringify({ error: '无效的激活码或已被使用' }),
      { status: 400, headers: corsHeaders }
    )
  }
  
  // 计算VIP有效期
  const now = new Date()
  const vipDuration = codeData.duration_days || 30 // 默认30天
  
  // 查询用户当前VIP状态
  const { data: profile } = await supabase
    .from('profiles')
    .select('is_vip, vipend')
    .eq('userid', userId)
    .single()
  
  let vipStart = now
  let vipEnd = new Date(now)
  vipEnd.setDate(vipEnd.getDate() + vipDuration)
  
  // 如果用户已经是VIP且未过期，则延长期限
  if (profile.is_vip && profile.vipend && new Date(profile.vipend) > now) {
    vipEnd = new Date(profile.vipend)
    vipEnd.setDate(vipEnd.getDate() + vipDuration)
  }
  
  // 开始数据库事务
  const { error: transactionError } = await supabase.rpc('activate_vip', {
    p_user_id: userId,
    p_code_id: codeData.id,
    p_vip_start: vipStart.toISOString(),
    p_vip_end: vipEnd.toISOString()
  })
  
  if (transactionError) {
    return new Response(
      JSON.stringify({ error: 'VIP激活失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  return new Response(
    JSON.stringify({
      message: 'VIP激活成功',
      vip_start: vipStart.toISOString(),
      vip_end: vipEnd.toISOString(),
      days: vipDuration
    }),
    { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
  )
}

// 处理VIP续费
async function handleVipRenewal(supabase, userId, req) {
  // 获取续费信息
  const { months } = await req.json()
  
  if (!months || months < 1) {
    return new Response(
      JSON.stringify({ error: '无效的续费月数' }),
      { status: 400, headers: corsHeaders }
    )
  }
  
  // 计算续费天数
  const renewDays = months * 30
  
  // 查询用户当前VIP状态
  const { data: profile, error } = await supabase
    .from('profiles')
    .select('is_vip, vipend')
    .eq('userid', userId)
    .single()
  
  if (error) {
    return new Response(
      JSON.stringify({ error: '获取用户VIP状态失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  const now = new Date()
  let vipEnd
  
  // 如果用户已经是VIP且未过期，则延长期限
  if (profile.is_vip && profile.vipend && new Date(profile.vipend) > now) {
    vipEnd = new Date(profile.vipend)
  } else {
    vipEnd = now
  }
  
  // 添加续费天数
  vipEnd.setDate(vipEnd.getDate() + renewDays)
  
  // 更新用户VIP状态
  const { error: updateError } = await supabase
    .from('profiles')
    .update({
      is_vip: true,
      vipend: vipEnd.toISOString(),
      updated_at: now.toISOString()
    })
    .eq('userid', userId)
  
  if (updateError) {
    return new Response(
      JSON.stringify({ error: 'VIP续费失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  // 记录续费历史
  await supabase
    .from('vip_transactions')
    .insert({
      user_id: userId,
      transaction_type: 'renewal',
      amount: months,
      created_at: now.toISOString()
    })
  
  return new Response(
    JSON.stringify({
      message: 'VIP续费成功',
      vip_end: vipEnd.toISOString(),
      days_added: renewDays
    }),
    { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
  )
}

// CORS 头配置
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
} 