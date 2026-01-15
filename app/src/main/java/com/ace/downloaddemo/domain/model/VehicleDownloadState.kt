package com.ace.downloaddemo.domain.model

/**
 * 首页资源下载状态
 * 用于管理用户选择车型后首页资源的下载流程
 */
sealed class VehicleDownloadState {
    /**
     * 车型已选择，尚未开始下载首页资源
     */
    data class Selected(val vehicleName: String) : VehicleDownloadState()

    /**
     * 正在下载首页资源
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
    ) : VehicleDownloadState()

    /**
     * 首页资源下载完成，UI 可以显示
     */
    data class Ready(val vehicleName: String) : VehicleDownloadState()

    /**
     * 首页资源下载失败
     * @param error 错误信息
     * @param failedFile 失败的文件名
     */
    data class Failed(
        val error: String,
        val failedFile: String = ""
    ) : VehicleDownloadState()
}
