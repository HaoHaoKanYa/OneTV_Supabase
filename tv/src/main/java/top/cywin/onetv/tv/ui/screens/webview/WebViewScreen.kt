package top.cywin.onetv.tv.ui.screens.webview // 定义包名，指定该类所在的包

import android.annotation.SuppressLint // 导入SuppressLint注解，用于忽略警告
import android.content.Context // 导入Context类，提供应用的上下文
import android.graphics.Bitmap // 导入Bitmap类，用于表示图像
import android.view.MotionEvent // 导入MotionEvent类，用于处理触摸事件
import android.view.ViewGroup // 导入ViewGroup类，视图的容器
import android.webkit.JavascriptInterface // 导入JavascriptInterface类，用于与JavaScript进行交互
import android.webkit.WebSettings // 导入WebSettings类，WebView设置
import android.webkit.WebView // 导入WebView类，Web浏览器控件
import android.webkit.WebViewClient // 导入WebViewClient类，用于处理WebView的页面事件
import androidx.compose.foundation.background // 导入background修饰符，用于设置背景颜色
import androidx.compose.foundation.layout.Box // 导入Box布局
import androidx.compose.foundation.layout.fillMaxHeight // 导入fillMaxHeight修饰符，用于填满最大高度
import androidx.compose.foundation.layout.fillMaxSize // 导入fillMaxSize修饰符，用于填满最大尺寸
import androidx.compose.runtime.Composable // 导入Composable注解，表示可以在Compose UI中使用的函数
import androidx.compose.runtime.getValue // 导入getValue函数，用于获取状态值
import androidx.compose.runtime.mutableStateOf // 导入mutableStateOf，用于创建可变的状态
import androidx.compose.runtime.remember // 导入remember函数，保存状态
import androidx.compose.runtime.setValue // 导入setValue函数，用于设置状态值
import androidx.compose.ui.Alignment // 导入Alignment类，用于对齐布局中的元素
import androidx.compose.ui.Modifier // 导入Modifier类，用于修饰UI组件
import androidx.compose.ui.graphics.Color // 导入Color类，用于颜色的定义
import androidx.compose.ui.graphics.toArgb // 导入toArgb扩展函数，将颜色转换为ARGB格式
import androidx.compose.ui.viewinterop.AndroidView // 导入AndroidView，允许在Compose中使用传统的Android视图
import top.cywin.onetv.core.data.utils.ChannelUtil // 导入ChannelUtil工具类，用于处理频道相关的操作
import top.cywin.onetv.tv.ui.material.Visible // 导入Visible组件，用于根据状态显示/隐藏内容
import top.cywin.onetv.tv.ui.screens.webview.components.WebViewPlaceholder // 导入WebViewPlaceholder组件，用于显示占位符

