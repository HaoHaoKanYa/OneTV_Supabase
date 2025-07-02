// Supabase Edge Function: support-management
// 处理客服管理功能，包括获取活跃对话、创建对话、关闭对话、获取客服统计信息、检查权限、获取用户角色、添加用户角色、移除用户角色

/**
 * OneTV 客服支持管理 Edge Function
 *
 * 功能说明：
 * - 客服对话管理（创建、获取、关闭对话）
 * - 用户反馈处理
 * - 多角色权限管理（检查权限、获取角色、添加/移除角色）
 * - 客服统计数据
 *
 * 支持的操作：
 * - get_active_conversation: 获取用户的活跃对话
 * - create_conversation: 创建新的客服对话
 * - close_conversation: 关闭客服对话
 * - get_support_stats: 获取客服统计数据
 * - check_permission: 检查用户权限
 * - get_user_roles: 获取用户角色列表
 * - add_user_role: 为用户添加角色
 * - remove_user_role: 移除用户角色
 * - delete_feedback: 删除用户反馈
 *
 * 权限系统：
 * - 支持多角色系统（一个用户可以同时拥有多个角色）
 * - 角色层级：super_admin(6) > admin(5) > support(4) > moderator(3) > vip(2) > user(1)
 * - 权限检查基于角色和具体权限路径
 *
 * @author OneTV Team
 * @version 2.0
 * @date 2025-07-01
 */

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

// CORS 跨域配置
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

/**
 * 主服务函数 - 处理所有客服支持相关的请求
 */
