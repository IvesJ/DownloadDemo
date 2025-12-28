package com.ace.downloaddemo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ace.downloaddemo.core.AutoDownloadConfig
import com.ace.downloaddemo.service.AutoDownloadService

/**
 * 开机广播接收器
 * 监听系统开机完成事件，自动启动下载服务
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "📱 系统开机完成")

                // 检查是否启用开机自启动
                val config = AutoDownloadConfig(context)
                if (config.autoStartOnBoot) {
                    Log.i(TAG, "✅ 开机自启动已启用，启动自动下载服务")
                    AutoDownloadService.start(context)
                } else {
                    Log.i(TAG, "⚠️ 开机自启动已禁用，跳过")
                }
            }
        }
    }
}
