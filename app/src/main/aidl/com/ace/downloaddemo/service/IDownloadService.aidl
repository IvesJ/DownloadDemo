package com.ace.downloaddemo.service;

import com.ace.downloaddemo.service.IDownloadProgressCallback;
import com.ace.downloaddemo.service.DownloadState;

/**
 * 下载服务 AIDL 接口
 * 提供跨用户的下载控制和状态查询
 */
interface IDownloadService {
    /**
     * 注册进度回调监听器
     * @param callback 回调接口
     */
    void registerCallback(IDownloadProgressCallback callback);

    /**
     * 注销进度回调监听器
     * @param callback 回调接口
     */
    void unregisterCallback(IDownloadProgressCallback callback);

    /**
     * 获取指定 Feature 的当前下载状态
     * @param featureId Feature ID
     * @return 下载状态，如果不存在返回 null
     */
    DownloadState getDownloadState(int featureId);

    /**
     * 开始下载 Feature
     * @param featureId Feature ID
     * @param filesJson 文件列表 JSON（为了简化传参，使用 JSON 字符串）
     */
    void startDownload(int featureId, String filesJson);

    /**
     * 取消下载
     * @param featureId Feature ID
     */
    void cancelDownload(int featureId);

    /**
     * 重试下载
     * @param featureId Feature ID
     * @param filesJson 文件列表 JSON
     */
    void retryDownload(int featureId, String filesJson);
}
