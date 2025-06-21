package top.cywin.onetv.tv.supabase

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.utils.Logger
import top.cywin.onetv.tv.ui.theme.MyTVTheme

/**
 * Supabase忘记密码活动
 * 使用Supabase Auth UI进行密码重置，支持横竖屏切换
 */
class SupabaseForgotPasswordActivity : ComponentActivity() {
    private val log = Logger.create("ForgotPasswordActivity")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化Supabase客户端
        SupabaseClient.initialize(this)
        
        setContent {
            ForgotPasswordContent(
                onResetInitiated = { handleResetInitiated() },
                onBackPressed = { finish() }
            )
        }
    }
    
    /**
     * 忘记密码界面内容，支持屏幕旋转
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ForgotPasswordContent(onResetInitiated: () -> Unit, onBackPressed: () -> Unit) {
        // 获取当前屏幕配置
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        
        MyTVTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("找回密码") },
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
                    SupabaseAuthUI.ForgotPasswordScreen(
                        onResetInitiated = onResetInitiated
                    )
                }
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
     * 处理密码重置请求已发送事件
     */
    private fun handleResetInitiated() {
        lifecycleScope.launch {
            try {
                // 显示重置链接已发送提示
                Toast.makeText(
                    this@SupabaseForgotPasswordActivity,
                    "密码重置邮件已发送，请检查您的邮箱",
                    Toast.LENGTH_LONG
                ).show()
                
                log.i("密码重置邮件已发送")
                
                // 关闭当前活动，返回登录界面
                finish()
            } catch (e: Exception) {
                log.e("密码重置处理失败: ${e.message}", e)
                Toast.makeText(
                    this@SupabaseForgotPasswordActivity,
                    "密码重置请求已发送，但出现了一些问题: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
} 