package top.cywin.onetv.core.data.repositories.supabase

import android.util.Log
import top.cywin.onetv.core.data.BuildConfig

/**
 * Supabase 相关常量
 */
object SupabaseConstants {
    /**
     * Supabase Edge Functions 端点
     */
    const val EDGE_FUNCTION_APP_CONFIG = "app-configs"
    
    /**
     * 应用ID
     */
    const val APP_ID = "onetv"
    
    // 不再使用硬编码的默认值，完全依赖环境变量和CI配置
    
    /**
     * 获取引导URL
     * 注意：此URL仅用于首次启动时加载真实配置，不用于实际API调用
     */
    internal val bootstrapUrl: String
        get() {
            val url = BuildConfig.BOOTSTRAP_URL
            if (url.isEmpty()) {
                Log.e("SupabaseConstants", "环境变量 BOOTSTRAP_URL 未设置，应用可能无法正常工作")
            } else {
                Log.d("SupabaseConstants", "使用引导URL: $url")
            }
            return url
        }
    
    /**
     * 获取引导密钥
     * 注意：此密钥仅用于首次启动时加载真实配置，不用于实际API调用
     */
    internal val bootstrapKey: String
        get() {
            val key = BuildConfig.BOOTSTRAP_KEY
            if (key.isEmpty()) {
                Log.e("SupabaseConstants", "环境变量 BOOTSTRAP_KEY 未设置，应用可能无法正常工作")
            } else {
                // 不要在日志中打印完整密钥
                Log.d("SupabaseConstants", "使用引导密钥: ${key.take(10)}...")
            }
            return key
        }
}

/**
 * 获取Supabase URL - 已废弃
 * 请使用SupabaseClient中的动态配置
 */
@Deprecated("使用SupabaseClient中的动态配置替代", ReplaceWith("SupabaseClient.getUrl()"))
fun getSupabaseUrl(): String {
    throw IllegalStateException("不应直接调用此方法，请使用SupabaseClient中的动态配置")
}

/**
 * 获取Supabase匿名API密钥 - 已废弃
 * 请使用SupabaseClient中的动态配置
 */
@Deprecated("使用SupabaseClient中的动态配置替代", ReplaceWith("SupabaseClient.getKey()"))
fun getSupabaseAnonKey(): String {
    throw IllegalStateException("不应直接调用此方法，请使用SupabaseClient中的动态配置")
}
