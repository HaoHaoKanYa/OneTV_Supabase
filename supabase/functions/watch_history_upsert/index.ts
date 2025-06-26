// Supabase Edge Function: watch_history_upsert
// 专用于观看历史记录的upsert操作，确保并发安全和重复记录防护
// Edge Function for watch history upsert operations
// Provides concurrent-safe upsert operations with duplicate record protection

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

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

    // Verify the JWT token and get user
    const token = authHeader.replace('Bearer ', '')
    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token)
    
    if (authError || !user) {
      console.error('Authentication failed:', authError)
      return new Response(
        JSON.stringify({ success: false, error: 'Authentication failed' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    const userId = user.id

    // Parse request body
    const body = await req.json()
    const { channelName, channelUrl, duration, watchStart, watchEnd } = body

    console.log(`Upsert watch history: user=${userId}, channel=${channelName}, duration=${duration}`)

    // Validate required parameters
    if (!channelName || !channelUrl || !duration || duration <= 0) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing or invalid required parameters' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Calculate watch times if not provided
    const now = new Date().toISOString()
    const startTime = watchStart || new Date(new Date().getTime() - duration * 1000).toISOString()
    const endTime = watchEnd || now

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
      console.error("Upsert watch history failed:", error)
      return new Response(
        JSON.stringify({ success: false, error: error.message }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    const isNewRecord = data && data.length > 0
    console.log(`Watch history upsert result: ${isNewRecord ? 'inserted' : 'duplicate'}`)

    return new Response(
      JSON.stringify({ 
        success: true, 
        message: isNewRecord ? "Record inserted" : "Duplicate record ignored",
        data: { 
          inserted: isNewRecord,
          record: data?.[0] || null
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
