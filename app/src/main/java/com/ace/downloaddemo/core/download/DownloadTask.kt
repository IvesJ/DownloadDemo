package com.ace.downloaddemo.core.download

data class DownloadTask(
    val url: String,
    val savePath: String,
    val md5: String,
    val config: DownloadConfig = DownloadConfig.DEFAULT,
    val onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
)
