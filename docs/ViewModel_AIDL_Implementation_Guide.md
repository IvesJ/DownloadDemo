# ViewModel AIDL 实现指南

## 概述
这是 DownloadViewModel 绑定 AutoDownloadService 并通过 AIDL 接收实时下载进度的完整实现指南。

## 核心实现

### 1. 添加必要的依赖注入

```kotlin
@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configParser: ConfigParser,
    private val fileCleanupManager: FileCleanupManager
) : ViewModel() {

    private val gson = Gson()

    // AIDL Service 连接
    private var downloadService: IDownloadService? = null
    private var serviceBound = false

    // ServiceConnection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloadService = IDownloadService.Stub.asInterface(service)
            serviceBound = true
            Log.i(TAG, "✅ 成功绑定下载服务")

            // 注册回调
            downloadService?.registerCallback(downloadCallback)

            // 查询所有 Feature 的当前状态
            viewModelScope.launch {
                queryAllStatesFromService()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound = false
            Log.w(TAG, "⚠️ 下载服务断开连接")
        }
    }

    // AIDL 回调实现
    private val downloadCallback = object : IDownloadProgressCallback.Stub() {
        override fun onDownloadStateChanged(featureId: Int, state: DownloadState?) {
            if (state == null) return

            Log.d(TAG, "📡 AIDL 回调：Feature #$featureId 状态变化 -> ${state.stateType}")

            // 将 AIDL DownloadState 转换为 FeatureDownloadState
            val featureState = when (state.stateType) {
                DownloadState.STATE_IDLE -> FeatureDownloadState.Idle
                DownloadState.STATE_DOWNLOADING -> {
                    FeatureDownloadState.Downloading(
                        progress = state.progress,
                        currentFile = state.currentFile,
                        completedFiles = state.completedFiles,
                        totalFiles = state.totalFiles
                    )
                }
                DownloadState.STATE_COMPLETED -> FeatureDownloadState.Completed
                DownloadState.STATE_FAILED -> {
                    FeatureDownloadState.Failed(
                        error = state.error,
                        failedFile = state.failedFile
                    )
                }
                DownloadState.STATE_CANCELED -> FeatureDownloadState.Canceled
                else -> return
            }

            // 在主线程更新 UI 状态
            viewModelScope.launch(Dispatchers.Main) {
                updateFeatureState(featureId, featureState)
            }
        }
    }

    init {
        // 绑定服务
        bindDownloadService()
    }

    override fun onCleared() {
        super.onCleared()
        // 注销回调
        downloadService?.unregisterCallback(downloadCallback)
        // 解绑服务
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
```

### 2. 服务绑定方法

```kotlin
private fun bindDownloadService() {
    val intent = Intent(context, AutoDownloadService::class.java)
    val flags = Context.BIND_AUTO_CREATE

    val bound = context.bindService(intent, serviceConnection, flags)
    if (bound) {
        Log.i(TAG, "📞 正在绑定下载服务...")
    } else {
        Log.e(TAG, "❌ 绑定下载服务失败")
    }
}
```

### 3. 查询所有状态

```kotlin
private suspend fun queryAllStatesFromService() = withContext(Dispatchers.IO) {
    try {
        _featuresState.value.forEach { feature ->
            val state = downloadService?.getDownloadState(feature.id)
            if (state != null) {
                // 转换并更新状态
                val featureState = convertDownloadState(state)
                withContext(Dispatchers.Main) {
                    updateFeatureState(feature.id, featureState)
                }
            }
        }
    } catch (e: RemoteException) {
        Log.e(TAG, "❌ 查询状态失败", e)
    }
}
```

### 4. 下载控制方法

```kotlin
fun downloadFeature(featureId: Int) {
    val feature = _featuresState.value.find { it.id == featureId }
    if (feature == null) {
        Log.e(TAG, "❌ 找不到 Feature #$featureId")
        return
    }

    viewModelScope.launch {
        try {
            // 将文件列表序列化为 JSON
            val filesJson = gson.toJson(feature.files)

            // 通过 AIDL 调用下载
            downloadService?.startDownload(featureId, filesJson)

            Log.i(TAG, "✅ 已通知服务开始下载 Feature #$featureId")
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 调用下载失败", e)
            _errorMessage.value = "启动下载失败: ${e.message}"
        }
    }
}

fun cancelFeature(featureId: Int) {
    viewModelScope.launch {
        try {
            downloadService?.cancelDownload(featureId)
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 取消下载失败", e)
        }
    }
}

fun retryFeature(featureId: Int) {
    val feature = _featuresState.value.find { it.id == featureId }
    if (feature == null) return

    viewModelScope.launch {
        try {
            val filesJson = gson.toJson(feature.files)
            downloadService?.retryDownload(featureId, filesJson)
        } catch (e: RemoteException) {
            Log.e(TAG, "❌ 重试下载失败", e)
        }
    }
}
```

## 完整数据流

```
用户点击下载按钮
  ↓
ViewModel.downloadFeature(featureId)
  ↓
downloadService.startDownload(featureId, filesJson)
  ↓ AIDL IPC (跨用户)
AutoDownloadService (User 0)
  ├─ gson.fromJson(filesJson) → List<FileInfo>
  ├─ observeFeatureForAIDL(featureId)  // 启动状态监听
  └─ featureDownloadManager.downloadFeature()
       ↓ Flow.collect()
    convertToDownloadState()
       ↓
    notifyAllCallbacks()
       ↓ AIDL IPC (回调)
ViewModel.downloadCallback.onDownloadStateChanged()
  ↓
updateFeatureState(featureId, state)
  ↓
UI 自动重组，显示进度
```

## 优势

### ✅ 跨用户实时进度
- User 10 可以看到 User 0 触发的下载进度
- 高频进度更新（onProgress 回调）通过 AIDL 实时传递

### ✅ 系统级可靠性
- AIDL 是 Android 标准 IPC 机制
- RemoteCallbackList 自动处理进程死亡

### ✅ 状态持久化
- 数据库保存完整状态（跨重启）
- stateCache 提供快速查询（内存）

### ✅ 清晰的架构分层
```
UI Layer (ViewModel)
  ↕ AIDL IPC
Service Layer (AutoDownloadService, singleUser)
  ↕ Flow
Domain Layer (FeatureDownloadManager)
  ↕ Repository
Data Layer (Database + Network)
```

## 测试建议

1. **单用户测试**：
   - 主用户下载，查看进度实时更新

2. **跨用户测试**：
   - User 0 绑定服务并下载
   - 切换到 User 10
   - 打开 App → 应能看到实时进度

3. **进程重启测试**：
   - 下载进行中
   - 杀死 Activity 进程
   - 重新打开 → 重新绑定 Service，进度继续

4. **多客户端测试**：
   - User 0 和 User 10 同时打开 App
   - 任一用户触发下载
   - 两个用户都能看到实时进度

## 日志示例

```
📞 正在绑定下载服务...
✅ 成功绑定下载服务
📞 注册回调，当前回调数: 1
📡 AIDL 回调：Feature #1 状态变化 -> downloading
🔄 更新 Feature #1 状态: Downloading (progress=0.15)
📡 AIDL 回调：Feature #1 状态变化 -> downloading
🔄 更新 Feature #1 状态: Downloading (progress=0.34)
...
📡 AIDL 回调：Feature #1 状态变化 -> completed
🔄 更新 Feature #1 状态: Completed
```
