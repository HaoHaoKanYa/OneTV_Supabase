package top.cywin.onetv.core.data.repositories.supabase

import android.util.Log
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Supabase存储仓库
 * 处理与Supabase存储相关的操作，如获取IPTV播放列表等
 */
class StorageRepository {
    private val client = SupabaseClient.client
    private val storage = client.storage
    
    // IPTV资源存储桶名称
    private val BUCKET_NAME = "iptv-sources"
    
    /**
     * 获取存储桶中的所有文件列表
     * @return List<String> 文件名列表
     */
    suspend fun listPlaylistFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            val bucket = storage[BUCKET_NAME]
            val files = bucket.list("")
            return@withContext files.map { it.name }
        } catch (e: Exception) {
            Log.e("StorageRepository", "Error listing playlist files", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 下载指定的播放列表文件
     * @param filename 文件名
     * @return String 文件内容（M3U格式）
     */
    suspend fun downloadPlaylist(filename: String): String = withContext(Dispatchers.IO) {
        try {
            val bucket = storage[BUCKET_NAME]
            val bytes = bucket.downloadPublic(filename)
            return@withContext String(bytes)
        } catch (e: Exception) {
            Log.e("StorageRepository", "Error downloading playlist: $filename", e)
            throw e
        }
    }
    
    /**
     * 保存M3U播放列表到本地缓存
     * @param filename 文件名
     * @param content 文件内容
     * @param cacheDir 缓存目录
     * @return File 保存的文件
     */
    suspend fun savePlaylistToCache(filename: String, content: String, cacheDir: File): File = withContext(Dispatchers.IO) {
        // 创建IPTV缓存目录
        val iptvDir = File(cacheDir, "iptv")
        if (!iptvDir.exists()) {
            iptvDir.mkdirs()
        }
        
        // 保存文件
        val file = File(iptvDir, filename)
        file.writeText(content)
        return@withContext file
    }
    
    /**
     * 获取播放列表文件的公共访问URL
     * @param filename 文件名
     * @return String 公共URL
     */
    suspend fun getPlaylistPublicUrl(filename: String): String = withContext(Dispatchers.IO) {
        try {
            val bucket = storage[BUCKET_NAME]
            return@withContext bucket.publicUrl(filename)
        } catch (e: Exception) {
            Log.e("StorageRepository", "Error getting public URL for: $filename", e)
            throw e
        }
    }
} 