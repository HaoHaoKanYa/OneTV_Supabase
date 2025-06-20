package top.cywin.onetv.tv.ui.screens.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.cywin.onetv.tv.ui.screens.settings.SettingsCategories
import top.cywin.onetv.tv.ui.utils.handleKeyEvents

@Composable
fun SettingsCategoryList(
    modifier: Modifier = Modifier,
    currentCategoryProvider: () -> SettingsCategories,
    hoverCategoryProvider: () -> SettingsCategories = currentCategoryProvider,
    onCategoryHover: (SettingsCategories) -> Unit = {},
    onCategorySelected: (SettingsCategories) -> Unit = {},
    enableAnimation: Boolean = false
) {
    val categories = SettingsCategories.entries
    val firstRowCategories = categories.take(7)
    val secondRowCategories = categories.drop(7)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 第一行设置项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            firstRowCategories.forEach { category ->
                SettingsCategoryItem(
                    modifier = Modifier.weight(1f),
                    icon = category.icon,
                    title = category.title,
                    isSelectedProvider = { currentCategoryProvider() == category },
                    isHoveredProvider = { hoverCategoryProvider() == category },
                    onCategoryHover = { onCategoryHover(category) },
                    onCategorySelected = { onCategorySelected(category) },
                    enableAnimation = enableAnimation
                )
            }
        }

        // 第二行设置项
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            secondRowCategories.forEach { category ->
                SettingsCategoryItem(
                    modifier = Modifier.weight(1f),
                    icon = category.icon,
                    title = category.title,
                    isSelectedProvider = { currentCategoryProvider() == category },
                    isHoveredProvider = { hoverCategoryProvider() == category },
                    onCategoryHover = { onCategoryHover(category) },
                    onCategorySelected = { onCategorySelected(category) },
                    enableAnimation = enableAnimation
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isSelectedProvider: () -> Boolean = { false },
    isHoveredProvider: () -> Boolean = { false },
    onCategoryHover: () -> Unit = {},
    onCategorySelected: () -> Unit = {},
    enableAnimation: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // 动画状态
    val isSelected = isSelectedProvider()
    val isHovered = isHoveredProvider()

    // 透明度动画代替缩放动画
    val alpha by animateFloatAsState(
        targetValue = if ((isFocused || isHovered) && enableAnimation) 1.0f else 0.7f,
        animationSpec = tween(durationMillis = 200),
        label = "alpha"
    )

    // 背景颜色动画
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.3f)
            isFocused || isHovered -> MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = backgroundColor,
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface.copy(0.3f),
        ),
        selected = isSelected,
        onClick = { onCategorySelected() },
        leadingContent = {
            Icon(
                icon,
                title,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(alpha)
            )
        },
        headlineContent = {
            Text(
                text = title,
                modifier = Modifier.alpha(alpha)
            )
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onCategoryHover()
            }
            .handleKeyEvents(
                isFocused = { isFocused },
                focusRequester = focusRequester,
                onSelect = { onCategorySelected() },
            ),
    )
}