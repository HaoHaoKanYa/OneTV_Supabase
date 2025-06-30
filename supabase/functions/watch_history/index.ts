// 观看历史 Edge Function
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

interface GetWatchHistoryParams {
  page?: number
  pageSize?: number
  timeRange?: string
  sortBy?: string
  sortOrder?: string
}

serve(async (req) => {
  try {
    // 检查请求方法
    if (req.method === 'OPTIONS') {
      return new Response('ok', { headers: corsHeaders })
    }

    // 根据请求方法获取用户ID
    let userId: string | undefined

    if (req.method === 'GET') {
      // GET请求：从URL参数获取用户ID
      const url = new URL(req.url)
      userId = url.searchParams.get('userId') || undefined
    } else if (req.method === 'POST') {
      // POST请求：从请求体获取用户ID
      try {
        const bodyText = await req.text()
        if (bodyText && bodyText.trim()) {
          const body = JSON.parse(bodyText)
          userId = body.userId
        }
      } catch (e) {
        console.error('JSON解析错误:', e)
        return new Response(
          JSON.stringify({ error: 'JSON格式错误' }),
          { status: 400, headers: corsHeaders }
        )
      }
    }

    // 验证必需参数
    if (!userId) {
      return new Response(
        JSON.stringify({ error: '缺少用户ID参数' }),
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

    // 根据请求方法处理不同操作
    if (req.method === 'GET') {
      // 获取观看历史列表
      return await getWatchHistory(req, supabaseClient, userId)
    } else if (req.method === 'POST') {
      // 记录观看历史
      const body = await req.json()
      return await recordWatchHistory(body, supabaseClient, userId)
    } else {
      return new Response(
        JSON.stringify({ error: '不支持的请求方法' }),
        { status: 405, headers: corsHeaders }
      )
    }

  } catch (error) {
    console.error('watch_history function error:', error)
    return new Response(
      JSON.stringify({ error: '服务器内部错误', details: error.message }),
      { status: 500, headers: corsHeaders }
    )
  }
})

// 获取观看历史列表
async function getWatchHistory(req: Request, supabaseClient: any, userId: string) {
  try {
    const url = new URL(req.url)
    const page = parseInt(url.searchParams.get('page') || '1')
    const pageSize = parseInt(url.searchParams.get('pageSize') || '20')
    const timeRange = url.searchParams.get('timeRange') || 'all'
    const sortBy = url.searchParams.get('sortBy') || 'watch_start'
    const sortOrder = url.searchParams.get('sortOrder') || 'desc'

    // 构建查询
    let query = supabaseClient
      .from('watch_history')
      .select('*', { count: 'exact' })
      .eq('user_id', userId)

    // 时间范围过滤
    if (timeRange !== 'all') {
      const now = new Date()
      let startDate: Date

      switch (timeRange) {
        case 'today':
          startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate())
          break
        case 'week':
          startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
          break
        case 'month':
          startDate = new Date(now.getFullYear(), now.getMonth(), 1)
          break
        case 'year':
          startDate = new Date(now.getFullYear(), 0, 1)
          break
        default:
          startDate = new Date(0)
      }

      query = query.gte('watch_start', startDate.toISOString())
    }

    // 排序
    query = query.order(sortBy, { ascending: sortOrder === 'asc' })

    // 分页
    const from = (page - 1) * pageSize
    const to = from + pageSize - 1
    query = query.range(from, to)

    const { data, error, count } = await query

    if (error) {
      throw error
    }

    // 计算统计数据
    const totalWatchTime = data?.reduce((sum: number, item: any) => sum + (item.duration || 0), 0) || 0
    const totalChannels = new Set(data?.map((item: any) => item.channel_name)).size
    const totalRecords = count || 0

    return new Response(
      JSON.stringify({
        success: true,
        items: data || [],
        pagination: {
          page,
          pageSize,
          totalRecords,
          totalPages: Math.ceil(totalRecords / pageSize)
        },
        statistics: {
          totalWatchTime,
          totalChannels,
          totalRecords
        }
      }),
      { headers: corsHeaders }
    )

  } catch (error) {
    console.error('getWatchHistory error:', error)
    return new Response(
      JSON.stringify({ error: '获取观看历史失败', details: error.message }),
      { status: 500, headers: corsHeaders }
    )
  }
}

// 记录观看历史
async function recordWatchHistory(body: any, supabaseClient: any, userId: string) {
  try {
    const { channelName, channelUrl, duration, watchStart, watchEnd } = body as SupabaseWatchHistoryItem

    // 验证必需参数
    if (!channelName || !channelUrl || !duration) {
      return new Response(
        JSON.stringify({ error: '缺少必需参数: channelName, channelUrl, duration' }),
        { status: 400, headers: corsHeaders }
      )
    }

    // 生成时间戳
    const now = new Date().toISOString()
    const startTime = watchStart || new Date(Date.now() - duration * 1000).toISOString()
    const endTime = watchEnd || now

    // 插入观看历史记录
    const { data, error } = await supabaseClient
      .from('watch_history')
      .insert({
        user_id: userId,
        channel_name: channelName,
        channel_url: channelUrl,
        duration: Number(duration), // 确保duration是数字类型，匹配SupabaseWatchHistoryItem.duration(Long)
        watch_start: startTime,
        watch_end: endTime,
        created_at: now
      })
      .select()

    if (error) {
      throw error
    }

    return new Response(
      JSON.stringify({
        success: true,
        data: data?.[0] || null,
        message: '观看历史记录成功'
      }),
      { headers: corsHeaders }
    )

  } catch (error) {
    console.error('recordWatchHistory error:', error)
    return new Response(
      JSON.stringify({ error: '记录观看历史失败', details: error.message }),
      { status: 500, headers: corsHeaders }
    )
  }
}