package com.ace.downloaddemo.service

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 下载状态数据类（用于 AIDL 传输）
 *
 * 通过 AIDL 在 Service 和客户端之间传递下载状态
 */
@Parcelize
data class DownloadState(
    /**
     * 状态类型: idle, downloading, completed, failed, canceled
     */
    val stateType: String,

    /**
     * 下载进度 (0.0 - 1.0)
     */
    val progress: Float = 0f,

    /**
     * 当前下载的文件名
     */
    val currentFile: String = "",

    /**
     * 已完成的文件数
     */
    val completedFiles: Int = 0,

    /**
     * 总文件数
     */
    val totalFiles: Int = 0,

    /**
     * 错误信息（失败时）
     */
    val error: String = "",

    /**
     * 失败的文件名（失败时）
     */
    val failedFile: String = ""
) : Parcelable {
    companion object {
        const val STATE_IDLE = "idle"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELED = "canceled"
    }
}
