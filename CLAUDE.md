# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**DownLoadDemo** 是一个面向展览/展台场景的多用户 Android 下载管理系统。支持断点续传下载、MD5 校验、通过 AIDL IPC 实现多用户数据共享、开机自动下载以及增量更新和文件清理功能。

**技术栈**: Kotlin 2.0.21, Android SDK 36 (最低 28), Coroutines 1.7.3, Hilt 2.50, Room 2.6.1, OkHttp 4.12.0

## 构建命令

```bash
# 构建 Debug APK
.\gradlew.bat assembleDebug

# 构建 Release APK
.\gradlew.bat assembleRelease

# 清理构建
.\gradlew.bat clean

# 安装到设备
.\gradlew.bat installDebug

# 运行单元测试
.\gradlew.bat test

# 运行设备测试
.\gradlew.bat connectedAndroidTest

# 运行 Lint 检查
.\gradlew.bat lint
```

## 架构设计

本项目遵循 **Clean Architecture + MVVM**，层次分明：

```
ui/ (表示层)
  ├── MainActivity.kt          # ViewBinding UI 宿主
  ├── DownloadViewModel.kt     # ViewModel with StateFlow
  └── adapter/                 # RecyclerView 适配器

domain/ (业务逻辑层)
  ├── FeatureDownloadManager.kt   # 核心下载编排器
  ├── model/                      # 密封类状态定义
  └── repository/                 # 仓库接口

data/ (数据层)
  ├── repository/                 # 仓库实现
  ├── local/                      # Room 数据库
  ├── provider/                   # ContentProvider 跨用户同步
  ├── parser/                     # JSON 配置解析
  └── service/                    # AIDL 服务管理器

core/ (基础设施层)
  ├── download/                   # HTTP 下载引擎（支持断点续传）
  ├── storage/                    # 文件管理与清理
  └── validation/                 # MD5 校验（带缓存）
```

### 核心架构模式

1. **MVVM**: ViewModel 绝不直接绑定 AIDL Service - 使用 `DownloadServiceManager` 作为仓库抽象
2. **StateFlow**: 所有状态变更通过 `StateFlow<FeatureDownloadState>` 流动
3. **Repository 模式**: `DownloadServiceManager` 封装 AIDL 细节，暴露简洁接口
4. **依赖注入**: Hilt 管理所有单例 (`@Singleton`) 和生命周期

### 多用户架构

应用作为**系统应用**运行，配置了 `singleUser="true"` 服务：

- `AutoDownloadService` 仅运行在 User 0（系统用户）
- AIDL IPC (`IDownloadService`) 实现跨用户通信
- `DownloadStateProvider` (ContentProvider) 跨用户同步状态
- 共享存储路径: `/storage/emulated/0/Android/data/.../SharedDownloads`

**权限要求**: `INTERACT_ACROSS_USERS` 需要系统签名或 root。

## 重要开发注意事项

### 模拟模式配置

位于 `core/MockConfig.kt`：

```kotlin
const val MOCK_DOWNLOAD_MODE = true      // 生产环境设为 false
const val MOCK_MD5_VALIDATION = true     // 生产环境设为 false
```

**在没有真实 URL/MD5 的情况下不要将这两个设为 false** - 配置文件包含占位数据。

### 自动启动配置

位于 `core/AutoDownloadConfig.kt`：

```kotlin
var autoStartOnBoot: Boolean = true   # 控制开机自启动
```

部分厂商限制自启动；用户可能需要在设置中手动授权。

### 增量更新系统

应用支持智能增量更新：
- `FeatureDownloadManager.checkForUpdates()` 比较 MD5 哈希
- `FileCleanupManager.scanAndCleanUnusedFiles()` 清理孤儿文件
- 菜单操作："检查更新"、"清理存储"

### MVVM 重构规范

**关键原则**: ViewModel 绝不能直接绑定 AIDL Service。遵循 `docs/MVVM_Refactoring_Guide.md` 中的模式：

```kotlin
// ❌ 错误 - ViewModel 直接处理 AIDL
class DownloadViewModel {
    private var downloadService: IDownloadService?
    private val serviceConnection = object : ServiceConnection { ... }
}

// ✅ 正确 - 使用仓库抽象
class DownloadViewModel @Inject constructor(
    private val downloadServiceManager: DownloadServiceManager  # 抽象接口
) {
    fun downloadFeature(featureId: Int) {
        viewModelScope.launch {
            val result = downloadServiceManager.startDownload(featureId, files)
            result.onFailure { error -> /* 处理错误 */ }
        }
    }
}
```

