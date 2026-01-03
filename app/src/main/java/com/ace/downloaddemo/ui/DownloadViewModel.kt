package com.ace.downloaddemo.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ace.downloaddemo.core.storage.FileCleanupManager
import com.ace.downloaddemo.data.parser.ConfigParser
import com.ace.downloaddemo.data.service.DownloadServiceManager
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import com.ace.downloaddemo.ui.model.FeatureUIState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configParser: ConfigParser,
    private val fileCleanupManager: FileCleanupManager,
    private val downloadServiceManager: DownloadServiceManager  // 注入ServiceManager
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

        // 监听服务连接状态，连接成功后查询所有Feature状态
        viewModelScope.launch {
            downloadServiceManager.isServiceConnected.collect { isConnected ->
                if (isConnected) {
                    Log.i(TAG, "✅ 下载服务已连接，查询所有Feature状态")
                    queryAllStatesFromService()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 注意：不在这里解绑Service，因为它是Singleton，应该在Application层管理生命周期
        Log.d(TAG, "🎬 ViewModel销毁")
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

                    FeatureUIState(
                        id = feature.id,
                        title = feature.mainTitle,
                        subtitle = feature.subTitle,
                        downloadState = FeatureDownloadState.Idle,  // 初始状态，稍后通过Manager查询
                        files = files
                    )
                }

                // 为每个Feature启动状态监听
                features.forEach { feature ->
                    viewModelScope.launch {
                        downloadServiceManager.observeFeatureState(feature.id).collect { state ->
                            updateFeatureState(feature.id, state)
                        }
                    }
                }

                Log.i(TAG, "✅ 配置加载完成，等待服务连接后查询状态")

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
     * 从服务查询所有 Feature 的当前状态
     */
    private suspend fun queryAllStatesFromService() = withContext(Dispatchers.IO) {
        val currentFeatures = _featuresState.value
        Log.d(TAG, "🔍 查询 ${currentFeatures.size} 个 Feature 的状态")

        currentFeatures.forEach { feature ->
            downloadServiceManager.queryFeatureState(feature.id)
            // 状态会通过observeFeatureState的Flow自动更新到UI
        }
    }

    /**
     * 下载Feature
     */
    fun downloadFeature(featureId: Int) {
        Log.i(TAG, "👆 用户点击下载 Feature #$featureId")

        val feature = _featuresState.value.find { it.id == featureId }
        if (feature == null) {
            Log.e(TAG, "❌ 找不到 Feature #$featureId")
            return
        }

        viewModelScope.launch {
            val result = downloadServiceManager.startDownload(featureId, feature.files)
            result.onSuccess {
                Log.i(TAG, "✅ 已通知服务开始下载: ${feature.title}")
            }.onFailure { error ->
                Log.e(TAG, "❌ 启动下载失败: ${feature.title}", error)
                _errorMessage.value = "启动下载失败: ${error.message}"
            }
        }
    }

    /**
     * 重试下载Feature
     */
    fun retryFeature(featureId: Int) {
        Log.i(TAG, "🔄 用户点击重试 Feature #$featureId")

        val feature = _featuresState.value.find { it.id == featureId }
        if (feature == null) {
            Log.e(TAG, "❌ 找不到 Feature #$featureId")
            return
        }

        viewModelScope.launch {
            val result = downloadServiceManager.retryDownload(featureId, feature.files)
            result.onSuccess {
                Log.i(TAG, "✅ 已通知服务重试下载: ${feature.title}")
            }.onFailure { error ->
                Log.e(TAG, "❌ 重试下载失败: ${feature.title}", error)
                _errorMessage.value = "重试下载失败: ${error.message}"
            }
        }
    }

    /**
     * 取消Feature下载
     */
    fun cancelFeature(featureId: Int) {
        Log.w(TAG, "🚫 用户取消下载 Feature #$featureId")

        viewModelScope.launch {
            val result = downloadServiceManager.cancelDownload(featureId)
            result.onSuccess {
                Log.i(TAG, "✅ 已通知服务取消下载")
            }.onFailure { error ->
                Log.e(TAG, "❌ 取消下载失败", error)
            }
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

                // 2. 更新UI状态（通过AIDL查询实际状态）
                _featuresState.value = features.map { feature ->
                    val files = configParser.extractAllFiles(feature)

                    FeatureUIState(
                        id = feature.id,
                        title = feature.mainTitle,
                        subtitle = feature.subTitle,
                        downloadState = FeatureDownloadState.Idle,  // 初始状态，稍后通过 AIDL 查询
                        files = files
                    )
                }

                // 3. 从服务查询所有Feature的当前状态
                queryAllStatesFromService()

                // 4. 清理不再需要的文件
                Log.i(TAG, "🧹 开始清理不再需要的文件...")
                val cleanupResult = fileCleanupManager.scanAndCleanUnusedFiles(config)

                Log.i(TAG, "🎉 更新检查完成")

                if (cleanupResult.deletedFiles > 0) {
                    _errorMessage.value = "检查完成，清理${cleanupResult.deletedFiles}个文件，释放${cleanupResult.getFreedSpaceMB()}MB"
                } else {
                    _errorMessage.value = "检查完成，所有配置已更新"
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
     * 通过Manager调用服务进行下载
     */
    fun updateFeature(featureId: Int) {
        Log.i(TAG, "🔄 用户触发更新 Feature #$featureId")

        val feature = _featuresState.value.find { it.id == featureId }
        if (feature == null) {
            Log.e(TAG, "❌ 找不到 Feature #$featureId")
            return
        }

        viewModelScope.launch {
            Log.i(TAG, "▶️ 启动增量更新: ${feature.title}")
            val result = downloadServiceManager.startDownload(featureId, feature.files)
            result.onSuccess {
                Log.i(TAG, "✅ 已通知服务更新: ${feature.title}")
            }.onFailure { error ->
                Log.e(TAG, "❌ 启动更新失败: ${feature.title}", error)
                _errorMessage.value = "启动更新失败: ${error.message}"
            }
        }
    }
}
