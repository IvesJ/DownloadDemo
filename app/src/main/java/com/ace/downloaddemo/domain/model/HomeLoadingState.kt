package com.ace.downloaddemo.domain.model

/**
 * 首页加载状态
 *
 * 用于描述首页素材加载的完整生命周期
 */
sealed class HomeLoadingState {
    /**
     * 初始化状态
     */
    object Initializing : HomeLoadingState()

    /**
     * 正在加载配置文件
     */
    object LoadingConfig : HomeLoadingState()

    /**
     * 配置加载失败
     * @param error 错误信息
     * @param canRetry 是否可重试
     */
    data class ConfigFailed(
        val error: String,
        val canRetry: Boolean = true
    ) : HomeLoadingState()

    /**
     * 正在下载首页资源
     * @param vehicleName 车型名称
     * @param progress 下载进度 (0.0 - 1.0)
     * @param currentFile 当前文件
     * @param completedFiles 已完成文件数
     * @param totalFiles 总文件数
     */
    data class DownloadingHomeResources(
        val vehicleName: String,
        val progress: Float = 0f,
        val currentFile: String = "",
        val completedFiles: Int = 0,
        val totalFiles: Int = 0
    ) : HomeLoadingState()

    /**
     * 首页资源下载失败
     * @param vehicleName 车型名称
     * @param error 错误信息
     */
    data class DownloadFailed(
        val vehicleName: String,
        val error: String
    ) : HomeLoadingState()

    /**
     * 首页就绪
     * @param vehicleName 车型名称
     */
    data class Ready(
        val vehicleName: String
    ) : HomeLoadingState()
}
