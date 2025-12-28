package com.ace.downloaddemo.core.download

interface FileDownloader {
    /**
     * 下载文件
     * @param url 下载URL
     * @param savePath 保存路径
     * @param md5 文件MD5（用于校验，可选）
     * @param onProgress 进度回调
     * @return 下载结果
     */
    suspend fun download(
        url: String,
        savePath: String,
        md5: String = "",
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): DownloadResult

    /**
     * 暂停下载
     */
    suspend fun pause(url: String)

    /**
     * 取消下载
     */
    suspend fun cancel(url: String)

    /**
     * 取消所有下载
     */
    suspend fun cancelAll()
}
