package com.ace.downloaddemo.domain

import android.content.Context
import android.util.Log
import com.ace.downloaddemo.core.download.DownloadConfig
import com.ace.downloaddemo.core.download.DownloadResult
import com.ace.downloaddemo.core.download.DownloadTask
import com.ace.downloaddemo.core.download.DownloadWorker
import com.ace.downloaddemo.core.storage.FileManager
import com.ace.downloaddemo.core.validation.MD5Validator
import com.ace.downloaddemo.data.local.DownloadDao
import com.ace.downloaddemo.data.model.FileInfo
import com.ace.downloaddemo.data.provider.DownloadStateProvider
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadWorker: DownloadWorker,
    private val fileManager: FileManager,
    private val md5Validator: MD5Validator,
    private val downloadDao: DownloadDao
) {

    companion object {
        private const val TAG = "FeatureDownloadMgr"
    }

    // 存储每个Feature的下载状态
    private val featureStates = ConcurrentHashMap<Int, MutableStateFlow<FeatureDownloadState>>()

    /**
     * 更新Feature状态（同时更新内存和数据库）
     */
    private suspend fun updateFeatureState(featureId: Int, state: FeatureDownloadState) {
        // 更新内存状态
        val stateFlow = featureStates.getOrPut(featureId) {
            MutableStateFlow(FeatureDownloadState.Idle)
        }
        stateFlow.value = state

        // 持久化到数据库
        val entity = when (state) {
            is FeatureDownloadState.Idle -> {
                com.ace.downloaddemo.data.local.DownloadStateEntity(
                    featureId = featureId,
                    stateType = com.ace.downloaddemo.data.local.DownloadStateEntity.STATE_IDLE
                )
            }
            is FeatureDownloadState.Downloading -> {
                com.ace.downloaddemo.data.local.DownloadStateEntity(
                    featureId = featureId,
                    stateType = com.ace.downloaddemo.data.local.DownloadStateEntity.STATE_DOWNLOADING,
                    progress = state.progress,
                    currentFile = state.currentFile,
                    completedFiles = state.completedFiles,
                    totalFiles = state.totalFiles
                )
            }
            is FeatureDownloadState.Completed -> {
                com.ace.downloaddemo.data.local.DownloadStateEntity(
                    featureId = featureId,
                    stateType = com.ace.downloaddemo.data.local.DownloadStateEntity.STATE_COMPLETED,
                    progress = 1.0f
                )
            }
            is FeatureDownloadState.Failed -> {
                com.ace.downloaddemo.data.local.DownloadStateEntity(
                    featureId = featureId,
                    stateType = com.ace.downloaddemo.data.local.DownloadStateEntity.STATE_FAILED,
                    error = state.error,
                    failedFile = state.failedFile
                )
            }
            is FeatureDownloadState.Canceled -> {
                com.ace.downloaddemo.data.local.DownloadStateEntity(
                    featureId = featureId,
                    stateType = com.ace.downloaddemo.data.local.DownloadStateEntity.STATE_CANCELED
                )
            }
        }
        downloadDao.insertOrUpdateState(entity)

        // 通过 ContentProvider 通知所有观察者（跨用户）
        context.contentResolver.notifyChange(
            DownloadStateProvider.CONTENT_URI,
            null
        )
        Log.d(TAG, "📡 通知 ContentProvider 数据已更新: Feature #$featureId")
    }

    /**
     * 获取Feature的下载状态Flow
     */
    fun getFeatureState(featureId: Int): StateFlow<FeatureDownloadState> {
        return featureStates.getOrPut(featureId) {
            MutableStateFlow(FeatureDownloadState.Idle)
        }.asStateFlow()
    }

    /**
     * 下载Feature的所有文件
     */
    suspend fun downloadFeature(featureId: Int, files: List<FileInfo>) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🚀 开始下载 Feature #$featureId")
        Log.i(TAG, "📦 文件总数: ${files.size}")
        Log.i(TAG, "════════════════════════════════════════")

        val stateFlow = featureStates.getOrPut(featureId) {
            MutableStateFlow(FeatureDownloadState.Idle)
        }

        updateFeatureState(featureId, FeatureDownloadState.Downloading(
            progress = 0f,
            currentFile = "",
            completedFiles = 0,
            totalFiles = files.size
        ))

        val totalFiles = files.size
        var completedFiles = 0

        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "📥 开始逐个下载文件...")

        for ((index, file) in files.withIndex()) {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "📄 [${index + 1}/$totalFiles] ${file.fileName}")

            // 更新状态：开始下载当前文件
            Log.i(TAG, "📥 开始下载: ${file.fileName}")
            updateFeatureState(featureId, FeatureDownloadState.Downloading(
                progress = completedFiles.toFloat() / totalFiles,
                currentFile = file.fileName,
                completedFiles = completedFiles,
                totalFiles = totalFiles
            ))

            // 下载文件
            val filePath = fileManager.getFilePath(file.fileName)
            val result = downloadWorker.downloadFile(
                DownloadTask(
                    url = file.fileResUrl,
                    savePath = filePath,
                    md5 = file.fileMd5,
                    onProgress = { downloaded, total ->
                        val fileProgress = if (total > 0) {
                            downloaded.toFloat() / total
                        } else {
                            0f
                        }
                        val totalProgress = (completedFiles + fileProgress) / totalFiles

                        stateFlow.value = FeatureDownloadState.Downloading(
                            progress = totalProgress,
                            currentFile = file.fileName,
                            completedFiles = completedFiles,
                            totalFiles = totalFiles
                        )
                    }
                )
            )

        // 处理下载结果
        when (result) {
            is DownloadResult.Success -> {
                completedFiles++
                Log.i(TAG, "✅ 文件下载成功: ${file.fileName}")
                Log.i(TAG, "📊 进度: $completedFiles/$totalFiles (${completedFiles * 100 / totalFiles}%)")
            }

            is DownloadResult.Failed -> {
                Log.e(TAG, "❌ 下载失败: ${file.fileName}")
                Log.e(TAG, "💥 错误: ${result.error}")
                Log.e(TAG, "💔 Feature #$featureId 下载失败")
                updateFeatureState(featureId, FeatureDownloadState.Failed(
                    error = result.error,
                    failedFile = file.fileName
                ))
                return
            }

            is DownloadResult.Canceled -> {
                Log.w(TAG, "⚠️ 下载已取消: ${file.fileName}")
                Log.w(TAG, "🚫 Feature #$featureId 下载取消")
                updateFeatureState(featureId, FeatureDownloadState.Canceled)
                return
            }
        }
        }

        // 所有文件下载完成
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🎉 Feature #$featureId 所有文件下载完成！")
        Log.i(TAG, "✅ 共完成 $completedFiles 个文件")
        Log.i(TAG, "════════════════════════════════════════")
        updateFeatureState(featureId, FeatureDownloadState.Completed)
    }

    /**
     * 重试下载Feature
     */
    suspend fun retryFeature(featureId: Int, files: List<FileInfo>) {
        Log.i(TAG, "🔄 重试下载 Feature #$featureId")
        downloadFeature(featureId, files)
    }

    /**
     * 取消Feature下载
     */
    suspend fun cancelFeature(featureId: Int) {
        Log.w(TAG, "🚫 取消下载 Feature #$featureId")
        updateFeatureState(featureId, FeatureDownloadState.Canceled)
        downloadWorker.cancelAll()
    }

    /**
     * 重置Feature状态
     */
    suspend fun resetFeatureState(featureId: Int) {
        Log.d(TAG, "🔄 重置 Feature #$featureId 状态")
        updateFeatureState(featureId, FeatureDownloadState.Idle)
    }

    /**
     * 检查Feature是否已完成下载
     */
    suspend fun isFeatureDownloaded(featureId: Int, files: List<FileInfo>): Boolean {
        Log.d(TAG, "🔍 检查 Feature #$featureId 是否已下载 (${files.size}个文件)")
        val isDownloaded = files.all { file ->
            fileManager.checkFileExistsAndValid(file.fileName, file.fileMd5)
        }
        if (isDownloaded) {
            Log.i(TAG, "✅ Feature #$featureId 已全部下载")
        } else {
            Log.d(TAG, "❌ Feature #$featureId 未完全下载")
        }
        return isDownloaded
    }

    /**
     * 检查Feature是否有更新
     * 返回需要下载/更新的文件列表
     */
    suspend fun checkForUpdates(featureId: Int, files: List<FileInfo>): UpdateCheckResult {
        Log.i(TAG, "🔄 检查 Feature #$featureId 是否有更新...")

        val filesToDownload = mutableListOf<FileInfo>()
        val filesToDelete = mutableListOf<String>()
        val upToDateFiles = mutableListOf<String>()

        for (file in files) {
            val localFile = File(fileManager.getFilePath(file.fileName))

            if (!localFile.exists()) {
                // 文件不存在，需要下载
                Log.d(TAG, "📥 需要下载: ${file.fileName} (文件不存在)")
                filesToDownload.add(file)
            } else {
                // 文件存在，检查MD5是否匹配
                if (md5Validator.validate(localFile, file.fileMd5)) {
                    // MD5匹配，文件是最新的
                    Log.d(TAG, "✅ 文件已是最新: ${file.fileName}")
                    upToDateFiles.add(file.fileName)
                } else {
                    // MD5不匹配，需要重新下载
                    Log.d(TAG, "🔄 需要更新: ${file.fileName} (MD5不匹配)")
                    filesToDelete.add(file.fileName)
                    filesToDownload.add(file)
                }
            }
        }

        val result = UpdateCheckResult(
            featureId = featureId,
            totalFiles = files.size,
            upToDateFiles = upToDateFiles.size,
            filesToDownload = filesToDownload,
            filesToDelete = filesToDelete
        )

        Log.i(TAG, "📊 检查结果 Feature #$featureId:")
        Log.i(TAG, "   ✅ 已是最新: ${result.upToDateFiles} 个")
        Log.i(TAG, "   📥 需要下载: ${result.filesToDownload.size} 个")
        Log.i(TAG, "   🗑️ 需要删除: ${result.filesToDelete.size} 个")

        return result
    }

    /**
     * 增量更新Feature
     * 只下载需要更新的文件
     */
    suspend fun updateFeature(featureId: Int, files: List<FileInfo>) {
        Log.i(TAG, "🔄 开始增量更新 Feature #$featureId")

        // 1. 检查哪些文件需要更新
        val updateResult = checkForUpdates(featureId, files)

        // 2. 如果所有文件都是最新的，无需更新
        if (updateResult.filesToDownload.isEmpty()) {
            Log.i(TAG, "✅ Feature #$featureId 所有文件已是最新，无需更新")
            updateFeatureState(featureId, FeatureDownloadState.Completed)
            return
        }

        // 3. 删除需要重新下载的旧文件
        for (fileName in updateResult.filesToDelete) {
            Log.d(TAG, "🗑️ 删除旧文件: $fileName")
            fileManager.deleteFile(fileName)
        }

        // 4. 下载需要更新的文件
        Log.i(TAG, "📥 开始下载 ${updateResult.filesToDownload.size} 个需要更新的文件")
        downloadFeature(featureId, updateResult.filesToDownload)
    }
}

/**
 * 更新检查结果
 */
data class UpdateCheckResult(
    val featureId: Int,
    val totalFiles: Int,
    val upToDateFiles: Int,
    val filesToDownload: List<FileInfo>,
    val filesToDelete: List<String>
) {
    fun hasUpdates(): Boolean = filesToDownload.isNotEmpty()
    fun isComplete(): Boolean = upToDateFiles == totalFiles
}
