package com.ace.downloaddemo.core

/**
 * 模拟配置类
 * 用于控制是否启用模拟模式
 *
 * 由于download.json中的URL和MD5都是mock数据，无法真实下载和校验
 * 在开发和测试阶段，可以启用模拟模式来演示功能
 *
 * TODO: 生产环境请将所有模拟开关设置为 false
 */
object MockConfig {

    /**
     * 模拟下载开关
     * true: 模拟下载过程，不进行真实的网络请求
     * false: 使用真实的HTTP下载
     */
    const val MOCK_DOWNLOAD_MODE = true

    /**
     * 模拟MD5校验开关
     * true: 跳过真实的MD5计算，只检查文件是否存在
     * false: 计算文件真实MD5并与预期值比对
     */
    const val MOCK_MD5_VALIDATION = true

    /**
     * 模拟下载速度（毫秒/块）
     * 每下载一个数据块的延迟时间，用于模拟网络速度
     */
    const val MOCK_DOWNLOAD_DELAY_MS = 100L

    /**
     * 模拟下载块大小（字节）
     * 每次模拟下载的数据块大小
     */
    const val MOCK_CHUNK_SIZE = 100 * 1024L // 100KB
}
