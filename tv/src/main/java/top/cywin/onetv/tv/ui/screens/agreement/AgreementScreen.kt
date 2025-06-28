import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.cywin.onetv.core.data.utils.Constants
import top.cywin.onetv.tv.ui.rememberChildPadding
import top.cywin.onetv.tv.ui.theme.MyTVTheme
import top.cywin.onetv.tv.ui.tooling.PreviewWithLayoutGrids
import top.cywin.onetv.tv.ui.utils.customBackground
import top.cywin.onetv.tv.ui.utils.focusOnLaunched
import top.cywin.onetv.tv.ui.utils.handleKeyEvents

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type


@Composable
fun AgreementScreen(
    modifier: Modifier = Modifier,
    onAgree: () -> Unit = {},
    onDisagree: () -> Unit = {},
    onDisableUiFocusOptimize: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // 创建 FocusRequester，并在 LaunchedEffect 中主动请求焦点
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .customBackground()
            .padding(top = childPadding.top),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("壹来电视使用须知", style = MaterialTheme.typography.headlineMedium)

        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()  // 确保接收焦点
                    .width(586.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(16.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        // 捕获按下事件，防止重复触发
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(
                                            lazyListState.firstVisibleItemIndex + 1
                                        )
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(
                                            maxOf(lazyListState.firstVisibleItemIndex - 1, 0)
                                        )
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    // 允许向右键切换到同意按钮
                                    false
                                }
                                Key.DirectionLeft -> {
                                    // 允许向左键切换到不同意按钮
                                    false
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                contentPadding = PaddingValues(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val messages = listOf(
                    "欢迎使用${Constants.APP_TITLE}，请在使用前仔细阅读以下协议条款：",
                    "1. 本软件仅供技术研究与学习交流使用，严禁用于任何商业场景或非法用途。",
                    "2. 本软件自身不制作、不存储、不传播任何音视频内容，所有直播流均来源于用户自定义添加或者第三方网络公开资源。",
                    "3. 软件内涉及的代码、图标、文档等知识产权均归属原始权利人，开发者仅进行技术整合与界面优化。",
                    "4. 知识产权保护声明：",
                    "   a) 开发者始终尊重并致力于保护第三方合法权益",
                    "   b) 若权利人认为存在侵权内容，请通过页面联系方式提交包含以下材料的完整通知：",
                    "      - 权利归属证明文件扫描件",
                    "      - 涉嫌侵权内容在本软件中的具体定位信息",
                    "      - 具有法律效力的权利主张声明书",
                    "   c) 开发者在收到符合要求的通知后，将启动内部合规审查流程",
                    "   d) 经复核确认存在争议的内容，开发者将依据技术可行性采取适当措施",
                    "   e) 因技术特性限制，部分内容处理可能存在合理延迟",
                    "5. 本软件内置Google Analytics仅用于采集匿名化功能使用数据（如频道切换频次、功能使用时长），用于优化软件性能，绝不收集设备信息、用户身份等敏感数据。",
                    "6. 您知悉并同意：使用本软件可能存在的包括但不限于以下风险：",
                    "   a) 因网络传输导致的内容延迟、卡顿或解析失败",
                    "   b) 第三方直播源的内容合法性风险",
                    "   c) 软件运行导致的设备耗电量增加",
                    "   d) 其他不可预见的兼容性问题",
                    "7. 免责声明：开发者不对以下事项承担任何责任：",
                    "   a) 因使用本软件导致的直接或间接损失",
                    "   b) 第三方内容的知识产权纠纷",
                    "   c) 软件使用引发的法律风险",
                    "   d) 因软件漏洞导致的数据安全问题",
                    "8. 您承诺：作为具备完全民事行为能力的自然人，在使用本软件前已充分理解并自愿承担所有风险，开发者已通过本协议充分履行告知义务。",
                    "重要提示：滚动查看即视为已阅读，继续使用将默认您接受全部条款。",
                    "《用户协议与隐私说明》\n" +
                            "开发者个人维护项目 最后更新：2025年[3月]/[10日]\n" +
                            "一、重要须知（请逐条阅读）\n" +
                            "1.1 性质声明\n" +
                            "本软件仅供技术学习与交流使用，禁止用于商业用途。用户不得对本软件进行二次贩卖、捆绑销售或用于盈利性服务。\n" +
                            "1.2 内容免责\n" +
                            "本软件不生产、存储或提供任何直播/点播内容，所有资源均来自网络公开内容。我们对内容的合法性、准确性及稳定性不做担保，亦不承担相关责任。\n" +
                            "播放内容完全取决于用户自行配置或网络公开资源。\n" +
                            "开发者无法验证内容源的合法性，强烈建议用户：",
                    "✓ 自行添加版权合规源，\n" +
                            "✓ 支持版权，人人有责，\n" +
                            "✓ 发现异常内容立即断开播放并自行检查节目源头。\n" +
                            "1.3 资源溯源\n" +
                            "软件内代码、UI设计及文档资源均基于开发者社区公开贡献构建，若涉及第三方知识产权问题，请通过文末联系方式提交权属证明，我们将在24小时内响应处理。\n" +
                            "1.4 责任确认\n" +
                            "您应遵守所在地区法律法规，合理使用网络资源。\n" +
                            "因用户行为导致的版权纠纷、数据滥用等后果需自行承担，与本软件及开发者无关。\n" +
                            "本软件为个人开发者开源学习项目，无商业团队运营、无公司主体。\n" +
                            "严禁利用本软件从事违法活动或接入非法内容源，开发者不监控用户使用行为。\n" +
                            "因使用本软件导致的版权纠纷、行政处罚、设备损坏等风险由用户自行承担。\n" +
                            "开发者保留随时终止项目运营且不提前通知的权利。\n" +
                            "二、隐私与数据规则\n" +
                            "2.1 数据收集清单\n" +
                            "数据类型\t用途\t存储方\t留存周期\n" +
                            "设备基础信息\t崩溃修复/版本兼容性\t本地\t卸载即删除\n" +
                            "匿名播放日志\t解码器性能优化\tGoogle云\t30天自动清除\n" +
                            "第三方账号信息\t依第三方公司服务政策\n" +
                            "2.2 登录专项说明\n" +
                            "最低权限原则：服务器仅为管理个人账号信息，绝不可能会接触到你个人设备上的密码、手机号等敏感信息，\n" +
                            "加密保障：个人务必保管好账号密码，在健康的网络环境下展开相关活动，\n" +
                            "账号管理：因测试阶段，如你个人需要注销账号，请通过页面联系方式联系开发者修改/删除权限，\n" +
                            "风险提示：本服务中断会因不可预知的原因导致登录功能暂时不可用，开发者不承担连带责任。\n" +
                            "2.3 用户控制权\n" +
                            "数据追踪开关：应用启动会调用2.1条款相关说明，\n" +
                            "本地数据清除：通过\"设置-恢复出厂\"可彻底删除本设备所有记录（不含Authing服务器数据）。\n" +
                            "三、免责条款\n" +
                            "3.1 内容层面\n" +
                            "用户需自行确保所播放内容符合所在地法律法规，开发者不对资源内容做任何背书，\n" +
                            "在收到版权方有效通知（需提供：权属证明+侵权时间戳截图），用户请务必按相关规定处理并在24小时内移除相关源链接，\n" +
                            "3.2 技术层面\n" +
                            "不保证与所有设备/系统版本兼容，Root设备或自定义ROM导致的故障不予支持，请自行检测，\n" +
                            "禁止通过技术手段劫持软件通信协议，因此造成的账号封禁后果自负\n" +
                            "3.3 隐私层面\n" +
                            "使用公共网络（如咖啡厅WiFi）播放可能导致数据被第三方截获，开发者不会对此承担责任，建议启用VPN加密传输。\n" +
                            "四、其他\n" +
                            "4.1 协议变更：更新内容将公示于本条款，不另行通知，继续使用视为接受新条款，\n" +
                            "4.2 唯一联系方式：见软件内（仅处理侵权投诉，不提供技术支持），\n" +
                            "4.3 极端情况：如遇不可抗力导致项目终止，用户应自行备份配置数据。\n" +
                            "五：其他\n" +
                            "5.1 本声明不具备法律合同效力，仅为风险提示！\n"
                )

                items(messages) { message ->
                    Text(
                        text = message,
                        style = if (message.startsWith("重要提示")) {
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = childPadding.bottom),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            modifier = Modifier
                                .focusOnLaunched()
                                .handleKeyEvents(onSelect = onAgree)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        onDisableUiFocusOptimize()
                                        onAgree()
                                    })
                                },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            ),
                            onClick = { },
                        ) {
                            Text("已阅读并同意")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            modifier = Modifier
                                .handleKeyEvents(onSelect = onDisagree),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            ),
                            onClick = { },
                        ) {
                            Text("我不同意")
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=720dp,dpi=213,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
private fun AgreementScreenPreview() {
    MyTVTheme {
        AgreementScreen()
        PreviewWithLayoutGrids { }
    }
}