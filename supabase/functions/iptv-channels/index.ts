// Supabase Edge Function: iptv-channels
// 处理IPTV频道请求，根据用户权限返回相应的频道列表
import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.42.0'

// 频道权限配置，分别对应不同用户角色
const CHANNEL_PERMISSIONS = {
  guest: ['公众号【壹来了】', '地方频道', '央视频道'],
  user: ['卫视频道'],
  vip: ['电竞频道', '体育频道', '香港频道', '澳门频道', '台湾频道', '影视频道测试', '备用频道测试']
}

// 处理请求
serve(async (req) => {
  try {
    // 解析请求URL和参数
    const url = new URL(req.url)
    const ispType = url.searchParams.get('ispType')
    
    // 检查ispType参数
    if (!ispType || !['yidong', 'dianxin', 'public'].includes(ispType)) {
      return new Response(
        JSON.stringify({ error: '无效的ispType参数' }),
        { status: 400, headers: corsHeaders }
      )
    }

    // 根据ispType确定要获取的文件名
    const filename = ispType === 'yidong' ? 'wuzhou_cmcc.m3u' :
                    ispType === 'dianxin' ? 'wuzhou_telecom.m3u' :
                    'onetv_api_result.m3u'
    
    // 创建Supabase客户端
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )
    
    // 初始化权限组
    let allowedGroups = [...CHANNEL_PERMISSIONS.guest]
    
    // 从请求头获取授权信息
    const authHeader = req.headers.get('Authorization')
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7)
      
      // 验证用户Token
      const { data: { user }, error } = await supabaseAdmin.auth.getUser(token)
      
      if (!error && user) {
        // 查询用户资料
        const { data: profiles } = await supabaseAdmin
          .from('profiles')
          .select('*')
          .eq('userid', user.id)
          .single()
        
        if (profiles) {
          // 根据VIP状态扩展权限组
          allowedGroups = profiles.is_vip 
            ? [...CHANNEL_PERMISSIONS.vip, ...CHANNEL_PERMISSIONS.user, ...CHANNEL_PERMISSIONS.guest]
            : [...CHANNEL_PERMISSIONS.user, ...CHANNEL_PERMISSIONS.guest]
        }
      }
    }
    
    // 从存储中获取M3U文件
    const { data: fileData, error: fileError } = await supabaseAdmin
      .storage
      .from('iptv-sources')
      .download(filename)
    
    if (fileError || !fileData) {
      return new Response(
        JSON.stringify({ error: '频道数据未找到' }),
        { status: 404, headers: corsHeaders }
      )
    }
    
    // 将文件内容转换为字符串
    const m3uContent = await fileData.text()
    
    // 过滤频道内容
    const filteredContent = m3uContent.split('\n').filter((line) => {
      if (line.startsWith('#EXTINF')) {
        const groupMatch = line.match(/group-title="([^"]+)"/)
        return groupMatch && allowedGroups.includes(groupMatch[1])
      }
      return true
    }).join('\n')
    
    // 返回过滤后的频道列表
    return new Response(filteredContent, {
      headers: { 
        'Content-Type': 'application/vnd.apple.mpegurl',
        ...corsHeaders
      }
    })
    
  } catch (error) {
    // 捕获未预期的异常
    console.error('处理频道请求失败:', error)
    return new Response(
      JSON.stringify({ 
        error: '服务器内部错误', 
        details: error.message
      }),
      { status: 500, headers: corsHeaders }
    )
  }
})

// CORS 头配置
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization'
} 