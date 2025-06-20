package top.cywin.onetv.tv.ui.material

import androidx.compose.runtime.Composable

@Composable
fun Visible(visibleProvider: () -> Boolean = { false }, content: @Composable () -> Unit) {
    if (visibleProvider()) content()
}