## 核心数据流

### 下载发起流程

```
MainActivity (用户点击下载)
  → DownloadViewModel.downloadFeature()
  → DownloadServiceManager.startDownload() [AIDL IPC]
  → AutoDownloadService (User 0)
  → FeatureDownloadManager.downloadFeature()
  → DownloadWorker.downloadFile() [Semaphore 并发控制]
  → FileDownloaderImpl.download() [OkHttp Range 请求]
  → MD5Validator.validate()
  → 通过 Flow 更新状态
  → DownloadStateProvider.notifyChange() [跨用户]
  → IDownloadProgressCallback.onDownloadStateChanged() [AIDL 回调]
  → ViewModel StateFlow 更新
  → UI 重组 (ViewBinding)
```

### 状态同步机制

状态通过多种机制流转：
1. **内存**: `ConcurrentHashMap<Int, MutableStateFlow<FeatureDownloadState>>`
2. **持久化**: Room 数据库 (`DownloadStateEntity`)
3. **跨用户**: ContentProvider (`DownloadStateProvider`) + AIDL 回调

## 配置数据结构

下载配置 (`data/download.json`) 结构：

```json
{
  "exhibitionInfos": [
    {
      "id": "exhibition_001",
      "vehicle": "车辆名称",
      "coverName": "...",
      "homeResourceZipMD5": "...",
      "homeResourceZipUrl": "...",
      "featureConfigs": [
        {
          "id": 1,
          "mainTitle": "品牌",
          "cardResourceZipMD5": "...",
          "cardResourceZipUrl": "...",
          "configTabs": [
            {
              "id": "...",
              "name": "...",
              "contents": [...],
              "subTabs": [...]
            }
          ]
        }
      ]
    }
  ]
}
```

使用 `ConfigParser.parse()` 加载，然后 `FeatureDownloadManager.extractAllFiles()` 获取扁平化文件列表。

## 常见开发任务

### 添加新的下载 Feature

1. 更新 `data/download.json` 添加新 feature 配置
2. 无需代码修改 - 系统自动从配置发现 features

### 修改下载行为

- **并发限制**: 修改 `DownloadWorker.semaphore` 许可数（默认: 3）
- **块大小**: 修改 `MockConfig.MOCK_CHUNK_SIZE`（模拟模式）
- **存储路径**: 修改 `FileManager.getDownloadDir()`

### 测试多用户功能

需要系统签名或 root 设备：

```bash
# 作为系统应用推送
adb push app-debug.apk /system/priv-app/DownLoadDemo/

# 授予跨用户权限
adb shell pm grant com.ace.downloaddemo android.permission.INTERACT_ACROSS_USERS
```

## 文件索引

| 文件 | 用途 |
|------|---------|
| `ui/MainActivity.kt` | 主入口，菜单处理 |
| `ui/DownloadViewModel.kt` | UI 状态管理 |
| `domain/FeatureDownloadManager.kt` | 核心下载编排器 |
| `data/service/DownloadServiceManager.kt` | AIDL 服务抽象 |
| `service/AutoDownloadService.kt` | 后台下载服务 (User 0) |
| `core/download/FileDownloaderImpl.kt` | HTTP 下载（断点续传） |
| `core/storage/FileManager.kt` | 文件路径管理 |
| `core/validation/MD5Validator.kt` | MD5 计算（带缓存） |
| `data/parser/ConfigParser.kt` | JSON 配置解析 |
| `di/AppModule.kt` | Hilt 依赖注入配置 |
| `core/MockConfig.kt` | 模拟模式开关 |
| `core/AutoDownloadConfig.kt` | 自动启动配置 |

## 参考文档

- `MOCK_MODE_README.md` - 模拟模式说明
- `AUTO_START_README.md` - 开机自启动功能
- `INCREMENTAL_UPDATE_README.md` - 更新检查和文件清理
- `TECH_STACK_SUMMARY.md` - 完整技术栈概览
- `docs/multi-user-implementation.md` - 多用户架构详解
- `docs/MVVM_Refactoring_Guide.md` - MVVM 架构规范
