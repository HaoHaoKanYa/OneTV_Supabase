package top.cywin.onetv.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import top.cywin.onetv.core.designsystem.theme.Colors
import top.cywin.onetv.core.designsystem.theme.LocalColors
import top.cywin.onetv.core.designsystem.theme.darkColors
import top.cywin.onetv.core.designsystem.theme.lightColors

@Composable
fun MyTVTheme(
    isInDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isInDarkTheme) colorSchemeForDarkMode else colorSchemeForLightMode
    val colors = if (isInDarkTheme) darkColors else lightColors

    // 调整表面透明度
    MaterialTheme(
        colorScheme = colorScheme.copy(
            surface = colorScheme.surface.copy(alpha
            = 0.8f),      // 调整表面透明度
            background = colorScheme.background.copy(alpha = 0.8f) // 调整背景透明度
        ),
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalColors provides colors,
        ) {
            content()
        }
    }
}

val MaterialTheme.colors: Colors
    @Composable
    get() = LocalColors.current