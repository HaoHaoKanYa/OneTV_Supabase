package top.cywin.onetv.tv.supabase

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import top.cywin.onetv.core.data.repositories.supabase.SupabaseConstants
import top.cywin.onetv.core.data.repositories.supabase.SupabaseSessionManager
import top.cywin.onetv.core.data.repositories.supabase.SupabaseUserDataIptv
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseApiClient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * 用户设置界面组件
 */
@Composable
fun SupabaseUserSettings(
    userData: SupabaseUserDataIptv?,
    isLoading: Boolean,
    context: Context
) {
    val scope = rememberCoroutineScope()
    
    // 设置状态
    var userProfile by remember { mutableStateOf(UserProfile()) }
    
    // 加载状态
    var isSettingsLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    
    // 从服务器加载设置
    LaunchedEffect(userData) {
        if (userData != null && !isLoading) {
            try {
                isSettingsLoading = true
                
                // 先尝试从缓存加载
                val cachedSettings = SupabaseUserSettingsSessionManager.getCachedUserSettings(context)
                
                // 检查缓存是否有效
                if (cachedSettings != null && SupabaseUserSettingsSessionManager.isCacheValid(context)) {
                    // 如果缓存有效，直接使用缓存数据
                    userProfile = UserProfile(
                        gender = cachedSettings.gender,
                        birthDate = cachedSettings.birthDate,
                        region = cachedSettings.region,
                        languagePreference = cachedSettings.languagePreference,
                        timezone = cachedSettings.timezone,
                        displayName = cachedSettings.displayName,
                        avatarUrl = cachedSettings.avatarUrl,
                        bio = cachedSettings.bio
                    )
                    
                    Log.d("UserSettings", "使用缓存的用户设置 | 显示名称: ${cachedSettings.displayName ?: "未设置"} | 缓存有效期: 30天")
                    isSettingsLoading = false
                } else {
                    // 缓存无效或不存在，从服务器加载
                    withContext(Dispatchers.IO) {
                        try {
                            val settings = loadUserSettings(context)
                            
                            // 在主线程中更新UI
                            withContext(Dispatchers.Main) {
                                // 加载个人资料
                                userProfile = UserProfile(
                                    gender = settings.gender,
                                    birthDate = settings.birthDate,
                                    region = settings.region,
                                    languagePreference = settings.languagePreference,
                                    timezone = settings.timezone,
                                    displayName = settings.displayName,
                                    avatarUrl = settings.avatarUrl,
                                    bio = settings.bio
                                )
                                
                                // 保存到缓存
                                val userSettings = SupabaseUserSettingsSessionManager.UserSettings(
                                    userId = userData.userid,
                                    theme = settings.theme,
                                    playerSettings = JSONObject(settings.playerSettings.toString()),
                                    notificationEnabled = settings.notificationEnabled,
                                    gender = settings.gender,
                                    birthDate = settings.birthDate,
                                    region = settings.region,
                                    languagePreference = settings.languagePreference,
                                    timezone = settings.timezone,
                                    displayName = settings.displayName,
                                    avatarUrl = settings.avatarUrl,
                                    bio = settings.bio
                                )
                                
                                SupabaseUserSettingsSessionManager.saveUserSettings(context, userSettings)
                                Log.d("UserSettings", "从服务器获取新的用户设置 | 显示名称: ${settings.displayName ?: "未设置"} | 原因: ${
                                    if (cachedSettings == null) "无缓存" else "缓存已失效"
                                }")
                                
                                isSettingsLoading = false
                            }
                        } catch (e: Exception) {
                            Log.e("UserSettings", "从服务器获取用户设置失败", e)
                            
                            // 如果从服务器获取失败但有缓存，仍然使用缓存
                            if (cachedSettings != null) {
                                withContext(Dispatchers.Main) {
                                    userProfile = UserProfile(
                                        gender = cachedSettings.gender,
                                        birthDate = cachedSettings.birthDate,
                                        region = cachedSettings.region,
                                        languagePreference = cachedSettings.languagePreference,
                                        timezone = cachedSettings.timezone,
                                        displayName = cachedSettings.displayName,
                                        avatarUrl = cachedSettings.avatarUrl,
                                        bio = cachedSettings.bio
                                    )
                                    
                                    Log.d("UserSettings", "服务器获取失败，使用过期缓存数据 | 显示名称: ${cachedSettings.displayName ?: "未设置"}")
                                    isSettingsLoading = false
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "加载设置失败: ${e.message}" to false
                                    isSettingsLoading = false
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UserSettings", "加载设置异常", e)
                statusMessage = "加载设置失败: ${e.message}" to false
                isSettingsLoading = false
            }
        } else {
            isSettingsLoading = false
        }
    }
    
    // 保存设置
    fun saveSettings() {
        scope.launch {
            try {
                isSaving = true
                
                withContext(Dispatchers.IO) {
                    // 创建设置JSON
                    val settings = buildJsonObject {
                        // 保存个人资料字段
                        userProfile.gender?.let { put("gender", it) }
                        userProfile.birthDate?.let { put("birth_date", it) }
                        userProfile.region?.let { put("region", it) }
                        put("language_preference", userProfile.languagePreference)
                        put("timezone", userProfile.timezone)
                        userProfile.displayName?.let { put("display_name", it) }
                        userProfile.avatarUrl?.let { put("avatar_url", it) }
                        userProfile.bio?.let { put("bio", it) }
                    }
                    
                    // 使用SupabaseApiClient保存设置
                    val apiClient = SupabaseApiClient.getInstance()
                    val response = apiClient.updateUserSettings(settings)
                    
                    // 处理响应
                    withContext(Dispatchers.Main) {
                        val success = response.jsonObject["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        if (success) {
                            // 更新缓存
                            val cachedSettings = SupabaseUserSettingsSessionManager.getCachedUserSettings(context)
                            if (cachedSettings != null) {
                                val updatedSettings = cachedSettings.copy(
                                    gender = userProfile.gender,
                                    birthDate = userProfile.birthDate,
                                    region = userProfile.region,
                                    languagePreference = userProfile.languagePreference,
                                    timezone = userProfile.timezone,
                                    displayName = userProfile.displayName,
                                    avatarUrl = userProfile.avatarUrl,
                                    bio = userProfile.bio
                                )
                                SupabaseUserSettingsSessionManager.saveUserSettings(context, updatedSettings)
                                Log.d("UserSettings", "设置已更新并保存到缓存")
                            }
                            
                            Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                            statusMessage = "设置已保存" to true
                        } else {
                            val errorMsg = response.jsonObject["error"]?.jsonPrimitive?.content ?: "未知错误"
                            Log.e("UserSettings", "保存设置失败: $errorMsg")
                            statusMessage = "保存设置失败: $errorMsg" to false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UserSettings", "保存设置异常", e)
                statusMessage = "保存设置出错: ${e.message}" to false
            } finally {
                isSaving = false
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    statusMessage = null
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading || isSettingsLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFFFFD700)
            )
            Text(
                text = "加载设置中...",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            // 状态消息
            statusMessage?.let { (message, isSuccess) ->
                Text(
                    text = message,
                    color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
            
            // 缓存状态逻辑保留，但不再显示指示器
            var lastLoadedTime by remember { mutableStateOf(0L) }
            
            LaunchedEffect(Unit) {
                lastLoadedTime = SupabaseUserSettingsSessionManager.getLastLoadedTime(context)
            }
            
            val currentTime = if (lastLoadedTime > 0) System.currentTimeMillis() else 0
            val timeDiff = currentTime - lastLoadedTime
            
            // 个人资料设置
            SettingsSection(title = "个人资料") {
                // 显示名称
                EnhancedProfileTextField(
                    label = "显示名称",
                    value = userProfile.displayName ?: "",
                    onValueChange = { userProfile = userProfile.copy(displayName = it) }
                )
                
                // 性别选择
                EnhancedProfileDropdown(
                    label = "性别",
                    value = userProfile.gender ?: "",
                    options = listOf("男", "女", "其他", "不愿透露"),
                    onValueChange = { userProfile = userProfile.copy(gender = it) }
                )
                
                // 出生日期 - 使用改进的日期选择器
                EnhancedDatePicker(
                    label = "出生日期",
                    value = userProfile.birthDate ?: "",
                    onValueChange = { userProfile = userProfile.copy(birthDate = it) }
                )
                
                // 地区 - 使用三级级联选择
                RegionPicker(
                    label = "地区",
                    value = userProfile.region ?: "",
                    onValueChange = { userProfile = userProfile.copy(region = it) }
                )
                
                // 语言偏好
                EnhancedProfileDropdown(
                    label = "语言偏好",
                    value = userProfile.languagePreference,
                    options = listOf("简体中文", "繁体中文", "English", "日本語", "한국어"),
                    optionValues = listOf("zh-CN", "zh-TW", "en-US", "ja-JP", "ko-KR"),
                    onValueChange = { userProfile = userProfile.copy(languagePreference = it) }
                )
                
                // 时区设置
                EnhancedProfileDropdown(
                    label = "时区",
                    value = userProfile.timezone,
                    options = listOf("中国标准时间", "日本标准时间", "美国东部时间", "英国标准时间"),
                    optionValues = listOf("Asia/Shanghai", "Asia/Tokyo", "America/New_York", "Europe/London"),
                    onValueChange = { userProfile = userProfile.copy(timezone = it) }
                )
                
                // 个人简介
                EnhancedProfileTextField(
                    label = "个人简介",
                    value = userProfile.bio ?: "",
                    onValueChange = { userProfile = userProfile.copy(bio = it) },
                    maxLines = 3
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 刷新按钮
                Button(
                    onClick = {
                        // 手动使缓存失效并刷新数据
                        scope.launch {
                            SupabaseUserSettingsSessionManager.invalidateCache(context)
                        }
                        isSettingsLoading = true
                        
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val settings = loadUserSettings(context)
                                    
                                    withContext(Dispatchers.Main) {
                                        userProfile = UserProfile(
                                            gender = settings.gender,
                                            birthDate = settings.birthDate,
                                            region = settings.region,
                                            languagePreference = settings.languagePreference,
                                            timezone = settings.timezone,
                                            displayName = settings.displayName,
                                            avatarUrl = settings.avatarUrl,
                                            bio = settings.bio
                                        )
                                        
                                        // 保存到缓存
                                        val userSettings = SupabaseUserSettingsSessionManager.UserSettings(
                                            userId = userData?.userid ?: "",
                                            theme = settings.theme,
                                            playerSettings = JSONObject(settings.playerSettings.toString()),
                                            notificationEnabled = settings.notificationEnabled,
                                            gender = settings.gender,
                                            birthDate = settings.birthDate,
                                            region = settings.region,
                                            languagePreference = settings.languagePreference,
                                            timezone = settings.timezone,
                                            displayName = settings.displayName,
                                            avatarUrl = settings.avatarUrl,
                                            bio = settings.bio
                                        )
                                        
                                        SupabaseUserSettingsSessionManager.saveUserSettings(context, userSettings)
                                        Log.d("UserSettings", "手动刷新：从服务器获取新的用户设置")
                                        
                                        isSettingsLoading = false
                                        statusMessage = "设置已刷新" to true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("UserSettings", "刷新设置失败", e)
                                isSettingsLoading = false
                                statusMessage = "刷新设置失败: ${e.message}" to false
                            }
                        }
                    },
                    enabled = !isSettingsLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3498DB),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(end = 8.dp)
                ) {
                    Text("刷新设置", fontWeight = FontWeight.Bold)
                }
                
                // 保存按钮
                Button(
                    onClick = { saveSettings() },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(start = 8.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("保存设置", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 设置分类节组件
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF2C3E50).copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 用于显示分类标题
            // 根据需求删除了多余标题，所以只在分类标题处显示
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            content()
        }
    }
}

/**
 * 加载用户设置
 */
suspend fun loadUserSettings(
    context: Context
): UserSettings {
    return try {
        // 使用SupabaseApiClient获取用户设置
        val apiClient = SupabaseApiClient.getInstance()
        val response = apiClient.getUserSettings()
        
        // 从响应中获取设置
        val settingsData = response.jsonObject["settings"]?.jsonObject
        if (settingsData != null) {
            // 获取播放器设置
            val playerSettingsJson = settingsData["player_settings"]?.jsonObject
            val playerSettings = if (playerSettingsJson != null) {
                PlayerSettings(
                    autoPlay = playerSettingsJson["autoPlay"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                    highQuality = playerSettingsJson["highQuality"]?.jsonPrimitive?.content?.toBoolean() ?: true
                )
            } else {
                PlayerSettings(autoPlay = true, highQuality = true)
            }
            
            // 创建用户设置对象
            UserSettings(
                theme = settingsData["theme"]?.jsonPrimitive?.content ?: "dark",
                notificationEnabled = settingsData["notification_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                playerSettings = playerSettings,
                
                // 个人资料字段
                gender = settingsData["gender"]?.jsonPrimitive?.content,
                birthDate = settingsData["birth_date"]?.jsonPrimitive?.content,
                region = settingsData["region"]?.jsonPrimitive?.content,
                languagePreference = settingsData["language_preference"]?.jsonPrimitive?.content ?: "zh-CN",
                timezone = settingsData["timezone"]?.jsonPrimitive?.content ?: "Asia/Shanghai",
                displayName = settingsData["display_name"]?.jsonPrimitive?.content,
                avatarUrl = settingsData["avatar_url"]?.jsonPrimitive?.content,
                bio = settingsData["bio"]?.jsonPrimitive?.content
            )
        } else {
            // 返回默认设置
            UserSettings(
                theme = "dark",
                notificationEnabled = true,
                playerSettings = PlayerSettings(
                    autoPlay = true,
                    highQuality = true
                ),
                languagePreference = "zh-CN",
                timezone = "Asia/Shanghai"
            )
        }
    } catch (e: Exception) {
        Log.e("UserSettings", "加载设置异常", e)
        // 返回默认设置
        UserSettings(
            theme = "dark",
            notificationEnabled = true,
            playerSettings = PlayerSettings(
                autoPlay = true,
                highQuality = true
            ),
            languagePreference = "zh-CN",
            timezone = "Asia/Shanghai"
        )
    }
}

/**
 * 用户设置数据类
 */
data class UserSettings(
    val theme: String,
    val notificationEnabled: Boolean,
    val playerSettings: PlayerSettings,
    
    // 新增用户个人资料字段
    val gender: String? = null,
    val birthDate: String? = null,
    val region: String? = null,
    val languagePreference: String = "zh-CN",
    val timezone: String = "Asia/Shanghai",
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null
)

/**
 * 播放器设置数据类
 */
data class PlayerSettings(
    val autoPlay: Boolean,
    val highQuality: Boolean
) {
    /**
     * 将PlayerSettings转换为JSON字符串
     */
    override fun toString(): String {
        return "{\"autoPlay\":$autoPlay,\"highQuality\":$highQuality}"
    }
}

/**
 * 用户个人资料数据类
 */
data class UserProfile(
    var gender: String? = null,
    var birthDate: String? = null,
    var region: String? = null,
    var languagePreference: String = "zh-CN",
    var timezone: String = "Asia/Shanghai",
    var displayName: String? = null,
    var avatarUrl: String? = null,
    var bio: String? = null
)

/**
 * 设置开关项组件
 */
@Composable
fun SettingsToggleItem(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFFFFD700),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

/**
 * 增强的个人资料文本输入组件
 */
@Composable
fun EnhancedProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxLines: Int = 1
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF1E2A3A),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFD700).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            androidx.compose.material3.TextField(
                value = value,
                onValueChange = onValueChange,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 增强的个人资料下拉选择组件
 */
@Composable
fun EnhancedProfileDropdown(
    label: String,
    value: String,
    options: List<String>,
    optionValues: List<String> = emptyList(),
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val mappedOptions = if (optionValues.isNotEmpty() && optionValues.size == options.size) {
        options.zip(optionValues).toMap()
    } else {
        options.associateWith { it }
    }
    
    // 根据值查找显示文本
    val displayText = if (optionValues.isNotEmpty()) {
        options.getOrNull(optionValues.indexOf(value)) ?: value
    } else {
        value
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF1E2A3A),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFD700).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { expanded = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (displayText.isNotEmpty()) displayText else "请选择",
                    color = if (displayText.isNotEmpty()) Color.White else Color.Gray
                )
                
                // 下拉图标
                Text(
                    text = "▼",
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp
                )
            }
            
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF1A1A1A))
                    .fillMaxWidth(0.9f)
            ) {
                options.forEach { option ->
                    val optionValue = mappedOptions[option] ?: option
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onValueChange(optionValue)
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (optionValue == value) Color(0xFF2C3E50) else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

/**
 * 地区三级选择器
 */
@Composable
fun RegionPicker(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showRegionPicker by remember { mutableStateOf(false) }
    var selectedProvince by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("") }
    var selectedDistrict by remember { mutableStateOf("") }
    
    // 解析初始地区
    LaunchedEffect(value) {
        if (value.isNotEmpty()) {
            val parts = value.split(" ")
            if (parts.size >= 1) selectedProvince = parts[0]
            if (parts.size >= 2) selectedCity = parts[1]
            if (parts.size >= 3) selectedDistrict = parts[2]
        }
    }
    
    // 模拟地区数据
    val provinces = listOf("北京", "上海", "广东", "江苏", "浙江", "四川", "湖北", "湖南", "河北", "河南")
    val cities = mapOf(
        "北京" to listOf("北京市"),
        "上海" to listOf("上海市"),
        "广东" to listOf("广州", "深圳", "珠海", "佛山", "东莞"),
        "江苏" to listOf("南京", "苏州", "无锡", "常州", "镇江"),
        "浙江" to listOf("杭州", "宁波", "温州", "嘉兴", "湖州"),
        "四川" to listOf("成都", "绵阳", "德阳", "自贡", "攀枝花"),
        "湖北" to listOf("武汉", "宜昌", "襄阳", "十堰", "荆州"),
        "湖南" to listOf("长沙", "株洲", "湘潭", "衡阳", "邵阳"),
        "河北" to listOf("石家庄", "唐山", "保定", "秦皇岛", "张家口"),
        "河南" to listOf("郑州", "开封", "洛阳", "平顶山", "安阳")
    )
    val districts = mapOf(
        "北京市" to listOf("东城区", "西城区", "朝阳区", "海淀区", "丰台区"),
        "上海市" to listOf("黄浦区", "徐汇区", "长宁区", "静安区", "普陀区"),
        "广州" to listOf("越秀区", "海珠区", "荔湾区", "天河区", "白云区"),
        "深圳" to listOf("福田区", "罗湖区", "南山区", "宝安区", "龙岗区"),
        "杭州" to listOf("上城区", "下城区", "江干区", "拱墅区", "西湖区"),
        "武汉" to listOf("江岸区", "江汉区", "硚口区", "汉阳区", "武昌区"),
        "成都" to listOf("锦江区", "青羊区", "金牛区", "武侯区", "成华区")
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF1E2A3A),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFD700).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { showRegionPicker = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (value.isNotEmpty()) value else "请选择地区",
                    color = if (value.isNotEmpty()) Color.White else Color.Gray
                )
                
                // 位置图标
                Text(
                    text = "📍",
                    fontSize = 16.sp
                )
            }
        }
        
        // 地区选择器对话框
        if (showRegionPicker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "选择地区",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 省份选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("省份: ", color = Color.White, modifier = Modifier.width(80.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFF2C3E50),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            var isProvinceDropdownExpanded by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isProvinceDropdownExpanded = true },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedProvince.isNotEmpty()) selectedProvince else "请选择",
                                    color = if (selectedProvince.isNotEmpty()) Color.White else Color.Gray
                                )
                                Text("▼", color = Color(0xFFFFD700), fontSize = 12.sp)
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = isProvinceDropdownExpanded,
                                onDismissRequest = { isProvinceDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                provinces.forEach { province ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(province, color = Color.White) },
                                        onClick = {
                                            if (selectedProvince != province) {
                                                selectedProvince = province
                                                selectedCity = ""
                                                selectedDistrict = ""
                                            }
                                            isProvinceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 城市选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("城市: ", color = Color.White, modifier = Modifier.width(80.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFF2C3E50),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            var isCityDropdownExpanded by remember { mutableStateOf(false) }
                            val citiesList = cities[selectedProvince] ?: emptyList()
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectedProvince.isNotEmpty()) { 
                                        isCityDropdownExpanded = true 
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedCity.isNotEmpty()) selectedCity else "请选择",
                                    color = if (selectedCity.isNotEmpty()) Color.White else Color.Gray
                                )
                                if (selectedProvince.isNotEmpty()) {
                                    Text("▼", color = Color(0xFFFFD700), fontSize = 12.sp)
                                }
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = isCityDropdownExpanded,
                                onDismissRequest = { isCityDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                citiesList.forEach { city ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(city, color = Color.White) },
                                        onClick = {
                                            if (selectedCity != city) {
                                                selectedCity = city
                                                selectedDistrict = ""
                                            }
                                            isCityDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 区县选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("区县: ", color = Color.White, modifier = Modifier.width(80.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFF2C3E50),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            var isDistrictDropdownExpanded by remember { mutableStateOf(false) }
                            val districtsList = districts[selectedCity] ?: emptyList()
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectedCity.isNotEmpty()) { 
                                        isDistrictDropdownExpanded = true 
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedDistrict.isNotEmpty()) selectedDistrict else "请选择",
                                    color = if (selectedDistrict.isNotEmpty()) Color.White else Color.Gray
                                )
                                if (selectedCity.isNotEmpty()) {
                                    Text("▼", color = Color(0xFFFFD700), fontSize = 12.sp)
                                }
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = isDistrictDropdownExpanded,
                                onDismissRequest = { isDistrictDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                districtsList.forEach { district ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(district, color = Color.White) },
                                        onClick = {
                                            selectedDistrict = district
                                            isDistrictDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showRegionPicker = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("取消")
                        }
                        
                        Button(
                            onClick = {
                                val region = buildString {
                                    append(selectedProvince)
                                    if (selectedCity.isNotEmpty()) {
                                        append(" ")
                                        append(selectedCity)
                                    }
                                    if (selectedDistrict.isNotEmpty()) {
                                        append(" ")
                                        append(selectedDistrict)
                                    }
                                }
                                onValueChange(region)
                                showRegionPicker = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 增强的日期选择器组件
 */
@Composable
fun EnhancedDatePicker(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var year by remember { mutableStateOf(1990) }
    var month by remember { mutableStateOf(1) }
    var day by remember { mutableStateOf(1) }
    
    // 解析初始日期
    LaunchedEffect(value) {
        if (value.isNotEmpty()) {
            try {
                val parts = value.split("-")
                if (parts.size == 3) {
                    year = parts[0].toInt()
                    month = parts[1].toInt()
                    day = parts[2].toInt()
                }
            } catch (e: Exception) {
                // 解析失败，使用默认值
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF1E2A3A),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFD700).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { showDatePicker = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (value.isNotEmpty()) value else "请选择日期",
                color = if (value.isNotEmpty()) Color.White else Color.Gray
            )
                
                // 日历图标
                Text(
                    text = "📅",
                    fontSize = 16.sp
                )
            }
        }
        
        // 日期选择器对话框
        if (showDatePicker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "选择日期",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 年份选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("年份: ", color = Color.White, modifier = Modifier.width(80.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFF2C3E50),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            var isYearDropdownExpanded by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isYearDropdownExpanded = true },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(year.toString(), color = Color.White)
                                Text("▼", color = Color(0xFFFFD700), fontSize = 12.sp)
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = isYearDropdownExpanded,
                                onDismissRequest = { isYearDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                for (y in 1930..2024) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(y.toString(), color = Color.White) },
                                        onClick = {
                                            year = y
                                            isYearDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 月份选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("月份: ", color = Color.White, modifier = Modifier.width(80.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFF2C3E50),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            var isMonthDropdownExpanded by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isMonthDropdownExpanded = true },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(month.toString(), color = Color.White)
                                Text("▼", color = Color(0xFFFFD700), fontSize = 12.sp)
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = isMonthDropdownExpanded,
                                onDismissRequest = { isMonthDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                for (m in 1..12) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(m.toString(), color = Color.White) },
                                        onClick = {
                                            month = m
                                            isMonthDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 日期选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("日期: ", color = Color.White, modifier = Modifier.width(80.dp))
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = Color(0xFF2C3E50),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            var isDayDropdownExpanded by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDayDropdownExpanded = true },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(day.toString(), color = Color.White)
                                Text("▼", color = Color(0xFFFFD700), fontSize = 12.sp)
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = isDayDropdownExpanded,
                                onDismissRequest = { isDayDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                val daysInMonth = when (month) {
                                    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                                    4, 6, 9, 11 -> 30
                                    else -> 31
                                }
                                
                                for (d in 1..daysInMonth) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(d.toString(), color = Color.White) },
                                        onClick = {
                                            day = d
                                            isDayDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showDatePicker = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("取消")
                        }
                        
                        Button(
                            onClick = {
                                // 格式化日期字符串
                                val formattedMonth = if (month < 10) "0$month" else month.toString()
                                val formattedDay = if (day < 10) "0$day" else day.toString()
                                onValueChange("$year-$formattedMonth-$formattedDay")
                                showDatePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}
