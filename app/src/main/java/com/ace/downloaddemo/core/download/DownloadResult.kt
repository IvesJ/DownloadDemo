package com.ace.downloaddemo.core.download

sealed class DownloadResult {
    data class Success(val path: String) : DownloadResult()
    data class Failed(val error: String, val throwable: Throwable? = null) : DownloadResult()
    object Canceled : DownloadResult()
}
