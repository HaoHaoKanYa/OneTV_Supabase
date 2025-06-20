package top.cywin.onetv.tv.ui.screens.settings.components

import androidx.compose.runtime.Composable // 导入 Compose 框架中的 Composable 注解，用于定义 UI 组件
import androidx.compose.runtime.getValue // 导入 getValue，用于获取可变状态的值
import androidx.compose.runtime.mutableStateOf // 导入 mutableStateOf，用于创建可变的状态
import androidx.compose.runtime.remember // 导入 remember，用于记住状态，在组合过程中不会丢失
import androidx.compose.runtime.setValue // 导入 setValue，用于设置可变状态的值
import androidx.compose.ui.Modifier // 导入 Modifier，用于修改组件的布局和行为
import androidx.compose.ui.focus.FocusRequester // 导入 FocusRequester，用于请求焦点
import androidx.compose.ui.focus.focusRequester // 导入 focusRequester 修饰符，用于为视图请求焦点
import androidx.lifecycle.viewmodel.compose.viewModel // 导入 viewModel 用于访问 ViewModel
import androidx.tv.material3.Switch // 导入 Switch 组件，用于显示和控制开关状态
import top.cywin.onetv.core.data.utils.Constants // 导入 Constants，包含常量定义
import top.cywin.onetv.core.util.utils.humanizeMs // 导入 humanizeMs，转换毫秒值为可读的时间格式
import top.cywin.onetv.tv.ui.material.LocalPopupManager // 导入 LocalPopupManager，用于管理弹出框
import top.cywin.onetv.tv.ui.screens.components.SelectDialog // 导入 SelectDialog 组件，用于选择对话框
import top.cywin.onetv.tv.ui.screens.settings.SettingsViewModel // 导入 SettingsViewModel，用于管理设置界面的数据
import top.cywin.onetv.tv.ui.utils.Configs // 导入 Configs，包含 UI 配置
import java.text.DecimalFormat // 导入 DecimalFormat，用于格式化数字

