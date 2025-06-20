package top.cywin.onetv.mobile.ui.theme

import androidx.compose.material3.darkColorScheme  // 导入用于暗色模式的颜色方案
import androidx.compose.material3.lightColorScheme  // 导入用于亮色模式的颜色方案
import top.cywin.onetv.core.designsystem.theme.darkColors  // 导入暗色调颜色
import top.cywin.onetv.core.designsystem.theme.lightColors  // 导入亮色调颜色

// 为暗色模式定义颜色方案
val colorSchemeForDarkMode = darkColorScheme(
    primary = darkColors.primary,  // 主色
    onPrimary = darkColors.onPrimary,  // 主色上面的文字颜色
    primaryContainer = darkColors.primaryContainer,  // 主色容器背景色
    onPrimaryContainer = darkColors.onPrimaryContainer,  // 主色容器上文字颜色
    secondary = darkColors.secondary,  // 副色
    onSecondary = darkColors.onSecondary,  // 副色上面的文字颜色
    secondaryContainer = darkColors.secondaryContainer,  // 副色容器背景色
    onSecondaryContainer = darkColors.onSecondaryContainer,  // 副色容器上文字颜色
    tertiary = darkColors.tertiary,  // 第三色
    onTertiary = darkColors.onTertiary,  // 第三色上面的文字颜色
    tertiaryContainer = darkColors.tertiaryContainer,  // 第三色容器背景色
    onTertiaryContainer = darkColors.onTertiaryContainer,  // 第三色容器上文字颜色
    error = darkColors.error,  // 错误色
    onError = darkColors.onError,  // 错误色上文字颜色
    background = darkColors.background,  // 背景色
    onBackground = darkColors.onBackground,  // 背景色上文字颜色
    surface = darkColors.surface,  // 表面颜色
    onSurface = darkColors.onSurface,  // 表面颜色上文字颜色
    surfaceVariant = darkColors.surfaceVariant,  // 表面变体颜色
    onSurfaceVariant = darkColors.onSurfaceVariant,  // 表面变体上文字颜色
    outline = darkColors.outline,  // 边框颜色
    outlineVariant = darkColors.outlineVariant,  // 边框变体颜色
    scrim = darkColors.scrim,  // 背景遮罩颜色
    inverseSurface = darkColors.inverseSurface,  // 反转表面颜色
    inverseOnSurface = darkColors.inverseOnSurface,  // 反转表面上文字颜色
    inversePrimary = darkColors.inversePrimary,  // 反转主色
    errorContainer = darkColors.errorContainer,  // 错误容器颜色
    onErrorContainer = darkColors.onErrorContainer,  // 错误容器上文字颜色
    surfaceBright = darkColors.surfaceBright,  // 明亮表面颜色
    surfaceContainer = darkColors.surfaceContainer,  // 表面容器颜色
    surfaceContainerHigh = darkColors.surfaceContainerHigh,  // 高亮表面容器颜色
    surfaceContainerHighest = darkColors.surfaceContainerHighest,  // 最高亮表面容器颜色
    surfaceContainerLow = darkColors.surfaceContainerLow,  // 低亮表面容器颜色
    surfaceContainerLowest = darkColors.surfaceContainerLowest,  // 最低亮表面容器颜色
    surfaceDim = darkColors.surfaceDim,  // 暗色表面容器颜色
)

// 为亮色模式定义颜色方案
val colorSchemeForLightMode = lightColorScheme(
    primary = lightColors.primary,  // 主色
    onPrimary = lightColors.onPrimary,  // 主色上面的文字颜色
    primaryContainer = lightColors.primaryContainer,  // 主色容器背景色
    onPrimaryContainer = lightColors.onPrimaryContainer,  // 主色容器上文字颜色
    secondary = lightColors.secondary,  // 副色
    onSecondary = lightColors.onSecondary,  // 副色上面的文字颜色
    secondaryContainer = lightColors.secondaryContainer,  // 副色容器背景色
    onSecondaryContainer = lightColors.onSecondaryContainer,  // 副色容器上文字颜色
    tertiary = lightColors.tertiary,  // 第三色
    onTertiary = lightColors.onTertiary,  // 第三色上面的文字颜色
    tertiaryContainer = lightColors.tertiaryContainer,  // 第三色容器背景色
    onTertiaryContainer = lightColors.onTertiaryContainer,  // 第三色容器上文字颜色
    error = lightColors.error,  // 错误色
    onError = lightColors.onError,  // 错误色上文字颜色
    background = lightColors.background,  // 背景色
    onBackground = lightColors.onBackground,  // 背景色上文字颜色
    surface = lightColors.surface,  // 表面颜色
    onSurface = lightColors.onSurface,  // 表面颜色上文字颜色
    surfaceVariant = lightColors.surfaceVariant,  // 表面变体颜色
    onSurfaceVariant = lightColors.onSurfaceVariant,  // 表面变体上文字颜色
    outline = lightColors.outline,  // 边框颜色
    outlineVariant = lightColors.outlineVariant,  // 边框变体颜色
    scrim = lightColors.scrim,  // 背景遮罩颜色
    inverseSurface = lightColors.inverseSurface,  // 反转表面颜色
    inverseOnSurface = lightColors.inverseOnSurface,  // 反转表面上文字颜色
    inversePrimary = lightColors.inversePrimary,  // 反转主色
    errorContainer = lightColors.errorContainer,  // 错误容器颜色
    onErrorContainer = lightColors.onErrorContainer,  // 错误容器上文字颜色
    surfaceBright = lightColors.surfaceBright,  // 明亮表面颜色
    surfaceContainer = lightColors.surfaceContainer,  // 表面容器颜色
    surfaceContainerHigh = lightColors.surfaceContainerHigh,  // 高亮表面容器颜色
    surfaceContainerHighest = lightColors.surfaceContainerHighest,  // 最高亮表面容器颜色
    surfaceContainerLow = lightColors.surfaceContainerLow,  // 低亮表面容器颜色
    surfaceContainerLowest = lightColors.surfaceContainerLowest,  // 最低亮表面容器颜色
    surfaceDim = lightColors.surfaceDim,  // 暗色表面容器颜色
)
