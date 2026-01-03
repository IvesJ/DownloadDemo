package com.ace.downloaddemo.core.download

/**
 * 下载配置
 * 用于控制下载前后的校验行为
 */
data class DownloadConfig(
    /**
     * 是否在下载前检查文件是否已存在且有效
     * 默认：true（跳过已存在的有效文件）
     */
    val checkExistingFile: Boolean = true,

    /**
     * 是否在下载前检查磁盘空间
     * 默认：true
     */
    val checkDiskSpace: Boolean = false,

    /**
     * 是否在下载后验证MD5
     * 默认：true
     */
    val validateMd5AfterDownload: Boolean = true,

    /**
     * 是否强制重新下载（即使文件已存在且有效）
     * 默认：false
     */
    val forceRedownload: Boolean = false,

    /**
     * MD5校验失败时是否删除文件
     * 默认：true（删除无效文件）
     */
    val deleteFileOnMD5Failure: Boolean = true,

    /**
     * 预留磁盘空间（字节）
     * 在检查磁盘空间时会预留这部分空间
     * 默认：100MB
     */
    val reservedDiskSpace: Long = 100 * 1024 * 1024L
) {
    companion object {
        /**
         * 默认配置（启用所有检查）
         */
        val DEFAULT = DownloadConfig()

        /**
         * 跳过所有检查（仅用于测试或特殊场景）
         */
        val NO_VALIDATION = DownloadConfig(
            checkExistingFile = false,
            checkDiskSpace = false,
            validateMd5AfterDownload = false
        )

        /**
         * 强制重新下载（即使文件已存在）
         */
        val FORCE_REDOWNLOAD = DownloadConfig(
            forceRedownload = true
        )
    }
}
