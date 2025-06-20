// Supabase Edge Function: channel-favorites
// 处理频道收藏功能，提供添加、删除和查询收藏频道的功能
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
      // 获取用户收藏的频道
      return await getFavoriteChannels(supabaseAdmin, user.id)
    } else if (req.method === 'POST') {
      // 添加收藏频道
      return await addFavoriteChannel(supabaseAdmin, user.id, req)
    } else if (req.method === 'DELETE') {
      // 删除收藏频道
      return await deleteFavoriteChannel(supabaseAdmin, user.id, req)
    } else {
      return new Response(
        JSON.stringify({ error: '不支持的请求方法' }),
        { status: 405, headers: corsHeaders }
      )
    }
  } catch (error) {
    // 捕获未预期的异常
    console.error('处理频道收藏请求失败:', error)
    return new Response(
      JSON.stringify({ 
        error: '服务器内部错误', 
        details: error.message
      }),
      { status: 500, headers: corsHeaders }
    )
  }
})

// 获取用户收藏的频道
async function getFavoriteChannels(supabase, userId) {
  // 查询用户收藏的频道
  const { data, error } = await supabase
    .from('channel_favorites')
    .select('*')
    .eq('user_id', userId)
    .order('created_at', { ascending: false })
  
  if (error) {
    return new Response(
      JSON.stringify({ error: '获取收藏频道失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  return new Response(
    JSON.stringify({ favorites: data }),
    { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
  )
}

// 添加收藏频道
async function addFavoriteChannel(supabase, userId, req) {
  // 获取频道信息
  const { channelName, channelUrl } = await req.json()
  
  if (!channelName || !channelUrl) {
    return new Response(
      JSON.stringify({ error: '缺少频道名称或URL' }),
      { status: 400, headers: corsHeaders }
    )
  }
  
  // 检查是否已经收藏
  const { data: existingData } = await supabase
    .from('channel_favorites')
    .select('id')
    .eq('user_id', userId)
    .eq('channel_url', channelUrl)
    .maybeSingle()
  
  if (existingData) {
    return new Response(
      JSON.stringify({ message: '该频道已收藏', id: existingData.id }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
  
  // 添加收藏
  const { data, error } = await supabase
    .from('channel_favorites')
    .insert({
      user_id: userId,
      channel_name: channelName,
      channel_url: channelUrl,
      created_at: new Date().toISOString()
    })
    .select()
  
  if (error) {
    return new Response(
      JSON.stringify({ error: '添加收藏失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  return new Response(
    JSON.stringify({ message: '添加收藏成功', favorite: data[0] }),
    { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
  )
}

// 删除收藏频道
async function deleteFavoriteChannel(supabase, userId, req) {
  // 获取频道URL或ID
  const url = new URL(req.url)
  const favoriteId = url.searchParams.get('id')
  const channelUrl = url.searchParams.get('url')
  
  if (!favoriteId && !channelUrl) {
    return new Response(
      JSON.stringify({ error: '缺少收藏ID或频道URL' }),
      { status: 400, headers: corsHeaders }
    )
  }
  
  let query = supabase
    .from('channel_favorites')
    .delete()
    .eq('user_id', userId)
  
  // 根据ID或URL删除
  if (favoriteId) {
    query = query.eq('id', favoriteId)
  } else {
    query = query.eq('channel_url', channelUrl)
  }
  
  const { error } = await query
  
  if (error) {
    return new Response(
      JSON.stringify({ error: '删除收藏失败' }),
      { status: 500, headers: corsHeaders }
    )
  }
  
  return new Response(
    JSON.stringify({ message: '删除收藏成功' }),
    { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
  )
}

// CORS 头配置
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
} 