package top.cywin.onetv.tv.test

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey

/**
 * VIP权限测试Activity
 * 用于验证VIP权限修复的效果
 */
class VipPermissionTestActivity : ComponentActivity() {
    
    private val TAG = "VipPermissionTest"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VipPermissionTestScreen()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun VipPermissionTestScreen() {
        var testResults by remember { mutableStateOf(listOf<TestResult>()) }
        var isRunning by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "VIP权限修复测试",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        testResults = runVipPermissionTests()
                        isRunning = false
                    }
                },
                enabled = !isRunning
            ) {
                Text(if (isRunning) "测试中..." else "开始测试")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (testResults.isNotEmpty()) {
                LazyColumn {
                    items(testResults) { result ->
                        TestResultCard(result)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    
    @Composable
    fun TestResultCard(result: TestResult) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (result.passed) Color(0xFF4CAF50).copy(alpha = 0.2f) 
                                else Color(0xFFF44336).copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = result.testName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (result.passed) "✅ 通过" else "❌ 失败",
                        color = if (result.passed) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
                
                if (result.details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.details,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
    
    private suspend fun runVipPermissionTests(): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        // 测试1: 单例模式验证
        results.add(testSingletonPattern())
        
        // 测试2: SessionToken设置验证
        results.add(testSessionTokenSetting())
        
        // 测试3: 缓存管理验证
        results.add(testCacheManagement())
        
        // 测试4: IPTV频道API调用验证
        results.add(testIptvChannelApi())
        
        return results
    }
    
    private fun testSingletonPattern(): TestResult {
        return try {
            val instance1 = SupabaseApiClient.getInstance()
            val instance2 = SupabaseApiClient.getInstance()
            
            val passed = instance1 === instance2
            TestResult(
                testName = "单例模式验证",
                passed = passed,
                details = if (passed) "SupabaseApiClient正确实现单例模式" 
                         else "SupabaseApiClient单例模式实现有问题"
            )
        } catch (e: Exception) {
            TestResult(
                testName = "单例模式验证",
                passed = false,
                details = "测试异常: ${e.message}"
            )
        }
    }
    
    private suspend fun testSessionTokenSetting(): TestResult {
        return try {
            val apiClient = SupabaseApiClient.getInstance()
            val testToken = "test_token_${System.currentTimeMillis()}"
            
            // 检查初始状态
            val initialStatus = apiClient.hasSessionToken()
            
            // 设置token
            apiClient.setSessionToken(testToken)
            
            // 检查设置后状态
            val afterStatus = apiClient.hasSessionToken()
            val statusInfo = apiClient.getSessionTokenStatus()
            
            val passed = afterStatus && statusInfo.contains("已设置")
            TestResult(
                testName = "SessionToken设置验证",
                passed = passed,
                details = "初始状态: $initialStatus, 设置后: $afterStatus, 状态: $statusInfo"
            )
        } catch (e: Exception) {
            TestResult(
                testName = "SessionToken设置验证",
                passed = false,
                details = "测试异常: ${e.message}"
            )
        }
    }
    
    private fun testCacheManagement(): TestResult {
        return try {
            // 测试缓存刷新功能
            SupabaseCacheManager.refreshUserPermissionCache(this)
            
            TestResult(
                testName = "缓存管理验证",
                passed = true,
                details = "用户权限缓存刷新功能正常"
            )
        } catch (e: Exception) {
            TestResult(
                testName = "缓存管理验证",
                passed = false,
                details = "测试异常: ${e.message}"
            )
        }
    }
    
    private suspend fun testIptvChannelApi(): TestResult {
        return try {
            val apiClient = SupabaseApiClient.getInstance()
            
            // 设置测试token
            val session = SupabaseCacheManager.getCache<String>(this, SupabaseCacheKey.SESSION)
            if (!session.isNullOrEmpty()) {
                apiClient.setSessionToken(session)
            }
            
            // 测试API调用（这里只测试调用是否成功，不验证具体内容）
            val result = apiClient.getIptvChannels("yidong")
            
            val passed = result.isNotEmpty()
            TestResult(
                testName = "IPTV频道API调用验证",
                passed = passed,
                details = if (passed) "API调用成功，返回数据长度: ${result.length}" 
                         else "API调用失败或返回空数据"
            )
        } catch (e: Exception) {
            TestResult(
                testName = "IPTV频道API调用验证",
                passed = false,
                details = "API调用异常: ${e.message}"
            )
        }
    }
    
    data class TestResult(
        val testName: String,
        val passed: Boolean,
        val details: String
    )
}
