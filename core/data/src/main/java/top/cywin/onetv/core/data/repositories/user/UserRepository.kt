package top.cywin.onetv.core.data.repositories.user

// SupabaseUserRepository.kt（完整实现）

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient // 导入OkHttp库中的OkHttpClient类，用于发送网络请求
import okhttp3.Request // 导入OkHttp库中的Request类，用于构建HTTP请求
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.HmacAlgorithms
import org.json.JSONObject // 导入org.json库中的JSONObject类，用于解析JSON数据
import top.cywin.onetv.core.data.network.await // 导入扩展函数await，用于异步等待网络响应
import top.cywin.onetv.core.data.repositories.user.UserDataIptv // 导入UserDataIptv数据类，用于封装用户数据
import org.apache.commons.codec.digest.HmacUtils
import java.lang.StrictMath.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class UserRepository { // 定义一个名为UserRepository的类，用于处理用户的网络请求和数据解析
    private val client = OkHttpClient() // 创建OkHttpClient实例，用于发送网络请求

    suspend fun isVIP(sessionId: String): Boolean {
        val userData = getUserData(sessionId)
        return userData.isVIP
    }

    // 定义一个挂起函数getUserData，接收sessionId参数，返回UserDataIptv类型的数据
    // 修改 SupabaseUserRepository 的 getUserData 方法，强制指定 IO 线程
    suspend fun getUserData(sessionId: String): UserDataIptv = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://iptv.liubaotea.online/api/user-info")
                .header("Authorization", "Bearer $sessionId")
                .build()
            val response = client.newCall(request).await()
            if (!response.isSuccessful) {
                throw Exception("请求失败: ${response.code}")
            }
            // ✅ 修复：只读取一次响应体
            val responseBody = response.body?.string() ?: throw Exception("响应体为空")
            Log.d("SupabaseUserRepository", "原始响应数据: $responseBody")
            parseUserData(responseBody)
        } catch (e: Exception) {
            if (e.message?.contains("401") == true) {
                Log.e("SupabaseUserRepository", "会话已过期｜请重新登录", e)
            } else {
                Log.e("SupabaseUserRepository", "网络请求异常", e)
            }
            throw Exception("获取用户信息失败: ${e.message}")
        }
    }

    // SupabaseUserRepository.kt
    private fun parseUserData(json: String?): UserDataIptv {
        val obj = JSONObject(json ?: throw Exception("响应为空"))
        val serverSign = obj.optString("_sign")
        val userId = obj.optString("userId")
        // 保持原始VIP结束时间处理逻辑
        val rawVipEnd = obj.optString("vipEnd")
        Log.d("SupabaseUserRepository", "原始VIP结束时间：$rawVipEnd")  // 原有日志保留

        val vipEnd = when {
            rawVipEnd.equals("null", ignoreCase = true) -> "0"
            rawVipEnd.isEmpty() -> "0"
            else -> rawVipEnd
        }

        // 修改1：增强时间戳校验
        // 修改 parseUserData 方法的时间校验部分
        val serverTime = obj.optLong("_timestamp", 0).takeIf { it > 0 }
            ?.let { it * 1000 } // ✨ 关键修复：秒转毫秒
            ?: throw Exception("服务器时间戳无效")

        // 修改2：增加时区转换的日志显示
        val currentTime = System.currentTimeMillis()
        Log.d("SupabaseUserRepository", "时间戳验证 | 服务器时间：${formatBeijingTime(serverTime)} " +
                "| 本地时间：${formatBeijingTime(currentTime)} " +
                "| 差值：${currentTime - serverTime}ms")

        // 修改3：精确计算时间差
        // 修改时间差计算
        val timeDiff = abs(currentTime - serverTime)
        if (timeDiff > 300000) { // 保持5分钟容差
            throw Exception("服务器时间不同步（允许±5分钟，实际差值：${timeDiff/1000}秒）")
        }
// 在 parseUserData 中添加调试日志
        Log.d("SupabaseUserRepository",
            "时间戳转换 | 原始值：${obj.optLong("_timestamp")}秒 " +
                    "转换后：${serverTime}ms " +
                    "格式化：${formatBeijingTime(serverTime)}")
        // 保持原有签名验证逻辑
        val data = "${userId}:$vipEnd".toByteArray(Charsets.UTF_8)
        val hmac = HmacUtils.getInitializedMac(HmacAlgorithms.HMAC_SHA_256, "onetv".toByteArray(Charsets.UTF_8))
        val localSign = Hex.encodeHexString(hmac.doFinal(data))

        Log.d("SupabaseUserRepository", "签名明文数据: userId=$userId, vipEnd=$vipEnd")
        Log.d("SupabaseUserRepository", "Server Sign: $serverSign\nLocal Sign: $localSign")
        Log.d("SupabaseUserRepository", "签名验证 | 服务端：$serverSign | 本地生成：$localSign")

        if (serverSign != localSign) {
            throw Exception("数据签名验证失败")
        }

        // 保持原有用户数据解析逻辑
        val userData = UserDataIptv(
            userId = userId,
            username = obj.optString("username", ""),
            email = obj.optString("email", ""),
            regTime = obj.optString("regTime", ""),
            isVIP = obj.optBoolean("isVIP", false),
            accountStatus = obj.optString("accountStatus", "未知"),
            vipStart = obj.optString("vipStart").takeIf { it.isNotEmpty() },
            vipEnd = obj.optString("vipEnd").takeIf { it.isNotEmpty() },
            lastLoginDevice = obj.optString("lastLoginDevice"),
            lastLoginTime = obj.optString("lastLoginTime").takeIf { it.isNotEmpty() }
        )

        Log.d("SupabaseUserRepository", "完整解析结果 | VIP状态：${userData.isVIP} | 到期时间：${userData.vipEnd}")
        return userData
    }

    }


//250309创建解决IptvRepository.kt未解析引用问题

private fun formatBeijingTime(time: Long): String {
    if (time <= 0) return "未记录" // 新增判断逻辑
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(time))
}