package com.ace.downloaddemo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 下载状态实体
 * 用于跨用户共享下载状态信息
 */
@Entity(tableName = "download_states")
data class DownloadStateEntity(
    @PrimaryKey
    val featureId: Int,

    /**
     * 状态类型：idle, downloading, completed, failed, canceled
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
    val failedFile: String = "",

    /**
     * 最后更新时间戳
     */
    val lastUpdatedTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATE_IDLE = "idle"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELED = "canceled"
    }
}
