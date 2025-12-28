# 模拟下载模式说明

## 概述

由于 `data/download.json` 中的 URL 和 MD5 都是 mock 数据，无法进行真实的下载和校验。本项目实现了**模拟下载模式**，可以在开发和测试阶段演示完整的下载流程。

## 配置文件

所有模拟相关的配置都在 `MockConfig.kt` 中统一管理：

```kotlin
D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\core\MockConfig.kt
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `MOCK_DOWNLOAD_MODE` | Boolean | `true` | 是否启用模拟下载模式 |
| `MOCK_MD5_VALIDATION` | Boolean | `true` | 是否启用模拟MD5校验 |
| `MOCK_DOWNLOAD_DELAY_MS` | Long | `100L` | 模拟下载延迟（毫秒/块） |
| `MOCK_CHUNK_SIZE` | Long | `100KB` | 模拟下载块大小 |

## 模拟功能特性

### 1. 模拟下载 (FileDownloaderImpl.kt)

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\core\download\FileDownloaderImpl.kt:47`

**功能**:
- ✅ 模拟HTTP下载过程
- ✅ 支持断点续传（通过临时文件 `.downloading`）
- ✅ 模拟分块下载和进度回调
- ✅ 模拟网络延迟
- ✅ 支持取消和暂停操作

**实现逻辑**:
```kotlin
if (MockConfig.MOCK_DOWNLOAD_MODE) {
    // 模拟下载流程
    // 1. 创建临时文件
    // 2. 分块写入模拟数据
    // 3. 回调进度
    // 4. 完成后重命名为最终文件
}
```

### 2. 模拟MD5校验 (MD5Validator.kt)

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\core\validation\MD5Validator.kt:30`

**功能**:
- ✅ 模拟MD5校验通过
- ✅ 检查文件是否存在
- ✅ 检查文件大小是否大于0

**实现逻辑**:
```kotlin
if (MockConfig.MOCK_MD5_VALIDATION) {
    // 模拟校验：文件存在且大小>0即通过
    return file.exists() && file.isFile && file.length() > 0
}
```

## 完整的下载流程

即使在模拟模式下，所有的业务逻辑检查都会正常执行：

1. **Feature下载管理** - `FeatureDownloadManager.kt:43`
   - ✅ 检查磁盘空间
   - ✅ 检查文件是否已存在
   - ✅ 计算总体进度
   - ✅ 处理下载失败和重试

2. **并发控制** - `DownloadWorker.kt:13`
   - ✅ 使用Semaphore控制并发数（默认3个）
   - ✅ 队列管理

3. **状态通知** - `FeatureDownloadState.kt:5`
   - ✅ Idle（待下载）
   - ✅ Downloading（下载中 + 进度）
   - ✅ Completed（已完成）
   - ✅ Failed（失败 + 错误信息）
   - ✅ Canceled（已取消）

4. **UI更新** - `MainActivity.kt:21`
   - ✅ 实时显示下载进度
   - ✅ 按钮状态切换（下载/进度/完成/重试）
   - ✅ 显示当前下载文件和完成数

## 如何切换到生产模式

当你有真实的下载URL和MD5时，只需修改 `MockConfig.kt`:

```kotlin
object MockConfig {
    // 设置为 false 启用真实下载
    const val MOCK_DOWNLOAD_MODE = false

    // 设置为 false 启用真实MD5校验
    const val MOCK_MD5_VALIDATION = false
}
```

## 测试场景

### 场景1: 正常下载流程
1. 打开应用，查看Feature列表
2. 点击任意Feature的"下载"按钮
3. 观察进度条实时更新（0% -> 100%）
4. 观察文件计数更新（1/10 -> 10/10）
5. 下载完成后按钮变为"已完成"

### 场景2: 断点续传
1. 开始下载某个Feature
2. 中途关闭应用（或杀进程）
3. 重新打开应用
4. 再次点击下载，观察从断点继续（`.downloading`文件）

### 场景3: 并发下载
1. 快速点击多个Feature的下载按钮
2. 观察最多同时下载3个Feature（Semaphore限制）
3. 前面的完成后，后续的自动开始

### 场景4: 下载失败重试
1. 下载过程中可以模拟失败（修改MockConfig添加失败概率）
2. 观察按钮变为"重试"
3. 点击重试重新下载

## 模拟数据说明

### 模拟文件大小
- 每个文件大小随机：1MB ~ 5MB
- 可在 `FileDownloaderImpl.mockDownload()` 中修改

### 模拟下载速度
- 每100ms下载100KB
- 可通过 `MockConfig.MOCK_DOWNLOAD_DELAY_MS` 和 `MOCK_CHUNK_SIZE` 调整

### 模拟文件内容
- 写入空字节 `ByteArray(size) { 0 }`
- 只用于演示，不包含实际内容

## 注意事项

⚠️ **重要提醒**:
1. 模拟模式仅用于开发和测试
2. 生产环境务必设置 `MOCK_DOWNLOAD_MODE = false`
3. 模拟文件会占用真实磁盘空间，记得清理
4. 模拟MD5校验不验证文件内容，仅检查文件存在性

## 相关文件索引

| 文件 | 说明 |
|------|------|
| `MockConfig.kt` | 模拟配置统一管理 |
| `FileDownloaderImpl.kt` | 下载实现（含模拟逻辑） |
| `MD5Validator.kt` | MD5校验（含模拟逻辑） |
| `FeatureDownloadManager.kt` | Feature级别下载管理 |
| `DownloadWorker.kt` | 并发控制 |
| `MainActivity.kt` | UI展示 |

## 技术架构

```
UI Layer (MainActivity + Adapter)
    ↓ StateFlow
ViewModel Layer (DownloadViewModel)
    ↓
Domain Layer (FeatureDownloadManager)
    ↓
Core Layer (DownloadWorker + FileDownloader + MD5Validator)
    ↓
[MockConfig] ← 模拟开关控制
```

## 常见问题

**Q: 为什么需要模拟模式？**
A: 因为download.json中的URL和MD5都是示例数据，无法真实下载。模拟模式让你可以在没有真实服务器的情况下测试完整流程。

**Q: 模拟下载的文件存在哪里？**
A: 存储在应用的外部私有目录：`getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)`

**Q: 模拟模式会影响性能测试吗？**
A: 会的，模拟下载没有网络IO，速度比真实下载快得多。性能测试需要使用真实下载。

**Q: 如何清理模拟下载的文件？**
A: 可以通过FileManager提供的方法清理，或直接卸载应用。
