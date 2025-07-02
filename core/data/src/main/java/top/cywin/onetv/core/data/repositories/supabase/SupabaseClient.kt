package top.cywin.onetv.core.data.repositories.supabase

import android.content.Context
import android.content.Intent
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log
import top.cywin.onetv.core.data.BuildConfig

/**
 * Supabase客户端单例类
 * 用于初始化和获取Supabase客户端实例
 */
object SupabaseClient {
    private const val TAG = "SupabaseClient"
    
    // 存储应用上下文
    private var appContext: Context? = null
    
    // 使用SupabaseConstants中的引导配置
    private val bootstrapUrl: String
        get() = SupabaseConstants.bootstrapUrl
    
    private val bootstrapKey: String
        get() = SupabaseConstants.bootstrapKey
    
    // 配置数据表和本地存储配置
    private const val CONFIG_PREF_NAME = "supabase_config"
    
    // 定义移动端APP重定向URL
    const val REDIRECT_URL = "io.onetv.app://auth-callback"
    
    // 存储实际使用的URL和密钥
    private var supabaseUrl: String = ""
    private var supabaseKey: String = ""
    
    // 是否已初始化
    private var isInitialized = false
    
    /**
     * 获取应用上下文
     * @return 应用上下文，如果未初始化则返回null
     */
    fun getAppContext(): Context? {
        return appContext
    }
    
