// 观看历史批量upsert Edge Function
import { serve } from 'https://deno.land/std@0.170.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.7'

// CORS 头部设置
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
}

// 统一使用项目的 SupabaseWatchHistoryItem 数据类型结构
interface SupabaseWatchHistoryItem {
  id?: string
  channelName: string
  channelUrl: string
  duration: number  // 对应 Kotlin 的 Long，在 TypeScript 中使用 number
  watchStart: string
  watchEnd?: string
  userId?: string
}

interface BatchUpsertRequest {
  records: SupabaseWatchHistoryItem[]
}

serve(async (req) => {
  try {
    // 检查请求方法
    if (req.method === 'OPTIONS') {
      return new Response('ok', { headers: corsHeaders })
    }

    // 解析请求参数
    const body = await req.json()
    const { records, userId } = body

    // 验证必需参数
    if (!userId) {
      return new Response(
        JSON.stringify({ error: '缺少用户ID参数' }),
        { status: 400, headers: corsHeaders }
      )
    }

    if (!records || !Array.isArray(records) || records.length === 0) {
      return new Response(
        JSON.stringify({ error: '缺少records参数或records为空' }),
        { status: 400, headers: corsHeaders }
      )
    }

    // 直接使用SERVICE_ROLE_KEY创建数据库操作客户端
    // 这是最简单可靠的方式，避免所有认证复杂性
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      { auth: { persistSession: false } }
    )

    // 只支持POST方法进行批量upsert
    if (req.method !== 'POST') {
      return new Response(
        JSON.stringify({ error: '只支持POST方法' }),
        { status: 405, headers: corsHeaders }
      )
    }

    return await batchUpsertWatchHistory(records, supabaseClient, userId)

  } catch (error) {
    console.error('watch_history_upsert function error:', error)
    return new Response(
      JSON.stringify({ error: '服务器内部错误', details: error.message }),
      { status: 500, headers: corsHeaders }
    )
  }
})

// 批量upsert观看历史记录
async function batchUpsertWatchHistory(records: SupabaseWatchHistoryItem[], supabaseClient: any, userId: string) {
  try {
    // 验证参数
    if (!records || !Array.isArray(records) || records.length === 0) {
      return new Response(
        JSON.stringify({ error: '缺少records参数或records为空' }),
        { status: 400, headers: corsHeaders }
      )
    }

    console.log(`开始批量upsert ${records.length} 条观看历史记录，用户ID: ${userId}`)

    // 准备插入数据
    const now = new Date().toISOString()
    const insertData = records.map((record, index) => {
      const { channelName, channelUrl, duration, watchStart, watchEnd } = record

      // 验证必需字段
      if (!channelName || !channelUrl || !duration) {
        console.error(`记录 #${index + 1} 缺少必需字段:`, { channelName, channelUrl, duration })
        throw new Error(`记录缺少必需字段: channelName=${channelName}, channelUrl=${channelUrl}, duration=${duration}`)
      }

      const startTime = watchStart || new Date(Date.now() - duration * 1000).toISOString()
      const endTime = watchEnd || now

      const insertRecord = {
        user_id: userId,
        channel_name: channelName,
        channel_url: channelUrl,
        duration: Number(duration), // 确保duration是数字类型，匹配SupabaseWatchHistoryItem.duration(Long)
        watch_start: startTime,
        watch_end: endTime,
        created_at: now
      }

      console.log(`准备插入记录 #${index + 1}:`, insertRecord)
      return insertRecord
    })

    // 执行批量upsert操作
    // 使用upsert来处理重复记录，基于数据库的唯一约束 (user_id, channel_name, channel_url, watch_start)
    const { data, error } = await supabaseClient
      .from('watch_history')
      .upsert(insertData, {
        onConflict: 'user_id,channel_name,channel_url,watch_start'
      })
      .select()

    if (error) {
      console.error('批量upsert失败:', {
        error: error,
        message: error.message,
        details: error.details,
        hint: error.hint,
        code: error.code
      })
      throw error
    }

    const insertedCount = data?.length || 0
    const duplicatesCount = records.length - insertedCount

    console.log(`批量upsert完成: 插入=${insertedCount}, 重复=${duplicatesCount}`)

    return new Response(
      JSON.stringify({
        success: true,
        data: {
          inserted: insertedCount,
          duplicates: duplicatesCount,
          total: records.length
        },
        message: `成功处理 ${records.length} 条记录`
      }),
      { headers: corsHeaders }
    )

  } catch (error) {
    console.error('batchUpsertWatchHistory error:', {
      error: error,
      message: error.message,
      stack: error.stack,
      name: error.name
    })
    return new Response(
      JSON.stringify({
        error: '批量upsert观看历史失败',
        details: error.message,
        errorType: error.name,
        stack: error.stack
      }),
      { status: 500, headers: corsHeaders }
    )
  }
}