package top.cywin.onetv.tv.supabase

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseRepository
import top.cywin.onetv.tv.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Supabase身份验证UI组件
 * 提供登录、注册和密码重置功能的自定义实现
 */
object SupabaseAuthUI {
    
    private val repository = SupabaseRepository()
    
    /**
     * 登录组件
     * @param onLoginSuccess 登录成功回调
     */
    @Composable
    fun LoginScreen(onLoginSuccess: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var passwordVisible by remember { mutableStateOf(false) }
        var rememberCredentials by remember { mutableStateOf(true) } // 默认选中记住账号密码
        
        // 获取登录进度日志
        val progressLogMessages = LoginProgressState.logMessages
        // 控制进度日志区域的显示 - 只要有日志消息就显示
        val showProgressLog = progressLogMessages.isNotEmpty()
        
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current
        val scrollState = rememberScrollState()
        
        // 加载保存的账号密码
        LaunchedEffect(Unit) {
            val sharedPrefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPrefs.getString("saved_email", "")
            val savedPassword = sharedPrefs.getString("saved_password", "")
            val savedRemember = sharedPrefs.getBoolean("remember_credentials", true)
            
            email = savedEmail ?: ""
            password = savedPassword ?: ""
            rememberCredentials = savedRemember
            
            // 清除之前的登录日志
            LoginProgressState.clearLogMessages()
        }
        
        // 检查是否已经登录
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val user = repository.getCurrentUser()
                    if (user != null) {
                        onLoginSuccess()
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseAuthUI", "检查当前用户失败", e)
                }
            }
        }
        
        // 自适应布局
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 使用Box包装滚动区域，确保内容居中且可滚动
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = if (isPortrait) 24.dp else 64.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 在竖屏模式下，Logo和标题垂直排列
                // 在横屏模式下，使用更紧凑的布局
                
                Spacer(modifier = Modifier.height(if (isPortrait) 40.dp else 20.dp))
                
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.onetv_logo),
                    contentDescription = "OneTV Logo",
                    modifier = Modifier
                        .size(if (isPortrait) 100.dp else 80.dp)
                        .padding(bottom = 16.dp)
                )
                
                Text(
                    text = "登录账号",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // 错误信息
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // 电子邮件输入
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱地址") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoading,
                    singleLine = true
                )
                
                // 密码输入
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isLoading,
                    singleLine = true
                )
                
                // 记住账号密码选项
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Checkbox(
                        checked = rememberCredentials,
                        onCheckedChange = { rememberCredentials = it }
                    )
                    Text(
                        text = "记住账号密码",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // 登录按钮
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            LoginProgressState.clearLogMessages()
                            LoginProgressState.addLogMessage("开始登录...")
                            
                            try {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "请输入邮箱和密码"
                                    isLoading = false
                                    return@launch
                                }
                                
                                val session = repository.loginUser(email, password)
                                if (session != null) {
                                    // 保存账号密码（如果选择了记住）
                                    val sharedPrefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                                    sharedPrefs.edit().apply {
                                        if (rememberCredentials) {
                                            putString("saved_email", email)
                                            putString("saved_password", password)
                                        } else {
                                            remove("saved_email")
                                            remove("saved_password")
                                        }
                                        putBoolean("remember_credentials", rememberCredentials)
                                        apply()
                                    }
                                    
                                    LoginProgressState.addLogMessage("登录成功，准备更新用户数据...")
                                    
                                    // 登录成功
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "登录失败，请检查邮箱和密码"
                                }
                            } catch (e: Exception) {
                                val errorMsg = when {
                                    e.message?.contains("Invalid login credentials") == true -> "邮箱或密码错误"
                                    e.message?.contains("Email not confirmed") == true -> "邮箱未验证，请先验证邮箱"
                                    e.message?.contains("network") == true -> "网络连接错误，请检查网络"
                                    else -> e.message ?: "登录失败，请稍后重试"
                                }
                                errorMessage = errorMsg
                                Log.e("SupabaseAuthUI", "登录失败", e)
                                LoginProgressState.addLogMessage("错误: $errorMsg")
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("登录")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 添加社交登录按钮区域
                Text(
                    text = "或者使用以下方式登录",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 谷歌登录按钮
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                
                                try {
                                    LoginProgressState.addLogMessage("正在使用Google账号登录...")
                                    
                                    repository.signInWithGoogle()
                                    
                                    LoginProgressState.addLogMessage("Google登录请求已发送，等待授权...")
                                } catch (e: Exception) {
                                    val errorMsg = e.message ?: "Google登录失败，请稍后重试"
                                    errorMessage = errorMsg
                                    Log.e("SupabaseAuthUI", "Google登录失败", e)
                                    LoginProgressState.addLogMessage("错误: $errorMsg")
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f)
                            .padding(end = 4.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google登录",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google")
                        }
                    }
                    
                    // GitHub登录按钮
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                
                                try {
                                    LoginProgressState.addLogMessage("正在使用GitHub账号登录...")
                                    
                                    repository.signInWithGithub()
                                    
                                    LoginProgressState.addLogMessage("GitHub登录请求已发送，等待授权...")
                                } catch (e: Exception) {
                                    val errorMsg = e.message ?: "GitHub登录失败，请稍后重试"
                                    errorMessage = errorMsg
                                    Log.e("SupabaseAuthUI", "GitHub登录失败", e)
                                    LoginProgressState.addLogMessage("错误: $errorMsg")
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f)
                            .padding(start = 4.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = "GitHub登录",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GitHub")
                        }
                    }
                }
                
                // 进度日志区域 - 当有日志消息时显示，直到登录完成
                AnimatedVisibility(
                    visible = showProgressLog,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .heightIn(max = 200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                "处理进度",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Divider(modifier = Modifier.padding(bottom = 8.dp))
                            
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                            ) {
                                items(progressLogMessages) { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                                
                                // 自动滚动到底部
                                item {
                                    LaunchedEffect(progressLogMessages.size) {
                                        if (progressLogMessages.isNotEmpty()) {
                                            delay(100)  // 短暂延迟确保UI更新
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 注册和忘记密码链接
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { 
                            // 跳转到注册页面
                            context.startActivity(Intent(context, SupabaseRegisterActivity::class.java))
                        }
                    ) {
                        Text("注册账号")
                    }
                    
                    TextButton(
                        onClick = { 
                            // 跳转到忘记密码页面
                            context.startActivity(Intent(context, SupabaseForgotPasswordActivity::class.java))
                        }
                    ) {
                        Text("忘记密码")
                    }
                }
                
                // 底部提示文本
                Text(
                    text = "登录即表示您同意服务条款和隐私政策",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(if (isPortrait) 40.dp else 20.dp))
            }
        }
    }
    
    /**
     * 注册组件
     * @param onSignUpSuccess 注册成功回调
     * @param enabled 是否启用注册功能，默认为true
     */
    @Composable
    fun RegisterScreen(onSignUpSuccess: () -> Unit, enabled: Boolean = true) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var passwordVisible by remember { mutableStateOf(false) }
        
        // 用户协议相关状态
        var isAgreementAccepted by remember { mutableStateOf(false) }
        var showAgreementDialog by remember { mutableStateOf(false) }
        
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        
        // 控制UI元素是否启用
        val uiEnabled = enabled && !isLoading
        // 用户是否满足注册条件
        val canRegister = uiEnabled && isAgreementAccepted
        
        // 显示用户协议对话框
        if (showAgreementDialog) {
            val agreementScrollState = rememberScrollState()
            val context = LocalContext.current
            val agreementText = remember {
                try {
                    val inputStream = context.assets.open("User_Agreement_And_Disclaimer.md")
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append("\n")
                    }
                    
                    reader.close()
                    inputStream.close()
                    stringBuilder.toString()
                } catch (e: Exception) {
                    Log.e("SupabaseAuthUI", "读取用户协议文件失败: ${e.message}", e)
                    "无法加载用户协议内容，请稍后再试"
                }
            }
            
            AlertDialog(
                onDismissRequest = { showAgreementDialog = false },
                title = { Text("用户协议与免责声明") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            text = "请滚动阅读全部内容",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        ) {
                            Text(
                                text = agreementText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .verticalScroll(agreementScrollState)
                                    .padding(4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isAgreementAccepted,
                                onCheckedChange = { isAgreementAccepted = it }
                            )
                            Text("我已完整阅读并接受本协议的全部条款")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showAgreementDialog = false }
                    ) {
                        Text("确认")
                    }
                }
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPortrait) 24.dp else 64.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(if (isPortrait) 40.dp else 20.dp))
            
            // Logo
            Image(
                painter = painterResource(id = R.drawable.onetv_logo),
                contentDescription = "OneTV Logo",
                modifier = Modifier
                    .size(if (isPortrait) 100.dp else 80.dp)
                    .padding(bottom = 16.dp)
            )
            
            Text(
                text = "注册账号",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // 错误信息
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // 用户名输入
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = uiEnabled,
                singleLine = true
            )
            
            // 电子邮件输入
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("电子邮件") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = uiEnabled,
                singleLine = true
            )
            
            // 密码输入
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = uiEnabled,
                singleLine = true
            )
            
            // 用户协议同意区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAgreementAccepted,
                    onCheckedChange = { isAgreementAccepted = it },
                    enabled = uiEnabled
                )
                TextButton(
                    onClick = { showAgreementDialog = true },
                    enabled = uiEnabled
                ) {
                    Text("点击阅读《ONETV 用户协议与免责声明》")
                }
            }
            
            // 注册按钮
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        try {
                            // 验证输入
                            if (username.isBlank() || email.isBlank() || password.isBlank()) {
                                errorMessage = "请填写所有必填字段"
                                isLoading = false
                                return@launch
                            }
                            
                            if (password.length < 6) {
                                errorMessage = "密码长度至少为6位"
                                isLoading = false
                                return@launch
                            }
                            
                            // 检查用户名是否可用
                            val isUsernameAvailable = repository.checkUsernameAvailable(username)
                            if (!isUsernameAvailable) {
                                errorMessage = "用户名已被使用"
                                isLoading = false
                                return@launch
                            }
                            
                            // 注册用户
                            repository.registerUser(email, password, username)
                            onSignUpSuccess()
                        } catch (e: Exception) {
                            val errorMsg = when {
                                e.message?.contains("User already registered") == true -> "该邮箱已注册"
                                e.message?.contains("network") == true -> "网络连接错误，请检查网络"
                                e.message?.contains("password") == true -> "密码不符合要求，请使用更复杂的密码"
                                else -> e.message ?: "注册失败，请稍后重试"
                            }
                            errorMessage = errorMsg
                            Log.e("SupabaseAuthUI", "注册失败", e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = canRegister
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("注册")
                }
            }
            
            Spacer(modifier = Modifier.height(if (isPortrait) 40.dp else 20.dp))
        }
    }
    
    /**
     * 忘记密码组件
     * @param onResetInitiated 重置密码请求发送成功回调
     */
    @Composable
    fun ForgotPasswordScreen(onResetInitiated: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isPortrait) 24.dp else 64.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(if (isPortrait) 40.dp else 20.dp))
            
            // Logo
            Image(
                painter = painterResource(id = R.drawable.onetv_logo),
                contentDescription = "OneTV Logo",
                modifier = Modifier
                    .size(if (isPortrait) 100.dp else 80.dp)
                    .padding(bottom = 16.dp)
            )
            
            Text(
                text = "忘记密码",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            Text(
                text = "请输入您的电子邮件地址，我们将向您发送密码重置链接",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // 错误信息
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // 电子邮件输入
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("电子邮件") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading,
                singleLine = true
            )
            
            // 发送重置链接按钮
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        try {
                            if (email.isBlank()) {
                                errorMessage = "请输入邮箱地址"
                                isLoading = false
                                return@launch
                            }
                            
                            repository.sendPasswordResetEmail(email)
                            onResetInitiated()
                        } catch (e: Exception) {
                            val errorMsg = when {
                                e.message?.contains("User not found") == true -> "该邮箱未注册"
                                e.message?.contains("network") == true -> "网络连接错误，请检查网络"
                                else -> e.message ?: "发送重置链接失败，请稍后重试"
                            }
                            errorMessage = errorMsg
                            Log.e("SupabaseAuthUI", "发送密码重置邮件失败", e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("发送重置链接")
                }
            }
            
            Spacer(modifier = Modifier.height(if (isPortrait) 40.dp else 20.dp))
        }
    }
    
    /**
     * 初始化Supabase客户端
     * 在应用启动时调用
     */
    fun initialize(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // 获取存储的会话，如果有
                val token = getSessionFromPreferences(context)
                if (!token.isNullOrEmpty()) {
                    Log.d("SupabaseAuthUI", "尝试恢复会话")
                    
                    try {
                        // 使用保存的令牌恢复会话
                        val supabaseClient = SupabaseClient.client
                        
                        // 使用Supabase SDK提供的会话恢复方法
                        // 首先检查是否有存储的会话
                        val currentSession = supabaseClient.auth.currentSessionOrNull()
                        
                        if (currentSession == null) {
                            // 如果没有当前会话，尝试使用保存的令牌恢复
                            // 注意：我们需要使用其他方式恢复会话
                            
                            try {
                                // 尝试使用存储的令牌获取用户信息
                                // 在新版本的 Supabase SDK 中，我们需要自己实现会话恢复
                                // 这里简单记录一下，实际在Repository中实现
                                Log.d("SupabaseAuthUI", "会话令牌存在，但需要重新登录")
                                
                                // 验证恢复的会话是否有效
                                val user = supabaseClient.auth.currentUserOrNull()
                                if (user != null) {
                                    Log.d("SupabaseAuthUI", "会话恢复成功，用户ID: ${user.id}")
                                    
                                    // 刷新会话以确保令牌有效
                                    try {
                                        // 注意：refreshSession方法需要 refreshToken 参数
                                        // 尝试从当前会话中获取 refreshToken
                                        val currentUserSession = supabaseClient.auth.currentSessionOrNull()
                                        if (currentUserSession?.refreshToken != null) {
                                            supabaseClient.auth.refreshSession(currentUserSession.refreshToken)
                                            Log.d("SupabaseAuthUI", "会话刷新成功")
                                        } else {
                                            // 如果没有 refreshToken，则无法刷新
                                            Log.w("SupabaseAuthUI", "无法获取 refreshToken，跳过会话刷新")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SupabaseAuthUI", "刷新会话失败: ${e.message}", e)
                                        // 会话刷新失败，但仍然可以继续使用当前会话
                                    }
                                } else {
                                    Log.e("SupabaseAuthUI", "会话恢复后无法获取用户信息")
                                    // 清除无效的会话
                                    clearSessionFromPreferences(context)
                                }
                            } catch (e: Exception) {
                                Log.e("SupabaseAuthUI", "使用令牌恢复会话失败: ${e.message}", e)
                                clearSessionFromPreferences(context)
                            }
                        } else {
                            Log.d("SupabaseAuthUI", "已存在有效会话，无需恢复")
                            
                            // 确保存储的令牌是最新的
                            currentSession.accessToken?.let { newToken ->
                                if (newToken != token) {
                                    saveSessionToPreferences(context, newToken)
                                    Log.d("SupabaseAuthUI", "已更新存储的会话令牌")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SupabaseAuthUI", "恢复会话失败: ${e.message}", e)
                        // 会话恢复失败，清除存储的令牌
                        clearSessionFromPreferences(context)
                    }
                } else {
                    Log.d("SupabaseAuthUI", "没有存储的会话令牌")
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuthUI", "初始化失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 将会话保存到SharedPreferences
     */
    private fun saveSessionToPreferences(context: Context, token: String) {
        context.getSharedPreferences("user", Context.MODE_PRIVATE).edit()
            .putString("session", token)
            .apply()
    }
    
    /**
     * 从SharedPreferences获取会话
     */
    fun getSessionFromPreferences(context: Context): String? {
        return context.getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("session", null)
    }
    
    /**
     * 清除存储的会话令牌
     */
    private fun clearSessionFromPreferences(context: Context) {
        context.getSharedPreferences("user", Context.MODE_PRIVATE).edit()
            .remove("session")
            .apply()
        Log.d("SupabaseAuthUI", "已清除存储的会话令牌")
    }
} 