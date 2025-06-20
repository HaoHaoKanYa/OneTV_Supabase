package top.cywin.onetv.core.data

import android.content.Context
import top.cywin.onetv.core.data.utils.Globals
import top.cywin.onetv.core.data.utils.SP

object AppData {
    fun init(context: Context) {
        Globals.cacheDir = context.cacheDir
        SP.init(context)
    }
}