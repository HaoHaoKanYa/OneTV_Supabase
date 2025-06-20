package top.cywin.onetv.tv.ui.screens.settings

import androidx.compose.runtime.compositionLocalOf

data class LocalSettingsCurrent(
    val uiFocusOptimize: Boolean = true
)

val LocalSettings = compositionLocalOf { LocalSettingsCurrent() }