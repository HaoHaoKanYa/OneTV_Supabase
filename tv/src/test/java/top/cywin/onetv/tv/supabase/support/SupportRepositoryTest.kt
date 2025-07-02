package top.cywin.onetv.tv.supabase.support

import org.junit.Test
import org.junit.Assert.*

/**
 * SupportRepository的单元测试
 * 测试设备信息解析和用户统计功能
 */
class SupportRepositoryTest {

    @Test
    fun testParseDeviceInfo_withValidDeviceString() {
        // 创建SupportRepository实例
        val repository = SupportRepository()
        
        // 使用反射访问私有方法进行测试
        val method = repository.javaClass.getDeclaredMethod("parseDeviceInfo", String::class.java)
        method.isAccessible = true
        
        // 测试有效的设备信息字符串
        val deviceInfo = "XIAOMI MI TV 4A (Android 9, API 28)"
        val result = method.invoke(repository, deviceInfo) as Triple<String, String, String>
        
        // 验证解析结果
        assertEquals("XIAOMI MI TV 4A", result.first) // 设备型号
        assertNotNull(result.second) // 应用版本（从BuildConfig获取）
        assertEquals("Android 9", result.third) // 系统版本
    }

    @Test
    fun testParseDeviceInfo_withEmptyString() {
        val repository = SupportRepository()
        val method = repository.javaClass.getDeclaredMethod("parseDeviceInfo", String::class.java)
        method.isAccessible = true
        
        // 测试空字符串
        val result = method.invoke(repository, "") as Triple<String, String, String>
        
        // 验证返回默认值
        assertEquals("未知", result.first)
        assertEquals("未知", result.third)
        // 应用版本可能从BuildConfig获取，所以不检查具体值
    }

    @Test
    fun testParseDeviceInfo_withoutAndroidVersion() {
        val repository = SupportRepository()
        val method = repository.javaClass.getDeclaredMethod("parseDeviceInfo", String::class.java)
        method.isAccessible = true
        
        // 测试没有Android版本信息的设备字符串
        val deviceInfo = "SAMSUNG SMART TV"
        val result = method.invoke(repository, deviceInfo) as Triple<String, String, String>
        
        // 验证解析结果
        assertEquals("SAMSUNG SMART TV", result.first)
        assertEquals("未知", result.third) // 没有Android版本信息
    }

    @Test
    fun testParseDeviceInfo_withComplexAndroidVersion() {
        val repository = SupportRepository()
        val method = repository.javaClass.getDeclaredMethod("parseDeviceInfo", String::class.java)
        method.isAccessible = true
        
        // 测试复杂的Android版本信息
        val deviceInfo = "Google Chromecast (Android 10)"
        val result = method.invoke(repository, deviceInfo) as Triple<String, String, String>
        
        // 验证解析结果
        assertEquals("Google Chromecast", result.first)
        assertEquals("Android 10", result.third)
    }

    @Test
    fun testWatchTimeFormatting() {
        // 测试观看时长格式化逻辑
        // 这里可以添加对观看时长格式化的测试
        
        // 测试秒数转换为小时分钟格式
        val testCases = mapOf(
            3661L to "1小时1分钟", // 1小时1分钟1秒
            3600L to "1小时", // 1小时
            60L to "1分钟", // 1分钟
            30L to "30秒", // 30秒
            0L to "0分钟" // 0秒
        )
        
        testCases.forEach { (seconds, expected) ->
            val formatted = formatWatchTime(seconds)
            assertEquals("格式化 ${seconds}秒 应该得到 $expected", expected, formatted)
        }
    }
    
    /**
     * 辅助方法：格式化观看时长
     * 复制SupportRepository中的逻辑用于测试
     */
    private fun formatWatchTime(watchTimeSeconds: Long): String {
        return if (watchTimeSeconds > 0) {
            val hours = watchTimeSeconds / 3600
            val minutes = (watchTimeSeconds % 3600) / 60
            when {
                hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
                hours > 0 -> "${hours}小时"
                minutes > 0 -> "${minutes}分钟"
                else -> "${watchTimeSeconds}秒"
            }
        } else {
            "0分钟"
        }
    }
}
