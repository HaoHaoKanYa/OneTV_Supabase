package top.cywin.onetv.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.cywin.onetv.tv.ui.screens.main.MainViewModel
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// LoginActivity.kt中的相关代码修改

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyTVTheme {
                LoginScreen(
                    onLoginSuccess = { sessionId ->
                        saveSessionToSharedPreferences(sessionId)
                        Log.d("LoginActivity", "✅ Session更新成功｜session=${sessionId.take(6)}...")

                        val mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

                        // 强制执行缓存清除，无论用户是否为VIP
                        mainViewModel.clearAllCache(true) {
                            // 在清除旧缓存后，通过后台线程安全获取新数据
                            lifecycleScope.launch {
                                // 使用自定义的 suspend 函数将 forceRefreshUserDataSync 转换为挂起函数
                                val success = withContext(Dispatchers.IO) {
                                    forceRefreshUserDataSyncSuspend(mainViewModel, this@LoginActivity)
                                }

                                // 判断数据是否加载成功
                                if (success) {
                                    Log.d("LoginActivity", "🎉 新数据已加载｜开始跳转")
                                    startActivity(
                                        Intent(this@LoginActivity, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                    finish()
                                } else {
                                    Log.e("LoginActivity", "❌ 数据加载失败｜保持当前页面")
                                }
                            }
                        }
                    },
                    onRegisterClick = { startActivity(Intent(this, RegisterActivity::class.java)) },
                    onForgotPasswordClick = { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
                )
            }
        }
    }

    private fun saveSessionToSharedPreferences(session: String) {
        getSharedPreferences("user", MODE_PRIVATE).edit().putString("session", session).apply()
    }
}

/**
 * 将MainViewModel.forceRefreshUserDataSync的回调式API包装为一个挂起函数，
 * 便于在协程中使用并获取返回的Boolean值。
 *
 * @param mainViewModel MainViewModel实例
 * @param context 当前上下文
 * @return 数据刷新成功返回true，否则返回false
 */
suspend fun forceRefreshUserDataSyncSuspend(mainViewModel: MainViewModel, context: Context): Boolean =
    suspendCancellableCoroutine { cont ->
        mainViewModel.forceRefreshUserDataSync(context) { success ->
            cont.resume(success)
        }
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE).apply {
            email = getString("saved_email", "") ?: ""
            rememberMe = getBoolean("remember_me", false)
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val borderColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFFF6B6B),
        targetValue = Color(0xFF4ECDC4),
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo Section
        Box(
            modifier = Modifier
                .size(120.dp)
                .border(4.dp, borderColor, CircleShape)
                .clip(CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.onetv_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Titles
        Text(
            text = "欢迎回来",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "很高兴再次见到您，请登录您的账号",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Error Message
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Input Fields
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        // 关键修改：将注册链接整合到操作行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：记住账号
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it }
                )
                Text("记住账号", modifier = Modifier.padding(start = 8.dp))
            }

            // 中间：注册链接（TV端焦点居中）
            TextButton(
                onClick = onRegisterClick,
                modifier = Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally)
            ) {
                Text("没有账号？立即注册")
            }

            // 右侧：忘记密码
            TextButton(onClick = onForgotPasswordClick) {
                Text("忘记密码？")
            }
        }

        // Login Button
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "请输入邮箱和密码"
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    try {
                        val sessionId = login(email, password)
                        context.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE).edit()
                            .apply {
                                if (rememberMe) putString("saved_email", email) else remove("saved_email")
                                putBoolean("remember_me", rememberMe)
                            }.apply()
                        onLoginSuccess(sessionId)
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "登录失败"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            ) else Text("登录")
        }

        // Footer
        Text(
            text = "登录即表示您同意：\n资源可能因不可抗力的原因而失效，开发者保留解释权。\n本平台仅作为第三方资源的整合入口，不提供直播/点播服务接口，\n所有内容责任均由第三方承担，开发者不承担任何责任",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

// 登录API调用
suspend fun login(email: String, password: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://iptv.liubaotea.online/api/login"
            val requestBody = "{\"email\":\"$email\",\"password\":\"$password\"}"

            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val outputStream = connection.outputStream
            outputStream.write(requestBody.toByteArray())
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                // 解析JSON响应
                val sessionId = org.json.JSONObject(response).getString("sessionId")
                sessionId
            } else {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                errorStream?.close()

                val errorMessage = try {
                    org.json.JSONObject(errorResponse).getString("error")
                } catch (e: Exception) {
                    "登录失败 (错误码: $responseCode)"
                }

                throw Exception(errorMessage)
            }
        } catch (e: Exception) {
            throw Exception(e.message ?: "网络错误，请稍后重试")
        }

    }
}