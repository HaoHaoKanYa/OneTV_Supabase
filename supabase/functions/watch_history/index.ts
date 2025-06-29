// Supabase Edge Function: watch_history
// 处理观看历史数据，提供分页、过滤和统计功能
// Edge Function for handling watch history data
// This function provides efficient data loading with pagination, filtering and statistics

import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

/**
 * 统一的观看历史记录数据模型
 */
interface WatchHistoryItem {
  id?: string;
  channelName: string;
  channelUrl: string;
  duration: number;
  watchStart: string;
  watchEnd: string;
  userId?: string;
}

/**
 * 数据库表字段映射
 */
const DB_FIELD_MAP = {
  id: 'id',
  channelName: 'channel_name',
  channelUrl: 'channel_url',
  duration: 'duration',
  watchStart: 'watch_start',
  watchEnd: 'watch_end',
  userId: 'user_id'
};

/**
 * 将数据库记录转换为统一的WatchHistoryItem格式
 */
function dbRecordToWatchHistoryItem(record: any): WatchHistoryItem {
  return {
    id: record.id,
    channelName: record.channel_name,
    channelUrl: record.channel_url,
    duration: record.duration,
    watchStart: record.watch_start,
    watchEnd: record.watch_end,
    userId: record.user_id
  };
}

/**
 * 将统一的WatchHistoryItem转换为数据库记录格式
 */