@SuppressLint("SetJavaScriptEnabled") // 忽略JavaScript启用的警告
@Composable
fun WebViewScreen( // 定义WebViewScreen Composable函数
    modifier: Modifier = Modifier, // modifier参数，用于修饰UI组件
    urlProvider: () -> String = { "${ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX}https://tv.cctv.com/live/index.shtml" }, // urlProvider，获取WebView的URL
    onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> }, // 视频分辨率变化回调
) {
    val url = urlProvider().replace(ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX, "") // 获取并清理URL
    var placeholderVisible by remember { mutableStateOf(true) } // 用于控制占位符的显示状态

    Box(modifier = modifier.fillMaxSize()) { // 使用Box布局填满父容器
        AndroidView( // 在Compose中嵌入Android视图
            modifier = Modifier
                .align(Alignment.Center) // 居中显示
                .fillMaxHeight() // 填满高度
                .background(Color.Black), // 设置背景色为黑色
            factory = {
                MyWebView(it).apply { // 创建MyWebView实例并设置配置
                    webViewClient = MyClient( // 设置WebViewClient，用于监听页面加载事件
                        onPageStarted = { placeholderVisible = true }, // 页面开始加载时显示占位符
                        onPageFinished = { placeholderVisible = false }, // 页面加载完成时隐藏占位符
                    )

                    setBackgroundColor(Color.Black.toArgb()) // 设置WebView背景为黑色
                    layoutParams = ViewGroup.LayoutParams( // 设置WebView的布局参数
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    settings.javaScriptEnabled = true // 启用JavaScript
                    settings.useWideViewPort = true // 使用广视口
                    settings.loadWithOverviewMode = true // 加载页面时缩放至合适大小
                    settings.domStorageEnabled = true // 启用DOM存储
                    settings.databaseEnabled = true // 启用数据库
                    settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK // 缓存模式：如果缓存可用则加载缓存，否则加载网络
                    settings.loadsImagesAutomatically = true // 自动加载图片
                    settings.blockNetworkImage = false // 不阻止网络图片加载
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0" // 设置用户代理
                    settings.cacheMode = WebSettings.LOAD_DEFAULT // 默认缓存模式
                    settings.javaScriptCanOpenWindowsAutomatically = true // 允许JavaScript自动打开窗口
                    settings.setSupportZoom(false) // 禁用缩放
                    settings.displayZoomControls = false // 隐藏缩放控件
                    settings.builtInZoomControls = false // 禁用内建缩放控制
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // 允许混合内容
                    settings.mediaPlaybackRequiresUserGesture = false // 禁止媒体播放需要用户手势

                    isHorizontalScrollBarEnabled = false // 禁用水平滚动条
                    isVerticalScrollBarEnabled = false // 禁用垂直滚动条
                    isClickable = false // 禁用点击事件
                    isFocusable = false // 禁用聚焦事件
                    isFocusableInTouchMode = false // 禁用触摸模式下的聚焦

                    addJavascriptInterface( // 添加JavaScript接口
                        MyWebViewInterface( // 设置接口对象
                            onVideoResolutionChanged = onVideoResolutionChanged,
                        ), "Android" // 设置接口名称为"Android"
                    )
                }
            },
            update = { it.loadUrl(url) }, // 更新WebView并加载URL
        )

        Visible({ placeholderVisible }) { WebViewPlaceholder() } // 显示占位符组件，当placeholderVisible为true时
    }
}

class MyClient( // 自定义WebViewClient类，用于处理页面加载事件
    private val onPageStarted: () -> Unit, // 页面开始加载的回调
    private val onPageFinished: () -> Unit, // 页面加载完成的回调
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { // 页面开始加载时调用
        onPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) { // 页面加载完成时调用
        view.evaluateJavascript( // 执行JavaScript代码
            """ 
            ;(async () => { 
                function delay(ms) { 
                    return new Promise(resolve => setTimeout(resolve, ms)); 
                } 
                 
                while(true) { 
                  const containerEl = document.querySelector('[id^=vodbox]') || document.querySelector('#player') 
                  if(!containerEl) { 
                      await delay(100) 
                      continue 
                  } 
                   
                  document.body.style = 'width: 100vw; height: 100vh; margin: 0; min-width: 0; background: #000;' 
                   
                  containerEl.style = 'width: 100%; height: 100%;' 
                  document.body.append(containerEl) 

                  ;[...document.body.children].forEach((el) => { 
                    if(el.tagName.toLowerCase() == 'div' && !el.id.startsWith('vodbox') && !el.id.startsWith('player')) { 
                        el.remove() 
                    } 
                  }) 
                   
                  const mask = document.createElement('div') 
                  mask.addEventListener('click', () => {}) 
                  mask.style = 'width: 100%; height: 100%; position: absolute; top: 0; left: 0; z-index: 1000;' 
                  document.body.append(mask) 
                   
                  const videoEl = document.querySelector('video') 
                  videoEl.volume = 1 
                  videoEl.autoplay = true 
                   
                  break 
                } 
                 
               await delay(1000) 
               const videoEl = document.querySelector('video') 
               if(videoEl.paused) videoEl.play() 
                
               while(true) { 
                 await delay(1000) 
                 if(videoEl.videoWidth * videoEl.videoHeight == 0) continue 
                  
                 Android.changeVideoResolution(videoEl.videoWidth ,videoEl.videoHeight) 
                 break 
               } 
            })() 
        """.trimIndent()
        ) {
            onPageFinished() // 页面加载完成后执行
        }
    }
}

class MyWebView(context: Context) : WebView(context) { // 自定义WebView类
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean { // 重写触摸事件，禁止WebView响应触摸
        return false
    }
}

class MyWebViewInterface( // 定义JavaScript接口类
    private val onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> }, // 视频分辨率变化回调
) {
    @JavascriptInterface
    fun changeVideoResolution(width: Int, height: Int) { // JavaScript调用此方法以改变视频分辨率
        onVideoResolutionChanged(width, height) // 调用回调函数
    }
}