// 这个 Composable 函数定义了设置页面的 UI 组件
@Composable
fun SettingsCategoryUI(
    modifier: Modifier = Modifier, // 修饰符，允许修改 UI 组件的布局
    settingsViewModel: SettingsViewModel = viewModel(), // 获取 SettingsViewModel 实例
) {
    // 创建设置内容列表
    SettingsContentList(modifier) {
        // 定义每一项设置
        item {
            // 设置项：节目进度
            SettingsListItem(
                modifier = Modifier.focusRequester(it), // 设置焦点请求器
                headlineContent = "节目进度", // 主标题
                supportingContent = "在频道项底部显示当前节目进度条", // 副标题
                trailingContent = {
                    Switch(settingsViewModel.uiShowEpgProgrammeProgress, null) // 显示开关，用于控制是否显示节目进度条
                },
                onSelected = {
                    // 切换显示状态
                    settingsViewModel.uiShowEpgProgrammeProgress =
                        !settingsViewModel.uiShowEpgProgrammeProgress
                },
            )
        }

        item {
            // 设置项：常驻底部节目进度
            SettingsListItem(
                headlineContent = "常驻底部节目进度", // 主标题
                supportingContent = "在播放器底部显示当前节目进度条", // 副标题
                trailingContent = {
                    Switch(settingsViewModel.uiShowEpgProgrammePermanentProgress, null) // 显示开关
                },
                onSelected = {
                    // 切换显示状态
                    settingsViewModel.uiShowEpgProgrammePermanentProgress =
                        !settingsViewModel.uiShowEpgProgrammePermanentProgress
                },
            )
        }

        item {
            // 设置项：台标显示
            SettingsListItem(
                headlineContent = "台标显示", // 主标题
                trailingContent = {
                    Switch(settingsViewModel.uiShowChannelLogo, null) // 显示开关
                },
                onSelected = {
                    // 切换显示状态
                    settingsViewModel.uiShowChannelLogo = !settingsViewModel.uiShowChannelLogo
                },
            )
        }

        item {
            // 设置项：经典选台界面
            SettingsListItem(
                headlineContent = "经典选台界面", // 主标题
                supportingContent = "将选台界面替换为经典三段式结构", // 副标题
                trailingContent = {
                    Switch(settingsViewModel.uiUseClassicPanelScreen, null) // 显示开关
                },
                onSelected = {
                    // 切换显示状态
                    settingsViewModel.uiUseClassicPanelScreen =
                        !settingsViewModel.uiUseClassicPanelScreen
                },
            )
        }

        item {
            // 设置项：时间显示
            val timeShowRangeSeconds = Constants.UI_TIME_SCREEN_SHOW_DURATION / 1000 // 获取显示时间范围（秒）

            SettingsListItem(
                headlineContent = "时间显示", // 主标题
                supportingContent = when (settingsViewModel.uiTimeShowMode) { // 根据设置选择显示方式
                    Configs.UiTimeShowMode.HIDDEN -> "不显示时间"
                    Configs.UiTimeShowMode.ALWAYS -> "总是显示时间"
                    Configs.UiTimeShowMode.EVERY_HOUR -> "整点前后${timeShowRangeSeconds}s显示时间"
                    Configs.UiTimeShowMode.HALF_HOUR -> "半点前后${timeShowRangeSeconds}s显示时间"
                },
                trailingContent = when (settingsViewModel.uiTimeShowMode) { // 显示当前时间模式的文字
                    Configs.UiTimeShowMode.HIDDEN -> "隐藏"
                    Configs.UiTimeShowMode.ALWAYS -> "常显"
                    Configs.UiTimeShowMode.EVERY_HOUR -> "整点"
                    Configs.UiTimeShowMode.HALF_HOUR -> "半点"
                },
                onSelected = {
                    // 切换时间显示模式
                    settingsViewModel.uiTimeShowMode =
                        Configs.UiTimeShowMode.entries.let {
                            it[(it.indexOf(settingsViewModel.uiTimeShowMode) + 1) % it.size]
                        }
                },
            )
        }

        item {
            // 设置项：超时自动关闭界面
            val popupManager = LocalPopupManager.current // 获取弹出框管理器
            val focusRequester = remember { FocusRequester() } // 创建焦点请求器
            var visible by remember { mutableStateOf(false) } // 控制对话框是否可见

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester), // 设置焦点请求器
                headlineContent = "超时自动关闭界面", // 主标题
                supportingContent = "影响选台界面，快捷操作等界面", // 副标题
                trailingContent = settingsViewModel.uiScreenAutoCloseDelay.humanizeMs(), // 显示当前超时设置的时间
                onSelected = {
                    // 打开对话框
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true, // 支持远程配置
            )

            // 选择对话框，用于选择超时自动关闭时间
            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "超时自动关闭界面",
                currentDataProvider = { settingsViewModel.uiScreenAutoCloseDelay }, // 当前选中的值
                dataListProvider = { listOf(5, 10, 15, 20, 25, 30).map { it.toLong() * 1000 } }, // 可选的时间列表（秒转毫秒）
                dataText = { it.humanizeMs() }, // 显示格式化后的时间
                onDataSelected = {
                    // 更新选中的时间
                    settingsViewModel.uiScreenAutoCloseDelay = it
                    visible = false
                },
            )
        }

        item {
            // 设置项：界面整体缩放比例
            val popupManager = LocalPopupManager.current // 获取弹出框管理器
            val focusRequester = remember { FocusRequester() } // 创建焦点请求器
            var visible by remember { mutableStateOf(false) } // 控制对话框是否可见

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester), // 设置焦点请求器
                headlineContent = "界面整体缩放比例", // 主标题
                trailingContent = when (settingsViewModel.uiDensityScaleRatio) { // 根据缩放比例显示不同内容
                    0f -> "自适应"
                    else -> "×${DecimalFormat("#.#").format(settingsViewModel.uiDensityScaleRatio)}"
                },
                onSelected = {
                    // 打开对话框
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true, // 支持远程配置
            )

            // 选择对话框，用于选择缩放比例
            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "界面整体缩放比例",
                currentDataProvider = { settingsViewModel.uiDensityScaleRatio },
                dataListProvider = { listOf(0f) + (5..20).map { it * 0.1f } }, // 缩放比例选项
                dataText = {
                    when (it) {
                        0f -> "自适应"
                        else -> "×${DecimalFormat("#.#").format(it)}"
                    }
                },
                onDataSelected = {
                    // 更新选中的缩放比例
                    settingsViewModel.uiDensityScaleRatio = it
                    visible = false
                },
            )
        }

        item {
            // 设置项：界面字体缩放比例
            val popupManager = LocalPopupManager.current // 获取弹出框管理器
            val focusRequester = remember { FocusRequester() } // 创建焦点请求器
            var visible by remember { mutableStateOf(false) } // 控制对话框是否可见

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester), // 设置焦点请求器
                headlineContent = "界面字体缩放比例", // 主标题
                trailingContent = "×${DecimalFormat("#.#").format(settingsViewModel.uiFontScaleRatio)}", // 显示字体缩放比例
                onSelected = {
                    // 打开对话框
                    popupManager.push(focusRequester, true)
                    visible = true
                },
                remoteConfig = true, // 支持远程配置
            )

            // 选择对话框，用于选择字体缩放比例
            SelectDialog(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
                title = "界面字体缩放比例",
                currentDataProvider = { settingsViewModel.uiFontScaleRatio },
                dataListProvider = { (5..20).map { it * 0.1f } }, // 字体缩放比例选项
                dataText = { "×${DecimalFormat("#.#").format(it)}" },
                onDataSelected = {
                    // 更新选中的字体缩放比例
                    settingsViewModel.uiFontScaleRatio = it
                    visible = false
                },
            )
        }

        item {
            // 设置项：焦点优化
            SettingsListItem(
                headlineContent = "焦点优化", // 主标题
                supportingContent = "关闭后可解决触摸设备在部分场景下闪退", // 副标题
                trailingContent = {
                    Switch(settingsViewModel.uiFocusOptimize, null) // 显示开关
                },
                onSelected = {
                    // 切换显示状态
                    settingsViewModel.uiFocusOptimize = !settingsViewModel.uiFocusOptimize
                },
            )
        }
    }
}
