// 文件路径：core/src/main/java/top/cywin/onetv/core/data/repositories/user/ApiService.kt
package top.cywin.onetv.core.data.repositories.user

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class BaseResponse(
    val success: Boolean,
    val error: String? = null
)

interface ApiService {
    // 在 ApiService 接口中添加注册接口
    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>
    @GET("api/check-username")
    suspend fun checkUsername(@Query("username") username: String): Response<UsernameAvailableResponse>
    @POST("api/reset-password")
    suspend fun resetPassword(@Body request: ResetRequest): Response<ResetPasswordResponse>
    // 发送验证码接口
    @POST("api/send-reset-code")
    suspend fun sendResetCode(@Body request: EmailRequest): Response<SendResetCodeResponse>
    @POST("api/send-register-code")
    suspend fun sendRegisterCode(@Body request: EmailRequest): Response<BaseResponse>



}

data class UsernameAvailableResponse(
    val available: Boolean,
    val message: String
)
// 在 ApiService.kt 中添加数据类
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    //val code: String // 新增验证码字段
)



// 确保包含响应模型
data class RegisterResponse(
    val success: Boolean,
    val error: String? = null
)

// 用于发送验证码接口的请求与响应数据类
data class EmailRequest(
    val email: String
)

data class SendResetCodeResponse(
    val success: Boolean,
    val error: String? = null
)

// 密码重置请求数据类，前端已使用该类传递邮箱、验证码和新密码
data class ResetRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

// 密码重置接口的响应数据类（可根据实际后端返回内容进行扩展）
data class ResetPasswordResponse(
    val success: Boolean,
    val error: String? = null
)

