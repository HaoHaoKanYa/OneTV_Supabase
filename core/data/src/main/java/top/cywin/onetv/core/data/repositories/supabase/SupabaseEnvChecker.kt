package top.cywin.onetv.core.data.repositories.supabase

import android.util.Log
import top.cywin.onetv.core.data.BuildConfig

/**
 * Supabase环境变量检查工具类
 * 用于在应用启动时检查关键环境变量是否已设置
 */
object SupabaseEnvChecker {
    private const val TAG = "SupabaseEnvChecker"
    
    /**
     * 检查所有关键环境变量
     * 将检查结果打印到日志
     */
    fun checkAllEnvVariables() {
        Log.i(TAG, "检查Supabase环境变量...")
        
        // 检查 BOOTSTRAP_URL
        val bootstrapUrl = BuildConfig.BOOTSTRAP_URL
        Log.i(TAG, "BOOTSTRAP_URL 实际值: '$bootstrapUrl'")
        if (bootstrapUrl.isEmpty()) {
            Log.e(TAG, "环境变量 BOOTSTRAP_URL 未设置，应用可能无法正常工作")
            Log.e(TAG, "请在项目构建时提供环境变量，或在GitHub仓库中设置Secrets")
        } else {
            Log.i(TAG, "BOOTSTRAP_URL 已设置: $bootstrapUrl")
        }
        
        // 检查 BOOTSTRAP_KEY
        val bootstrapKey = BuildConfig.BOOTSTRAP_KEY
        Log.i(TAG, "BOOTSTRAP_KEY 实际值: '${bootstrapKey}'")
        if (bootstrapKey.isEmpty()) {
            Log.e(TAG, "环境变量 BOOTSTRAP_KEY 未设置，应用可能无法正常工作")
            Log.e(TAG, "请在项目构建时提供环境变量，或在GitHub仓库中设置Secrets")
        } else {
            Log.i(TAG, "BOOTSTRAP_KEY 已设置: ${bootstrapKey}")
        }
        
        // 检查 BuildConfig 版本信息
        Log.i(TAG, "BuildConfig.DEBUG = ${BuildConfig.DEBUG}")
        Log.i(TAG, "BuildConfig.BUILD_TYPE = ${BuildConfig.BUILD_TYPE}")
        // 版本信息可能在不同模块有所不同，避免直接引用可能不存在的常量
    }
} 