package com.ace.downloaddemo.core.download

import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadWorker @Inject constructor(
    private val downloader: FileDownloader
) {
    // 最大并发下载数
    private val maxConcurrency = 3
    private val semaphore = Semaphore(maxConcurrency)

    /**
     * 下载文件（带并发控制）
     */
    suspend fun downloadFile(task: DownloadTask): DownloadResult {
        semaphore.acquire()
        return try {
            downloader.download(
                url = task.url,
                savePath = task.savePath,
                md5 = task.md5,
                onProgress = task.onProgress
            )
        } finally {
            semaphore.release()
        }
    }

    /**
     * 暂停下载
     */
    suspend fun pause(url: String) {
        downloader.pause(url)
    }

    /**
     * 取消下载
     */
    suspend fun cancel(url: String) {
        downloader.cancel(url)
    }

    /**
     * 取消所有下载
     */
    suspend fun cancelAll() {
        downloader.cancelAll()
    }
}
