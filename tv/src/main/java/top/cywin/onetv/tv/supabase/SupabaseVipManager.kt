package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import top.cywin.onetv.core.data.repositories.supabase.SupabaseConstants
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserRepository
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import top.cywin.onetv.tv.supabase.SupabaseUserProfileInfoSessionManager

private const val TAG = "VipManager"

/**
 * VIP管理界面
 */
@Composable
fun SupabaseVipManager(
    userData: SupabaseUserDataIptv?,
    isLoading: Boolean,
    context: Context
) {
    val scope = rememberCoroutineScope()
    var isCheckingStatus by remember { mutableStateOf(false) }
    var isActivating by remember { mutableStateOf(false) }
    var activationCode by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val focusRequester = remember { FocusRequester() }
    
    // VIP状态
    var vipStatus by remember { mutableStateOf<VipStatus?>(null) }
    
    // 检查VIP状态
    fun checkVipStatus() {
        scope.launch {
            try {
                isCheckingStatus = true
                val session = SupabaseSessionManager.getSession(context)
                if (session == null) {
                    Log.w(TAG, "未登录，无法获取VIP状态")
                    statusMessage = "未登录，请先登录" to false
                    return@launch
                }
                
                Log.d(TAG, "开始从服务器获取VIP状态, sessionId: ${session.take(8)}...")
                
                // 尝试调用服务器函数API
                withContext(Dispatchers.IO) { // 确保网络请求在IO线程
                    try {
                        val apiUrl = "${SupabaseClient.getUrl()}/functions/v1/vip-management/status"
                        Log.d(TAG, "开始API调用: $apiUrl")
                        
                        val httpClient = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build()
                        
                        val request = Request.Builder()
                            .url(apiUrl)
                            .addHeader("Authorization", "Bearer $session")
                            .addHeader("apikey", SupabaseClient.getKey())
                            .addHeader("Content-Type", "application/json")
                            .get()
                            .build()
                        
                        Log.d(TAG, "发送VIP状态API请求...")
                        httpClient.newCall(request).execute().use { response ->
                            val responseBody = response.body?.string()
                            Log.d(TAG, "API响应: ${response.code}, body: $responseBody")
                            
                            if (response.isSuccessful && responseBody != null) {
                                val jsonObject = JSONObject(responseBody)
                                vipStatus = VipStatus(
                                    isVip = jsonObject.getBoolean("is_vip"),
                                    vipStart = jsonObject.optString("vipstart"),
                                    vipEnd = jsonObject.optString("vipend"),
                                    daysRemaining = jsonObject.optInt("days_remaining")
                                )
                                withContext(Dispatchers.Main) {
                                    statusMessage = "VIP状态已更新" to true
                                }
                                Log.d(TAG, "成功获取VIP状态: $vipStatus")
                            } else {
                                throw Exception("API调用失败: ${response.code} - ${responseBody ?: "无响应内容"}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "API调用异常", e)
                        
                        // 如果API调用失败但有用户数据，则使用用户数据构建VIP状态
                        withContext(Dispatchers.Main) {
                            if (userData != null && vipStatus == null) {
                                val isVip = userData.is_vip
                                val vipStart = userData.vipstart
                                val vipEnd = userData.vipend
                                
                                // 计算剩余天数
                                val remainingDays = if (vipEnd != null) {
                                    calculateVipRemainingDays(vipEnd)
                                } else {
                                    0
                                }
                                
                                vipStatus = VipStatus(
                                    isVip = isVip,
                                    vipStart = vipStart,
                                    vipEnd = vipEnd,
                                    daysRemaining = remainingDays
                                )
                                
                                statusMessage = "使用本地VIP状态" to true
                            } else {
                                statusMessage = "获取VIP状态失败，请稍后再试" to false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "VIP状态检查异常", e)
                // 如果异常后vipStatus为空但userData不为空，使用用户数据构建默认状态
                if (vipStatus == null && userData != null) {
                    vipStatus = VipStatus(
                        isVip = userData.is_vip,
                        vipStart = userData.vipstart,
                        vipEnd = userData.vipend,
                        daysRemaining = if (userData.vipend != null) calculateVipRemainingDays(userData.vipend) else 0
                    )
                    statusMessage = "已加载本地VIP状态" to true
                } else {
                    statusMessage = "检查VIP状态出错: ${e.message}" to false
                }
            } finally {
                isCheckingStatus = false
                // 5秒后清除状态消息
                if (statusMessage?.first?.contains("失败") == true || statusMessage?.first?.contains("错误") == true) {
                    scope.launch {
                        kotlinx.coroutines.delay(5000)
                        statusMessage = null
                    }
                } else {
                    statusMessage = null
                }
            }
        }
    }
    
    // 激活VIP
    fun activateVip() {
        scope.launch {
            try {
                if (activationCode.isBlank()) {
                    statusMessage = "激活码不能为空" to false
                    return@launch
                }
                
                isActivating = true
                val session = SupabaseSessionManager.getSession(context)
                if (session == null) {
                    Log.w(TAG, "未登录，无法激活VIP")
                    statusMessage = "未登录，请先登录" to false
                    return@launch
                }
                
                Log.d(TAG, "准备激活VIP, 激活码: ${activationCode.trim()}, sessionId: ${session.take(8)}...")
                
                // 确保网络请求在IO线程上执行
                withContext(Dispatchers.IO) {
                    try {
                        val apiUrl = "${SupabaseClient.getUrl()}/functions/v1/vip-management/activate"
                        Log.d(TAG, "开始API调用: $apiUrl")
                        
                        val httpClient = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .build()
                        
                        // 请求体
                        val jsonBody = JSONObject().apply {
                            put("activationCode", activationCode.trim())
                        }
                        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
                        
                        val request = Request.Builder()
                            .url(apiUrl)
                            .addHeader("Authorization", "Bearer $session")
                            .addHeader("apikey", SupabaseClient.getKey())
                            .addHeader("Content-Type", "application/json")
                            .post(requestBody)
                            .build()
                        
                        Log.d(TAG, "发送VIP激活请求...")
                        httpClient.newCall(request).execute().use { response ->
                            val responseBody = response.body?.string()
                            Log.d(TAG, "VIP激活响应: ${response.code}, body: $responseBody")
                            
                            withContext(Dispatchers.Main) {
                                if (response.isSuccessful && responseBody != null) {
                                    val jsonObject = JSONObject(responseBody)
                                    val message = jsonObject.optString("message", "激活成功")
                                    val days = jsonObject.optInt("days", 365)  // 默认365天
                                    
                                    Log.d(TAG, "VIP激活成功: $message, 天数: $days")
                                    
                                    // 激活成功后，强制刷新VIP状态
                                    // 使用户资料缓存失效，确保下次加载时获取最新数据
                                    SupabaseUserProfileInfoSessionManager.invalidateCache(context)
                                    Log.d(TAG, "已使用户资料缓存失效，下次将从服务器获取最新数据")
                                    
                                    // 刷新VIP状态
                                    checkVipStatus()
                                    
                                    Toast.makeText(context, "VIP激活成功，有效期 $days 天", Toast.LENGTH_LONG).show()
                                    statusMessage = "激活成功：$message" to true
                                    activationCode = ""
                                } else {
                                    Log.e(TAG, "激活VIP失败: $responseBody")
                                    val errorMessage = if (responseBody != null) {
                                        try {
                                            JSONObject(responseBody).optString("error", "激活失败，请检查激活码")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "解析错误响应失败", e)
                                            "激活失败，请检查激活码"
                                        }
                                    } else {
                                        "激活失败，请检查激活码"
                                    }
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                    statusMessage = errorMessage to false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "VIP激活网络请求异常", e)
                        withContext(Dispatchers.Main) {
                            statusMessage = "激活VIP出错: ${e.message}" to false
                            Toast.makeText(context, "激活VIP失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "激活VIP过程异常", e)
                statusMessage = "激活VIP出错: ${e.message}" to false
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "激活VIP失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isActivating = false
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (statusMessage?.second == false) {
                        statusMessage = null
                    }
                }
            }
        }
    }
    
    // 初始加载
    LaunchedEffect(userData) {
        if (!isLoading && userData != null) {
            // 每次打开界面都从服务器获取最新VIP状态
            checkVipStatus()
        }
    }
    
    // UI界面
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFFFFD700)
            )
            Text(
                text = "加载中...",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            // 状态消息
            statusMessage?.let { (message, isSuccess) ->
                Text(
                    text = message,
                    color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
            
            // VIP状态信息
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF2C3E50).copy(alpha = 0.8f),// 调整背景透明度
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 标题和刷新按钮放在同一行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "当前VIP状态",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        // 刷新按钮移到标题行
                        IconButton(
                            onClick = { checkVipStatus() },
                            enabled = !isCheckingStatus,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = Color(0xFF2C3E50), // 更接近整体背景
                                    shape = RoundedCornerShape(4.dp)
                                )
                        ) {
                            if (isCheckingStatus) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新VIP状态",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    // 添加间距
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    vipStatus?.let { status ->
                        VipInfoRow(
                            label = "会员状态",
                            value = if (status.isVip) "VIP会员" else "普通用户",
                            valueColor = if (status.isVip) Color(0xFFFFD700) else Color.LightGray
                        )
                        
                        if (status.isVip) {
                            VipInfoRow(label = "生效时间", value = formatVipDateTime(status.vipStart))
                            VipInfoRow(label = "到期时间", value = formatVipDateTime(status.vipEnd))
                            VipInfoRow(
                                label = "剩余天数",
                                value = "${status.daysRemaining} 天",
                                valueColor = when {
                                    status.daysRemaining > 30 -> Color(0xFF4CAF50)
                                    status.daysRemaining > 7 -> Color(0xFFFFC107)  
                                    else -> Color(0xFFF44336)                
                                }
                            )
                        }
                    } ?: run {
                        Text(
                            text = "加载VIP状态中...",
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 激活码输入区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF2C3E50).copy(alpha = 0.8f),// 调整背景透明度
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "VIP激活（激活后需要重新登录账号）",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "输入有效的激活码以激活或延长您的VIP会员",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 激活码输入框
                    OutlinedTextField(
                        value = activationCode,
                        onValueChange = { activationCode = it },
                        label = { Text("输入激活码") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = Color.Black.copy(alpha = 0.8f),// 调整背景透明度
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.8f),// 调整背景透明度
                            focusedIndicatorColor = Color(0xFFFFD700),
                            focusedLabelColor = Color(0xFFFFD700)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    
                    // 激活按钮
                    Button(
                        onClick = { activateVip() },
                        enabled = !isActivating && activationCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isActivating) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("激活VIP", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * VIP状态数据类
 */
data class VipStatus(
    val isVip: Boolean,
    val vipStart: String?,
    val vipEnd: String?,
    val daysRemaining: Int
)

/**
 * VIP信息行组件
 */
@Composable
fun VipInfoRow(
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
            color = Color.LightGray,
            fontSize = 16.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 计算VIP剩余天数
 */
fun calculateVipRemainingDays(endDateString: String?): Int {
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
            Log.e(TAG, "无法解析日期: $endDateString")
            return 0
        }
        
        val currentDate = Date()
        val diff = endDate.time - currentDate.time
        val days = (diff / (1000 * 60 * 60 * 24)).toInt()
        
        // 记录日志以便调试
        Log.d(TAG, "日期计算: 当前=${currentDate}, 结束=${endDate}, 差值=${diff}毫秒, ${days}天")
        
        // 确保至少返回0或正数
        return if (days < 0) 0 else days
    } catch (e: Exception) {
        Log.e(TAG, "计算剩余天数异常: $endDateString", e)
        0  // 如果出现任何异常，默认返回0
    }
}

/**
 * 格式化日期时间显示
 */
fun formatVipDateTime(dateTime: String?): String {
    return dateTime?.let {
        try {
            // Supabase返回的日期格式可能是ISO 8601格式
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")  // Supabase日期是UTC时间

            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(it)
            date?.let { outputFormat.format(it) } ?: it
        } catch (e: Exception) {
            // 尝试另一种格式
            try {
                val simpleFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = simpleFormat.parse(it)
                date?.let { simpleFormat.format(it) } ?: it
            } catch (e2: Exception) {
                Log.e(TAG, "日期格式化错误: $it", e2)
                it  // 如果解析失败，则返回原始字符串
            }
        }
    } ?: "未设置"
}
