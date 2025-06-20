package top.cywin.onetv.tv.supabase

/**
 * 应用透明度说明
 * 
 * 在Compose中，使用Color().copy(alpha = value)设置透明度时：
 * - alpha值范围从0.0f到1.0f
 * - 0.0f = 完全透明（0%不透明度）
 * - 1.0f = 完全不透明（100%不透明度）
 * 
 * 透明度计算关系：
 * - alpha = 0.8f 表示80%不透明，即20%透明
 * - alpha = 0.1f 表示10%不透明，即90%透明
 * 
 * 注意：
 * - 在此项目中，alpha=0.8f，意味着背景有20%的透明度
 * - 如果需要更改透明度，调整alpha值即可：
 *   - 更透明 = 降低alpha值
 *   - 更不透明 = 提高alpha值
 */

// 当前项目使用的透明度常量
object TransparencySettings {
    // 背景透明度
    const val BACKGROUND_ALPHA = 0.8f  // 80%不透明/20%透明
    
    // 容器透明度
    const val CONTAINER_ALPHA = 0.8f   // 80%不透明/20%透明
    
    // 输入框透明度
    const val INPUT_ALPHA = 0.8f       // 80%不透明/20%透明
} 