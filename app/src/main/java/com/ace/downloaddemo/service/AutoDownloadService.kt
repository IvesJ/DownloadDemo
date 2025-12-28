package com.ace.downloaddemo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ace.downloaddemo.R
import com.ace.downloaddemo.data.parser.ConfigParser
import com.ace.downloaddemo.domain.FeatureDownloadManager
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import com.ace.downloaddemo.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 自动下载服务
 * 用于在后台自动下载所有Feature的资源
 * 支持开机自启动
 */
@AndroidEntryPoint
class AutoDownloadService : Service() {

    @Inject
    lateinit var configParser: ConfigParser

    @Inject
    lateinit var featureDownloadManager: FeatureDownloadManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var notificationManager: NotificationManager
    private var currentFeatureId: Int = 0
    private var totalFeatures: Int = 0
    private var completedFeatures: Int = 0

    companion object {
        private const val TAG = "AutoDownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "auto_download_channel"
        private const val CHANNEL_NAME = "自动下载"

        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, AutoDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, AutoDownloadService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 自动下载服务创建")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification("准备开始下载...", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "🎬 自动下载服务启动")

        // 开始自动下载
        startAutoDownload()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🛑 自动下载服务销毁")
        serviceScope.cancel()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示自动下载进度"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(content: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("自动下载服务")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(content: String, progress: Int) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(content, progress))
    }

    /**
     * 开始自动下载
     */
    private fun startAutoDownload() {
        serviceScope.launch {
            try {
                // 1. 解析配置文件
                Log.i(TAG, "📄 开始解析配置文件")
                val config = configParser.parse("download.json")

                if (config == null) {
                    Log.e(TAG, "❌ 配置文件解析失败")
                    updateNotification("配置文件解析失败", 0)
                    stopSelf()
                    return@launch
                }

                // 2. 获取所有Feature
                val features = config.exhibitionInfos.flatMap { it.featureConfigs }
                totalFeatures = features.size
                completedFeatures = 0

                Log.i(TAG, "📦 共有 $totalFeatures 个Feature需要下载")
                updateNotification("开始下载，共 $totalFeatures 个Feature", 0)

                // 3. 逐个下载Feature
                for ((index, feature) in features.withIndex()) {
                    currentFeatureId = feature.id
                    val files = configParser.extractAllFiles(feature)

                    Log.i(TAG, "📥 开始下载 Feature ${feature.id}: ${feature.mainTitle} (${files.size}个文件)")
                    updateNotification(
                        "正在下载: ${feature.mainTitle} (${index + 1}/$totalFeatures)",
                        (completedFeatures * 100 / totalFeatures)
                    )

                    // 检查是否已下载
                    if (featureDownloadManager.isFeatureDownloaded(feature.id, files)) {
                        Log.i(TAG, "✅ Feature ${feature.id} 已下载，跳过")
                        completedFeatures++
                        continue
                    }

                    // 监听下载状态
                    var downloadCompleted = false
                    val stateJob = launch {
                        featureDownloadManager.getFeatureState(feature.id).collectLatest { state ->
                            when (state) {
                                is FeatureDownloadState.Downloading -> {
                                    val progress = (state.progress * 100).toInt()
                                    val overallProgress = ((completedFeatures + state.progress) * 100 / totalFeatures).toInt()
                                    updateNotification(
                                        "${feature.mainTitle}: $progress% (${state.completedFiles}/${state.totalFiles})",
                                        overallProgress
                                    )
                                }
                                is FeatureDownloadState.Completed -> {
                                    Log.i(TAG, "✅ Feature ${feature.id} 下载完成")
                                    completedFeatures++
                                    downloadCompleted = true
                                }
                                is FeatureDownloadState.Failed -> {
                                    Log.e(TAG, "❌ Feature ${feature.id} 下载失败: ${state.error}")
                                    updateNotification(
                                        "下载失败: ${feature.mainTitle} - ${state.error}",
                                        (completedFeatures * 100 / totalFeatures)
                                    )
                                    // 可以选择重试或跳过
                                    downloadCompleted = true
                                }
                                else -> {}
                            }
                        }
                    }

                    // 开始下载
                    featureDownloadManager.downloadFeature(feature.id, files)

                    // 等待下载完成
                    while (!downloadCompleted) {
                        kotlinx.coroutines.delay(500)
                    }

                    stateJob.cancel()
                }

                // 4. 全部完成
                Log.i(TAG, "🎉 所有Feature下载完成")
                updateNotification("下载完成，共 $totalFeatures 个Feature", 100)

                // 延迟3秒后停止服务
                kotlinx.coroutines.delay(3000)
                stopSelf()

            } catch (e: Exception) {
                Log.e(TAG, "❌ 自动下载出错", e)
                updateNotification("下载出错: ${e.message}", 0)
                stopSelf()
            }
        }
    }
}
