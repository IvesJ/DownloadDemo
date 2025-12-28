package com.ace.downloaddemo.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ace.downloaddemo.core.storage.FileCleanupManager
import com.ace.downloaddemo.data.parser.ConfigParser
import com.ace.downloaddemo.domain.FeatureDownloadManager
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import com.ace.downloaddemo.ui.model.FeatureUIState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val configParser: ConfigParser,
    private val featureDownloadManager: FeatureDownloadManager,
    private val fileCleanupManager: FileCleanupManager
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadViewModel"
    }

    private val _featuresState = MutableStateFlow<List<FeatureUIState>>(emptyList())
    val featuresState: StateFlow<List<FeatureUIState>> = _featuresState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        Log.d(TAG, "🎬 ViewModel初始化")
        loadConfig()
    }

    /**
     * 加载配置文件
     */
    fun loadConfig() {
        Log.i(TAG, "📄 开始加载配置文件...")

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 尝试从data目录读取
                Log.d(TAG, "📂 解析配置文件: download.json")
                val config = configParser.parse("download.json")

                if (config == null) {
                    Log.e(TAG, "❌ 配置文件解析失败")
                    _errorMessage.value = "配置文件解析失败"
                    _isLoading.value = false
                    return@launch
                }

                val features = config.exhibitionInfos.flatMap { it.featureConfigs }
                Log.i(TAG, "✅ 配置文件解析成功，共 ${features.size} 个Feature")

                _featuresState.value = features.map { feature ->
                    val files = configParser.extractAllFiles(feature)
                    Log.d(TAG, "📦 Feature #${feature.id}: ${feature.mainTitle} (${files.size}个文件)")

                    // 检查是否已下载完成
                    val isDownloaded = featureDownloadManager.isFeatureDownloaded(feature.id, files)
                    val initialState = if (isDownloaded) {
                        Log.i(TAG, "✅ Feature #${feature.id} 已下载")
                        FeatureDownloadState.Completed
                    } else {
                        Log.d(TAG, "⏳ Feature #${feature.id} 未下载")
                        FeatureDownloadState.Idle
                    }

                    FeatureUIState(
                        id = feature.id,
                        title = feature.mainTitle,
                        subtitle = feature.subTitle,
                        downloadState = initialState,
                        files = files
                    )
                }

                Log.i(TAG, "🔗 开始监听各Feature下载状态...")
                // 监听每个Feature的下载状态
                features.forEach { feature ->
                    viewModelScope.launch {
                        featureDownloadManager.getFeatureState(feature.id).collect { state ->
                            updateFeatureState(feature.id, state)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载配置失败", e)
                e.printStackTrace()
                _errorMessage.value = "加载配置失败: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.d(TAG, "✓ 配置加载流程完成")
            }
        }
    }

    /**
     * 下载Feature
     */
    fun downloadFeature(featureId: Int) {
        Log.i(TAG, "👆 用户点击下载 Feature #$featureId")
        viewModelScope.launch {
            val feature = _featuresState.value.find { it.id == featureId }
            if (feature == null) {
                Log.e(TAG, "❌ 找不到 Feature #$featureId")
                return@launch
            }

            Log.i(TAG, "▶️ 启动下载: ${feature.title}")
            featureDownloadManager.downloadFeature(featureId, feature.files)
        }
    }

    /**
     * 重试下载Feature
     */
    fun retryFeature(featureId: Int) {
        Log.i(TAG, "🔄 用户点击重试 Feature #$featureId")
        viewModelScope.launch {
            val feature = _featuresState.value.find { it.id == featureId }
            if (feature == null) {
                Log.e(TAG, "❌ 找不到 Feature #$featureId")
                return@launch
            }

            Log.i(TAG, "🔁 重新启动下载: ${feature.title}")
            featureDownloadManager.retryFeature(featureId, feature.files)
        }
    }

    /**
     * 取消Feature下载
     */
    fun cancelFeature(featureId: Int) {
        Log.w(TAG, "🚫 用户取消下载 Feature #$featureId")
        viewModelScope.launch {
            featureDownloadManager.cancelFeature(featureId)
        }
    }

    /**
     * 打开Feature（仅在下载完成后）
     */
    fun openFeature(featureId: Int) {
        val feature = _featuresState.value.find { it.id == featureId } ?: return

        if (feature.downloadState is FeatureDownloadState.Completed) {
            Log.i(TAG, "📂 打开 Feature: ${feature.title}")
            // TODO: 实现打开Feature的逻辑
            _errorMessage.value = "打开 Feature: ${feature.title}"
        } else {
            Log.w(TAG, "⚠️ Feature未完成，无法打开: ${feature.title}")
        }
    }

    /**
     * 更新Feature的下载状态
     */
    private fun updateFeatureState(featureId: Int, state: FeatureDownloadState) {
        Log.d(TAG, "🔄 更新 Feature #$featureId 状态: ${state::class.simpleName}")
        _featuresState.value = _featuresState.value.map { feature ->
            if (feature.id == featureId) {
                feature.copy(downloadState = state)
            } else {
                feature
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 检查所有Feature的更新
     * 会重新加载配置文件，检查哪些文件需要更新
     */
    fun checkForUpdates() {
        Log.i(TAG, "🔄 检查配置更新...")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 1. 重新解析配置文件（模拟从云端获取最新配置）
                Log.d(TAG, "📄 重新加载配置文件: download.json")
                val config = configParser.parse("download.json")

                if (config == null) {
                    Log.e(TAG, "❌ 配置文件解析失败")
                    _errorMessage.value = "配置文件解析失败"
                    _isLoading.value = false
                    return@launch
                }

                val features = config.exhibitionInfos.flatMap { it.featureConfigs }
                Log.i(TAG, "✅ 配置文件解析成功，共 ${features.size} 个Feature")

                // 2. 检查每个Feature是否有更新
                var totalUpdates = 0
                _featuresState.value = features.map { feature ->
                    val files = configParser.extractAllFiles(feature)
                    val updateResult = featureDownloadManager.checkForUpdates(feature.id, files)

                    if (updateResult.hasUpdates()) {
                        totalUpdates++
                        Log.i(TAG, "🔄 Feature #${feature.id} 有更新: ${updateResult.filesToDownload.size} 个文件需要下载")
                    }

                    // 根据更新检查结果设置状态
                    val initialState = when {
                        updateResult.isComplete() -> {
                            Log.i(TAG, "✅ Feature #${feature.id} 已是最新版本")
                            FeatureDownloadState.Completed
                        }
                        updateResult.hasUpdates() -> {
                            Log.d(TAG, "⏳ Feature #${feature.id} 需要更新")
                            FeatureDownloadState.Idle
                        }
                        else -> FeatureDownloadState.Idle
                    }

                    FeatureUIState(
                        id = feature.id,
                        title = feature.mainTitle,
                        subtitle = feature.subTitle,
                        downloadState = initialState,
                        files = files
                    )
                }

                // 3. 清理不再需要的文件
                Log.i(TAG, "🧹 开始清理不再需要的文件...")
                val cleanupResult = fileCleanupManager.scanAndCleanUnusedFiles(config)

                Log.i(TAG, "🎉 更新检查完成")
                Log.i(TAG, "📊 共有 $totalUpdates 个Feature需要更新")

                if (cleanupResult.deletedFiles > 0) {
                    _errorMessage.value = "检查完成：${totalUpdates}个更新，清理${cleanupResult.deletedFiles}个文件，释放${cleanupResult.getFreedSpaceMB()}MB"
                } else if (totalUpdates > 0) {
                    _errorMessage.value = "检查完成：发现${totalUpdates}个Feature有更新"
                } else {
                    _errorMessage.value = "所有Feature均为最新版本"
                }

                // 4. 重新监听各Feature的下载状态
                Log.i(TAG, "🔗 重新监听各Feature下载状态...")
                features.forEach { feature ->
                    viewModelScope.launch {
                        featureDownloadManager.getFeatureState(feature.id).collect { state ->
                            updateFeatureState(feature.id, state)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 检查更新失败", e)
                e.printStackTrace()
                _errorMessage.value = "检查更新失败: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.d(TAG, "✓ 更新检查流程完成")
            }
        }
    }

    /**
     * 清理不再需要的文件（不检查更新，只清理）
     */
    fun cleanupUnusedFiles() {
        Log.i(TAG, "🧹 手动清理不再需要的文件...")
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val config = configParser.parse("download.json")
                if (config == null) {
                    _errorMessage.value = "无法加载配置文件"
                    return@launch
                }

                val result = fileCleanupManager.scanAndCleanUnusedFiles(config)

                if (result.deletedFiles > 0) {
                    _errorMessage.value = "清理完成：删除${result.deletedFiles}个文件，释放${result.getFreedSpaceMB()}MB空间"
                } else {
                    _errorMessage.value = "没有需要清理的文件"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 清理文件失败", e)
                _errorMessage.value = "清理失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 更新Feature（增量下载）
     */
    fun updateFeature(featureId: Int) {
        Log.i(TAG, "🔄 用户触发更新 Feature #$featureId")
        viewModelScope.launch {
            val feature = _featuresState.value.find { it.id == featureId }
            if (feature == null) {
                Log.e(TAG, "❌ 找不到 Feature #$featureId")
                return@launch
            }

            Log.i(TAG, "▶️ 启动增量更新: ${feature.title}")
            featureDownloadManager.updateFeature(featureId, feature.files)
        }
    }
}
