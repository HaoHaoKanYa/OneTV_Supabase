// Supabase Edge Function: online-users
// 处理在线用户统计功能，根据当前日期和时间计算在线用户数量

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

// ====================== 常规时间段配置 ======================
const ONLINE_STRATEGY = {
  weekday: [
    { start: 0, end: 7, min: 30, max: 70 },
    { start: 7, end: 11, min: 50, max: 150 },
    { start: 11, end: 13, min: 270, max: 350 },
    { start: 13, end: 18, min: 350, max: 450 },
    { start: 18, end: 22, min: 950, max: 1500 },
    { start: 22, end: 23, min: 450, max: 550 },
    { start: 23, end: 24, min: 100, max: 200 }
  ],
  weekend: [
    { start: 0, end: 7, min: 130, max: 170 },
    { start: 7, end: 11, min: 450, max: 550 },
    { start: 11, end: 13, min: 950, max: 1500 },
    { start: 13, end: 18, min: 2000, max: 3000 },
    { start: 18, end: 22, min: 4000, max: 6000 },
    { start: 22, end: 23, min: 3000, max: 5000 },
    { start: 23, end: 24, min: 1000, max: 2000 }
  ]
};

// ====================== 特殊时间段配置 ======================
// 普通节假日配置
const ORDINARY_HOLIDAY_STRATEGY = [
  { start: 0, end: 7, min: 350, max: 500 },
  { start: 7, end: 11, min: 1000, max: 2000 },
  { start: 11, end: 13, min: 2000, max: 3000 },
  { start: 13, end: 18, min: 3000, max: 7000 },
  { start: 18, end: 22, min: 7000, max: 11000 },
  { start: 22, end: 23, min: 4000, max: 5000 },
  { start: 23, end: 24, min: 1000, max: 2000 }
];

// 春节配置
const CHINESE_NEW_YEAR_STRATEGY = [
  { start: 0, end: 7, min: 1350, max: 2000 },
  { start: 7, end: 11, min: 2000, max: 3000 },
  { start: 11, end: 13, min: 3000, max: 4000 },
  { start: 13, end: 18, min: 4000, max: 10000 },
  { start: 18, end: 22, min: 10000, max: 15000 },
  { start: 22, end: 23, min: 8000, max: 9000 },
  { start: 23, end: 24, min: 3000, max: 5000 }
];

// 春节除夕配置
const CHINESE_NEW_YEAR_EVE_STRATEGY = [
  { start: 1, end: 7, min: 1350, max: 2000 },
  { start: 7, end: 11, min: 2000, max: 5000 },
  { start: 11, end: 13, min: 5000, max: 10000 },
  { start: 13, end: 18, min: 15000, max: 20000 },
  { start: 18, end: 22, min: 20000, max: 30000 },
  { start: 22, end: 23, min: 30000, max: 40000 },
  { start: 23, end: 24, min: 40000, max: 50000 }
];

// 硬编码2025年节假日信息
const ORDINARY_HOLIDAYS_2025 = [
  '2025-01-01', '2025-04-04', '2025-04-05', '2025-04-06', 
  '2025-05-01', '2025-05-02', '2025-05-03', '2025-05-04', '2025-05-05', 
  '2025-05-31', '2025-06-01', '2025-06-02', 
  '2025-10-01', '2025-10-02', '2025-10-03', '2025-10-04', '2025-10-05', '2025-10-06', '2025-10-07', '2025-10-08'
];

// 春节期间和除夕日期
const CHINESE_NEW_YEAR_PERIOD_2025 = { start: '2025-01-28', end: '2025-02-04' };
const CHINESE_NEW_YEAR_EVE_2025 = '2025-01-28';

// ====================== 辅助函数 ======================
// 格式化日期为 "YYYY-MM-DD"
function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// 判断当前日期是否为普通节假日
function isOrdinaryHoliday(date: Date): boolean {
  const dateStr = formatDate(date);
  return ORDINARY_HOLIDAYS_2025.includes(dateStr);
}

// 判断当前日期是否在春节期间
function isChineseNewYear(date: Date): boolean {
  const dateStr = formatDate(date);
  return dateStr >= CHINESE_NEW_YEAR_PERIOD_2025.start && dateStr <= CHINESE_NEW_YEAR_PERIOD_2025.end;
}

// 判断当前日期是否为春节除夕
function isChineseNewYearEve(date: Date): boolean {
  const dateStr = formatDate(date);
  return dateStr === CHINESE_NEW_YEAR_EVE_2025;
}

// ====================== 在线用户数据表结构 ======================
interface OnlineUsersData {
  total: number;
  base: number;
  real: number;
  updated: number;
}

