package com.ace.downloaddemo.core.download

import android.util.Log
import com.ace.downloaddemo.core.MockConfig
import com.ace.downloaddemo.core.storage.FileManager
import com.ace.downloaddemo.core.validation.MD5Validator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileDownloaderImpl @Inject constructor(
    private val fileManager: FileManager,
    private val md5Validator: MD5Validator
) : FileDownloader {

    companion object {
        private const val TAG = "FileDownloaderImpl"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 存储取消状态的Map
    private val canceledUrls = ConcurrentHashMap.newKeySet<String>()

    override suspend fun download(
        url: String,
        savePath: String,
        md5: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DownloadResult {
        return downloadWithConfig(url, savePath, md5, DownloadConfig.DEFAULT, onProgress)
    }

    override suspend fun downloadWithConfig(
        url: String,
        savePath: String,
        md5: String,
        config: DownloadConfig,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 检查是否已取消
            if (canceledUrls.contains(url)) {
                canceledUrls.remove(url)
                return@withContext DownloadResult.Canceled
            }

            val file = File(savePath)
            val fileName = file.name

            // ========== 步骤1: 检查文件是否已存在且有效 ==========
            if (config.checkExistingFile && !config.forceRedownload) {
                if (fileManager.checkFileExistsAndValid(fileName, md5)) {
                    Log.i(TAG, "⏩ 文件已存在且有效，跳过下载: $fileName")
                    return@withContext DownloadResult.Success(savePath)
                }
            }

            // ========== 步骤2: 检查磁盘空间 ==========
            if (config.checkDiskSpace) {
                // 使用文件已有大小或默认估算10MB
                val estimatedSize = if (file.exists()) file.length() else 10 * 1024 * 1024L
                val availableSpace = fileManager.getAvailableDiskSpace()
                val requiredSpace = estimatedSize + config.reservedDiskSpace

                if (availableSpace < requiredSpace) {
                    val errorMsg = "磁盘空间不足: 需要${requiredSpace / 1024 / 1024}MB, 可用${availableSpace / 1024 / 1024}MB"
                    Log.e(TAG, "❌ $errorMsg")
                    return@withContext DownloadResult.Failed(errorMsg)
                }
            }

            // ========== 步骤3: 执行下载 ==========

            // ==================== 模拟下载模式 ====================
            // 由于download.json中的URL和MD5都是mock数据，无法真实下载
            // 这里模拟下载过程，但保留所有逻辑检查
            // 配置开关: MockConfig.MOCK_DOWNLOAD_MODE
            if (MockConfig.MOCK_DOWNLOAD_MODE) {
                val mockResult = mockDownload(url, savePath, onProgress)
                // 如果mock下载失败或取消，直接返回
                if (mockResult !is DownloadResult.Success) {
                    return@withContext mockResult
                }
                // 如果成功，继续执行MD5校验（步骤4）
            } else {
            val tempFile = File("$savePath.downloading")

            // 确保父目录存在
            file.parentFile?.mkdirs()

            // 检查已下载的大小
            val downloaded = if (tempFile.exists()) tempFile.length() else 0L

            // 构建请求，支持断点续传
            val request = Request.Builder()
                .url(url)
                .apply {
                    if (downloaded > 0) {
                        header("Range", "bytes=$downloaded-")
                    }
                }
                .build()

            // 执行下载
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext DownloadResult.Failed(
                    "HTTP ${response.code}: ${response.message}"
                )
            }

            // 获取内容长度
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
            val totalSize = if (downloaded > 0 && response.code == 206) {
                // 206 Partial Content - 服务器支持断点续传
                downloaded + contentLength
            } else {
                // 200 OK - 服务器不支持断点续传或从头开始
                if (downloaded > 0 && response.code == 200) {
                    // 服务器不支持断点续传，删除临时文件重新下载
                    tempFile.delete()
                }
                contentLength
            }

            // 写入文件
            val inputStream = response.body?.byteStream()
                ?: return@withContext DownloadResult.Failed("Response body is null")

            FileOutputStream(tempFile, downloaded > 0 && response.code == 206).use { output ->
                inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0
                    var currentDownloaded = downloaded

                    while (isActive && input.read(buffer).also { bytesRead = it } != -1) {
                        // 检查是否被取消
                        if (canceledUrls.contains(url)) {
                            canceledUrls.remove(url)
                            return@withContext DownloadResult.Canceled
                        }

                        output.write(buffer, 0, bytesRead)
                        currentDownloaded += bytesRead

                        // 回调进度
                        onProgress(currentDownloaded, totalSize)
                    }
                }
            }

            // 检查是否完全下载
            if (!isActive || canceledUrls.contains(url)) {
                canceledUrls.remove(url)
                return@withContext DownloadResult.Canceled
            }

            // 下载完成，重命名临时文件
            if (tempFile.exists()) {
                if (file.exists()) {
                    file.delete()
                }
                tempFile.renameTo(file)
            }
            }
            // ========================================================

            // ========== 步骤4: MD5校验 ==========
            if (config.validateMd5AfterDownload && md5.isNotEmpty()) {
                Log.d(TAG, "🔐 开始MD5校验: $fileName")

                if (md5Validator.validate(file, md5)) {
                    Log.i(TAG, "✅ MD5校验通过: $fileName")
                    return@withContext DownloadResult.Success(savePath)
                } else {
                    val actualMd5 = md5Validator.calculateMD5(file)
                    val errorMsg = "MD5校验失败: 期望$md5, 实际$actualMd5"
                    Log.e(TAG, "❌ $errorMsg: $fileName")

                    // 根据配置决定是否删除文件
                    if (config.deleteFileOnMD5Failure) {
                        file.delete()
                        Log.d(TAG, "🗑️ 已删除校验失败的文件: $fileName")
                    }

                    return@withContext DownloadResult.Failed(errorMsg)
                }
            }

            // 如果不需要MD5校验，直接返回成功
            DownloadResult.Success(savePath)

        } catch (e: CancellationException) {
            DownloadResult.Canceled
        } catch (e: Exception) {
            e.printStackTrace()
            DownloadResult.Failed(e.message ?: "Download failed", e)
        }
    }

    /**
     * 模拟下载过程
     * TODO: 这是模拟方法，生产环境请删除此方法并设置 MockConfig.MOCK_DOWNLOAD_MODE = false
     */
    private suspend fun mockDownload(
        url: String,
        savePath: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DownloadResult {
        val file = File(savePath)
        val tempFile = File("$savePath.downloading")

        // 确保父目录存在
        file.parentFile?.mkdirs()

        // 判断是否是首页资源下载（URL包含 home）
        val isHomeResource = url.contains("home")

        // 使用固定大小的模拟文件，方便测试
        val totalSize = MockConfig.MOCK_FILE_SIZE

        // 根据类型选择不同的下载速度
        val delayMs = if (isHomeResource) MockConfig.MOCK_HOME_RESOURCE_DELAY_MS else MockConfig.MOCK_DOWNLOAD_DELAY_MS
        val chunkSize = if (isHomeResource) MockConfig.MOCK_HOME_RESOURCE_CHUNK_SIZE else MockConfig.MOCK_CHUNK_SIZE

        Log.i(TAG, "🔵 [模拟下载] 开始下载: ${file.name}, 总大小: ${totalSize / 1024}KB, 类型: ${if (isHomeResource) "首页资源" else "Feature文件"}")

        // 检查已下载的大小（支持断点续传模拟）
        var currentDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        if (currentDownloaded > 0) {
            Log.i(TAG, "🟡 [断点续传] 已下载: ${currentDownloaded / 1024}KB, 继续下载...")
        }

        // 如果已经完成，直接返回成功
        if (currentDownloaded >= totalSize) {
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
            Log.i(TAG, "✅ [模拟下载] 已完成: ${file.name}")
            return DownloadResult.Success(savePath)
        }

        // 模拟分块下载
        FileOutputStream(tempFile, true).use { output ->
            while (currentDownloaded < totalSize) {
                // 检查是否被取消
                if (canceledUrls.contains(url)) {
                    canceledUrls.remove(url)
                    Log.w(TAG, "⚠️ [模拟下载] 已取消: ${file.name}")
                    return DownloadResult.Canceled
                }

                // 模拟网络延迟（可配置）
                delay(delayMs)

                // 写入模拟数据
                val bytesToWrite = minOf(chunkSize, totalSize - currentDownloaded).toInt()
                val mockData = ByteArray(bytesToWrite) { 0 }
                output.write(mockData)

                currentDownloaded += bytesToWrite

                // 回调进度
                val progress = (currentDownloaded * 100 / totalSize).toInt()
                onProgress(currentDownloaded, totalSize)

                // 每20%打印一次日志
                if (progress % 20 == 0) {
                    Log.d(TAG, "📊 [模拟下载] ${file.name}: $progress% (${currentDownloaded / 1024}KB / ${totalSize / 1024}KB)")
                }
            }
        }

        // 下载完成，重命名临时文件
        if (tempFile.exists()) {
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
        }

        Log.i(TAG, "✅ [模拟下载] 完成: ${file.name}, 总大小: ${currentDownloaded / 1024}KB")
        return DownloadResult.Success(savePath)
    }

    override suspend fun pause(url: String) {
        // 暂停等同于取消，但保留临时文件
        canceledUrls.add(url)
    }

    override suspend fun cancel(url: String) {
        canceledUrls.add(url)
    }

    override suspend fun cancelAll() {
        // 这里可以遍历所有正在进行的下载并取消
        // 为简化实现，暂时不维护下载列表
    }
}
