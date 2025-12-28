package com.ace.downloaddemo.domain.model

sealed class FeatureDownloadState {
    /**
     * 未开始下载
     */
    object Idle : FeatureDownloadState()

    /**
     * 下载中
     * @param progress 进度 0.0 ~ 1.0
     * @param currentFile 当前下载的文件名
     * @param completedFiles 已完成文件数
     * @param totalFiles 总文件数
     */
    data class Downloading(
        val progress: Float,
        val currentFile: String = "",
        val completedFiles: Int = 0,
        val totalFiles: Int = 0
    ) : FeatureDownloadState()

    /**
     * 下载完成
     */
    object Completed : FeatureDownloadState()

    /**
     * 下载失败
     * @param error 错误信息
     * @param failedFile 失败的文件名
     */
    data class Failed(
        val error: String,
        val failedFile: String = ""
    ) : FeatureDownloadState()

    /**
     * 已取消
     */
    object Canceled : FeatureDownloadState()
}
