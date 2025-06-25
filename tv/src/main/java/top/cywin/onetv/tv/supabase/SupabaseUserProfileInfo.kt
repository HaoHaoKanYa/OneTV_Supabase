package top.cywin.onetv.tv.supabase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager

/**
 * 用户详细资料界面
 *
 * @param userData 用户数据
 * @param isLoading 是否正在加载
 */
@Composable
fun SupabaseUserProfileInfo(
    userData: SupabaseUserDataIptv?,
    isLoading: Boolean
) {
    val context = LocalContext.current
    var cachedUserData by remember { mutableStateOf<SupabaseUserDataIptv?>(null) }
    var localIsLoading by remember { mutableStateOf(isLoading) }
    
    // 如果传入的userData为null，尝试从缓存中获取用户数据
    LaunchedEffect(userData) {
        if (userData == null) {
            localIsLoading = true
            try {
                withContext(Dispatchers.IO) {
                    // 首先尝试获取任意类型的数据，避免直接类型转换
                    val anyData = SupabaseCacheManager.getCache<Any>(
                        context,
                        SupabaseCacheKey.USER_DATA
                    )
                    
                    // 使用安全转换方法处理数据
                    if (anyData != null) {
                        val convertedData = SupabaseCacheManager.safeConvertToUserData(anyData)
                        if (convertedData != null) {
                            Log.d("SupabaseUserProfile", "从缓存加载用户数据成功: ${convertedData.username} (${convertedData.userid})")
                            cachedUserData = convertedData
                        } else {
                            Log.d("SupabaseUserProfile", "缓存数据转换失败")
                        }
                    } else {
                        Log.d("SupabaseUserProfile", "缓存中未找到用户数据")
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseUserProfile", "从缓存加载用户数据失败: ${e.message}", e)
            }
            localIsLoading = false
        }
    }
    
    // 使用传入的userData或从缓存中获取的userData
    val effectiveUserData = userData ?: cachedUserData
    
    // 添加日志记录
    LaunchedEffect(effectiveUserData) {
        if (effectiveUserData != null) {
            Log.d("SupabaseUserProfile", "显示用户资料: ${effectiveUserData.username} (${effectiveUserData.userid})")
        } else {
            Log.d("SupabaseUserProfile", "未获取到用户资料数据")
        }
    }
    
    // 添加状态变量跟踪错误
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // 使用Effect处理数据验证，而不是直接在UI中使用try-catch
    if (effectiveUserData != null) {
        LaunchedEffect(effectiveUserData) {
            try {
                // 验证数据的可用性，如果有问题会抛出异常
                effectiveUserData.username
                effectiveUserData.userid
                // 验证成功，重置错误状态
                hasError = false
            } catch (e: Exception) {
                // 捕获并记录错误
                Log.e("SupabaseUserProfile", "数据验证异常: ${e.message}", e)
                hasError = true
                errorMessage = "加载用户资料时出现错误: ${e.message}"
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (localIsLoading) {
            // 加载中状态
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFFFFD700)
            )
            Text(
                text = "正在加载用户资料...",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        } else if (effectiveUserData == null) {
            // 无数据状态
            Text(
                text = "点击\"前往登录\"标签可进入登录界面，登录个人账号后获取用户数据",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else if (hasError) {
            // 错误状态
            Text(
                text = "加载用户资料时出现错误，请稍后再试",
                color = Color(0xFFF44336),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            // 用户基本信息
            ProfileInfoSection(title = "基本信息") {
                UserProfileInfoRow(label = "用户名", value = effectiveUserData.username)
                UserProfileInfoRow(label = "用户ID", value = effectiveUserData.userid)
                UserProfileInfoRow(label = "注册邮箱", value = effectiveUserData.email ?: "未设置")
                UserProfileInfoRow(label = "账户状态", value = effectiveUserData.accountstatus ?: "正常")
                UserProfileInfoRow(label = "注册时间", value = formatUserDateTime(effectiveUserData.created_at))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // VIP信息
            ProfileInfoSection(title = "VIP状态") {
                UserProfileInfoRow(
                    label = "VIP状态", 
                    value = if (effectiveUserData.is_vip) "VIP用户" else "普通用户",
                    valueColor = if (effectiveUserData.is_vip) Color(0xFFFFD700) else Color.LightGray
                )
                
                if (effectiveUserData.is_vip) {
                    UserProfileInfoRow(label = "开始时间", value = formatUserDateTime(effectiveUserData.vipstart))
                    UserProfileInfoRow(label = "到期时间", value = formatUserDateTime(effectiveUserData.vipend))
                    
                    // 计算剩余天数
                    val remainingDays = calculateRemainingDays(effectiveUserData.vipend)
                    UserProfileInfoRow(
                        label = "剩余时间", 
                        value = "$remainingDays 天",
                        valueColor = when {
                            remainingDays > 30 -> Color(0xFF4CAF50) // 绿色，剩余超过30天
                            remainingDays > 7 -> Color(0xFFFFC107)  // 黄色，剩余7-30天
                            else -> Color(0xFFF44336)               // 红色，剩余不足7天
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 登录信息
            ProfileInfoSection(title = "登录信息") {
                UserProfileInfoRow(label = "最后登录时间", value = formatUserDateTime(effectiveUserData.lastlogintime))
                UserProfileInfoRow(label = "最后登录设备", value = effectiveUserData.lastlogindevice ?: "未记录")
            }
        }
    }
}

/**
 * 资料信息小节
 */
@Composable
fun ProfileInfoSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        content()
    }
}

/**
 * 资料信息行
 */
@Composable
fun UserProfileInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = Color.LightGray,
            fontSize = 16.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp
        )
    }
}

/**
 * 格式化日期时间字符串
 */
fun formatUserDateTime(dateTimeString: String?): String {
    if (dateTimeString.isNullOrEmpty()) return "未设置"
    
    return try {
        // Supabase返回的日期格式可能是ISO 8601格式
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")  // Supabase日期是UTC时间

        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()  // 转为本地时间

        val date = inputFormat.parse(dateTimeString)
        date?.let { outputFormat.format(it) } ?: dateTimeString
    } catch (e: Exception) {
        // 尝试另一种格式
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(dateTimeString)
            date?.let { outputFormat.format(it) } ?: dateTimeString
        } catch (e2: Exception) {
            Log.e("SupabaseUserProfile", "日期格式化错误: $dateTimeString", e2)
            dateTimeString  // 如果解析失败，则返回原始字符串
        }
    }
}

/**
 * 计算剩余天数
 */
fun calculateRemainingDays(endDateString: String?): Int {
    if (endDateString.isNullOrEmpty()) return 0
    
    return try {
        // 首先尝试解析标准ISO格式
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")  // Supabase日期是UTC时间
        
        var endDate = try {
            inputFormat.parse(endDateString)
        } catch (e: Exception) {
            // 如果失败，尝试简单的年月日格式
            val simpleFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            simpleFormat.parse(endDateString)
        }
        
        if (endDate == null) {
            Log.e("SupabaseUserProfile", "无法解析日期: $endDateString")
            return 0
        }
        
        val currentDate = Date()
        val diff = endDate.time - currentDate.time
        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
        
        // 记录日志以便调试
        Log.d("SupabaseUserProfile", "日期计算: 当前=${currentDate}, 结束=${endDate}, 差值=${diff}毫秒, ${days}天")
        
        // 确保至少返回0或正数
        return if (days < 0) 0 else days
    } catch (e: Exception) {
        Log.e("SupabaseUserProfile", "计算剩余天数异常: $endDateString", e)
        0  // 如果出现任何异常，默认返回0
    }
} 