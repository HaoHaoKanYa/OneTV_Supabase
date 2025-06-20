// Supabase Edge Function: app-configs
// 处理App配置的获取，提供根据app_id获取配置的功能
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // 处理CORS预检请求
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    // 解析请求参数
    const { app_id } = await req.json()
    
    // 验证参数
    if (!app_id) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameter: app_id' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 }
      )
    }

    // 创建Supabase客户端
    // 注意: 此处使用服务端角色访问数据库，确保安全
    const supabaseClient = createClient(
      // 使用环境变量访问
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      { auth: { persistSession: false } }
    )

    // 查询app_configs表，获取活跃的配置
    const { data, error } = await supabaseClient
      .from('app_configs')
      .select('*')
      .eq('app_id', app_id)
      .eq('is_active', true)
      .single()

    if (error) {
      console.error('Error fetching app config:', error)
      return new Response(
        JSON.stringify({ error: 'Failed to fetch app configuration' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 500 }
      )
    }

    if (!data) {
      return new Response(
        JSON.stringify({ error: 'App configuration not found' }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 404 }
      )
    }

    // 返回配置数据
    return new Response(
      JSON.stringify(data),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 }
    )

  } catch (error) {
    console.error('Unexpected error:', error)
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 500 }
    )
  }
}) 