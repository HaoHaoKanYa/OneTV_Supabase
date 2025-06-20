package top.cywin.onetv.tv.ui.screens.settings.components  // 包的声明

import androidx.compose.runtime.Composable  // 导入 Compose 的 Composable 注解
import androidx.compose.ui.Modifier  // 导入 Modifier 类型，控制界面元素的修饰
import androidx.compose.ui.focus.focusRequester  // 导入焦点请求器，用于处理焦点
import androidx.lifecycle.viewmodel.compose.viewModel  // 导入 viewModel 支持，用于获取视图模型
import androidx.tv.material3.Switch  // 导入 TV 端的 Switch 控件
import top.cywin.onetv.tv.ui.screens.settings.SettingsViewModel  // 导入 SettingsViewModel，用于管理设置的状态

// 设置更新项的 Composable 函数
@Composable
fun SettingsCategoryUpdate(
    modifier: Modifier = Modifier,  // 默认修饰符，允许外部传递自定义修饰符
    settingsViewModel: SettingsViewModel = viewModel(),  // 使用 ViewModel 管理设置的状态
) {
    settingsViewModel.updateForceRemind = true  // 这里直接设置强提醒的默认值为 true（开启）250221添加
    SettingsContentList(modifier) {  // 显示设置项列表
        item {  // 第一个设置项：更新通道
            val list = mapOf(  // 创建更新通道的映射（稳定版和测试版）
                "stable" to "稳定版",  // 稳定版的映射
                "beta" to "测试版",  // 测试版的映射
            )

            // 设置列表项，显示更新通道
            SettingsListItem(
                modifier = Modifier.focusRequester(it),  // 为列表项添加焦点请求器
                headlineContent = "更新通道",  // 列表项标题
                trailingContent = list[settingsViewModel.updateChannel] ?: "",  // 根据当前选中的更新通道显示对应的文字
                onSelected = {  // 用户点击时切换更新通道
                    settingsViewModel.updateChannel =
                        list.keys.first { it != settingsViewModel.updateChannel }  // 切换更新通道
                },
            )
        }

        item {  // 第二个设置项：更新强提醒
            // 设置列表项，显示更新强提醒的状态
            SettingsListItem(
                headlineContent = "更新强提醒",  // 列表项标题
                supportingContent = if (settingsViewModel.updateForceRemind) "检测到新版本时会全屏提醒"  // 如果开启强提醒，显示相应提示
                else "检测到新版本时仅消息提示",  // 如果关闭强提醒，显示其他提示
                trailingContent = {
                    // Switch 控件，用于切换强提醒的开关
                    Switch(settingsViewModel.updateForceRemind, null)
                },
                onSelected = {  // 用户点击时切换强提醒状态
                    settingsViewModel.updateForceRemind = !settingsViewModel.updateForceRemind  // 切换强提醒状态
                },
            )
        }
    }
}
