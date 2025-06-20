package top.cywin.onetv.tv.ui.screens.update // 定义包名，用于组织和分类代码

import androidx.compose.runtime.getValue // 导入Compose库中的getValue函数，用于获取状态值
import androidx.compose.runtime.mutableStateOf // 导入mutableStateOf函数，用于创建可变状态
import androidx.compose.runtime.setValue // 导入setValue函数，用于更新状态值
import androidx.lifecycle.ViewModel // 导入ViewModel类，作为MVVM架构中的视图模型层
import top.cywin.onetv.core.data.entities.git.GitRelease // 导入GitRelease实体类，表示GitHub上的发布信息
import top.cywin.onetv.core.data.repositories.git.GitRepository // 导入GitRepository类，用于处理与GitHub相关的数据操作
import top.cywin.onetv.core.data.utils.Constants // 导入包含常量的Constants类
import top.cywin.onetv.core.data.utils.Logger // 导入Logger类，用于日志记录
import top.cywin.onetv.core.util.utils.Downloader // 导入Downloader类，用于文件下载
import top.cywin.onetv.core.util.utils.compareVersion // 导入compareVersion扩展函数，用于版本号比较
import top.cywin.onetv.tv.ui.material.Snackbar // 导入Snackbar类，用于显示短暂的消息提示
import top.cywin.onetv.tv.ui.material.SnackbarType // 导入SnackbarType枚举，定义消息提示类型
import java.io.File // 导入File类，用于文件操作

// UpdateViewModel继承自ViewModel，主要用于管理UI相关的数据逻辑
class UpdateViewModel : ViewModel() {
    // 创建一个Logger实例，用于本类的日志记录
    private val log = Logger.create(javaClass.simpleName)

    // 定义私有变量_isChecking，表示是否正在检查更新
    private var _isChecking = false
    // 定义私有变量_isUpdating，表示是否正在进行更新
    private var _isUpdating = false

    // 使用mutableStateOf创建一个名为_isUpdateAvailable的状态变量，表示是否有可用更新
    private var _isUpdateAvailable by mutableStateOf(false)
    // 提供一个只读属性isUpdateAvailable，返回_isUpdateAvailable的值
    val isUpdateAvailable get() = _isUpdateAvailable

    // 使用mutableStateOf创建一个名为_updateDownloaded的状态变量，表示更新是否已下载
    private var _updateDownloaded by mutableStateOf(false)
    // 提供一个只读属性updateDownloaded，返回_updateDownloaded的值
    val updateDownloaded get() = _updateDownloaded

    // 使用mutableStateOf创建一个名为_latestRelease的状态变量，存储最新的发布信息
    private var _latestRelease by mutableStateOf(GitRelease())
    // 提供一个只读属性latestRelease，返回_latestRelease的值
    val latestRelease get() = _latestRelease

    // 定义一个名为visible的状态变量，控制界面元素的可见性
    var visible by mutableStateOf(false)

    // suspend函数checkUpdate，用于异步检查更新
    suspend fun checkUpdate(currentVersion: String, channel: String) {
        // 如果当前正在检查更新或者已有可用更新，则直接返回
        if (_isChecking || _isUpdateAvailable) return

        try {
            // 根据渠道获取对应的GitHub发布地址
            val releaseUrl = Constants.GIT_RELEASE_LATEST_URL[channel] ?: return

            // 设置_isChecking为true，表示开始检查更新
            _isChecking = true
            // 调用GitRepository的latestRelease方法获取最新的发布信息
            _latestRelease = GitRepository().latestRelease(releaseUrl)
            // 记录线上版本号日志
            log.d("线上版本: ${_latestRelease.version}")
            // 使用compareVersion函数比较当前版本和最新版本，判断是否有新版本可用
            _isUpdateAvailable = _latestRelease.version.compareVersion(currentVersion) > 0
        } catch (ex: Exception) {
            // 如果发生异常，记录错误日志，并更新latestRelease的描述信息
            log.e("检查更新失败", ex)
            _latestRelease = _latestRelease.copy(description = ex.message ?: "检查更新失败")
        } finally {
            // 最后将_isChecking设置为false，表示检查更新结束
            _isChecking = false
        }
    }

    // suspend函数downloadAndUpdate，用于异步下载并应用更新
    suspend fun downloadAndUpdate(latestFile: File) {
        // 如果没有可用更新或正在更新，则直接返回
        if (!_isUpdateAvailable || _isUpdating) return

        // 设置_isUpdating为true，表示开始下载更新
        _isUpdating = true
        // 初始化_updateDownloaded为false，表示更新尚未完成下载
        _updateDownloaded = false

        // 显示开始下载更新的消息提示
        Snackbar.show(
            "开始下载更新",
            leadingLoading = true,
            duration = 10_000,
            id = "downloadProcess"
        )

        try {
            // 调用Downloader的downloadTo方法下载最新版本到指定路径，并在下载过程中更新进度
            Downloader.downloadTo(_latestRelease.downloadUrl, latestFile.path) {
                // 更新下载进度的消息提示
                Snackbar.show(
                    "正在下载更新: $it%",
                    leadingLoading = true,
                    duration = 10_000,
                    id = "downloadProcess"
                )
            }

            // 下载完成后，设置_updateDownloaded为true，表示更新已下载
            _updateDownloaded = true
            // 显示下载更新成功的消息提示
            Snackbar.show("下载更新成功")
        } catch (ex: Exception) {
            // 如果下载过程中出现异常，显示下载更新失败的消息提示
            Snackbar.show("下载更新失败", type = SnackbarType.ERROR)
        } finally {
            // 最后将_isUpdating设置为false，表示更新下载结束
            _isUpdating = false
        }
    }
}