serve(async (req) => {
  // 处理 CORS 预检请求
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    // 创建 Supabase 服务端客户端（使用 SERVICE_ROLE_KEY 以获得完整权限）
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      {
        auth: {
          autoRefreshToken: false,
          persistSession: false
        }
      }
    )

    // 获取用户认证信息
    const authHeader = req.headers.get('Authorization')!
    const token = authHeader.replace('Bearer ', '')

    const { data: { user }, error: authError } = await supabaseClient.auth.getUser(token)

    // 验证用户身份
    if (authError || !user) {
      return new Response(
        JSON.stringify({ error: '用户未认证' }),
        {
          status: 401,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 解析请求参数 - 支持从URL参数或请求体获取action
    const url = new URL(req.url)
    let action = url.searchParams.get('action')
    let requestBody = null

    console.log('请求方法:', req.method)
    console.log('请求URL:', req.url)
    console.log('URL参数中的action:', action)

    // 如果URL参数中没有action，尝试从请求体中获取
    if (!action) {
      try {
        const bodyText = await req.text()
        console.log('原始请求体文本:', bodyText)

        if (bodyText) {
          requestBody = JSON.parse(bodyText)
          action = requestBody.action
          console.log('从请求体解析action:', action, '完整请求体:', requestBody)
        }
      } catch (e) {
        console.error('解析请求体失败:', e.message)
        // 如果解析请求体失败，继续使用URL参数
      }
    }

    console.log('最终解析的action:', action)
    console.log('requestBody:', requestBody)

    // 根据操作类型分发请求
    switch (action) {
      case 'get_active_conversation':
        // 获取用户的活跃对话
        return await getActiveConversation(supabaseClient, user)
      case 'create_conversation':
        // 创建新的客服对话
        return await createConversation(req, supabaseClient, user)
      case 'close_conversation':
        // 关闭客服对话
        return await closeConversation(req, supabaseClient, user)
      case 'get_support_stats':
        // 获取客服统计数据
        return await getSupportStats(supabaseClient, user)
      case 'check_permission':
        // 检查用户权限
        return await checkPermission(req, supabaseClient, user)
      case 'get_user_roles':
        // 获取用户角色列表
        return await getUserRoles(supabaseClient, user)
      case 'add_user_role':
        // 为用户添加角色
        return await addUserRole(req, supabaseClient, user)
      case 'remove_user_role':
        // 移除用户角色
        return await removeUserRole(req, supabaseClient, user)
      case 'delete_feedback':
        // 删除用户反馈
        return await deleteFeedback(req, supabaseClient, user, requestBody)
      default:
        return new Response(
          JSON.stringify({ error: '无效的操作类型' }),
          {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' }
          }
        )
    }

  } catch (error) {
    // 处理异常错误
    return new Response(
      JSON.stringify({ error: `服务器错误: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
})

/**
 * ===========================================
 * 辅助函数区域 - Helper Functions
 * ===========================================
 */

/**
 * 获取用户的活跃对话
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 活跃对话信息
 */
async function getActiveConversation(supabaseClient: any, user: any) {
  try {
    // 使用数据库函数获取活跃对话
    const { data: conversation, error } = await supabaseClient
      .rpc('get_user_active_conversation', { p_user_id: user.id })

    if (error) {
      return new Response(
        JSON.stringify({ error: `获取对话失败: ${error.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        conversation: conversation?.[0] || null
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `获取对话异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 创建新的客服对话
 * @param req 请求对象
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 创建结果
 */
async function createConversation(req: Request, supabaseClient: any, user: any) {
  try {
    // 解析请求参数
    const { title, priority, initial_message } = await req.json()

    // 创建新的客服对话
    const { data: conversation, error: conversationError } = await supabaseClient
      .from('support_conversations')
      .insert({
        user_id: user.id,
        conversation_title: title || '客服对话',
        priority: priority || 'normal'
      })
      .select()
      .single()

    if (conversationError) {
      return new Response(
        JSON.stringify({ error: `创建对话失败: ${conversationError.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 如果有初始消息，添加到对话中
    if (initial_message) {
      await supabaseClient
        .from('support_messages')
        .insert({
          conversation_id: conversation.id,
          sender_id: user.id,
          message_text: initial_message,
          is_from_support: false
        })
    }

    return new Response(
      JSON.stringify({
        success: true,
        conversation: conversation
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `创建对话异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 关闭客服对话
 * @param req 请求对象
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 关闭结果
 */
async function closeConversation(req: Request, supabaseClient: any, user: any) {
  try {
    // 解析请求参数
    const { conversation_id } = await req.json()

    // 检查对话是否存在
    const { data: conversation, error: checkError } = await supabaseClient
      .from('support_conversations')
      .select('user_id, support_id')
      .eq('id', conversation_id)
      .single()

    if (checkError || !conversation) {
      return new Response(
        JSON.stringify({ error: '对话不存在' }),
        {
          status: 404,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 检查权限：用户只能关闭自己的对话，客服可以关闭分配给自己的对话
    if (conversation.user_id !== user.id && conversation.support_id !== user.id) {
      return new Response(
        JSON.stringify({ error: '权限不足' }),
        {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 执行关闭对话操作
    const { error: updateError } = await supabaseClient
      .from('support_conversations')
      .update({
        status: 'closed',
        closed_at: new Date().toISOString()
      })
      .eq('id', conversation_id)

    if (updateError) {
      return new Response(
        JSON.stringify({ error: `关闭对话失败: ${updateError.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: '对话已成功关闭'
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `关闭对话异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 获取客服统计信息（仅客服及以上角色可用）
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 统计数据
 */
async function getSupportStats(supabaseClient: any, user: any) {
  try {
    // 检查用户是否拥有客服权限（使用多角色系统）
    const { data: userRoles } = await supabaseClient
      .from('user_roles')
      .select('role_type')
      .eq('user_id', user.id)
      .eq('is_active', true)
      .or('expires_at.is.null,expires_at.gt.now()')

    const hasAdminRole = userRoles?.some(role =>
      ['support', 'admin', 'super_admin'].includes(role.role_type)
    ) || false

    if (!hasAdminRole) {
      return new Response(
        JSON.stringify({ error: '权限不足：需要客服及以上权限' }),
        {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 获取客服统计数据
    const { data: conversations } = await supabaseClient
      .rpc('get_support_conversations', { p_support_id: user.id })

    const { data: totalFeedback, error: feedbackError } = await supabaseClient
      .from('user_feedback')
      .select('status')
      .eq('admin_id', user.id)

    // 计算统计信息
    const stats = {
      active_conversations: conversations?.length || 0,
      total_feedback_handled: totalFeedback?.length || 0,
      pending_feedback: totalFeedback?.filter(f => f.status === 'submitted').length || 0
    }

    return new Response(
      JSON.stringify({
        success: true,
        stats: stats,
        conversations: conversations || []
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `获取统计数据异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 检查用户权限
 * @param req 请求对象
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 权限检查结果
 */
async function checkPermission(req: Request, supabaseClient: any, user: any) {
  try {
    // 解析请求参数
    const { permission } = await req.json()

    // 调用数据库函数检查权限
    const { data: result, error } = await supabaseClient
      .rpc('user_has_permission', {
        user_id: user.id,
        permission_path: permission
      })

    if (error) {
      return new Response(
        JSON.stringify({ error: `权限检查失败: ${error.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        has_permission: result || false
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `权限检查异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 获取用户角色列表
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 用户角色列表
 */
async function getUserRoles(supabaseClient: any, user: any) {
  try {
    // 调用数据库函数获取用户角色
    const { data: roles, error } = await supabaseClient
      .rpc('get_user_roles', { user_id: user.id })

    if (error) {
      return new Response(
        JSON.stringify({ error: `获取角色失败: ${error.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        roles: roles || ['user']
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `获取角色异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 为用户添加角色（需要管理员权限）
 * @param req 请求对象
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 添加结果
 */
async function addUserRole(req: Request, supabaseClient: any, user: any) {
  try {
    // 解析请求参数
    const { target_user_id, role_type, expires_at, role_permissions } = await req.json()

    // 调用数据库函数添加角色
    const { data: result, error } = await supabaseClient
      .rpc('add_user_role', {
        target_user_id,
        new_role: role_type,
        granted_by_user_id: user.id,
        expires_at: expires_at || null,
        role_permissions: role_permissions || {}
      })

    if (error) {
      return new Response(
        JSON.stringify({ error: `添加角色失败: ${error.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: '角色添加成功'
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `添加角色异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 移除用户角色（需要管理员权限）
 * @param req 请求对象
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 移除结果
 */
async function removeUserRole(req: Request, supabaseClient: any, user: any) {
  try {
    // 解析请求参数
    const { target_user_id, role_type } = await req.json()

    // 调用数据库函数移除角色
    const { data: result, error } = await supabaseClient
      .rpc('remove_user_role', {
        target_user_id,
        role_to_remove: role_type,
        removed_by_user_id: user.id
      })

    if (error) {
      return new Response(
        JSON.stringify({ error: `移除角色失败: ${error.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: '角色移除成功'
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `移除角色异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * 删除用户反馈
 * @param req 请求对象
 * @param supabaseClient Supabase 客户端
 * @param user 当前用户信息
 * @returns 删除结果
 */
async function deleteFeedback(req: Request, supabaseClient: any, user: any, requestBody: any = null) {
  try {
    // 解析请求参数
    let feedback_id

    if (requestBody) {
      feedback_id = requestBody.feedback_id
    } else {
      try {
        const body = await req.json()
        feedback_id = body.feedback_id
      } catch (e) {
        // 如果请求体解析失败，尝试从URL参数获取
        const url = new URL(req.url)
        feedback_id = url.searchParams.get('feedback_id')
      }
    }

    if (!feedback_id) {
      return new Response(
        JSON.stringify({ error: '缺少反馈ID' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 检查反馈是否存在且属于当前用户
    const { data: feedback, error: checkError } = await supabaseClient
      .from('user_feedback')
      .select('user_id, title')
      .eq('id', feedback_id)
      .single()

    if (checkError || !feedback) {
      return new Response(
        JSON.stringify({ error: '反馈不存在' }),
        {
          status: 404,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 检查权限：用户只能删除自己的反馈
    if (feedback.user_id !== user.id) {
      return new Response(
        JSON.stringify({ error: '权限不足：只能删除自己的反馈' }),
        {
          status: 403,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 执行删除操作
    const { error: deleteError } = await supabaseClient
      .from('user_feedback')
      .delete()
      .eq('id', feedback_id)
      .eq('user_id', user.id) // 双重确认安全性

    if (deleteError) {
      return new Response(
        JSON.stringify({ error: `删除反馈失败: ${deleteError.message}` }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    // 验证删除是否成功
    const { data: verifyData, error: verifyError } = await supabaseClient
      .from('user_feedback')
      .select('id')
      .eq('id', feedback_id)

    if (verifyError) {
      // 查询出错可能意味着删除成功
      return new Response(
        JSON.stringify({
          success: true,
          message: '反馈删除成功'
        }),
        {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    if (verifyData && verifyData.length > 0) {
      return new Response(
        JSON.stringify({ error: '删除失败：反馈仍然存在' }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      )
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: '反馈删除成功'
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: `删除反馈异常: ${error.message}` }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    )
  }
}

/**
 * ===========================================
 * 文件结束 - OneTV 客服支持管理系统
 *
 * 版本: 2.1
 * 更新时间: 2025-07-02
 * 功能: 多角色权限管理 + 客服对话系统 + 反馈删除
 * ===========================================
 */
