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
     * 优先使用外部存储的应用私有目录，不需要额外权限
     */
    fun getDownloadDir(): File {
        // Android 10+ 使用应用私有目录，无需存储权限
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        } else {
            // 低版本也使用应用私有目录
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        }

        // 如果外部存储不可用，使用内部存储
        val downloadDir = dir ?: File(context.filesDir, "downloads")

        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
            Log.d(TAG, "📁 创建下载目录: ${downloadDir.absolutePath}")
        }

        Log.d(TAG, "📂 下载目录: ${downloadDir.absolutePath}")
        return downloadDir
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
