package com.ace.downloaddemo.core.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.ace.downloaddemo.core.validation.MD5Validator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val md5Validator: MD5Validator
) {

    companion object {
        private const val TAG = "FileManager"
    }

    /**
     * 获取下载目录
     * 使用所有用户共享的外部存储目录
     * 注意：需要MANAGE_EXTERNAL_STORAGE权限（Android 11+）或WRITE_EXTERNAL_STORAGE权限
     */
    fun getDownloadDir(): File {
        // 使用设备共享的外部存储根目录，所有用户可访问
        // 路径示例：/storage/emulated/0/Android/data/com.ace.downloaddemo/files/SharedDownloads
        // 注意：emulated/0 是所有用户的共享存储，不会因用户切换而改变
        val sharedStorage = File(Environment.getExternalStorageDirectory(),
            "Android/data/${context.packageName}/files/SharedDownloads")

        // 备选方案：如果需要更通用的共享目录
        // val sharedStorage = File("/data/media/0/Android/data/${context.packageName}/files/SharedDownloads")

        if (!sharedStorage.exists()) {
            sharedStorage.mkdirs()
            Log.d(TAG, "📁 创建共享下载目录: ${sharedStorage.absolutePath}")
        }

        Log.d(TAG, "📂 共享下载目录: ${sharedStorage.absolutePath}")
        return sharedStorage
    }

    /**
     * 获取文件的完整路径
     */
    fun getFilePath(fileName: String): String {
        return File(getDownloadDir(), fileName).absolutePath
    }

    /**
     * 检查文件是否存在且MD5正确
     */
    suspend fun checkFileExistsAndValid(fileName: String, expectedMd5: String): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(getDownloadDir(), fileName)
            if (!file.exists() || !file.isFile) {
                Log.d(TAG, "❌ 文件不存在: $fileName")
                return@withContext false
            }

            Log.d(TAG, "✓ 文件存在，检查MD5: $fileName (${file.length() / 1024}KB)")

            // 检查MD5
            val isValid = md5Validator.validate(file, expectedMd5)
            if (isValid) {
                Log.i(TAG, "✅ 文件校验通过: $fileName")
            } else {
                Log.w(TAG, "⚠️ 文件MD5校验失败: $fileName")
            }
            isValid
        }
    }

    /**
     * 检查磁盘可用空间（单位：字节）
     */
    fun getAvailableDiskSpace(): Long {
        return try {
            val stat = StatFs(getDownloadDir().absolutePath)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            Log.d(TAG, "💾 可用空间: ${available / 1024 / 1024}MB")
            available
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取磁盘空间失败", e)
            0L
        }
    }

    /**
     * 检查是否有足够的磁盘空间
     */
    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        val availableSpace = getAvailableDiskSpace()
        // 预留100MB空间
        val reservedSpace = 100 * 1024 * 1024L
        val hasSpace = availableSpace > (requiredBytes + reservedSpace)

        if (hasSpace) {
            Log.i(TAG, "✅ 磁盘空间充足: 需要${requiredBytes / 1024 / 1024}MB, 可用${availableSpace / 1024 / 1024}MB")
        } else {
            Log.e(TAG, "❌ 磁盘空间不足: 需要${requiredBytes / 1024 / 1024}MB, 可用${availableSpace / 1024 / 1024}MB")
        }

        return hasSpace
    }

    /**
     * 删除文件
     */
    suspend fun deleteFile(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(getDownloadDir(), fileName)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        }
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(fileName: String): Long {
        val file = File(getDownloadDir(), fileName)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * 清理所有下载文件
     */
    suspend fun clearAllDownloads() {
        withContext(Dispatchers.IO) {
            getDownloadDir().listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        }
    }

    /**
     * 清理临时下载文件（.downloading后缀）
     */
    suspend fun clearTempFiles() {
        withContext(Dispatchers.IO) {
            getDownloadDir().listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".downloading")) {
                    file.delete()
                }
            }
        }
    }
}
