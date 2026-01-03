package com.ace.downloaddemo.data.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.ace.downloaddemo.data.model.FileInfo
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import com.ace.downloaddemo.service.AutoDownloadService
import com.ace.downloaddemo.service.DownloadState
import com.ace.downloaddemo.service.IDownloadProgressCallback
import com.ace.downloaddemo.service.IDownloadService
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载服务管理器
 *
 * 职责：
 * 1. 封装所有AIDL Service绑定逻辑
 * 2. 将AIDL回调转换为Flow供上层订阅
 * 3. 提供简洁的Repository接口
 * 4. 处理Service连接生命周期
 *
 * 优势：
 * - ViewModel不需要知道AIDL细节
 * - 易于单元测试（可以mock这个接口）
 * - 符合MVVM分层原则
 */
@Singleton
class DownloadServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DownloadServiceManager"
    }

    private val gson = Gson()

    // AIDL Service引用
    private var downloadService: IDownloadService? = null
    private var serviceBound = false

    // 服务连接状态
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    // 缓存所有Feature的状态Flow（每个Feature一个）
    private val featureStateFlows = mutableMapOf<Int, MutableStateFlow<FeatureDownloadState>>()

    /**
     * ServiceConnection回调
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloadService = IDownloadService.Stub.asInterface(service)
            serviceBound = true
            _isServiceConnected.value = true
            Log.i(TAG, "✅ 成功绑定下载服务 (AIDL)")

            // 注册AIDL回调
            try {
                downloadService?.registerCallback(downloadCallback)
                Log.i(TAG, "📞 已注册 AIDL 进度回调")
            } catch (e: RemoteException) {
                Log.e(TAG, "❌ 注册回调失败", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound = false
            _isServiceConnected.value = false
            Log.w(TAG, "⚠️ 下载服务断开连接")
        }
    }

    /**
     * AIDL进度回调实现
     * 接收Service的状态变化，分发到对应Feature的Flow
     */
    private val downloadCallback = object : IDownloadProgressCallback.Stub() {
        override fun onDownloadStateChanged(featureId: Int, state: DownloadState?) {
            if (state == null) return

            Log.d(TAG, "📡 AIDL 回调：Feature #$featureId 状态 -> ${state.stateType} (进度: ${(state.progress * 100).toInt()}%)")

            // 转换AIDL状态为Domain模型
            val featureState = convertAIDLState(state)

            // 更新对应Feature的Flow
            featureStateFlows.getOrPut(featureId) {
                MutableStateFlow(FeatureDownloadState.Idle)
            }.value = featureState
        }
    }

    /**
     * 绑定下载服务
     * 应在Application或首次使用时调用
     */
    fun bindService() {
        if (serviceBound) {
            Log.d(TAG, "服务已绑定，跳过")
            return
        }

        val intent = Intent(context, AutoDownloadService::class.java)
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        if (bound) {
            Log.i(TAG, "📞 正在绑定下载服务 (跨用户 AIDL)...")
        } else {
            Log.e(TAG, "❌ 绑定下载服务失败")
        }
    }

    /**
     * 解绑下载服务
     * 应在Application销毁时调用
     */
    fun unbindService() {
        if (!serviceBound) return

        try {
            downloadService?.unregisterCallback(downloadCallback)
            Log.d(TAG, "📴 已注销 AIDL 回调")
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 注销回调失败", e)
        }

        context.unbindService(serviceConnection)
        serviceBound = false
        _isServiceConnected.value = false
        Log.d(TAG, "🔌 已解绑下载服务")
    }

    /**
     * 获取指定Feature的下载状态Flow
     * ViewModel订阅此Flow即可实时获取状态更新
     */
    fun observeFeatureState(featureId: Int): StateFlow<FeatureDownloadState> {
        return featureStateFlows.getOrPut(featureId) {
            MutableStateFlow(FeatureDownloadState.Idle)
        }
    }

    /**
     * 查询Feature的当前状态（从Service缓存）
     * 用于服务绑定后的初始状态同步
     */
    suspend fun queryFeatureState(featureId: Int): FeatureDownloadState {
        return try {
            val state = downloadService?.getDownloadState(featureId)
            if (state != null) {
                Log.d(TAG, "📥 Feature #$featureId 当前状态: ${state.stateType}")
                convertAIDLState(state).also { featureState ->
                    // 更新到Flow
                    featureStateFlows.getOrPut(featureId) {
                        MutableStateFlow(FeatureDownloadState.Idle)
                    }.value = featureState
                }
            } else {
                FeatureDownloadState.Idle
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 查询状态失败: Feature #$featureId", e)
            FeatureDownloadState.Idle
        }
    }

    /**
     * 开始下载Feature
     */
    suspend fun startDownload(featureId: Int, files: List<FileInfo>): Result<Unit> {
        return try {
            val filesJson = gson.toJson(files)
            downloadService?.startDownload(featureId, filesJson)
            Log.i(TAG, "✅ 已通知服务开始下载 Feature #$featureId")
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 调用下载失败: Feature #$featureId", e)
            Result.failure(e)
        }
    }

    /**
     * 取消下载Feature
     */
    suspend fun cancelDownload(featureId: Int): Result<Unit> {
        return try {
            downloadService?.cancelDownload(featureId)
            Log.i(TAG, "✅ 已通知服务取消下载 Feature #$featureId")
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 取消下载失败: Feature #$featureId", e)
            Result.failure(e)
        }
    }

    /**
     * 重试下载Feature
     */
    suspend fun retryDownload(featureId: Int, files: List<FileInfo>): Result<Unit> {
        return try {
            val filesJson = gson.toJson(files)
            downloadService?.retryDownload(featureId, filesJson)
            Log.i(TAG, "✅ 已通知服务重试下载 Feature #$featureId")
            Result.success(Unit)
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 重试下载失败: Feature #$featureId", e)
            Result.failure(e)
        }
    }

    /**
     * 将AIDL DownloadState转换为Domain层的FeatureDownloadState
     */
    private fun convertAIDLState(state: DownloadState): FeatureDownloadState {
        return when (state.stateType) {
            DownloadState.STATE_IDLE -> FeatureDownloadState.Idle

            DownloadState.STATE_DOWNLOADING -> FeatureDownloadState.Downloading(
                progress = state.progress,
                currentFile = state.currentFile,
                completedFiles = state.completedFiles,
                totalFiles = state.totalFiles
            )

            DownloadState.STATE_COMPLETED -> FeatureDownloadState.Completed

            DownloadState.STATE_FAILED -> FeatureDownloadState.Failed(
                error = state.error,
                failedFile = state.failedFile
            )

            DownloadState.STATE_CANCELED -> FeatureDownloadState.Canceled

            else -> {
                Log.w(TAG, "⚠️ 未知状态类型: ${state.stateType}, 默认为Idle")
                FeatureDownloadState.Idle
            }
        }
    }
}
