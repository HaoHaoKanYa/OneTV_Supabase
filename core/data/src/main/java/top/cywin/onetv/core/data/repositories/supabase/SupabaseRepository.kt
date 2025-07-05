package top.cywin.onetv.core.data.repositories.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Supabase仓库
 * 处理与Supabase服务器交互的各种操作，如用户认证、数据获取等
 */
class SupabaseRepository {

    private val client = SupabaseClient.client
    private val apiClient = SupabaseApiClient.getInstance() // 使用单例ApiClient

    /**
     * 登录用户
     * @param email 用户邮箱
     * @param password 用户密码
     * @return 返回会话信息
     */
    suspend fun loginUser(email: String, password: String) = withContext(Dispatchers.IO) {
        // 使用邮箱密码登录
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        
        return@withContext client.auth.currentUserOrNull()?.let {
            client.auth.currentSessionOrNull()
        }
    }
    
    /**
     * 注册用户
     * @param email 用户邮箱
     * @param password 用户密码
     */
    suspend fun registerUser(email: String, password: String) {
        // 使用邮箱密码注册
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }
    
    /**
     * 注册用户（带用户名）
     * @param email 用户邮箱
     * @param password 用户密码
     * @param username 用户名
     */
    suspend fun registerUser(email: String, password: String, username: String) = withContext(Dispatchers.IO) {
        // 使用邮箱密码注册
        val userData = buildJsonObject {
            put("username", username)
        }
        
        val response = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = userData
        }
        
        // 创建用户资料
        val userId = client.auth.currentUserOrNull()?.id
        if (userId != null) {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            
            val profile = UserProfile(
                userid = userId,
                username = username,
                email = email,
                created_at = now,
                updated_at = now,
                accountstatus = "active"
            )
            
            client.postgrest["profiles"].insert(profile)
        }
    }
    
    /**
     * 检查用户名是否可用
     * @param username 要检查的用户名
     * @return 如果用户名可用返回true，否则返回false
     */
    suspend fun checkUsernameAvailable(username: String): Boolean = withContext(Dispatchers.IO) {
        val result = client.postgrest["profiles"]
            .select()
            .decodeList<UserProfile>()
            .filter { it.username == username }
        return@withContext result.isEmpty()
    }
    
    /**
     * 重置密码
     * @param email 用户邮箱
     */
    suspend fun resetPassword(email: String) {
        // 发送重置密码邮件
        client.auth.resetPasswordForEmail(email)
    }

    /**
     * 发送密码重置邮件
     * @param email 用户邮箱
     */
    suspend fun sendPasswordResetEmail(email: String) {
        client.auth.resetPasswordForEmail(email)
    }
    
    /**
     * 退出登录
     */
    suspend fun logout() {
        // 退出登录
        client.auth.signOut()
    }
    
    /**
     * 获取当前用户
     */
    fun getCurrentUser() = client.auth.currentUserOrNull()
    
    /**
     * 获取用户个人资料
     * @return 用户个人资料
     */
    suspend fun getUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id ?: return@withContext null
        
        val result = client.postgrest["profiles"]
            .select()
            .decodeList<UserProfile>()
            .firstOrNull { it.userid == userId }
            
        return@withContext result
    }
    
    /**
     * 获取用户访问令牌
     */
    fun getAccessToken() = client.auth.currentSessionOrNull()?.accessToken
    
    /**
     * 获取数据
     * @param table 表名
     */
    suspend fun getData(table: String): List<Map<String, Any>> {
        // 从Supabase获取数据
        return client.postgrest[table].select().decodeList<Map<String, Any>>()
    }
    
    /**
     * 上传文件
     * @param bucketName 存储桶名称
     * @param path 文件路径
     * @param data 文件数据
     */
    suspend fun uploadFile(bucketName: String, path: String, data: ByteArray) {
        // 上传文件到Supabase存储
        client.storage.from(bucketName).upload(path, data) {
            upsert = true
        }
    }
    
    /**
     * 更新用户登录设备信息
     * @param deviceInfo 设备信息
     */
    suspend fun updateLoginDevice(deviceInfo: String) = withContext(Dispatchers.IO) {
        val userId = client.auth.currentUserOrNull()?.id ?: return@withContext
        
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
        client.postgrest["profiles"]
            .update({
                set("lastlogindevice", deviceInfo)
                set("lastlogintime", now)
            }) {
                filter {
                    eq("userid", userId)
                }
            }
    }

    /**
     * 获取用户数据
     * 调用SupabaseUserRepository获取详细用户资料
     * @param accessToken 用户访问令牌
     * @return 用户详细资料
     */
    suspend fun getUserData(accessToken: String): SupabaseUserDataIptv {
        val userRepo = SupabaseUserRepository()
        return userRepo.getUserData(accessToken)
    }

    /**
     * 记录用户登录日志
     * 调用Edge Function记录登录信息
     * @param accessToken 访问令牌
     * @param deviceInfo 设备信息
     * @return 记录结果
     */
    suspend fun recordLoginLog(accessToken: String, deviceInfo: String): JsonObject {
        return apiClient.logUserLogin(deviceInfo, "app_client")
    }
    
    /**
     * 使用谷歌账号登录
     * 调用Supabase OAuth登录流程
     */
    suspend fun signInWithGoogle() {
        // 使用谷歌登录
        client.auth.signInWith(Google)
    }
    
    /**
     * 使用GitHub账号登录
     * 调用Supabase OAuth登录流程
     */
    suspend fun signInWithGithub() {
        // 使用GitHub登录
        client.auth.signInWith(Github)
    }
}

/**
 * 用户资料数据类
 * 
 * 字段说明：
 * - userid: 用户ID，主键
 * - username: 用户名
 * - email: 用户邮箱
 * - created_at: 注册时间
 * - updated_at: 资料更新时间
 * - is_vip: 是否为VIP用户
 * - vip_expiry: VIP过期时间（旧字段，保留兼容）
 * - vipstart: VIP开始时间
 * - vipend: VIP结束时间
 * - lastlogintime: 最后登录时间
 * - lastlogindevice: 最后登录设备
 * - accountstatus: 账户状态（active, suspended, deleted等）
 */
@Serializable
data class UserProfile(
    val userid: String,
    val username: String,
    val email: String? = null,
    val created_at: String,
    val updated_at: String,
    val is_vip: Boolean = false,
    val vip_expiry: String? = null,
    val vipstart: String? = null,
    val vipend: String? = null,
    val lastlogintime: String? = null,
    val lastlogindevice: String? = null,
    val accountstatus: String = "active"
) 