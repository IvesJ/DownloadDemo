package com.ace.downloaddemo.core.storage

import android.util.Log
import com.ace.downloaddemo.data.model.DownloadConfig
import com.ace.downloaddemo.data.model.FileInfo
import com.ace.downloaddemo.data.parser.ConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件清理管理器
 * 负责扫描和清理不再需要的已下载文件
 */
@Singleton
class FileCleanupManager @Inject constructor(
    private val fileManager: FileManager,
    private val configParser: ConfigParser
) {

    companion object {
        private const val TAG = "FileCleanupManager"
    }

    /**
     * 扫描并清理所有不再需要的文件
     */
    suspend fun scanAndCleanUnusedFiles(config: DownloadConfig): CleanupResult {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🧹 开始扫描和清理不再需要的文件...")

        return withContext(Dispatchers.IO) {
            // 1. 获取所有配置中需要的文件名
            val requiredFiles = getAllRequiredFiles(config)
            Log.i(TAG, "📋 配置文件中共需要 ${requiredFiles.size} 个文件")

            // 2. 获取本地已下载的所有文件
            val downloadDir = fileManager.getDownloadDir()
            val localFiles = downloadDir.listFiles()?.filter { it.isFile } ?: emptyList()
            Log.i(TAG, "📂 本地存储中共有 ${localFiles.size} 个文件")

            // 3. 找出不再需要的文件（孤儿文件）
            val unusedFiles = localFiles.filter { file ->
                val fileName = file.name
                // 排除临时下载文件
                if (fileName.endsWith(".downloading")) {
                    false
                } else {
                    !requiredFiles.contains(fileName)
                }
            }

            Log.i(TAG, "🗑️ 发现 ${unusedFiles.size} 个不再需要的文件")

            // 4. 删除不再需要的文件
            var deletedCount = 0
            var freedSpace = 0L

            for (file in unusedFiles) {
                val fileSize = file.length()
                Log.d(TAG, "🗑️ 删除文件: ${file.name} (${fileSize / 1024}KB)")

                if (file.delete()) {
                    deletedCount++
                    freedSpace += fileSize
                    Log.i(TAG, "✅ 已删除: ${file.name}")
                } else {
                    Log.e(TAG, "❌ 删除失败: ${file.name}")
                }
            }

            val result = CleanupResult(
                totalFiles = localFiles.size,
                deletedFiles = deletedCount,
                freedSpaceBytes = freedSpace,
                deletedFileNames = unusedFiles.map { it.name }
            )

            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "🎉 清理完成！")
            Log.i(TAG, "📊 删除文件: $deletedCount 个")
            Log.i(TAG, "💾 释放空间: ${freedSpace / 1024 / 1024}MB")
            Log.i(TAG, "════════════════════════════════════════")

            result
        }
    }

    /**
     * 获取配置中所有需要的文件名集合
     */
    private fun getAllRequiredFiles(config: DownloadConfig): Set<String> {
        val requiredFiles = mutableSetOf<String>()

        config.exhibitionInfos.forEach { exhibition ->
            exhibition.featureConfigs.forEach { feature ->
                // 提取该feature的所有文件
                val files = configParser.extractAllFiles(feature)
                files.forEach { fileInfo ->
                    requiredFiles.add(fileInfo.fileName)
                }
                Log.d(TAG, "📦 Feature #${feature.id}: ${feature.mainTitle} 需要 ${files.size} 个文件")
            }
        }

        return requiredFiles
    }

    /**
     * 清理临时下载文件（.downloading后缀）
     */
    suspend fun cleanTempFiles(): Int {
        Log.i(TAG, "🧹 清理临时下载文件...")

        return withContext(Dispatchers.IO) {
            val downloadDir = fileManager.getDownloadDir()
            val tempFiles = downloadDir.listFiles()?.filter {
                it.isFile && it.name.endsWith(".downloading")
            } ?: emptyList()

            Log.i(TAG, "🗑️ 发现 ${tempFiles.size} 个临时文件")

            var deletedCount = 0
            for (file in tempFiles) {
                Log.d(TAG, "🗑️ 删除临时文件: ${file.name}")
                if (file.delete()) {
                    deletedCount++
                    Log.i(TAG, "✅ 已删除: ${file.name}")
                }
            }

            Log.i(TAG, "✅ 清理临时文件完成，共删除 $deletedCount 个")
            deletedCount
        }
    }

    /**
     * 获取可以清理的文件列表（不执行删除）
     */
    suspend fun getUnusedFiles(config: DownloadConfig): List<UnusedFileInfo> {
        return withContext(Dispatchers.IO) {
            val requiredFiles = getAllRequiredFiles(config)
            val downloadDir = fileManager.getDownloadDir()
            val localFiles = downloadDir.listFiles()?.filter { it.isFile } ?: emptyList()

            localFiles.filter { file ->
                val fileName = file.name
                !fileName.endsWith(".downloading") && !requiredFiles.contains(fileName)
            }.map { file ->
                UnusedFileInfo(
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }
    }
}

/**
 * 清理结果
 */
data class CleanupResult(
    val totalFiles: Int,
    val deletedFiles: Int,
    val freedSpaceBytes: Long,
    val deletedFileNames: List<String>
) {
    fun getFreedSpaceMB(): Long = freedSpaceBytes / 1024 / 1024
}

/**
 * 不再使用的文件信息
 */
data class UnusedFileInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val lastModified: Long
) {
    fun getSizeMB(): Long = fileSizeBytes / 1024 / 1024
}
