package top.cywin.onetv.tv.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.cywin.onetv.core.data.entities.channel.ChannelGroupList
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories
import top.cywin.onetv.tv.supabase.SupabaseUserProfileScreen

@Composable
fun SettingsCategoryContent(
    modifier: Modifier = Modifier,
    currentCategoryProvider: () -> SettingsCategories,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    onNavigateToLogin: () -> Unit,
    onNavigateToCategory: ((SettingsCategories) -> Unit)? = null
) {
    val childPadding = rememberChildPadding()
    val currentCategory = currentCategoryProvider()

    Column(
        modifier = modifier.padding(top = childPadding.top),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = currentCategory.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = childPadding.start)
        )

        when (currentCategory) {
            SettingsCategories.USER -> SettingsCategoryUser(
                onNavigateToLogin = onNavigateToLogin,
                onNavigateToProfile = {
                    onNavigateToCategory?.invoke(SettingsCategories.PROFILE)
                }
            )
            SettingsCategories.ABOUT -> SettingsCategoryAbout()
            SettingsCategories.APP -> SettingsCategoryApp()
            SettingsCategories.IPTV -> SettingsCategoryIptv(
                channelGroupListProvider = channelGroupListProvider,
            )
            SettingsCategories.EPG -> SettingsCategoryEpg()
            SettingsCategories.EPG_RESERVE -> SettingsCategoryEpgReserve()
            SettingsCategories.UI -> SettingsCategoryUI()
            SettingsCategories.FAVORITE -> SettingsCategoryFavorite()
            SettingsCategories.UPDATE -> SettingsCategoryUpdate()
            SettingsCategories.VIDEO_PLAYER -> SettingsCategoryVideoPlayer()
            SettingsCategories.HTTP -> SettingsCategoryHttp()
            SettingsCategories.DEBUG -> SettingsCategoryDebug()
            SettingsCategories.LOG -> SettingsCategoryLog()
            SettingsCategories.MORE -> SettingsCategoryPush()
            SettingsCategories.PROFILE -> SettingsCategoryProfile()
        }
    }
}

/**
 * 个人中心设置内容
 */
@Composable
fun SettingsCategoryProfile() {
    SupabaseUserProfileScreen(
        onBackPressed = {} // 不需要返回操作，因为已在设置页面中
    )
}
