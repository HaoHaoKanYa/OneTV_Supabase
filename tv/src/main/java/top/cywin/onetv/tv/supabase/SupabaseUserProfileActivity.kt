package top.cywin.onetv.tv.supabase

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv

/**
 * Supabase用户个人中心Activity
 * 提供用户资料查看、VIP管理、用户设置等功能
 */
class SupabaseUserProfileActivity : ComponentActivity() {
    private val TAG = "SupabaseUserProfile"
    
    // 应用级别的协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 在UI加载前设置横屏模式
        setLandscapeOrientationForProfile()
        
        // 设置窗口透明
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // 设置窗口背景为透明
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        // 设置窗口半透明
        window.setDimAmount(0.3f) // 设置背景暗度，值越小越通透
        
        super.onCreate(savedInstanceState)
        
        // 检查用户登录状态
        val session = SupabaseSessionManager.getSession(this)
        if (session == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            resetOrientation()
            finish()
            return
        }
        
        // 设置UI界面
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f) // 表面透明度
                ) {
                    // 主界面组件
                    SupabaseUserProfileScreen(
                        onBackPressed = { 
                            resetOrientation()
                            finish() 
                        }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "个人中心界面已恢复")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "个人中心界面已暂停")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时重置屏幕方向
        resetOrientation()
        Log.d(TAG, "个人中心界面已销毁")
    }
} 