// ====================== 获取在线用户数据 ======================
async function calculateOnlineUsers(supabaseClient: any): Promise<OnlineUsersData> {
  try {
    // 1. 获取真实在线注册用户数（从数据库查询活跃用户会话）
    const currentTime = new Date().toISOString();
    console.log('[DEBUG] 当前时间:', currentTime);

    const { data: activeSessions, error: sessionError } = await supabaseClient
      .from('user_sessions')
      .select('id, user_id, expires_at, created_at, device_info')
      .gt('expires_at', currentTime);

    if (sessionError) {
      console.error('Error fetching active sessions:', sessionError);
      throw sessionError;
    }

    const realUsers = activeSessions?.length || 0;

    // 添加调试日志
    console.log('[DEBUG] 查询活跃会话:');
    console.log('- 查询条件: expires_at >', currentTime);
    console.log('- 找到会话数:', realUsers);
    if (activeSessions && activeSessions.length > 0) {
      console.log('- 活跃会话详情:');
      activeSessions.forEach((session, index) => {
        console.log(`  ${index + 1}. user_id: ${session.user_id}, expires_at: ${session.expires_at}, device: ${session.device_info}`);
      });
    } else {
      console.log('- 没有找到活跃会话');

      // 查询所有会话进行调试
      const { data: allSessions } = await supabaseClient
        .from('user_sessions')
        .select('id, user_id, expires_at, created_at, device_info')
        .order('created_at', { ascending: false })
        .limit(5);

      if (allSessions && allSessions.length > 0) {
        console.log('- 最近5个会话（用于调试）:');
        allSessions.forEach((session, index) => {
          const isExpired = new Date(session.expires_at) <= new Date(currentTime);
          console.log(`  ${index + 1}. user_id: ${session.user_id}, expires_at: ${session.expires_at}, expired: ${isExpired}, device: ${session.device_info}`);
        });
      }
    }

    // 2. 获取当前北京时间（UTC+8）
    const now = new Date(Date.now() + 8 * 3600 * 1000);
    const currentHour = now.getUTCHours(); // 当前小时（0-23）

    // 3. 根据当前日期判断是否为特殊节假日
    let strategy = null;
    if (isChineseNewYearEve(now)) {
      strategy = CHINESE_NEW_YEAR_EVE_STRATEGY;
      console.log('[STATS] 当前为春节除夕，使用春节除夕策略');
    } else if (isChineseNewYear(now)) {
      strategy = CHINESE_NEW_YEAR_STRATEGY;
      console.log('[STATS] 当前为春节期间，使用春节策略');
    } else if (isOrdinaryHoliday(now)) {
      strategy = ORDINARY_HOLIDAY_STRATEGY;
      console.log('[STATS] 当前为普通节假日，使用普通节假日策略');
    } else {
      // 非节假日：判断工作日或周末
      const isWeekend = [0, 6].includes(now.getUTCDay());
      strategy = isWeekend ? ONLINE_STRATEGY.weekend : ONLINE_STRATEGY.weekday;
      console.log('[STATS] 当前为', isWeekend ? '周末' : '工作日', '，使用常规策略');
    }

    // 4. 根据当前小时数从所选策略中找到对应的时间段
    const currentStrategy = strategy.find(({ start, end }) => 
      currentHour >= start && currentHour < end
    ) || strategy[0];

    // 5. 根据该时间段随机计算基数，并加上真实在线注册用户数量
    const randomBase = Math.floor(
      Math.random() * (currentStrategy.max - currentStrategy.min) + currentStrategy.min
    );

    // 6. 返回统计数据对象
    const result = {
      total: randomBase + realUsers,
      base: randomBase,
      real: realUsers,
      updated: Math.floor(Date.now() / 1000)
    };

    // 7. 保存数据到数据库（可选，用于统计历史记录）
    try {
      const { error } = await supabaseClient
        .from('online_users_stats')
        .insert({
          total: result.total,
          base: result.base,
          real: result.real,
          timestamp: new Date().toISOString()
        });

      if (error) {
        console.error('Error saving stats:', error);
      }
    } catch (err) {
      console.error('Failed to store stats:', err);
      // 继续执行，因为这只是辅助功能
    }

    return result;
  } catch (error) {
    console.error('[STATS] 计算失败:', error);
    return { total: 0, base: 0, real: 0, updated: Math.floor(Date.now() / 1000) };
  }
}

// ====================== 服务器函数入口 ======================
serve(async (req) => {
  try {
    // 初始化 Supabase 客户端
    // 注意：在 Edge Function 中，直接使用服务器端权限
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      {
        auth: {
          persistSession: false,
        }
      }
    );

    // 调用获取在线用户数据的函数
    const data = await calculateOnlineUsers(supabaseClient);
    
    // 返回JSON响应
    return new Response(
      JSON.stringify(data),
      {
        headers: {
          "Content-Type": "application/json",
          "Cache-Control": "public, max-age=30"
        },
      }
    );
  } catch (error) {
    // 错误处理
    console.error("Error in online-users function:", error);
    return new Response(
      JSON.stringify({
        total: 0,
        base: 0,
        real: 0,
        updated: Math.floor(Date.now() / 1000),
        status: "error",
        message: error.message
      }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" }
      }
    );
  }
}); 