    // 引导客户端 - 用于访问配置表和Edge Functions
    private val bootstrapClient by lazy {
        val url = BuildConfig.BOOTSTRAP_URL
        val key = BuildConfig.BOOTSTRAP_KEY
        Log.d(TAG, "创建引导客户端: URL=${url}")
        Log.d(TAG, "使用引导密钥(前10位): ${key.take(10)}...")
        
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Postgrest)
            install(Functions)
        }
    }
    
    // 懒加载方式创建Supabase客户端实例
    val client by lazy {
        if (!isInitialized) {
            Log.w(TAG, "警告: Supabase客户端在初始化前被访问")
        }
        
        val url = getUrl()
        val key = getKey()
        
        Log.d(TAG, "创建Supabase客户端: URL=${url}")
        
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            // 安装Auth模块
            install(Auth) {
                // 配置移动端重定向URL
                scheme = "io.onetv.app" // 深链接方案
                host = "auth-callback" // 深链接主机
                autoSaveToStorage = true // 自动保存会话到存储
                alwaysAutoRefresh = true // 自动刷新token
            }
            
            // 安装Postgrest模块用于数据库访问
            install(Postgrest)
            
            // 安装Storage模块用于存储文件
            install(Storage)
            
            // 安装Realtime模块用于实时数据
            install(Realtime)
            
            // 安装Functions模块用于Edge Functions
            install(Functions) {
                // 此处不使用onRequest和onResponse，避免编译错误
            }
        }
    }
    
    init {
        Log.d(TAG, "预初始化客户端实例...")
        Log.w(TAG, "警告: Supabase客户端在初始化前被访问")
        Log.d(TAG, "创建Supabase客户端: URL=${getUrl()}")
    }
    
    /**
     * 初始化Supabase，在应用启动时调用
     * 优先尝试从SharedPreferences读取缓存配置
     * 然后异步从Edge Functions加载最新配置
     */
    fun initialize(context: Context) {
        Log.i(TAG, "开始初始化 Supabase 客户端")
        
        // 保存应用上下文
        appContext = context.applicationContext
        
        // 先尝试从本地缓存加载配置
        try {
            val prefs = context.getSharedPreferences(CONFIG_PREF_NAME, Context.MODE_PRIVATE)
            val cachedUrl = prefs.getString("SUPABASE_URL", null)
            val cachedKey = prefs.getString("SUPABASE_ANON_KEY", null)
            
            if (!cachedUrl.isNullOrEmpty() && !cachedKey.isNullOrEmpty()) {
                // 使用缓存的配置初始化
                supabaseUrl = cachedUrl
                supabaseKey = cachedKey
                Log.i(TAG, "已从本地缓存读取Supabase配置: URL=${maskUrl(supabaseUrl)}")
                isInitialized = true
            } else {
                Log.d(TAG, "本地缓存中没有配置，将使用引导配置: URL=${maskUrl(bootstrapUrl)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取缓存配置失败，将使用引导配置", e)
        }
        
        // 无论是否从缓存读取成功，都尝试从远程加载最新配置
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 从Edge Functions加载最新配置
                loadConfigFromEdgeFunction(context)
                Log.i(TAG, "Supabase配置已更新")
            } catch (e: Exception) {
                Log.e(TAG, "从Edge Functions加载配置失败", e)
                // 如果从Edge Functions加载失败，尝试从数据库表加载
                try {
                    loadConfigFromDatabase(context)
                } catch (e2: Exception) {
                    Log.e(TAG, "从数据库加载配置也失败", e2)
                }
            }
        }
        
        // 强制初始化客户端实例
        Log.d(TAG, "预初始化客户端实例...")
        val clientInstance = client
        Log.i(TAG, "Supabase客户端初始化完成")
    }
    
    /**
     * 从Edge Functions加载配置
     */
    private suspend fun loadConfigFromEdgeFunction(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从Edge Functions加载配置...")
            
            // 调用Edge Functions获取配置
            val response = bootstrapClient.functions.invoke(
                SupabaseConstants.EDGE_FUNCTION_APP_CONFIG,
                mapOf("app_id" to SupabaseConstants.APP_ID)
            )
            
            // 解析响应
            val config = Json.decodeFromString<AppConfig>(response.toString())
            
            if (config != null) {
                Log.d(TAG, "成功从Edge Functions加载配置，项目: ${config.projectName}")
                
                // 更新URL和密钥
                if (!config.projectUrl.isNullOrEmpty() && !config.apiKey.isNullOrEmpty()) {
                    // 保存到SharedPreferences
                    val prefs = context.getSharedPreferences(CONFIG_PREF_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("SUPABASE_URL", config.projectUrl)
                        .putString("SUPABASE_ANON_KEY", config.apiKey)
                        .apply()
                    
                    // 如果配置已更新，更新内存中的配置
                    if (supabaseUrl != config.projectUrl || supabaseKey != config.apiKey) {
                        supabaseUrl = config.projectUrl
                        supabaseKey = config.apiKey
                        
                        Log.i(TAG, "Supabase配置已更新: ${maskUrl(config.projectUrl)}")
                    }
                    
                    // 标记为已初始化
                    isInitialized = true
                } else {
                    Log.w(TAG, "从Edge Functions加载的配置无效")
                }
            } else {
                Log.w(TAG, "未从Edge Functions获取到有效配置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从Edge Functions加载配置失败", e)
            throw e
        }
    }
    
    /**
     * 从数据库表加载配置（备用方法）
     */
    private suspend fun loadConfigFromDatabase(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始从数据库表加载配置...")
            
            // 查询配置表中活跃的ONETV配置
            val config = bootstrapClient.postgrest["app_configs"]
                .select {
                    filter {
                        eq("app_id", SupabaseConstants.APP_ID)
                        eq("is_active", true)
                    }
                }
                .decodeSingleOrNull<AppConfig>()
            
            if (config != null) {
                Log.d(TAG, "成功从数据库加载配置，项目: ${config.projectName}")
                
                // 更新URL和密钥
                if (!config.projectUrl.isNullOrEmpty() && !config.apiKey.isNullOrEmpty()) {
                    // 保存到SharedPreferences
                    val prefs = context.getSharedPreferences(CONFIG_PREF_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("SUPABASE_URL", config.projectUrl)
                        .putString("SUPABASE_ANON_KEY", config.apiKey)
                        .apply()
                    
                    // 如果配置已更新，更新内存中的配置
                    if (supabaseUrl != config.projectUrl || supabaseKey != config.apiKey) {
                        supabaseUrl = config.projectUrl
                        supabaseKey = config.apiKey
                        
                        Log.i(TAG, "Supabase配置已更新: ${maskUrl(config.projectUrl)}")
                    }
                    
                    // 标记为已初始化
                    isInitialized = true
                } else {
                    Log.w(TAG, "从数据库加载的配置无效")
                }
            } else {
                Log.w(TAG, "未在数据库表中找到活跃的ONETV配置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从数据库加载配置失败", e)
            throw e
        }
    }
    
    /**
     * 强制刷新配置
     * 从Edge Functions中重新加载最新配置
     */
    suspend fun refreshConfig(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            loadConfigFromEdgeFunction(context)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "从Edge Functions刷新配置失败", e)
            try {
                loadConfigFromDatabase(context)
                return@withContext true
            } catch (e2: Exception) {
                Log.e(TAG, "从数据库刷新配置也失败", e2)
                return@withContext false
            }
        }
    }
    
    /**
     * 设置Supabase配置
     * 用于在运行时更新配置
     */
    fun setConfig(context: Context, url: String, key: String) {
        supabaseUrl = url
        supabaseKey = key
        
        // 保存到SharedPreferences以便下次启动时使用
        try {
            val prefs = context.getSharedPreferences(CONFIG_PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("SUPABASE_URL", url)
                .putString("SUPABASE_ANON_KEY", key)
                .apply()
            Log.i(TAG, "Supabase配置已更新并保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存Supabase配置失败", e)
        }
        
        // 标记为已初始化
        isInitialized = true
    }
    
    /**
     * 获取当前配置的URL
     * 优先使用动态配置，否则使用引导URL
     */
    fun getUrl(): String {
        val url = if (supabaseUrl.isNotEmpty()) supabaseUrl else BuildConfig.BOOTSTRAP_URL
        Log.i(TAG, "使用的Supabase URL: '$url'")
        return url
    }
    
    /**
     * 获取当前配置的密钥
     * 优先使用动态配置，否则使用引导密钥
     */
    fun getKey(): String {
        val key = if (supabaseKey.isNotEmpty()) supabaseKey else BuildConfig.BOOTSTRAP_KEY
        // 不要在日志中打印完整密钥
        Log.i(TAG, "使用的Supabase KEY (前10位): '${key.take(10)}...'")
        return key
    }
    
    /**
     * 获取App配置方法
     * 直接从配置表中获取App配置信息
     */
    suspend fun getAppConfig(appId: String): AppConfig? {
        return try {
            client.postgrest["app_configs"]
                .select {
                    filter {
                        eq("app_id", appId)
                        eq("is_active", true)
                    }
                }
                .decodeSingleOrNull<AppConfig>()
        } catch (e: Exception) {
            Log.e(TAG, "获取App配置失败", e)
            null
        }
    }
    
    /**
     * 处理Android深链接
     */
    fun handleDeeplinks(intent: Intent) {
        // 在Supabase 3.1.4中，处理深链接的方式有变化
        // 现在由DefaultRedirectActivity自动处理，不需要手动调用
    }
    
    /**
     * 获取Auth模块实例
     */
    val auth get() = client.auth
    
    /**
     * 获取Postgrest模块实例
     */
    val postgrest get() = client.postgrest
    
    /**
     * 获取Storage模块实例
     */
    val storage get() = client.storage
    
    /**
     * 获取Realtime模块实例
     */
    val realtime get() = client.realtime
    
    /**
     * 获取Functions模块实例
     */
    val functions get() = client.functions
    
    /**
     * 对URL进行部分遮蔽处理，用于日志输出
     */
    private fun maskUrl(url: String): String {
        return try {
            // 只保留域名的前几个字符，其余用*替代
            val parts = url.split("://")
            if (parts.size < 2) return "***"
            
            val protocol = parts[0]
            val domain = parts[1].split(".").first()
            val maskedDomain = if (domain.length <= 3) {
                domain
            } else {
                domain.take(3) + "*".repeat(domain.length - 3)
            }
            
            "$protocol://$maskedDomain.***"
        } catch (e: Exception) {
            "***"
        }
    }
}

/**
 * App配置数据模型，对应Supabase表结构
 */
@Serializable
data class AppConfig(
    val id: Int,
    @SerialName("app_id") val appId: String,
    @SerialName("project_name") val projectName: String,
    @SerialName("project_url") val projectUrl: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("jwt_secret") val jwtSecret: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)