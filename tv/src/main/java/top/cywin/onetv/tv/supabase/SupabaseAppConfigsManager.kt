package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import java.util.concurrent.TimeUnit

/**
 * 应用配置数据类
 */
data class AppConfig(
    val configId: String,
    val configKey: String,
    val configValue: String,
    val configDescription: String? = null,
    val isGlobal: Boolean = false,
    val isUserSpecific: Boolean = false,
    val lastUpdated: String? = null
)

/**
 * Supabase应用配置管理器
 * 负责从Supabase获取、缓存和管理应用配置
 */
object SupabaseAppConfigsManager {
    private const val TAG = "AppConfigsManager"
    private const val API_PATH = "/functions/v1/app-configs"
    
    /**
     * 获取应用配置
     * 先尝试从缓存获取，如果缓存无效或不存在则从服务器获取
     * @param context 应用上下文
     * @param forceRefresh 是否强制从服务器刷新
     * @return 应用配置列表
     */
    suspend fun getAppConfigs(
        context: Context,
        forceRefresh: Boolean = false
    ): List<AppConfig> = withContext(Dispatchers.IO) {
        try {
            // 如果不强制刷新，先尝试从缓存获取
            if (!forceRefresh) {
                val cachedConfigs = SupabaseCacheManager.getCache<List<AppConfig>>(
                    context, 
                    SupabaseCacheKey.APP_CONFIGS
                )
                
                if (cachedConfigs != null) {
                    Log.d(TAG, "从缓存获取应用配置，共 ${cachedConfigs.size} 项")
                    return@withContext cachedConfigs
                }
            }
            
            // 缓存不存在或需要强制刷新，从服务器获取
            Log.d(TAG, "从服务器获取应用配置...")
            
            // 获取会话
            val session = SupabaseSessionManager.getSession(context)
            val apiUrl = "${SupabaseClient.getUrl()}$API_PATH"
            
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            val requestBuilder = Request.Builder()
                .url(apiUrl)
                .addHeader("apikey", SupabaseClient.getKey())
                .addHeader("Content-Type", "application/json")
            
            // 如果有会话，添加授权头
            if (session != null) {
                requestBuilder.addHeader("Authorization", "Bearer $session")
            }
            
            val request = requestBuilder.get().build()
            
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val configs = parseConfigsResponse(responseBody)
                    
                    // 保存到缓存
                    SupabaseCacheManager.saveCache(context, SupabaseCacheKey.APP_CONFIGS, configs)
                    
                    Log.d(TAG, "成功获取并缓存应用配置，共 ${configs.size} 项")
                    return@withContext configs
                } else {
                    Log.e(TAG, "获取应用配置失败: ${response.code} - ${responseBody ?: "无响应内容"}")
                    throw Exception("获取应用配置失败: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取应用配置异常", e)
            // 发生异常时，尝试返回缓存数据，即使前面因为forceRefresh没有使用缓存
            val cachedConfigs = SupabaseCacheManager.getCache<List<AppConfig>>(
                context, 
                SupabaseCacheKey.APP_CONFIGS
            )
            
            return@withContext cachedConfigs ?: emptyList()
        }
    }
    
    /**
     * 获取特定配置项
     * @param context 应用上下文
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @param forceRefresh 是否强制从服务器刷新
     * @return 配置值，如果不存在则返回默认值
     */
    suspend fun getConfigValue(
        context: Context,
        configKey: String,
        defaultValue: String = "",
        forceRefresh: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        try {
            val configs = getAppConfigs(context, forceRefresh)
            val config = configs.find { it.configKey == configKey }
            return@withContext config?.configValue ?: defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "获取配置项 $configKey 失败", e)
            return@withContext defaultValue
        }
    }
    
    /**
     * 保存配置到服务器
     * 注意：此操作需要管理员权限
     * @param context 应用上下文
     * @param configKey 配置键
     * @param configValue 配置值
     * @param configDescription 配置描述
     * @param isGlobal 是否全局配置
     * @param isUserSpecific 是否用户特定配置
     * @return 是否保存成功
     */
    suspend fun saveConfig(
        context: Context,
        configKey: String,
        configValue: String,
        configDescription: String = "",
        isGlobal: Boolean = true,
        isUserSpecific: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取会话
            val session = SupabaseSessionManager.getSession(context)
            if (session == null) {
                Log.w(TAG, "未登录，无法保存配置")
                return@withContext false
            }
            
            val apiUrl = "${SupabaseClient.getUrl()}$API_PATH"
            
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            // 创建请求体
            val jsonBody = JSONObject().apply {
                put("configKey", configKey)
                put("configValue", configValue)
                put("configDescription", configDescription)
                put("isGlobal", isGlobal)
                put("isUserSpecific", isUserSpecific)
            }
            
            val requestBody = okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                jsonBody.toString()
            )
            
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $session")
                .addHeader("apikey", SupabaseClient.getKey())
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    // 保存成功后，清除缓存，确保下次获取最新数据
                    SupabaseCacheManager.clearCache(context, SupabaseCacheKey.APP_CONFIGS)
                    
                    Log.d(TAG, "成功保存配置: $configKey")
                    return@withContext true
                } else {
                    Log.e(TAG, "保存配置失败: ${response.code} - ${responseBody ?: "无响应内容"}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存配置异常", e)
            return@withContext false
        }
    }
    
    /**
     * 清除配置缓存
     * @param context 应用上下文
     */
    suspend fun clearCache(context: Context) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.APP_CONFIGS)
        Log.d(TAG, "应用配置缓存已清除")
    }
    
    /**
     * 解析配置响应
     * @param responseBody 响应体
     * @return 配置列表
     */
    private fun parseConfigsResponse(responseBody: String): List<AppConfig> {
        val configs = mutableListOf<AppConfig>()
        
        try {
            val jsonArray = JSONObject(responseBody).getJSONArray("configs")
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                val config = AppConfig(
                    configId = jsonObject.optString("id", ""),
                    configKey = jsonObject.optString("config_key", ""),
                    configValue = jsonObject.optString("config_value", ""),
                    configDescription = jsonObject.optString("description", null),
                    isGlobal = jsonObject.optBoolean("is_global", false),
                    isUserSpecific = jsonObject.optBoolean("is_user_specific", false),
                    lastUpdated = jsonObject.optString("updated_at", null)
                )
                
                configs.add(config)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析配置响应失败", e)
        }
        
        return configs
    }
} 