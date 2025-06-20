package top.cywin.onetv.tv.supabase

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseRepository
import top.cywin.onetv.core.data.utils.Logger
import top.cywin.onetv.tv.R
import top.cywin.onetv.tv.ui.theme.MyTVTheme

/**
 * Supabase注册活动
 * 使用Supabase Auth UI进行用户注册，支持横竖屏切换
 */
class SupabaseRegisterActivity : ComponentActivity() {
    private val log = Logger.create("SupabaseRegisterActivity")
    private val repository = SupabaseRepository()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化Supabase客户端
        SupabaseClient.initialize(this)
        
        setContent {
            RegisterContent(
                onSignUpSuccess = { handleSignUpSuccess() },
                onBackPressed = { finish() },
                onSocialLogin = { socialLoginType ->
                    handleSocialLogin(socialLoginType)
                }
            )
        }
    }
    
    /**
     * 注册界面内容，支持屏幕旋转
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RegisterContent(
        onSignUpSuccess: () -> Unit, 
        onBackPressed: () -> Unit,
        onSocialLogin: (String) -> Unit
    ) {
        // 获取当前屏幕配置
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val scope = rememberCoroutineScope()
        
        MyTVTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("注册新账号") },
                        navigationIcon = {
                            IconButton(onClick = onBackPressed) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 标准注册表单
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            SupabaseAuthUI.RegisterScreen(
                                onSignUpSuccess = onSignUpSuccess
                            )
                        }
                        
                        // 社交登录区域
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "或者使用以下方式注册",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                            ) {
                                // 谷歌登录按钮
                                OutlinedButton(
                                    onClick = { onSocialLogin("google") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_google),
                                        contentDescription = "Google登录",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Google")
                                }
                                
                                // GitHub登录按钮
                                OutlinedButton(
                                    onClick = { onSocialLogin("github") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                ) {
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
                    }
                }
            }
        }
    }
    
    /**
     * 处理社交登录
     */
    private fun handleSocialLogin(socialLoginType: String) {
        lifecycleScope.launch {
            try {
                when (socialLoginType) {
                    "google" -> {
                        log.i("开始使用Google账号登录")
                        repository.signInWithGoogle()
                    }
                    "github" -> {
                        log.i("开始使用GitHub账号登录")
                        repository.signInWithGithub()
                    }
                }
                
                // 注意：社交登录是异步的，这里不会立即有结果
                // 用户将被重定向到授权页面，然后返回应用
                Toast.makeText(
                    this@SupabaseRegisterActivity,
                    "请在浏览器中完成授权",
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                log.e("社交登录失败: ${e.message}", e)
                Toast.makeText(
                    this@SupabaseRegisterActivity,
                    "登录失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * 处理屏幕配置变更，例如旋转
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 屏幕旋转时不需要特殊处理，Compose会自动重组界面
    }
    
    /**
     * 处理注册成功事件
     */
    private fun handleSignUpSuccess() {
        lifecycleScope.launch {
            try {
                // 显示注册成功提示
                Toast.makeText(
                    this@SupabaseRegisterActivity,
                    "注册成功！请检查您的邮箱以验证账户",
                    Toast.LENGTH_LONG
                ).show()
                
                log.i("用户注册成功，等待邮箱验证")
                
                // 关闭当前活动，返回登录界面
                finish()
            } catch (e: Exception) {
                log.e("注册后处理失败: ${e.message}", e)
                Toast.makeText(
                    this@SupabaseRegisterActivity,
                    "注册成功，但出现了一些问题: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
} 