package com.ace.downloaddemo.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ace.downloaddemo.core.storage.FileCleanupManager
import com.ace.downloaddemo.data.model.DownloadConfig
import com.ace.downloaddemo.data.model.ExhibitionInfo
import com.ace.downloaddemo.data.model.FeatureConfig
import com.ace.downloaddemo.data.parser.ConfigParser
import com.ace.downloaddemo.data.service.DownloadServiceManager
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import com.ace.downloaddemo.domain.model.HomeLoadingState
import com.ace.downloaddemo.domain.model.VehicleDownloadState
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
    private val downloadServiceManager: DownloadServiceManager,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadViewModel"

        // SharedPreferences keys
        private const val KEY_LAST_SELECTED_VEHICLE = "last_selected_vehicle"
        private const val KEY_LAST_SELECTED_VEHICLE_INDEX = "last_selected_vehicle_index"
        private const val KEY_DEFAULT_VEHICLE_HOME_READY = "default_vehicle_home_ready"
    }

    private val _featuresState = MutableStateFlow<List<FeatureUIState>>(emptyList())
    val featuresState: StateFlow<List<FeatureUIState>> = _featuresState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Vehicle 相关状态
    private val _vehicles = MutableStateFlow<List<String>>(emptyList())
    val vehicles: StateFlow<List<String>> = _vehicles.asStateFlow()

    private val _vehicleDownloadState = MutableStateFlow<VehicleDownloadState?>(null)
    val vehicleDownloadState: StateFlow<VehicleDownloadState?> = _vehicleDownloadState.asStateFlow()

    // 默认车型首页下载完成提示
    private val _defaultVehicleHomeReady = MutableStateFlow(false)
    val defaultVehicleHomeReady: StateFlow<Boolean> = _defaultVehicleHomeReady.asStateFlow()

    // 首页加载状态
    private val _homeLoadingState = MutableStateFlow<HomeLoadingState>(HomeLoadingState.Initializing)
    val homeLoadingState: StateFlow<HomeLoadingState> = _homeLoadingState.asStateFlow()

    // 内部缓存
    private var cachedConfig: DownloadConfig? = null
    private var selectedExhibitionInfo: ExhibitionInfo? = null
    private var lastSelectedVehicleIndex = -1
    private var isDefaultVehicleHomeReadyChecked = false

    init {
        Log.d(TAG, "🎬 ViewModel初始化")

        startHomeLoadingFlow()

        // 监听服务连接状态
        viewModelScope.launch {
            downloadServiceManager.isServiceConnected.collect { isConnected ->
                if (isConnected) {
                    Log.i(TAG, "✅ 下载服务已连接")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🎬 ViewModel销毁")
    }

    /**
     * 加载配置文件
     * 1. 先联网请求最新配置
     * 2. 更新 cachedConfig
     * 3. 提取 vehicle 列表
     * 4. 检查上次选择车型的首页状态
     */
    fun loadConfig() {
        Log.i(TAG, "📄 开始加载配置...")

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 步骤1: 联网请求最新配置
                Log.d(TAG, "🌐 联网请求最新配置...")
                val remoteConfig = fetchRemoteConfig()

                // 步骤2: 缓存配置到本地
                if (remoteConfig != null) {
                    saveConfigToLocal(remoteConfig)
                    cachedConfig = remoteConfig
                    Log.i(TAG, "✅ 远程配置获取成功并已缓存")
                } else {
                    // 联网失败，尝试使用本地缓存
                    Log.w(TAG, "⚠️ 联网获取配置失败，使用本地缓存")
                    val localConfig = configParser.parse("download.json")
                    if (localConfig != null) {
                        cachedConfig = localConfig
                    } else {
                        Log.e(TAG, "❌ 本地配置也不存在")
                        _errorMessage.value = "无法加载配置"
                        _isLoading.value = false
                        return@launch
                    }
                }

                // 步骤3: 提取 vehicle 列表
                val vehicleList = cachedConfig!!.exhibitionInfos.mapNotNull { it.vehicle }
                Log.i(TAG, "✅ 配置加载成功，发现 ${vehicleList.size} 个车型: $vehicleList")
                _vehicles.value = vehicleList

                // 步骤4: 读取上次选择的车型
                lastSelectedVehicleIndex = sharedPreferences.getInt(KEY_LAST_SELECTED_VEHICLE_INDEX, 0)
                val lastVehicleName = sharedPreferences.getString(KEY_LAST_SELECTED_VEHICLE, null)
                Log.i(TAG, "📌 上次选择的车型: $lastVehicleName (index: $lastSelectedVehicleIndex)")

                // 步骤5: 检查上次选择车型的首页下载状态
                if (vehicleList.isNotEmpty()) {
                    checkLastVehicleHomeStatus(vehicleList)
                }

                Log.i(TAG, "✅ 配置加载完成")

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
     * 检查上次选择车型的首页下载状态
     */
    private fun checkLastVehicleHomeStatus(vehicleList: List<String>) {
        val lastIndex = lastSelectedVehicleIndex.coerceIn(0, vehicleList.size - 1)
        val vehicleName = vehicleList[lastIndex]

        // 检查该车型是否已下载首页
        viewModelScope.launch {
            downloadServiceManager.isServiceConnected.collect { isConnected ->
                if (isConnected && cachedConfig != null) {
                    val exhibitionInfo = cachedConfig!!.exhibitionInfos[lastIndex]
                    val homeResources = configParser.extractHomeResources(exhibitionInfo)

                    if (homeResources.isEmpty()) {
                        // 没有首页资源，直接展示 features
                        Log.i(TAG, "✅ 车型 $vehicleName 无首页资源，直接展示 features")
                        _vehicleDownloadState.value = VehicleDownloadState.Ready(vehicleName)
                        exposeFeaturesToUI(exhibitionInfo)
                    } else {
                        // 检查首页资源是否已下载
                        val homeFeatureId = -(exhibitionInfo.hashCode() % 10000)

                        // 查询首页资源状态
                        downloadServiceManager.queryFeatureState(homeFeatureId)

                        // 监听首页资源状态
                        launch {
                            downloadServiceManager.observeFeatureState(homeFeatureId).collect { state ->
                                when (state) {
                                    is FeatureDownloadState.Idle,
                                    is FeatureDownloadState.Canceled -> {
                                        // 首页未下载，需要启动下载
                                        Log.i(TAG, "📥 车型 $vehicleName 首页未下载，开始下载")
                                        _vehicleDownloadState.value = VehicleDownloadState.Downloading(
                                            progress = 0f,
                                            currentFile = "",
                                            completedFiles = 0,
                                            totalFiles = homeResources.size
                                        )
                                        downloadHomeResources(exhibitionInfo)
                                    }
                                    is FeatureDownloadState.Downloading -> {
                                        // 首页正在下载，显示 loading
                                        Log.i(TAG, "📥 车型 $vehicleName 首页正在下载")
                                        _vehicleDownloadState.value = VehicleDownloadState.Downloading(
                                            progress = state.progress,
                                            currentFile = state.currentFile,
                                            completedFiles = state.completedFiles,
                                            totalFiles = state.totalFiles
                                        )
                                    }
                                    is FeatureDownloadState.Completed -> {
                                        // 首页已下载，展示 features
                                        Log.i(TAG, "✅ 车型 $vehicleName 首页已下载完成")
                                        _vehicleDownloadState.value = VehicleDownloadState.Ready(vehicleName)
                                        exposeFeaturesToUI(exhibitionInfo)
                                    }
                                    is FeatureDownloadState.Failed -> {
                                        // 首页下载失败，显示错误状态
                                        Log.e(TAG, "❌ 车型 $vehicleName 首页下载失败: ${state.error}")
                                        _vehicleDownloadState.value = VehicleDownloadState.Failed(
                                            error = state.error,
                                            failedFile = state.failedFile
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== 首页加载流程相关方法 ====================

    /**
     * 统一首页加载入口
     * 流程：
     * 1. 加载配置
     * 2. 确定目标车型（上次选择或第一个）
     * 3. 检查是否需要下载首页资源
     * 4. 启动下载或直接就绪
     */
    private fun startHomeLoadingFlow() {
        Log.i(TAG, "🎬 开始首页加载流程")
        viewModelScope.launch {
            _homeLoadingState.value = HomeLoadingState.LoadingConfig

            // 加载配置
            val configResult = loadConfigWithResult()

            configResult.onSuccess { config ->
                // 配置加载成功
                cachedConfig = config

                // 提取 vehicle 列表
                val vehicleList = config.exhibitionInfos.mapNotNull { it.vehicle }
                Log.i(TAG, "✅ 配置加载成功，发现 ${vehicleList.size} 个车型: $vehicleList")
                _vehicles.value = vehicleList

                if (vehicleList.isEmpty()) {
                    _homeLoadingState.value = HomeLoadingState.ConfigFailed("没有可用的车型配置")
                    return@launch
                }

                // 确定目标车型（上次选择或第一个）
                lastSelectedVehicleIndex = sharedPreferences.getInt(KEY_LAST_SELECTED_VEHICLE_INDEX, 0)
                    .coerceIn(0, vehicleList.size - 1)
                val vehicleName = vehicleList[lastSelectedVehicleIndex]

                Log.i(TAG, "🎯 目标车型: $vehicleName (index: $lastSelectedVehicleIndex)")

                // 保存车型选择
                sharedPreferences.edit()
                    .putInt(KEY_LAST_SELECTED_VEHICLE_INDEX, lastSelectedVehicleIndex)
                    .putString(KEY_LAST_SELECTED_VEHICLE, vehicleName)
                    .apply()

                val exhibitionInfo = config.exhibitionInfos[lastSelectedVehicleIndex]
                selectedExhibitionInfo = exhibitionInfo

                // 提取首页资源
                val homeResources = configParser.extractHomeResources(exhibitionInfo)

                if (homeResources.isEmpty()) {
                    // 没有首页资源，直接就绪
                    Log.i(TAG, "✅ 无首页资源，直接就绪")
                    _homeLoadingState.value = HomeLoadingState.Ready(vehicleName)
                    exposeFeaturesToUI(exhibitionInfo)
                } else {
                    // 需要下载首页资源
                    Log.i(TAG, "📥 需要下载首页资源 (${homeResources.size} 个文件)")
                    downloadHomeResourcesForFlow(exhibitionInfo, homeResources)
                }

            }.onFailure { error ->
                // 配置加载失败
                Log.e(TAG, "❌ 配置加载失败: ${error.message}", error)
                _homeLoadingState.value = HomeLoadingState.ConfigFailed(
                    error = error.message ?: "加载配置失败",
                    canRetry = true
                )
            }
        }
    }

    /**
     * 加载配置文件（带返回值）
     * 1. 先联网请求最新配置
     * 2. 更新 cachedConfig
     * 3. 返回 Result<DownloadConfig>
     */
    private suspend fun loadConfigWithResult(): Result<DownloadConfig> {
        return withContext(Dispatchers.IO) {
            try {
                // 步骤1: 联网请求最新配置
                Log.d(TAG, "🌐 联网请求最新配置...")
                val remoteConfig = fetchRemoteConfig()

                // 步骤2: 缓存配置到本地
                if (remoteConfig != null) {
                    saveConfigToLocal(remoteConfig)
                    Log.i(TAG, "✅ 远程配置获取成功并已缓存")
                    Result.success(remoteConfig)
                } else {
                    // 联网失败，尝试使用本地缓存
                    Log.w(TAG, "⚠️ 联网获取配置失败，使用本地缓存")
                    val localConfig = configParser.parse("download.json")
                    if (localConfig != null) {
                        Log.i(TAG, "✅ 本地配置加载成功")
                        Result.success(localConfig)
                    } else {
                        Log.e(TAG, "❌ 本地配置也不存在")
                        Result.failure(Exception("无法加载配置文件"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载配置失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 下载首页资源（带状态流）
     * @param exhibitionInfo 展览信息
     * @param homeResources 首页资源文件列表
     */
    private suspend fun downloadHomeResourcesForFlow(
        exhibitionInfo: ExhibitionInfo,
        homeResources: List<com.ace.downloaddemo.data.model.FileInfo>
    ) {
        val vehicleName = exhibitionInfo.vehicle ?: "未命名"

        Log.i(TAG, "📥 开始下载首页资源: $vehicleName")

        // 更新状态为下载中
        _homeLoadingState.value = HomeLoadingState.DownloadingHomeResources(
            vehicleName = vehicleName,
            progress = 0f,
            currentFile = "",
            completedFiles = 0,
            totalFiles = homeResources.size
        )

        // 使用负数 featureId 区分首页资源下载
        val homeFeatureId = -(exhibitionInfo.hashCode() % 10000)

        // 监听首页资源下载状态
        val downloadJob = viewModelScope.launch {
            downloadServiceManager.observeFeatureState(homeFeatureId).collect { state ->
                when (state) {
                    is FeatureDownloadState.Downloading -> {
                        _homeLoadingState.value = HomeLoadingState.DownloadingHomeResources(
                            vehicleName = vehicleName,
                            progress = state.progress,
                            currentFile = state.currentFile,
                            completedFiles = state.completedFiles,
                            totalFiles = state.totalFiles
                        )
                    }
                    is FeatureDownloadState.Completed -> {
                        Log.i(TAG, "✅ 首页资源下载完成")
                        _homeLoadingState.value = HomeLoadingState.Ready(vehicleName)
                        exposeFeaturesToUI(exhibitionInfo)
                    }
                    is FeatureDownloadState.Failed -> {
                        Log.e(TAG, "❌ 首页资源下载失败: ${state.error}")
                        _homeLoadingState.value = HomeLoadingState.DownloadFailed(
                            vehicleName = vehicleName,
                            error = state.error
                        )
                    }
                    is FeatureDownloadState.Canceled -> {
                        Log.w(TAG, "⚠️ 首页资源下载已取消")
                        _homeLoadingState.value = HomeLoadingState.DownloadFailed(
                            vehicleName = vehicleName,
                            error = "下载已取消"
                        )
                    }
                    else -> {}
                }
            }
        }

        // 启动下载
        val result = downloadServiceManager.startDownload(homeFeatureId, homeResources)
        result.onSuccess {
            Log.i(TAG, "✅ 已通知服务开始下载首页资源")
        }.onFailure { error ->
            Log.e(TAG, "❌ 启动首页资源下载失败", error)
            _homeLoadingState.value = HomeLoadingState.DownloadFailed(
                vehicleName = vehicleName,
                error = error.message ?: "未知错误"
            )
            downloadJob.cancel()
        }
    }

    /**
     * 重试首页加载
     * 根据当前状态决定重试策略
     */
    fun retryHomeLoadingFlow() {
        Log.i(TAG, "🔄 用户点击重试首页加载")

        val currentState = _homeLoadingState.value
        when (currentState) {
            is HomeLoadingState.ConfigFailed -> {
                // 配置加载失败，重新开始整个流程
                Log.i(TAG, "🔄 重新开始配置加载")
                startHomeLoadingFlow()
            }
            is HomeLoadingState.DownloadFailed -> {
                // 下载失败，重新下载首页资源
                Log.i(TAG, "🔄 重新下载首页资源")
                val exhibitionInfo = selectedExhibitionInfo
                if (exhibitionInfo != null) {
                    val homeResources = configParser.extractHomeResources(exhibitionInfo)
                    if (homeResources.isNotEmpty()) {
                        viewModelScope.launch {
                            downloadHomeResourcesForFlow(exhibitionInfo, homeResources)
                        }
                    }
                }
            }
            else -> {
                Log.w(TAG, "⚠️ 当前状态无需重试: ${currentState::class.simpleName}")
            }
        }
    }

    // ==================== 原有方法 ====================

    /**
     * 用户选择车型
     */
    fun onVehicleSelected(position: Int) {
        Log.i(TAG, "🚗 用户选择车型: position=$position")

        viewModelScope.launch {
            val config = cachedConfig
            if (config == null) {
                Log.e(TAG, "❌ 配置未加载")
                _errorMessage.value = "配置未加载"
                return@launch
            }

            if (position >= config.exhibitionInfos.size) {
                Log.e(TAG, "❌ 无效的车型索引: $position")
                return@launch
            }

            val exhibitionInfo = config.exhibitionInfos[position]
            selectedExhibitionInfo = exhibitionInfo

            val vehicleName = exhibitionInfo.vehicle ?: "未命名车型"
            Log.i(TAG, "✅ 选中车型: $vehicleName")

            // 保存选择的车型
            sharedPreferences.edit()
                .putInt(KEY_LAST_SELECTED_VEHICLE_INDEX, position)
                .putString(KEY_LAST_SELECTED_VEHICLE, vehicleName)
                .apply()
            lastSelectedVehicleIndex = position

            // 开始下载首页资源
            downloadHomeResources(exhibitionInfo)
        }
    }

    /**
     * 下载首页资源
     */
    private suspend fun downloadHomeResources(exhibitionInfo: ExhibitionInfo) {
        val vehicleName = exhibitionInfo.vehicle ?: "未命名"

        Log.i(TAG, "📥 开始下载首页资源: $vehicleName")

        // 提取首页资源文件
        val homeResources = configParser.extractHomeResources(exhibitionInfo)

        if (homeResources.isEmpty()) {
            Log.i(TAG, "✅ 无需下载首页资源，直接展示 features")
            _vehicleDownloadState.value = VehicleDownloadState.Ready(vehicleName)
            exposeFeaturesToUI(exhibitionInfo)
            return
        }

        Log.i(TAG, "📦 首页资源包含 ${homeResources.size} 个文件")

        // 更新状态为下载中
        _vehicleDownloadState.value = VehicleDownloadState.Downloading(
            progress = 0f,
            currentFile = "",
            completedFiles = 0,
            totalFiles = homeResources.size
        )

        // 使用负数 featureId 区分首页资源下载
        val homeFeatureId = -(exhibitionInfo.hashCode() % 10000)

        // 监听首页资源下载状态
        val downloadJob = viewModelScope.launch {
            downloadServiceManager.observeFeatureState(homeFeatureId).collect { state ->
                when (state) {
                    is FeatureDownloadState.Downloading -> {
                        _vehicleDownloadState.value = VehicleDownloadState.Downloading(
                            progress = state.progress,
                            currentFile = state.currentFile,
                            completedFiles = state.completedFiles,
                            totalFiles = state.totalFiles
                        )
                    }
                    is FeatureDownloadState.Completed -> {
                        Log.i(TAG, "✅ 首页资源下载完成")
                        _vehicleDownloadState.value = VehicleDownloadState.Ready(vehicleName)
                        exposeFeaturesToUI(exhibitionInfo)
                    }
                    is FeatureDownloadState.Failed -> {
                        Log.e(TAG, "❌ 首页资源下载失败: ${state.error}")
                        _vehicleDownloadState.value = VehicleDownloadState.Failed(
                            error = state.error,
                            failedFile = state.failedFile
                        )
                    }
                    is FeatureDownloadState.Canceled -> {
                        Log.w(TAG, "⚠️ 首页资源下载已取消")
                        _vehicleDownloadState.value = VehicleDownloadState.Selected(vehicleName)
                    }
                    else -> {}
                }
            }
        }

        // 启动下载
        val result = downloadServiceManager.startDownload(homeFeatureId, homeResources)
        result.onSuccess {
            Log.i(TAG, "✅ 已通知服务开始下载首页资源")
        }.onFailure { error ->
            Log.e(TAG, "❌ 启动首页资源下载失败", error)
            _vehicleDownloadState.value = VehicleDownloadState.Failed(
                error = error.message ?: "未知错误"
            )
            downloadJob.cancel()
        }
    }

    /**
     * 首页资源下载完成后，将 features 暴露给 UI
     */
    private fun exposeFeaturesToUI(exhibitionInfo: ExhibitionInfo) {
        val features = exhibitionInfo.featureConfigs

        Log.i(TAG, "📋 首页资源就绪，展示 ${features.size} 个 Feature")

        viewModelScope.launch {
            _featuresState.value = features.map { feature ->
                val files = configParser.extractAllFiles(feature)
                Log.d(TAG, "📦 Feature #${feature.id}: ${feature.mainTitle} (${files.size}个文件)")

                FeatureUIState(
                    id = feature.id,
                    title = feature.mainTitle,
                    subtitle = feature.subTitle,
                    downloadState = FeatureDownloadState.Idle,
                    files = files
                )
            }

            // 为每个 Feature 启动状态监听
            features.forEach { feature ->
                launch {
                    downloadServiceManager.observeFeatureState(feature.id).collect { state ->
                        updateFeatureState(feature.id, state)
                    }
                }
            }

            // 查询初始状态
            queryAllStatesFromService()

            // 开始监听默认车型首页下载状态
            monitorDefaultVehicleHomeReady()
        }
    }

    /**
     * 监听默认车型首页下载状态
     * 如果默认车型首页下载完成，显示提示
     */
    private fun monitorDefaultVehicleHomeReady() {
        if (cachedConfig == null || cachedConfig!!.exhibitionInfos.isEmpty()) {
            return
        }

        val defaultExhibitionInfo = cachedConfig!!.exhibitionInfos[0]
        val defaultVehicleName = defaultExhibitionInfo.vehicle ?: "默认车型"
        val homeFeatureId = -(defaultExhibitionInfo.hashCode() % 10000)

        viewModelScope.launch {
            downloadServiceManager.isServiceConnected.collect { isConnected ->
                if (isConnected) {
                    launch {
                        downloadServiceManager.observeFeatureState(homeFeatureId).collect { state ->
                            when (state) {
                                is FeatureDownloadState.Completed -> {
                                    // 默认车型首页下载完成
                                    if (!_defaultVehicleHomeReady.value) {
                                        Log.i(TAG, "✅ 默认车型首页下载完成，显示提示")
                                        _defaultVehicleHomeReady.value = true
                                        // 保存状态
                                        sharedPreferences.edit()
                                            .putBoolean(KEY_DEFAULT_VEHICLE_HOME_READY, true)
                                            .apply()
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
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
        }
    }

    /**
     * 联网获取最新配置
     */
    private suspend fun fetchRemoteConfig(): DownloadConfig? = withContext(Dispatchers.IO) {
        try {
            // 模拟网络延迟
            kotlinx.coroutines.delay(500)

            // 读取本地配置作为"远程"配置
            val config = configParser.parse("download.json")

            if (config == null) {
                Log.e(TAG, "❌ 远程配置解析失败")
                return@withContext null
            }

            Log.i(TAG, "✅ 远程配置获取成功，车型数: ${config.exhibitionInfos.size}")
            config
        } catch (e: Exception) {
            Log.e(TAG, "❌ 联网获取配置失败", e)
            null
        }
    }

    /**
     * 保存配置到本地
     */
    private suspend fun saveConfigToLocal(config: DownloadConfig) = withContext(Dispatchers.IO) {
        try {
            val gson = com.google.gson.Gson()
            val jsonString = gson.toJson(config)
            val file = java.io.File(context.filesDir, "download.json")
            file.writeText(jsonString)
            Log.i(TAG, "✅ 配置已保存到本地: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存配置失败", e)
        }
    }

    /**
     * 下载 Feature
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
     * 重试下载 Feature
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
     * 取消 Feature 下载
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
     * 打开 Feature（仅在下载完成后）
     */
    fun openFeature(featureId: Int) {
        val feature = _featuresState.value.find { it.id == featureId } ?: return

        if (feature.downloadState is FeatureDownloadState.Completed) {
            Log.i(TAG, "📂 打开 Feature: ${feature.title}")
            _errorMessage.value = "打开 Feature: ${feature.title}"
        } else {
            Log.w(TAG, "⚠️ Feature未完成，无法打开: ${feature.title}")
        }
    }

    /**
     * 更新 Feature 的下载状态
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
     * 检查所有 Feature 的更新
     */
    fun checkForUpdates() {
        Log.i(TAG, "🔄 检查配置更新...")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
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

                    FeatureUIState(
                        id = feature.id,
                        title = feature.mainTitle,
                        subtitle = feature.subTitle,
                        downloadState = FeatureDownloadState.Idle,
                        files = files
                    )
                }

                queryAllStatesFromService()

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
     * 更新 Feature（增量下载）
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
