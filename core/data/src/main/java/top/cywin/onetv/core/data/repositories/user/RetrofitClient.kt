// 文件路径：core/src/main/java/top/cywin/onetv/core/data/repositories/user/RetrofitClient.kt
package top.cywin.onetv.core.data.repositories.user

import androidx.compose.runtime.remember
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://iptv.liubaotea.online/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val instance: ApiService = retrofit.create(ApiService::class.java)
}
