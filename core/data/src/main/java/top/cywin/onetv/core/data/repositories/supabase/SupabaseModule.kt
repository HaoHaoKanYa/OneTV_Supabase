package top.cywin.onetv.core.data.repositories.supabase

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Supabase模块依赖注入
 * 提供Supabase客户端实例
 */
val supabaseModule = module {
    // 初始化SupabaseClient
    single { 
        // 获取Android上下文
        val context = androidContext()
        
        // 初始化SupabaseClient
        SupabaseClient.initialize(context)
        
        // 返回SupabaseClient单例
        SupabaseClient 
    }
    
    // 提供Supabase客户端实例
    single {
        // 直接使用SupabaseClient中的client实例
        // 这样可以确保使用动态加载的配置
        get<SupabaseClient>().client
    }
    
    // 提供Auth模块
    single { get<SupabaseClient>().auth }
    
    // 提供Postgrest模块
    single { get<SupabaseClient>().postgrest }
    
    // 提供Storage模块
    single { get<SupabaseClient>().storage }
    
    // 提供Realtime模块
    single { get<SupabaseClient>().realtime }
    
    // 提供Functions模块
    single { get<SupabaseClient>().functions }
} 