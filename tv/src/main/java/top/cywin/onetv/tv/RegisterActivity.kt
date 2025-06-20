package top.cywin.onetv.tv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.repositories.user.RetrofitClient
import top.cywin.onetv.core.data.repositories.user.SensitiveWordFilter
import top.cywin.onetv.core.data.repositories.user.RegisterRequest
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import kotlin.coroutines.cancellation.CancellationException

// 数据类，用于校验用户名状态
data class UsernameValidation(
    val isValid: Boolean = false,
    val message: String = "",
    val isChecking: Boolean = false
)

// 普通函数：校验用户名，不依赖 Compose 上下文
private fun validateUsername(input: String, context: Context): UsernameValidation {
    val regex = Regex("^[\\w\\u4e00-\\u9fa5]+$")
    return when {
        input.isEmpty() -> UsernameValidation()
        input.length < 4 -> UsernameValidation(message = "至少4个字符")
        input.length > 20 -> UsernameValidation(message = "不能超过20个字符")
        !input.matches(regex) -> UsernameValidation(message = "仅支持中文/英文/数字")
        SensitiveWordFilter(context).containsSensitiveWord(input) ->
            UsernameValidation(message = "包含敏感词")
        else -> UsernameValidation(isValid = true, message = "✔ 可用")
    }
}

// 数据类，用于其他校验状态（示例，可根据需要调整）
data class ValidationState(
    val isError: Boolean = false,
    val message: String = ""
)

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTVTheme {
                RegisterScreen(
                    onRegisterSuccess = {
                        Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    // 在组合函数顶部提前获取 context
    val context = LocalContext.current

    var username by remember { mutableStateOf("") }
    var usernameValidation by remember { mutableStateOf(UsernameValidation()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAgreed by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 防抖检查用的 Job
    var lastCheckJob: Job? by remember { mutableStateOf(null) }
    var validationState by remember { mutableStateOf(ValidationState()) }
    var showAgreementDialog by remember { mutableStateOf("") }

    // 流动边框动画
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
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 头像
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

        // 标题与副标题
        Text(
            text = "加入我们",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = "创建您的账号，开启精彩视界之旅",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 显示错误消息
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 用户名输入框
        // 用户名输入框及检查逻辑
        OutlinedTextField(
            value = username,
            onValueChange = { newInput ->
                username = newInput
                // 1. 本地校验（包括敏感词检测）
                usernameValidation = validateUsername(newInput, context)

                // 2. 只有当本地校验通过后才发起服务器检查
                lastCheckJob?.cancel()  // 取消之前的检查任务，避免竞争
                if (usernameValidation.isValid) {
                    lastCheckJob = scope.launch {
                        // 防抖延迟 500 毫秒
                        delay(500)
                        // 设置检查中状态，显示进度指示
                        usernameValidation = usernameValidation.copy(isChecking = true)
                        try {
                            val response = RetrofitClient.instance.checkUsername(newInput)
                            if (response.isSuccessful) {
                                val available = response.body()?.available ?: false
                                // 根据服务器返回结果更新状态
                                usernameValidation = usernameValidation.copy(
                                    isValid = available,
                                    message = if (available) "✔ 可用" else "✖ 已被使用"
                                )
                            }
                        } catch (e: Exception) {
                            // 如果捕获到的是协程取消异常，则直接抛出，不更新状态
                            if (e is CancellationException) throw e
                            // 其他异常则更新提示为“检查失败”
                            usernameValidation = usernameValidation.copy(message = "检查失败")
                        } finally {
                            // 检查完成后取消加载状态
                            usernameValidation = usernameValidation.copy(isChecking = false)
                        }
                    }
                }
            },
            label = { Text("用户名") },
            supportingText = {
                if (usernameValidation.message.isNotEmpty()) {
                    Text(
                        text = usernameValidation.message,
                        color = if (usernameValidation.isValid) Color.Green else Color.Red
                    )
                }
            },
            trailingIcon = {
                if (usernameValidation.isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // 邮箱输入框
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true
        )

        // 密码输入框
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
                        contentDescription = "Toggle Password"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        // 协议、复选框及返回登录行
        val isScrolledToBottom by remember {
            derivedStateOf { scrollState.value == scrollState.maxValue }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "《用户协议与隐私说明》",
                    modifier = Modifier.clickable { showAgreementDialog = "privacy" }
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAgreed,
                        onCheckedChange = { isAgreed = it },
                        enabled = isScrolledToBottom
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("我已阅读并同意上述协议")
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onBackClick) {
                    Text("已有账号？返回登录")
                }
            }
        }

        // 注册按钮
        Button(
            onClick = {
                if (!isAgreed) {
                    errorMessage = "请阅读并同意协议"
                    return@Button
                }
                if (username.isBlank()) {
                    errorMessage = "请输入用户名"
                    return@Button
                }
                if (!usernameValidation.isValid) {
                    errorMessage = "请检查用户名格式"
                    return@Button
                }
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "请输入邮箱和密码"
                    return@Button
                }

                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        register(email, password, username)
                        onRegisterSuccess()
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "注册失败"
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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("注册")
            }
        }

        // 协议对话框
        if (showAgreementDialog.isNotEmpty()) {
            val agreementText = remember {
                try {
                    val fileName = if (showAgreementDialog == "usage")
                        "Important_Notices.md"
                    else
                        "Privacy_Statement.md"
                    context.assets.open(fileName).bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "协议加载失败：${e.message}"
                }
            }

            AlertDialog(
                onDismissRequest = { showAgreementDialog = "" },
                title = {
                    Text(
                        text = if (showAgreementDialog == "usage") "使用协议" else "隐私条款",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = agreementText,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                (context as Activity).finish()
                            },
                            modifier = Modifier.padding(end = 24.dp)
                        ) {
                            Text("不同意")
                        }

                        Button(
                            onClick = {
                                isAgreed = true
                                showAgreementDialog = ""
                            }
                        ) {
                            Text("同意")
                        }
                    }
                }
            )
        }

        // 免责声明文本
        Text(
            text = "注册即表示您同意：\n" +
                    "本平台仅作为第三方资源的整合入口，不提供直播/点播服务接口，\n" +
                    "所有内容责任均由第三方承担，开发者不承担任何责任",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
        )
    }
}

// 注册函数（调用 API 进行注册）
suspend fun register(email: String, password: String, username: String) {
    try {
        val response = RetrofitClient.instance.register(
            RegisterRequest(email, password, username)
        )
        if (!response.isSuccessful || response.body()?.success != true) {
            throw Exception(response.body()?.error ?: "注册失败")
        }
    } catch (e: Exception) {
        throw Exception("网络错误: ${e.message}")
    }
}


