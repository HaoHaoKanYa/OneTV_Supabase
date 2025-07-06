package top.cywin.onetv.tv.ui.screens.settings.components

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import top.cywin.onetv.core.data.utils.Constants
import top.cywin.onetv.tv.R
import top.cywin.onetv.tv.ui.material.LocalPopupManager
import top.cywin.onetv.tv.ui.material.SimplePopup
import top.cywin.onetv.tv.ui.screens.components.Qrcode
import top.cywin.onetv.tv.ui.screens.guide.GuideScreen

@Composable
fun SettingsCategoryAbout(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo = rememberPackageInfo(),
) {
    SettingsContentList(modifier) {
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(it),
                headlineContent = "应用名称",
                trailingContent = Constants.APP_TITLE,
            )
        }

        item {
            SettingsListItem(
                headlineContent = "应用版本",
                trailingContent = packageInfo.versionName ?: "未知版本",
            )
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var showDialog by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "联系我们",
                // trailingContent = Constants.APP_REPO, // 暂时注释掉"关注公众号【壹来了】"文本：原来设置-关于-联系我们对应该的文件显示
                trailingIcon = Icons.AutoMirrored.Default.OpenInNew,
                onSelected = {
                    popupManager.push(focusRequester, true)
                    showDialog = true
                },
            )

            SimplePopup(
                visibleProvider = { showDialog },
                onDismissRequest = { showDialog = false },
            ) {
                Box(modifier = modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 暂时注释掉原来的公众号二维码和文本
                        /*
                        Image(
                            painter = painterResource(R.drawable.gongzhonghao_qr_image),
                            contentDescription = "公众号二维码",
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .width(200.dp)
                                .height(200.dp)
                        )

                        Text("扫码关注公众号")
                        */

                        // 新的联系我们小卡片信息
                        Box(
                            modifier = Modifier
                                .padding(32.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.3f), // 与设置页面相同的透明度
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 2.dp,
                                    color = Color(0xFFFFD700), // 金黄色边框
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "💬 联系我们",
                                    style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Text(
                                    text = "请前往个人中心客服支持菜单",
                                    style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var isGuideScreenVisible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "使用说明",
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onSelected = {
                    popupManager.push(focusRequester, true)
                    isGuideScreenVisible = true
                },
            )

            SimplePopup(
                visibleProvider = { isGuideScreenVisible },
                onDismissRequest = { isGuideScreenVisible = false },
            ) {
                GuideScreen(onClose = { isGuideScreenVisible = false })
            }
        }

        item {
            val popupManager = LocalPopupManager.current
            val focusRequester = remember { FocusRequester() }
            var visible by remember { mutableStateOf(false) }

            SettingsListItem(
                modifier = Modifier.focusRequester(focusRequester),
                headlineContent = "账号升级",
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onSelected = {
                    popupManager.push(focusRequester, true)
                    visible = true
                },
            )

            SimplePopup(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
            ) {
                // 暂时注释掉原来的赞赏二维码图片
                /*
                val painter = painterResource(R.drawable.mm_reward_qrcode)

                Image(
                    painter,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(300.dp),
                )
                */

                // 新的激活码获取小卡片信息
                Box(modifier = modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                color = Color.Black.copy(alpha = 0.3f), // 与设置页面相同的透明度
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = Color(0xFFFFD700), // 金黄色边框
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🔑 激活码获取",
                                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "请前往个人中心客服支持菜单",
                                style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPackageInfo(context: Context = LocalContext.current): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)
