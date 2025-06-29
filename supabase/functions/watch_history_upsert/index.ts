// Supabase Edge Function: watch_history_upsert
// 专用于观看历史记录的upsert操作，确保并发安全和重复记录防护
// Edge Function for watch history upsert operations
// Provides concurrent-safe upsert operations with duplicate record protection

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

interface WatchHistoryItem {
  id?: string;
  channelName: string;
  channelUrl: string;
  duration: number;
  watchStart: string;
  watchEnd: string;
  userId?: string;
}

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    // Create Supabase client
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // Get the authorization header
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Extract JWT token (simplified handling, consistent with other Edge Functions)
    const token = authHeader.substring(7)
    console.log(`Using JWT token: ${token.substring(0, 20)}...`)

    // Verify the JWT token and get user
    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token)
    
    if (authError || !user) {
      console.error('Authentication failed:', authError)
      return new Response(
        JSON.stringify({ success: false, error: 'Authentication failed' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    const userId = user.id

    // Parse request body safely
    let body;
    try {
      body = await req.json();
    } catch (e) {
      console.error('Failed to parse request body:', e);
      return new Response(
        JSON.stringify({ success: false, error: 'Invalid JSON in request body' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Process the request body to handle different formats
    const records = processRequestBody(body);
    
    console.log(`Batch upsert watch history: user=${userId}, records=${records.length}`)

    // Validate records array
    if (records.length === 0) {
      return new Response(
        JSON.stringify({ success: false, error: 'No valid records found in request' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    let insertedCount = 0
    let duplicateCount = 0
    const insertedRecords = []
    const now = new Date().toISOString()

    // Process each record
    for (const record of records) {
      const { channelName, channelUrl, duration, watchStart, watchEnd } = record

      // Validate required parameters for each record
      if (!channelName || !channelUrl || !duration || duration <= 0) {
        console.warn(`Skipping invalid record: ${JSON.stringify(record)}`)
        duplicateCount++
        continue
      }

      // Calculate watch times if not provided
      const startTime = watchStart || new Date(new Date().getTime() - duration * 1000).toISOString()
      const endTime = watchEnd || now

      try {
        // Use upsert with conflict resolution
        const { data, error } = await supabaseClient
          .from('watch_history')
          .upsert({
            user_id: userId,
            channel_name: channelName,
            channel_url: channelUrl,
            watch_start: startTime,
            watch_end: endTime,
            duration: duration,
            created_at: now
          }, {
            onConflict: 'user_id,channel_name,channel_url,watch_start',
            ignoreDuplicates: true
          })
          .select()

        if (error) {
          console.error(`Upsert record failed: ${error.message}`, record)
          if (error.code === '23505') {
            duplicateCount++
          }
        } else if (data && data.length > 0) {
          insertedCount++
          insertedRecords.push(data[0])
          console.log(`Inserted record: channel=${channelName}, id=${data[0].id}`)
        } else {
          duplicateCount++
          console.log(`Duplicate record ignored: channel=${channelName}`)
        }
      } catch (e) {
        console.error(`Error processing record: ${e.message}`, record)
        if (e.code === '23505') {
          duplicateCount++
        }
      }
    }

    console.log(`Batch upsert completed: inserted=${insertedCount}, duplicates=${duplicateCount}`)

    return new Response(
      JSON.stringify({
        success: true,
        message: insertedCount > 0 ?
          `Successfully inserted ${insertedCount} records, skipped ${duplicateCount} duplicates` :
          "All records already exist",
        data: {
          inserted: insertedCount,
          duplicates: duplicateCount,
          total: records.length,
          records: insertedRecords
        }
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error('Function error:', error)
    return new Response(
      JSON.stringify({ success: false, error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})

/**
 * 处理请求体，统一不同格式的输入
 * @param body 请求体
 * @returns 标准化的观看历史记录数组
 */
function processRequestBody(body: any): WatchHistoryItem[] {
  // 如果是单条记录
  if (body.channelName && body.channelUrl) {
    return [body as WatchHistoryItem];
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
