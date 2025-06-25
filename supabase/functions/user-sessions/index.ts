// Supabase Edge Function: user-sessions
// 统一管理 user_sessions 表的增删查
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4";

serve(async (req) => {
  try {
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      { auth: { persistSession: false } }
    );
    const { method } = req;
    const url = new URL(req.url);
    const params = Object.fromEntries(url.searchParams.entries());
    
    if (method === "GET") {
      // 查询会话，支持 user_id 查询
      const user_id = params.user_id;
      if (!user_id) {
        return new Response(JSON.stringify({ error: "user_id required" }), { status: 400 });
      }
      const { data, error } = await supabaseClient
        .from("user_sessions")
        .select("*")
        .eq("user_id", user_id)
        .order("created_at", { ascending: false });
      if (error) throw error;
      return new Response(JSON.stringify({ sessions: data }), { headers: { "Content-Type": "application/json" } });
    }
    
    if (method === "POST") {
      // 新增或刷新会话
      const body = await req.json();
      const { user_id, expires_at, device_info, ip_address, platform, app_version } = body;
      if (!user_id || !expires_at) {
        return new Response(JSON.stringify({ error: "user_id and expires_at required" }), { status: 400 });
      }
      // 先查找是否已有会话（同一user_id+device_info+platform+app_version）
      const { data: exist, error: findErr } = await supabaseClient
        .from("user_sessions")
        .select("id")
        .eq("user_id", user_id)
        .eq("device_info", device_info ?? null)
        .eq("platform", platform ?? null)
        .eq("app_version", app_version ?? null)
        .limit(1);
      if (findErr) throw findErr;
      let result;
      if (exist && exist.length > 0) {
        // 已有则更新 expires_at
        const { error: updateErr } = await supabaseClient
          .from("user_sessions")
          .update({ expires_at, device_info, ip_address, platform, app_version })
          .eq("id", exist[0].id);
        if (updateErr) throw updateErr;
        result = { updated: true, id: exist[0].id };
      } else {
        // 没有则插入新会话
        const { data: insertData, error: insertErr } = await supabaseClient
          .from("user_sessions")
          .insert([{ user_id, expires_at, device_info, ip_address, platform, app_version }])
          .select();
        if (insertErr) throw insertErr;
        result = { inserted: true, id: insertData?.[0]?.id };
      }
      return new Response(JSON.stringify(result), { headers: { "Content-Type": "application/json" } });
    }
    
    if (method === "DELETE") {
      // 删除会话，支持 id 或 user_id
      const body = await req.json().catch(() => ({}));
      const { id, user_id } = body;
      if (!id && !user_id) {
        return new Response(JSON.stringify({ error: "id or user_id required" }), { status: 400 });
      }
      let del;
      if (id) {
        del = await supabaseClient.from("user_sessions").delete().eq("id", id);
      } else {
        del = await supabaseClient.from("user_sessions").delete().eq("user_id", user_id);
      }
      if (del.error) throw del.error;
      return new Response(JSON.stringify({ deleted: true }), { headers: { "Content-Type": "application/json" } });
    }
    
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }
}); 