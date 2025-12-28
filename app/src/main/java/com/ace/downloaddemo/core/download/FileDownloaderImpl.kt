package com.ace.downloaddemo.core.download

import com.ace.downloaddemo.core.MockConfig
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
class FileDownloaderImpl @Inject constructor() : FileDownloader {

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
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 检查是否已取消
            if (canceledUrls.contains(url)) {
                canceledUrls.remove(url)
                return@withContext DownloadResult.Canceled
            }

            // ==================== 模拟下载模式 ====================
            // 由于download.json中的URL和MD5都是mock数据，无法真实下载
            // 这里模拟下载过程，但保留所有逻辑检查
            // 配置开关: MockConfig.MOCK_DOWNLOAD_MODE
            if (MockConfig.MOCK_DOWNLOAD_MODE) {
                return@withContext mockDownload(url, savePath, onProgress)
            }
            // ========================================================

            val file = File(savePath)
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

        // 模拟文件大小（1-5MB随机）
        val totalSize = (1 * 1024 * 1024L) + (Math.random() * 4 * 1024 * 1024).toLong()

        println("🔵 [模拟下载] 开始下载: ${file.name}, 总大小: ${totalSize / 1024}KB")

        // 检查已下载的大小（支持断点续传模拟）
        var currentDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        if (currentDownloaded > 0) {
            println("🟡 [断点续传] 已下载: ${currentDownloaded / 1024}KB, 继续下载...")
        }

        // 如果已经完成，直接返回成功
        if (currentDownloaded >= totalSize) {
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
            println("✅ [模拟下载] 已完成: ${file.name}")
            return DownloadResult.Success(savePath)
        }

        // 模拟分块下载
        FileOutputStream(tempFile, true).use { output ->
            while (currentDownloaded < totalSize) {
                // 检查是否被取消
                if (canceledUrls.contains(url)) {
                    canceledUrls.remove(url)
                    return DownloadResult.Canceled
                }

                // 模拟网络延迟（可配置）
                delay(MockConfig.MOCK_DOWNLOAD_DELAY_MS)

                // 写入模拟数据
                val bytesToWrite = minOf(MockConfig.MOCK_CHUNK_SIZE, totalSize - currentDownloaded).toInt()
                val mockData = ByteArray(bytesToWrite) { 0 }
                output.write(mockData)

                currentDownloaded += bytesToWrite

                // 回调进度
                onProgress(currentDownloaded, totalSize)
            }
        }

        // 下载完成，重命名临时文件
        if (tempFile.exists()) {
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
        }

        println("✅ [模拟下载] 完成: ${file.name}, 总大小: ${currentDownloaded / 1024}KB")
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
