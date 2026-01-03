package com.ace.downloaddemo.service;

import com.ace.downloaddemo.service.DownloadState;

/**
 * 下载进度回调接口
 * 客户端实现此接口接收实时下载进度
 */
interface IDownloadProgressCallback {
    /**
     * 下载状态变化回调
     * @param featureId Feature ID
     * @param state 下载状态
     */
    void onDownloadStateChanged(int featureId, in DownloadState state);
}