function watchHistoryItemToDbRecord(item: WatchHistoryItem, userId: string): any {
  return {
    id: item.id,
    channel_name: item.channelName,
    channel_url: item.channelUrl,
    duration: item.duration,
    watch_start: item.watchStart,
    watch_end: item.watchEnd,
    user_id: userId
  };
}

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
    console.log(`处理请求: ${req.url}, 方法: ${req.method}`);
    
    // 创建服务端Supabase客户端
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )
    
    const url = new URL(req.url)
    const action = url.searchParams.get('action') || 'list'
    console.log(`请求动作: ${action}`);
    
    // 从请求中获取用户ID
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      console.error("缺少授权头");
      return new Response(
        JSON.stringify({ error: 'Missing Authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // 提取JWT令牌（简化处理，与其他Edge Function保持一致）
    const token = authHeader.substring(7)
    console.log(`使用JWT令牌: ${token.substring(0, 20)}...`)

    const { data: { user }, error: userError } = await supabaseClient.auth.getUser(token)
    
    if (userError || !user) {
      console.error("无效令牌或未找到用户", userError);
      return new Response(
        JSON.stringify({ error: 'Invalid token or user not found' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }
    
    const userId = user.id
    console.log(`已验证用户: ${userId}`);
    
    // 处理POST请求 - 记录观看历史
    if (req.method === 'POST') {
      try {
        // 安全地解析JSON请求体
        let body;
        try {
          const bodyText = await req.text();
          if (bodyText && bodyText.trim()) {
            body = JSON.parse(bodyText);
          } else {
            body = {};
          }
        } catch (parseError) {
          console.error("解析请求体失败:", parseError);
          return new Response(
            JSON.stringify({ success: false, error: "无效的JSON请求体" }),
            { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }
        
        // 处理批量上传
        if (action === 'batch') {
          console.log("处理批量上传观看历史记录");

          if (!Array.isArray(body.records)) {
            return new Response(
              JSON.stringify({ success: false, error: 'Missing records array' }),
              { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
            )
          }

          const records = body.records as WatchHistoryItem[];
          const validRecords = [];
          const recordHashes = new Set(); // 用于检查本批次内的重复记录

          // 验证并准备记录
          for (const record of records) {
            const { channelName, channelUrl, watchStart, watchEnd, duration } = record;

            // 记录原始数据用于调试
            console.log(`处理记录: 频道=${channelName}, URL=${channelUrl?.substring(0, 30)}..., 时长=${duration}`);

            // 验证必要参数
            if (!channelName || !channelUrl || !duration || duration <= 0) {
              console.log(`跳过无效记录: ${JSON.stringify(record)}, 原因: ${!channelName ? '无频道名' : !channelUrl ? '无URL' : '无效时长'}`);
              continue;
            }

            // 生成记录的唯一标识（基于关键字段）
            const recordHash = `${userId}_${channelName}_${channelUrl}_${watchStart || new Date(new Date().getTime() - duration * 1000).toISOString()}`;

            // 检查本批次内是否有重复记录
            if (recordHashes.has(recordHash)) {
              console.log(`跳过本批次内重复记录: 频道=${channelName}, 时长=${duration}`);
              continue;
            }
            recordHashes.add(recordHash);

            const watchStartTime = watchStart || new Date(new Date().getTime() - duration * 1000).toISOString();
            const watchEndTime = watchEnd || new Date().toISOString();

            // 转换为数据库记录格式
            validRecords.push({
              user_id: userId,
              channel_name: channelName,
              channel_url: channelUrl,
              watch_start: watchStartTime,
              watch_end: watchEndTime,
              duration: duration,
              created_at: new Date().toISOString()
            });
          }

          if (validRecords.length === 0) {
            console.log("没有有效记录可插入");
            return new Response(
              JSON.stringify({ success: false, error: 'No valid records to insert' }),
              { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
            )
          }

          console.log(`准备插入 ${validRecords.length} 条有效记录（已去除本批次内重复）`);

          // 使用upsert操作处理重复记录，提高并发安全性
          let insertedCount = 0;
          let duplicateCount = 0;
          const insertedRecords = [];

          // 批量处理，但使用upsert确保原子性
          for (const record of validRecords) {
            try {
              // 使用upsert操作，如果记录已存在则忽略
              const { data, error } = await supabaseClient
                .from('watch_history')
                .upsert(record, {
                  onConflict: 'user_id,channel_name,channel_url,watch_start',
                  ignoreDuplicates: true
                })
                .select();

              if (error) {
                console.error(`插入记录失败: ${error.message}`);
                // 如果是唯一性约束错误，计为重复
                if (error.code === '23505') {
                  duplicateCount++;
                  console.log(`重复记录（约束）: 频道=${record.channel_name}, 开始时间=${record.watch_start}`);
                }
              } else if (data && data.length > 0) {
                // 成功插入新记录
                insertedCount++;
                insertedRecords.push(data[0]);
                console.log(`成功插入记录: 频道=${record.channel_name}, ID=${data[0].id}`);
              } else {
                // 记录已存在，被忽略
                duplicateCount++;
                console.log(`重复记录（忽略）: 频道=${record.channel_name}, 开始时间=${record.watch_start}`);
              }
            } catch (e) {
              console.error(`处理记录时出错: ${e.message}`);
              // 如果是唯一性约束错误，计为重复
              if (e.code === '23505') {
                duplicateCount++;
              }
            }
          }

          console.log(`批量处理完成: 插入 ${insertedCount} 条新记录, 跳过 ${duplicateCount} 条重复记录`);

          return new Response(
            JSON.stringify({
              success: true,
              message: insertedCount > 0 ?
                `成功插入 ${insertedCount} 条记录，跳过 ${duplicateCount} 条重复记录` :
                "所有记录都已存在",
              data: {
                inserted: insertedCount,
                duplicates: duplicateCount,
                total: validRecords.length,
                records: insertedRecords
              }
            }),
            { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          )
        } else {
          // 处理单条记录
          const item = body as WatchHistoryItem;
          const { channelName, channelUrl, duration } = item;

          console.log(`记录观看历史: ${channelName}, 时长: ${duration}秒`);

          // 验证必要参数
          if (!channelName || !channelUrl || duration === undefined || duration <= 0) {
            console.log(`无效参数: 频道=${channelName}, URL=${channelUrl ? '有值' : '无值'}, 时长=${duration}`);
            return new Response(
              JSON.stringify({ success: false, error: 'Missing or invalid required parameters: channelName, channelUrl, duration' }),
              { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
            )
          }

          // 记录观看历史
          const now = new Date().toISOString()

          // 计算开始时间（根据结束时间和持续时间）
          const startTime = new Date(new Date().getTime() - duration * 1000).toISOString()

          console.log(`准备插入记录: 频道=${channelName}, 开始=${startTime}, 结束=${now}, 时长=${duration}秒`);

          // 使用upsert操作确保并发安全
          const { data, error } = await supabaseClient
            .from('watch_history')
            .upsert({
              user_id: userId,
              channel_name: channelName,
              channel_url: channelUrl,
              watch_start: startTime,
              watch_end: now,
              duration: duration,
              created_at: now
            }, {
              onConflict: 'user_id,channel_name,channel_url,watch_start',
              ignoreDuplicates: true
            })
            .select()

          if (error) {
            console.error("Upsert观看历史记录失败:", error);
            // 如果是唯一性约束错误，返回重复记录信息
            if (error.code === '23505') {
              console.log(`重复记录: 频道=${channelName}, 开始时间=${startTime}`);
              return new Response(
                JSON.stringify({
                  success: true,
                  message: "记录已存在，跳过插入",
                  data: {
                    duplicate: true,
                    existing_record: null
                  }
                }),
                { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
              )
            }
            return new Response(
              JSON.stringify({ success: false, error: error.message }),
              { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
            )
          }

          const isNewRecord = data && data.length > 0;
          console.log(`观看历史记录处理完成: ${isNewRecord ? '插入新记录' : '重复记录忽略'}`);

          return new Response(
            JSON.stringify({
              success: true,
              message: isNewRecord ? "成功插入新记录" : "记录已存在，跳过插入",
              data: {
                duplicate: !isNewRecord,
                record: data?.[0] || null
              }
            }),
            { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          )
        }
      } catch (e) {
        console.error("处理POST请求时出错:", e);
        return new Response(
          JSON.stringify({ success: false, error: e.message || "处理POST请求时出错" }),
          { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        )
      }
    }
    
    // 解析请求参数
    let body = {}
    if (req.method === 'POST') {
      try {
        const bodyText = await req.text();
        if (bodyText && bodyText.trim()) {
          body = JSON.parse(bodyText);
        }
      } catch (e) {
        console.error("解析请求体失败:", e);
        // 如果解析失败，使用空对象
      }
    }
    
    const queryParams = Object.fromEntries(url.searchParams)
    const params = { ...queryParams, ...body }
    
    // 获取分页参数
    const page = parseInt(params.page) || 1
    const pageSize = parseInt(params.pageSize) || 20
    const offset = (page - 1) * pageSize
    
    // 获取时间范围参数
    const timeRange = params.timeRange || 'all'
    const startDate = params.startDate
    const endDate = params.endDate
    
    // 获取排序参数
    const sortBy = params.sortBy || 'watch_start'
    const sortOrder = params.sortOrder || 'desc'
    
    let response
    
    switch (action) {
      case 'list':
        try {
          console.log(`获取观看历史列表: 页码=${page}, 每页=${pageSize}, 时间范围=${timeRange}, 排序=${sortBy}:${sortOrder}`);
          
          // 构建查询
          let query = supabaseClient
            .from('watch_history')
            .select('*', { count: 'exact' })
            .eq('user_id', userId)
          
          // 应用时间过滤
          if (timeRange !== 'all') {
            const { start, end } = calculateTimeRange(timeRange, startDate, endDate)
            if (start) query = query.gte('watch_start', start)
            if (end) query = query.lte('watch_start', end)
          }
          
          // 应用排序
          query = query.order(sortBy, { ascending: sortOrder === 'asc' })
          
          // 应用分页
          const { data: historyItems, count, error } = await query
            .range(offset, offset + pageSize - 1)
          
          if (error) {
            console.error("获取观看历史列表失败:", error);
            response = {
              items: [],
              pagination: {
                page,
                pageSize,
                totalItems: 0,
                totalPages: 0
              },
              error: error.message
            }
          } else {
            const totalItems = count || 0
            const totalPages = Math.ceil(totalItems / pageSize) || 0
            
            console.log(`成功获取观看历史列表: ${historyItems?.length || 0}条记录, 共${totalItems}条`);
            
            // 将数据库记录转换为统一的WatchHistoryItem格式
            const items = historyItems ? historyItems.map(dbRecordToWatchHistoryItem) : [];
            
            response = {
              items: items,
              pagination: {
                page,
                pageSize,
                totalItems,
                totalPages
              }
            }
          }
        } catch (e) {
          console.error("处理list动作时出错:", e);
          response = {
            items: [],
            pagination: {
              page,
              pageSize,
              totalItems: 0,
              totalPages: 0
            },
            error: e.message || "处理list动作时出错"
          }
        }
        break
        
      case 'statistics':
        try {
          console.log(`获取观看统计数据: 时间范围=${timeRange}`);
          // 获取统计数据
          const stats = await getWatchStatistics(supabaseClient, userId, timeRange, startDate, endDate)
          response = { statistics: stats }
        } catch (e) {
          console.error("处理statistics动作时出错:", e);
          response = { 
            statistics: {
              totalWatchTime: 0,
              totalChannels: 0,
              totalWatches: 0,
              mostWatchedChannel: "错误",
              channelStatistics: [],
              error: e.message || "处理statistics动作时出错"
            } 
          }
        }
        break
        
      default:
        console.error(`无效的动作: ${action}`);
        return new Response(
          JSON.stringify({ error: 'Invalid action' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        )
    }
    
    // 确保响应是有效的JSON对象
    if (!response) {
      response = { error: "未能生成有效响应" };
    }
    
    // 安全地记录响应
    try {
      const responseStr = JSON.stringify(response);
      console.log(`发送响应: ${responseStr.substring(0, 100)}${responseStr.length > 100 ? '...' : ''}`);
    } catch (e) {
      console.error("无法序列化响应对象:", e);
      response = { error: "无法序列化响应对象", status: "error" };
    }
    
    try {
      return new Response(
        JSON.stringify(response),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    } catch (e) {
      console.error("创建响应对象失败:", e);
      return new Response(
        JSON.stringify({ error: "创建响应对象失败", status: "error" }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }
    
  } catch (error) {
    console.error("处理请求时出现顶层错误:", error);
    
    try {
      // 确保返回有效的JSON
      return new Response(
        JSON.stringify({ 
          error: error.message || "处理请求时出现未知错误",
          status: "error"
        }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    } catch (e) {
      // 最后的防御措施
      return new Response(
        '{"error":"严重错误，无法创建有效响应","status":"error"}',
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }
  }
})

/**
 * 计算时间范围
 */
function calculateTimeRange(timeRange, startDate, endDate) {
  if (startDate && endDate) {
    return { start: startDate, end: endDate }
  }
  
  const now = new Date()
  let start = null
  const end = now.toISOString()
  
  switch (timeRange) {
    case 'today':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString()
      break
    case 'week':
      const firstDay = new Date(now)
      firstDay.setDate(now.getDate() - now.getDay())
      firstDay.setHours(0, 0, 0, 0)
      start = firstDay.toISOString()
      break
    case 'month':
      start = new Date(now.getFullYear(), now.getMonth(), 1).toISOString()
      break
    case 'year':
      start = new Date(now.getFullYear(), 0, 1).toISOString()
      break
  }
  
  return { start, end }
}

/**
 * 获取观看统计数据
 */
async function getWatchStatistics(supabase, userId, timeRange, startDate, endDate) {
  try {
    const { start, end } = calculateTimeRange(timeRange, startDate, endDate)
    
    // 基础查询
    let query = supabase
      .from('watch_history')
      .select('*')
      .eq('user_id', userId)
    
    // 应用时间过滤
    if (start) query = query.gte('watch_start', start)
    if (end) query = query.lte('watch_start', end)
    
    const { data: historyItems, error } = await query
    
    if (error) {
      console.error("数据库查询错误:", error);
      return {
        totalWatchTime: 0,
        totalChannels: 0,
        totalWatches: 0,
        mostWatchedChannel: null,
        channelStatistics: [],
        error: `数据库查询错误: ${error.message}`
      };
    }
    
    // 检查historyItems是否为null或undefined
    if (!historyItems) {
      console.error("未获取到历史数据");
      return {
        totalWatchTime: 0,
        totalChannels: 0,
        totalWatches: 0,
        mostWatchedChannel: null,
        channelStatistics: [],
        error: "未获取到历史数据"
      };
    }
    
    // 计算统计数据
    let totalDuration = 0
    const channelCounts = {}
    const channelDurations = {}
    
    historyItems.forEach(item => {
      // 确保item和item.duration存在
      if (!item) return;
      
      const duration = item.duration || 0
      const channelName = item.channel_name || "未知频道"
      
      totalDuration += duration
      
      // 更新频道计数
      channelCounts[channelName] = (channelCounts[channelName] || 0) + 1
      
      // 更新频道时长
      channelDurations[channelName] = (channelDurations[channelName] || 0) + duration
    })
    
    // 找出最常观看的频道
    let mostWatchedChannel = null
    let maxDuration = 0
    
    Object.entries(channelDurations).forEach(([channel, duration]) => {
      if (duration > maxDuration) {
        mostWatchedChannel = channel
        maxDuration = duration
      }
    })
    
    // 构建返回数据
    const result = {
      totalWatchTime: totalDuration,
      totalChannels: Object.keys(channelCounts).length,
      totalWatches: historyItems.length,
      mostWatchedChannel: mostWatchedChannel || "无数据",
      channelStatistics: Object.entries(channelDurations).map(([channel, duration]) => ({
        channelName: channel,
        watchCount: channelCounts[channel],
        totalDuration: duration
      })).sort((a, b) => b.totalDuration - a.totalDuration).slice(0, 10) // 前10个频道
    };
    
    console.log("统计结果:", JSON.stringify(result));
    return result;
  } catch (error) {
    console.error("获取观看统计数据失败:", error);
    // 返回默认值而不是抛出异常，确保始终返回有效的JSON
    return {
      totalWatchTime: 0,
      totalChannels: 0,
      totalWatches: 0,
      mostWatchedChannel: "错误",
      channelStatistics: [],
      error: error.message || "未知错误"
    };
  }
}

/**
 * 处理请求体，统一不同格式的输入
 * @param body 请求体
 * @returns 标准化的观看历史记录数组
 */
function processRequestBody(body: any): any[] {
  // 如果是单条记录
  if (body.channelName && body.channelUrl) {
    return [body];
  }
  
  // 如果是批量记录
  if (body.records) {
    const { records } = body;
    // 确保records始终是数组
    return Array.isArray(records) ? records : 
           (typeof records === 'string' ? JSON.parse(records) : []);
  }
  
  return [];
} 