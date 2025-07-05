package top.cywin.onetv.core.data.repositories.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull

/**
 * Supabase用户仓库
 * 负责从Supabase服务获取用户数据
 */
class SupabaseUserRepository {
    private val apiClient = SupabaseApiClient.getInstance()
    private val TAG = "SupabaseUserRepository"

    /**
     * 检查用户是否为VIP
     * @param sessionId 用户会话ID
     * @return 如果用户是VIP则返回true，否则返回false
     */
    suspend fun isVIP(sessionId: String): Boolean {
        try {
            val vipStatus = apiClient.getVipStatus()
            return vipStatus["is_vip"]?.jsonPrimitive?.boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "获取VIP状态失败: ${e.message}", e)
            throw Exception("获取VIP状态失败: ${e.message}")
        }
    }

    /**
     * 获取用户数据
     * @param sessionId 用户会话ID（在使用Supabase时不需要显式传入，因为Supabase客户端已经包含了会话信息）
     * @return 用户数据
     */
    suspend fun getUserData(sessionId: String): SupabaseUserDataIptv = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从Supabase获取用户资料")
            
            // 使用SupabaseApiClient获取用户资料
            val userProfile = apiClient.getUserProfile()
            
            // 获取VIP状态信息
            val vipStatus = apiClient.getVipStatus()
            
            // 解析并构建SupabaseUserDataIptv对象
            val userData = parseUserData(userProfile, vipStatus)
            
            Log.d(TAG, "用户数据获取成功: ${userData.userid}")
            return@withContext userData
        } catch (e: Exception) {
            if (e.message?.contains("401") == true) {
                Log.e(TAG, "会话已过期｜请重新登录", e)
            } else {
                Log.e(TAG, "获取用户数据失败", e)
            }
            throw Exception("获取用户信息失败: ${e.message}")
        }
    }

    /**
     * 解析用户数据
     * @param userProfile 用户资料JSON对象
     * @param vipStatus VIP状态JSON对象
     * @return 解析后的SupabaseUserDataIptv对象
     */
    private fun parseUserData(userProfile: JsonObject, vipStatus: JsonObject): SupabaseUserDataIptv {
        // 从userProfile中提取基本用户信息
        val userid = userProfile["userid"]?.jsonPrimitive?.contentOrNull ?: ""
        val username = userProfile["username"]?.jsonPrimitive?.contentOrNull ?: ""
        val email = userProfile["email"]?.jsonPrimitive?.contentOrNull
        val created_at = userProfile["created_at"]?.jsonPrimitive?.contentOrNull ?: ""
        val updated_at = userProfile["updated_at"]?.jsonPrimitive?.contentOrNull ?: ""
        val accountstatus = userProfile["accountstatus"]?.jsonPrimitive?.contentOrNull ?: "未知"
        val lastlogintime = userProfile["lastlogintime"]?.jsonPrimitive?.contentOrNull
        val lastlogindevice = userProfile["lastlogindevice"]?.jsonPrimitive?.contentOrNull

        // 从vipStatus中提取VIP相关信息
        val is_vip = vipStatus["is_vip"]?.jsonPrimitive?.boolean ?: false
        val vipstart = vipStatus["vipstart"]?.jsonPrimitive?.contentOrNull
        val vipend = vipStatus["vipend"]?.jsonPrimitive?.contentOrNull
        val vip_expiry = vipStatus["vip_expiry"]?.jsonPrimitive?.contentOrNull

        Log.d(TAG, "解析用户数据 | 用户ID: $userid | VIP状态: $is_vip | 过期时间: $vipend")

        // 构建并返回用户数据对象
        return SupabaseUserDataIptv(
            userid = userid,
            username = username,
            email = email,
            created_at = created_at,
            updated_at = updated_at,
            is_vip = is_vip,
            accountstatus = accountstatus,
            vipstart = vipstart,
            vipend = vipend,
            lastlogindevice = lastlogindevice,
            lastlogintime = lastlogintime,
            vip_expiry = vip_expiry
        )
    }
}