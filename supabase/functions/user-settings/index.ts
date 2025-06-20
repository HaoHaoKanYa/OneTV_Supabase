// Supabase Edge Function: user-settings
// 处理用户设置请求，提供获取和更新用户设置的功能

// user-settings Edge Function - 处理用户设置的获取和更新

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1"

/**
 * 从请求中获取Supabase客户端
 * @param req 请求对象
 * @returns 初始化的Supabase客户端
 */
const getSupabaseClient = (req: Request) => {
  const authHeader = req.headers.get('Authorization')
  if (!authHeader) {
    throw new Error('缺少认证信息')
  }
  
  // 从Authorization头部获取访问令牌
  const token = authHeader.replace('Bearer ', '')
  
  // 初始化Supabase客户端
  const supabaseClient = createClient(
    Deno.env.get('SUPABASE_URL') ?? '',
    Deno.env.get('SUPABASE_ANON_KEY') ?? '',
    {
      global: {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    }
  )
  
  return supabaseClient
}

/**
 * 获取用户设置
 * @param supabaseClient Supabase客户端
 * @returns 用户设置数据
 */
const getUserSettings = async (supabaseClient: any) => {
  try {
    // 获取当前用户信息
    const { data: { user }, error: userError } = await supabaseClient.auth.getUser()
    
    if (userError || !user) {
      throw new Error('获取用户信息失败：' + (userError?.message || '未知错误'))
    }
    
    // 从数据库中获取用户设置
    const { data, error } = await supabaseClient
      .from('user_settings')
      .select('*')
      .eq('user_id', user.id)
      .single()
    
    if (error) {
      console.error('获取用户设置失败:', error.message)
      
      // 如果找不到记录，创建默认设置
      if (error.code === 'PGRST116') {
        return {
          success: true,
          settings: {
            theme: 'dark',
            notification_enabled: true,
            player_settings: {
              autoPlay: true,
              highQuality: true
            },
            language_preference: 'zh-CN',
            timezone: 'Asia/Shanghai'
          }
        }
      }
      
      throw new Error('获取用户设置失败：' + error.message)
    }
    
    return {
      success: true,
      settings: data
    }
  } catch (error) {
    console.error('获取用户设置时发生错误:', error.message)
    return {
      success: false,
      error: error.message,
      settings: {
        theme: 'dark',
        notification_enabled: true,
        player_settings: {
          autoPlay: true,
          highQuality: true
        },
        language_preference: 'zh-CN',
        timezone: 'Asia/Shanghai'
      }
    }
  }
}

/**
 * 更新用户设置
 * @param supabaseClient Supabase客户端
 * @param settings 要更新的设置数据
 * @returns 更新结果
 */
const updateUserSettings = async (supabaseClient: any, settings: any) => {
  try {
    // 获取当前用户信息
    const { data: { user }, error: userError } = await supabaseClient.auth.getUser()
    
    if (userError || !user) {
      throw new Error('获取用户信息失败：' + (userError?.message || '未知错误'))
    }
    
    // 准备要更新的数据
    const updateData = {
      user_id: user.id,
      updated_at: new Date().toISOString(),
      ...settings
    }
    
    // 使用upsert确保即使没有记录也能创建
    const { data, error } = await supabaseClient
      .from('user_settings')
      .upsert(updateData)
      .eq('user_id', user.id)
      .select()
    
    if (error) {
      console.error('更新用户设置失败:', error)
      throw new Error('更新用户设置失败：' + error.message)
    }
    
    return {
      success: true,
      data
    }
  } catch (error) {
    console.error('更新用户设置时发生错误:', error)
    return {
      success: false,
      error: error.message
    }
  }
}

/**
 * 处理请求的主函数
 */
serve(async (req) => {
  // 设置CORS头
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
    'Content-Type': 'application/json'
  }
  
  // 处理预检请求
  if (req.method === 'OPTIONS') {
    return new Response(null, {
      headers,
      status: 204,
    })
  }
  
  try {
    // 获取Supabase客户端
    const supabaseClient = getSupabaseClient(req)
    
    let result
    
    // 根据HTTP方法处理请求
    if (req.method === 'GET') {
      // 获取用户设置
      result = await getUserSettings(supabaseClient)
    } else if (req.method === 'POST' || req.method === 'PUT') {
      // 更新用户设置
      const settings = await req.json()
      result = await updateUserSettings(supabaseClient, settings)
    } else {
      return new Response(
        JSON.stringify({ 
          success: false, 
          error: '不支持的HTTP方法' 
        }),
        { headers, status: 405 }
      )
    }
    
    // 返回结果
    return new Response(
      JSON.stringify(result),
      { headers, status: 200 }
    )
  } catch (error) {
    console.error('处理请求时发生错误:', error)
    
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || '处理请求时发生错误'
      }),
      { headers, status: 500 }
    )
  }
}) 