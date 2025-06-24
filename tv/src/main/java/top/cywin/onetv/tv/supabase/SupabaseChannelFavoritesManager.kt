package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheKey
import top.cywin.onetv.core.data.repositories.supabase.cache.SupabaseCacheManager
import java.util.concurrent.TimeUnit

/**
 * 频道收藏项数据类
 */
data class ChannelFavorite(
    val id: String,
    val channelId: String,
    val channelName: String,
    val channelLogo: String? = null,
    val createdAt: String,
    val userId: String
)

/**
 * Supabase频道收藏管理器
 * 负责从Supabase获取、缓存和管理用户的频道收藏
 */
object SupabaseChannelFavoritesManager {
    private const val TAG = "ChannelFavoritesManager"
    private const val API_PATH = "/functions/v1/channel-favorites"
    
    /**
     * 获取用户收藏的频道列表
     * 先尝试从缓存获取，如果缓存无效或不存在则从服务器获取
     * @param context 应用上下文
     * @param forceRefresh 是否强制从服务器刷新
     * @return 收藏频道列表
     */
    suspend fun getFavoriteChannels(
        context: Context,
        forceRefresh: Boolean = false
    ): List<ChannelFavorite> = withContext(Dispatchers.IO) {
        try {
            // 如果不强制刷新，先尝试从缓存获取
            if (!forceRefresh) {
                val cachedFavorites = SupabaseCacheManager.getCache<List<ChannelFavorite>>(
                    context, 
                    SupabaseCacheKey.CHANNEL_FAVORITES
                )
                
                if (cachedFavorites != null) {
                    Log.d(TAG, "从缓存获取收藏频道，共 ${cachedFavorites.size} 项")
                    return@withContext cachedFavorites
                }
            }
            
            // 缓存不存在或需要强制刷新，从服务器获取
            Log.d(TAG, "从服务器获取收藏频道...")
            
            // 获取会话
            val session = SupabaseSessionManager.getSession(context)
            if (session == null) {
                Log.w(TAG, "未登录，无法获取收藏频道")
                return@withContext emptyList()
            }
            
            val apiUrl = "${SupabaseClient.getUrl()}$API_PATH"
            
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $session")
                .addHeader("apikey", SupabaseClient.getKey())
                .addHeader("Content-Type", "application/json")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val favorites = parseFavoritesResponse(responseBody)
                    
                    // 保存到缓存
                    SupabaseCacheManager.saveCache(context, SupabaseCacheKey.CHANNEL_FAVORITES, favorites)
                    
                    Log.d(TAG, "成功获取并缓存收藏频道，共 ${favorites.size} 项")
                    return@withContext favorites
                } else {
                    Log.e(TAG, "获取收藏频道失败: ${response.code} - ${responseBody ?: "无响应内容"}")
                    throw Exception("获取收藏频道失败: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取收藏频道异常", e)
            // 发生异常时，尝试返回缓存数据，即使前面因为forceRefresh没有使用缓存
            val cachedFavorites = SupabaseCacheManager.getCache<List<ChannelFavorite>>(
                context, 
                SupabaseCacheKey.CHANNEL_FAVORITES
            )
            
            return@withContext cachedFavorites ?: emptyList()
        }
    }
    
    /**
     * 添加频道到收藏
     * @param context 应用上下文
     * @param channelId 频道ID
     * @param channelName 频道名称
     * @param channelLogo 频道logo URL
     * @return 是否添加成功
     */
    suspend fun addToFavorites(
        context: Context,
        channelId: String,
        channelName: String,
        channelLogo: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取会话
            val session = SupabaseSessionManager.getSession(context)
            if (session == null) {
                Log.w(TAG, "未登录，无法添加收藏")
                return@withContext false
            }
            
            val apiUrl = "${SupabaseClient.getUrl()}$API_PATH"
            
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            // 创建请求体
            val jsonBody = JSONObject().apply {
                put("channelId", channelId)
                put("channelName", channelName)
                if (channelLogo != null) {
                    put("channelLogo", channelLogo)
                }
            }
            
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            
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
                    // 清除缓存，确保下次获取最新数据
                    SupabaseCacheManager.clearCache(context, SupabaseCacheKey.CHANNEL_FAVORITES)
                    
                    Log.d(TAG, "成功添加收藏: $channelName")
                    return@withContext true
                } else {
                    Log.e(TAG, "添加收藏失败: ${response.code} - ${responseBody ?: "无响应内容"}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加收藏异常", e)
            return@withContext false
        }
    }
    
    /**
     * 从收藏中移除频道
     * @param context 应用上下文
     * @param channelId 频道ID
     * @return 是否移除成功
     */
    suspend fun removeFromFavorites(
        context: Context,
        channelId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取会话
            val session = SupabaseSessionManager.getSession(context)
            if (session == null) {
                Log.w(TAG, "未登录，无法移除收藏")
                return@withContext false
            }
            
            val apiUrl = "${SupabaseClient.getUrl()}$API_PATH/$channelId"
            
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $session")
                .addHeader("apikey", SupabaseClient.getKey())
                .addHeader("Content-Type", "application/json")
                .delete()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (response.isSuccessful) {
                    // 清除缓存，确保下次获取最新数据
                    SupabaseCacheManager.clearCache(context, SupabaseCacheKey.CHANNEL_FAVORITES)
                    
                    Log.d(TAG, "成功移除收藏: $channelId")
                    return@withContext true
                } else {
                    Log.e(TAG, "移除收藏失败: ${response.code} - ${responseBody ?: "无响应内容"}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除收藏异常", e)
            return@withContext false
        }
    }
    
    /**
     * 检查频道是否已收藏
     * @param context 应用上下文
     * @param channelId 频道ID
     * @return 是否已收藏
     */
    suspend fun isChannelFavorited(
        context: Context,
        channelId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val favorites = getFavoriteChannels(context)
            return@withContext favorites.any { it.channelId == channelId }
        } catch (e: Exception) {
            Log.e(TAG, "检查频道收藏状态异常", e)
            return@withContext false
        }
    }
    
    /**
     * 清除收藏缓存
     * @param context 应用上下文
     */
    suspend fun clearCache(context: Context) {
        SupabaseCacheManager.clearCache(context, SupabaseCacheKey.CHANNEL_FAVORITES)
        Log.d(TAG, "频道收藏缓存已清除")
    }
    
    /**
     * 观察收藏频道变化
     * @param context 应用上下文
     * @return 收藏频道列表流
     */
    fun observeFavoriteChannels(context: Context): Flow<List<ChannelFavorite>?> {
        return SupabaseCacheManager.observeCache(context, SupabaseCacheKey.CHANNEL_FAVORITES)
    }
    
    /**
     * 解析收藏响应
     * @param responseBody 响应体
     * @return 收藏频道列表
     */
    private fun parseFavoritesResponse(responseBody: String): List<ChannelFavorite> {
        val favorites = mutableListOf<ChannelFavorite>()
        
        try {
            val jsonArray = JSONObject(responseBody).getJSONArray("favorites")
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                val favorite = ChannelFavorite(
                    id = jsonObject.optString("id", ""),
                    channelId = jsonObject.optString("channel_id", ""),
                    channelName = jsonObject.optString("channel_name", ""),
                    channelLogo = jsonObject.optString("channel_logo", null),
                    createdAt = jsonObject.optString("created_at", ""),
                    userId = jsonObject.optString("user_id", "")
                )
                
                favorites.add(favorite)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析收藏响应失败", e)
        }
        
        return favorites
    }
} 