package top.cywin.onetv.tv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import top.cywin.onetv.core.data.AppData
import top.cywin.onetv.core.data.repositories.supabase.SupabaseClient
import top.cywin.onetv.core.data.repositories.supabase.SupabaseEnvChecker
import java.io.File
import java.io.FileWriter
import java.io.IOException

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 检查环境变量
        SupabaseEnvChecker.checkAllEnvVariables()

        // 初始化 AppData 及其他必要组件
        AppData.init(applicationContext)
        
        // 初始化 SupabaseClient
        SupabaseClient.initialize(applicationContext)
        Log.i("MyTVApplication", "已初始化 SupabaseClient: URL=${SupabaseClient.getUrl()}")
        
        UnsafeTrustManager.enableUnsafeTrustManager()

        // 设置全局未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val errorMessage = "Unhandled exception in thread ${thread.name}:\n${throwable.stackTraceToString()}"
            Log.e("GlobalException", errorMessage)

            // 将错误日志写入 Android 推荐路径
            writeErrorLogToFile(errorMessage)

            // 启动邮件客户端，提示用户发送错误报告
            sendErrorReportViaEmail()

            // 终止应用进程
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }

    /**
     * 将错误日志追加写入应用私有存储目录中的 `onetv_error.log`
     */
    private fun writeErrorLogToFile(errorMessage: String) {
        try {
            val logFile = File(getExternalFilesDir(null), "onetv_error.log")
            FileWriter(logFile, true).use { writer ->
                writer.appendLine("----- Error at ${System.currentTimeMillis()} -----")
                writer.appendLine(errorMessage)
                writer.appendLine()
            }
        } catch (e: IOException) {
            Log.e("GlobalException", "Failed to write error log", e)
        }
    }

    /**
     * 启动邮件客户端，提示用户发送错误报告
     */
    private fun sendErrorReportViaEmail() {
        val logFile = File(getExternalFilesDir(null), "onetv_error.log")
        if (!logFile.exists()) {
            Log.e("GlobalException", "Error log file not found, skipping email report.")
            return
        }

        // 使用 FileProvider 生成安全的 URI（适配 Android 7.0 及以上）
        val fileUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            logFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("cyuan52@outlook.com"))  // 你的邮箱
            putExtra(Intent.EXTRA_SUBJECT, "OneTV Crash Report")
            putExtra(Intent.EXTRA_TEXT, "OneTV 遇到异常，错误日志已附加。")
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  // 允许邮件客户端访问日志文件
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(Intent.createChooser(intent, "选择邮件应用发送错误报告"))
        } catch (e: Exception) {
            Log.e("GlobalException", "Failed to launch email client", e)
        }
    }
}
