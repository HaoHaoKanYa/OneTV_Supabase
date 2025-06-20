package top.cywin.onetv.tv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.repositories.user.RetrofitClient
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import androidx.compose.ui.platform.LocalContext
import top.cywin.onetv.core.data.repositories.user.EmailRequest
import top.cywin.onetv.core.data.repositories.user.ResetRequest

class ForgotPasswordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTVTheme {
                ForgotPasswordScreen(
                    onResetSuccess = {
                        Toast.makeText(this, "验证码已发送至邮箱", Toast.LENGTH_LONG).show()
                    },
                    onBackClick = { finish() },
                    onResetComplete = { finish() } // Activity结束的回调
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onResetSuccess: () -> Unit,
    onBackClick: () -> Unit,
    onResetComplete: () -> Unit  // 新增回调参数，用于操作完成后的处理（例如关闭当前页面）
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCodeSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val infiniteTransition = rememberInfiniteTransition()
    val borderColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFFF6B6B),
        targetValue = Color(0xFF4ECDC4),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo区域
        Box(
            modifier = Modifier
                .size(120.dp)
                .border(4.dp, borderColor, CircleShape)
                .clip(CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.onetv_logo),
                contentDescription = "Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 提示文本
        Text(
            text = "请输入您的注册邮箱，",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (isCodeSent) "请输入收到的验证码和新密码" else "我们将向您发送验证码",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
            modifier = Modifier.padding(top = 8.dp)
        )

        // 错误提示
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // 邮箱输入框（始终显示）
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            enabled = !isCodeSent
        )

        // 验证码和新密码输入框（验证码发送后显示）
        if (isCodeSent) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("新密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }

        // 按钮区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 返回登录按钮
            TextButton(onClick = onBackClick) {
                Text("返回登录")
            }

            // 动态按钮：获取验证码或提交
            Button(
                onClick = {
                    if (email.isBlank()) {
                        errorMessage = "请输入邮箱"
                        return@Button
                    }

                    // 修改后的按钮点击逻辑
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            if (!isCodeSent) {
                                val response = RetrofitClient.instance.sendResetCode(EmailRequest(email))
                                if (response.isSuccessful) {
                                    isCodeSent = true
                                    countdown = 60
                                    onResetSuccess()
                                } else {
                                    // 处理后端返回的具体错误
                                    errorMessage = response.errorBody()?.string() ?: "验证码发送失败"
                                }
                            } else {
                                val response = RetrofitClient.instance.resetPassword(ResetRequest(email, code, newPassword))
                                if (response.isSuccessful) {
                                    Toast.makeText(context, "密码重置成功", Toast.LENGTH_SHORT).show()
                                    onResetComplete()
                                } else {
                                    errorMessage = response.errorBody()?.string() ?: "密码重置失败"
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "网络错误: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.width(150.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isCodeSent) "提交" else "获取验证码")
                }
            }
        }

        // 倒计时逻辑
        LaunchedEffect(countdown) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }
}

// 数据类定义
data class ResetRequest(val email: String, val code: String, val newPassword: String)
