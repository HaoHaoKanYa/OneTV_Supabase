/**
 * 缓存键定义
 * 
 * 此文件定义了所有缓存键及其对应的SharedPreferences存储位置。
 * 通过枚举形式组织，确保缓存键的一致性和可维护性。
 * 提供了获取用户相关键和服务相关键的辅助方法，便于分组操作缓存。
 */
package top.cywin.onetv.core.data.repositories.supabase.cache

/**
 * Supabase缓存键枚举类
 * 定义所有缓存键及其对应的SharedPreferences名称
 */
enum class SupabaseCacheKey(val prefsName: String, val keyName: String) {
    // 会话相关
    SESSION("supabase_user", "session"),
    
    // 用户数据相关
    USER_DATA("supabase_user_cache", "cached_user_data"),
    USER_DATA_RAW("supabase_user_cache", "cached_user_data_raw"),
    USER_DATA_JSON("supabase_user_cache", "cached_user_data_json"),
    USER_PROFILE("profile_info_cache", "cached_profile_data"),
    USER_SETTINGS("user_settings_cache", "cached_user_settings"),
    USER_VIP_STATUS("user_vip_cache", "cached_vip_status"),
    
    // 服务信息相关
    SERVICE_INFO("service_info_cache", "cached_service_info"),
    
    // 在线用户相关
    ONLINE_USERS("online_users_cache", "cached_online_users"),
    ONLINE_USERS_RAW("online_users_cache", "cached_online_users_raw"),
    
    // 观看历史相关
    WATCH_HISTORY("watch_history_cache", "cached_watch_history"),
    
    // 应用配置相关
    APP_CONFIGS("app_configs_cache", "cached_app_configs"),
    
    // 频道收藏相关
    CHANNEL_FAVORITES("channel_favorites_cache", "cached_channel_favorites"),
    
    // 时间戳相关
    LAST_LOADED_TIME("supabase_user_cache", "last_loaded_time"),
    USER_PROFILE_LAST_LOADED("profile_info_cache", "last_loaded_time"),
    USER_SETTINGS_LAST_LOADED("user_settings_cache", "last_loaded_time"),
    SERVICE_INFO_LAST_LOADED("service_info_cache", "last_loaded_time"),
    ONLINE_USERS_LAST_LOADED("online_users_cache", "last_loaded_time"),
    WATCH_HISTORY_LAST_LOADED("watch_history_cache", "last_loaded_time"),
    APP_CONFIGS_LAST_LOADED("app_configs_cache", "last_loaded_time"),
    CHANNEL_FAVORITES_LAST_LOADED("channel_favorites_cache", "last_loaded_time");
    
    companion object {
        /**
         * 获取所有用户相关的缓存键
         * @return 用户相关的缓存键列表
         */
        fun getUserRelatedKeys(): List<SupabaseCacheKey> {
            return listOf(
                SESSION,
                USER_DATA,
                USER_PROFILE,
                USER_SETTINGS,
                USER_VIP_STATUS,
                CHANNEL_FAVORITES,
                LAST_LOADED_TIME,
                USER_PROFILE_LAST_LOADED,
                USER_SETTINGS_LAST_LOADED,
                WATCH_HISTORY,
                WATCH_HISTORY_LAST_LOADED,
                CHANNEL_FAVORITES_LAST_LOADED
            )
        }
        
        /**
         * 获取所有服务相关的缓存键
         * @return 服务相关的缓存键列表
         */
        fun getServiceRelatedKeys(): List<SupabaseCacheKey> {
            return listOf(
                SERVICE_INFO,
                ONLINE_USERS,
                APP_CONFIGS,
                SERVICE_INFO_LAST_LOADED,
                ONLINE_USERS_LAST_LOADED,
                APP_CONFIGS_LAST_LOADED
            )
        }
    }
} 