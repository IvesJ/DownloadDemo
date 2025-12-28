# 增量更新与文件清理机制说明

## 功能概述

应用支持增量更新和智能文件清理功能，当云端配置文件（download.json）更新后，系统能够：

1. **增量下载**：只下载变化的文件，跳过已下载且MD5匹配的文件
2. **智能清理**：自动删除所有Feature都不再需要的文件，释放存储空间
3. **版本检测**：检查每个Feature是否有文件需要更新

## 核心组件

### 1. FileCleanupManager - 文件清理管理器

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\core\storage\FileCleanupManager.kt`

**功能**:
- 扫描本地下载目录中的所有文件
- 对比当前配置文件中所有Feature需要的文件
- 识别并删除不再需要的"孤儿文件"
- 统计清理结果（删除文件数、释放空间）

**核心方法**:

```kotlin
// 扫描并清理不再需要的文件
suspend fun scanAndCleanUnusedFiles(config: DownloadConfig): CleanupResult

// 获取可清理文件列表（不执行删除）
suspend fun getUnusedFiles(config: DownloadConfig): List<UnusedFileInfo>

// 清理临时下载文件
suspend fun cleanTempFiles(): Int
```

**清理逻辑**:

```
1. 解析配置文件，提取所有Feature需要的文件名 → Set<String>
2. 扫描本地下载目录，获取所有已下载的文件
3. 过滤出"孤儿文件"：
   - 不在配置文件中
   - 排除临时文件（.downloading后缀）
4. 逐个删除孤儿文件，统计释放空间
```

### 2. FeatureDownloadManager 扩展 - 增量更新检查

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\domain\FeatureDownloadManager.kt`

**新增功能**:

#### checkForUpdates() - 检查Feature更新

```kotlin
suspend fun checkForUpdates(featureId: Int, files: List<FileInfo>): UpdateCheckResult
```

**检查逻辑**:
```
对于每个文件：
  1. 检查本地是否存在
     - 不存在 → 标记为需要下载

  2. 文件存在，检查MD5
     - MD5匹配 → 已是最新版本
     - MD5不匹配 → 标记为需要更新（删除旧文件并重新下载）
```

**返回结果**:
```kotlin
data class UpdateCheckResult(
    val featureId: Int,
    val totalFiles: Int,              // 总文件数
    val upToDateFiles: Int,           // 已是最新的文件数
    val filesToDownload: List<FileInfo>, // 需要下载的文件列表
    val filesToDelete: List<String>    // 需要删除的文件名列表
)
```

#### updateFeature() - 增量更新Feature

```kotlin
suspend fun updateFeature(featureId: Int, files: List<FileInfo>)
```

**更新流程**:
```
1. 调用checkForUpdates()检查哪些文件需要更新
2. 如果所有文件都是最新的 → 直接标记为Completed
3. 删除需要重新下载的旧文件（MD5不匹配的）
4. 只下载需要更新的文件
```

### 3. DownloadViewModel 扩展 - UI层支持

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\ui\DownloadViewModel.kt`

**新增方法**:

#### checkForUpdates() - 检查所有Feature的更新

```kotlin
fun checkForUpdates()
```

**执行流程**:
```
1. 重新解析配置文件（模拟从云端获取最新配置）
2. 遍历所有Feature，调用checkForUpdates()检查更新
3. 自动调用fileCleanupManager清理不再需要的文件
4. 更新UI状态：
   - 已是最新 → Completed
   - 需要更新 → Idle（可点击下载）
5. 重新监听各Feature的下载状态
6. 显示检查结果Toast
```

#### cleanupUnusedFiles() - 仅清理文件

```kotlin
fun cleanupUnusedFiles()
```

只执行文件清理，不检查更新。

#### updateFeature() - 更新单个Feature

```kotlin
fun updateFeature(featureId: Int)
```

对指定Feature执行增量更新。

### 4. MainActivity - UI入口

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\ui\MainActivity.kt`

**菜单项**:

| 菜单项 | ID | 功能 |
|--------|-----|------|
| 检查更新 | `action_check_updates` | 检查所有Feature更新并清理不需要的文件 |
| 清理存储 | `action_cleanup_files` | 仅清理不再需要的文件 |
| 自动下载全部 | `action_auto_download` | 启动后台服务自动下载所有Feature |
| 停止自动下载 | `action_stop_auto_download` | 停止后台下载服务 |

## 使用场景

### 场景1: 云端配置更新 - 添加新文件

**云端操作**: download.json中某个Feature新增了文件

**用户操作**:
1. 点击菜单"检查更新"
2. 系统重新解析配置文件
3. 检测到新增文件，标记Feature为Idle
4. 用户点击该Feature的下载按钮
5. 系统只下载新增的文件，跳过已存在的文件

**日志示例**:
```
I/DownloadViewModel: 🔄 检查配置更新...
I/DownloadViewModel: ✅ 配置文件解析成功，共 5 个Feature
I/FeatureDownloadMgr: 🔄 检查 Feature #1 是否有更新...
I/FeatureDownloadMgr: 📥 需要下载: new_file.zip (文件不存在)
I/FeatureDownloadMgr: ✅ 文件已是最新: existing_file.zip
I/FeatureDownloadMgr: 📊 检查结果 Feature #1:
I/FeatureDownloadMgr:    ✅ 已是最新: 4 个
I/FeatureDownloadMgr:    📥 需要下载: 1 个
I/FeatureDownloadMgr:    🗑️ 需要删除: 0 个
```

### 场景2: 云端配置更新 - 文件内容变化

**云端操作**: download.json中某个文件的MD5值变化（内容更新）

**用户操作**:
1. 点击菜单"检查更新"
2. 系统检测到文件MD5不匹配
3. 自动删除旧文件
4. 重新下载新版本文件

**日志示例**:
```
I/FeatureDownloadMgr: 🔄 检查 Feature #2 是否有更新...
I/FeatureDownloadMgr: 🔄 需要更新: updated_file.zip (MD5不匹配)
I/FeatureDownloadMgr: 📊 检查结果 Feature #2:
I/FeatureDownloadMgr:    ✅ 已是最新: 3 个
I/FeatureDownloadMgr:    📥 需要下载: 1 个
I/FeatureDownloadMgr:    🗑️ 需要删除: 1 个

I/FeatureDownloadMgr: 🔄 开始增量更新 Feature #2
I/FeatureDownloadMgr: 🗑️ 删除旧文件: updated_file.zip
I/FeatureDownloadMgr: 📥 开始下载 1 个需要更新的文件
```

### 场景3: 云端配置更新 - 删除整个Feature

**云端操作**: download.json中移除了某个Feature

**用户操作**:
1. 点击菜单"检查更新"
2. 系统发现该Feature的所有文件都不再需要
3. 自动清理这些文件，释放存储空间

**日志示例**:
```
I/FileCleanupManager: 🧹 开始扫描和清理不再需要的文件...
I/FileCleanupManager: 📋 配置文件中共需要 15 个文件
I/FileCleanupManager: 📂 本地存储中共有 20 个文件
I/FileCleanupManager: 🗑️ 发现 5 个不再需要的文件
I/FileCleanupManager: 🗑️ 删除文件: old_feature_1.zip (5120KB)
I/FileCleanupManager: ✅ 已删除: old_feature_1.zip
...
I/FileCleanupManager: 🎉 清理完成！
I/FileCleanupManager: 📊 删除文件: 5 个
I/FileCleanupManager: 💾 释放空间: 25MB
```

### 场景4: 仅清理存储空间

**用户操作**:
1. 点击菜单"清理存储"
2. 系统扫描并删除不再需要的文件
3. 不检查更新，只执行清理

**适用情况**:
- 存储空间不足
- 想要释放空间但不想检查更新

## 工作流程

### 完整更新流程

```
用户点击"检查更新"
  ↓
DownloadViewModel.checkForUpdates()
  ↓
重新解析download.json（模拟云端更新）
  ↓
遍历所有Feature
  ↓
FeatureDownloadManager.checkForUpdates() ← 对每个Feature
  ↓                                        ↓
  ├── 检查每个文件                         对比本地文件
  │   ├── 文件不存在 → 需要下载              ↓
  │   ├── MD5匹配 → 已是最新             返回UpdateCheckResult
  │   └── MD5不匹配 → 需要更新              ↓
  ↓                                    更新UI状态
FileCleanupManager.scanAndCleanUnusedFiles()
  ↓
获取配置中所有需要的文件 → Set<String>
  ↓
扫描本地所有文件
  ↓
找出孤儿文件（不在配置中的）
  ↓
删除孤儿文件，统计释放空间
  ↓
显示检查结果Toast
  ↓
重新监听Feature状态
```

### 增量下载流程

```
用户点击需要更新的Feature
  ↓
DownloadViewModel.downloadFeature() 或 updateFeature()
  ↓
FeatureDownloadManager.updateFeature()
  ↓
checkForUpdates() → 获取需要更新的文件列表
  ↓
删除需要重新下载的旧文件（MD5不匹配）
  ↓
downloadFeature(filesToDownload) ← 只下载需要的文件
  ↓
遍历文件列表
  ↓
对于每个文件：
  ├── 检查本地是否存在且MD5正确
  │   ├── 是 → 跳过下载
  │   └── 否 → 下载文件
  ↓
所有文件完成 → 标记为Completed
```

## 数据结构

### CleanupResult - 清理结果

```kotlin
data class CleanupResult(
    val totalFiles: Int,           // 本地总文件数
    val deletedFiles: Int,         // 删除的文件数
    val freedSpaceBytes: Long,     // 释放的空间（字节）
    val deletedFileNames: List<String> // 删除的文件名列表
) {
    fun getFreedSpaceMB(): Long = freedSpaceBytes / 1024 / 1024
}
```

### UpdateCheckResult - 更新检查结果

```kotlin
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
```

### UnusedFileInfo - 未使用文件信息

```kotlin
data class UnusedFileInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val lastModified: Long
) {
    fun getSizeMB(): Long = fileSizeBytes / 1024 / 1024
}
```

## 日志输出

### 更新检查日志

```
I/DownloadViewModel: 🔄 检查配置更新...
I/DownloadViewModel: 📄 重新加载配置文件: download.json
I/DownloadViewModel: ✅ 配置文件解析成功，共 5 个Feature

I/FeatureDownloadMgr: 🔄 检查 Feature #1 是否有更新...
D/FeatureDownloadMgr: ✅ 文件已是最新: file1.zip
D/FeatureDownloadMgr: 📥 需要下载: file2.zip (文件不存在)
D/FeatureDownloadMgr: 🔄 需要更新: file3.zip (MD5不匹配)
I/FeatureDownloadMgr: 📊 检查结果 Feature #1:
I/FeatureDownloadMgr:    ✅ 已是最新: 1 个
I/FeatureDownloadMgr:    📥 需要下载: 2 个
I/FeatureDownloadMgr:    🗑️ 需要删除: 1 个

I/DownloadViewModel: 🔄 Feature #1 有更新: 2 个文件需要下载
```

### 文件清理日志

```
I/FileCleanupManager: ════════════════════════════════════════
I/FileCleanupManager: 🧹 开始扫描和清理不再需要的文件...
I/FileCleanupManager: 📋 配置文件中共需要 15 个文件
D/FileCleanupManager: 📦 Feature #1: 品牌 需要 3 个文件
D/FileCleanupManager: 📦 Feature #2: 展厅 需要 5 个文件
...
I/FileCleanupManager: 📂 本地存储中共有 18 个文件
I/FileCleanupManager: 🗑️ 发现 3 个不再需要的文件

D/FileCleanupManager: 🗑️ 删除文件: old_file_1.zip (2048KB)
I/FileCleanupManager: ✅ 已删除: old_file_1.zip
D/FileCleanupManager: 🗑️ 删除文件: old_file_2.zip (5120KB)
I/FileCleanupManager: ✅ 已删除: old_file_2.zip

I/FileCleanupManager: ════════════════════════════════════════
I/FileCleanupManager: 🎉 清理完成！
I/FileCleanupManager: 📊 删除文件: 3 个
I/FileCleanupManager: 💾 释放空间: 7MB
I/FileCleanupManager: ════════════════════════════════════════
```

### 增量下载日志

```
I/FeatureDownloadMgr: 🔄 开始增量更新 Feature #2
D/FeatureDownloadMgr: 🗑️ 删除旧文件: old_version.zip
I/FeatureDownloadMgr: 📥 开始下载 2 个需要更新的文件

I/FeatureDownloadMgr: ════════════════════════════════════════
I/FeatureDownloadMgr: 🚀 开始下载 Feature #2
I/FeatureDownloadMgr: 📦 文件总数: 2
I/FeatureDownloadMgr: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
I/FeatureDownloadMgr: 📥 开始逐个下载文件...
I/FeatureDownloadMgr: 📄 [1/2] new_file.zip
I/FeatureDownloadMgr: 📥 开始下载: new_file.zip
...
I/FeatureDownloadMgr: 🎉 Feature #2 所有文件下载完成！
```

## 模拟云端更新

### 方法1: 手动修改download.json

在 `app/src/main/assets/download.json` 中：

**添加新文件**:
```json
{
  "fileInfos": [
    {
      "fileName": "new_file_v2.zip",
      "fileResUrl": "https://example.com/new_file.zip",
      "fileMd5": "new_md5_value_here"
    }
  ]
}
```

**修改文件MD5（模拟内容更新）**:
```json
{
  "fileName": "existing_file.zip",
  "fileMd5": "updated_md5_value_here"  // 修改MD5
}
```

**删除Feature**: 直接从配置中移除整个Feature对象

### 方法2: 准备多个版本的配置文件

```
assets/
  ├── download.json         # 当前版本
  ├── download_v1.json      # 版本1
  ├── download_v2.json      # 版本2（新增Feature）
  └── download_v3.json      # 版本3（删除旧Feature）
```

在代码中切换配置文件名来模拟更新。

### 测试步骤

1. **初始状态**: 使用 download_v1.json，下载所有Feature
2. **模拟更新**: 将 download_v2.json 改名为 download.json
3. **检查更新**: 点击"检查更新"菜单
4. **观察结果**: 查看日志和UI状态变化
5. **验证清理**: 检查不再需要的文件是否被删除

## 性能优化

### 1. MD5计算优化

- FileManager.checkFileExistsAndValid() 中使用MD5Validator的缓存
- 已计算过的文件MD5会缓存在内存中
- 避免重复计算同一文件的MD5

### 2. 文件扫描优化

- FileCleanupManager一次性扫描所有文件
- 使用Set<String>快速查找文件是否需要
- 避免多次遍历目录

### 3. 增量下载优化

- checkForUpdates() 提前过滤出需要下载的文件
- 只下载真正需要的文件，跳过已下载且正确的文件
- 减少不必要的网络请求和磁盘IO

## 错误处理

### 配置文件解析失败

```kotlin
if (config == null) {
    Log.e(TAG, "❌ 配置文件解析失败")
    _errorMessage.value = "配置文件解析失败"
    return
}
```

### 文件删除失败

```kotlin
if (file.delete()) {
    deletedCount++
    Log.i(TAG, "✅ 已删除: ${file.name}")
} else {
    Log.e(TAG, "❌ 删除失败: ${file.name}")
}
```

### MD5校验失败

```kotlin
if (md5Validator.validate(localFile, file.fileMd5)) {
    Log.d(TAG, "✅ 文件已是最新: ${file.fileName}")
} else {
    Log.d(TAG, "🔄 需要更新: ${file.fileName} (MD5不匹配)")
    filesToDelete.add(file.fileName)
    filesToDownload.add(file)
}
```

## 注意事项

⚠️ **重要提醒**:

1. **配置文件更新**:
   - 生产环境中需要实现从云端拉取配置文件的逻辑
   - 当前实现只是从本地assets重新解析
   - 可以使用HTTP请求或Firebase Remote Config等方式

2. **文件清理风险**:
   - 确保配置文件正确，避免误删需要的文件
   - 建议添加确认对话框，让用户确认清理操作
   - 可以先使用getUnusedFiles()预览要清理的文件

3. **MD5匹配逻辑**:
   - 当前使用模拟模式，只检查文件是否存在
   - 生产环境需要计算真实MD5值进行比对
   - 可以通过MockConfig.MOCK_MD5_VALIDATION控制

4. **并发安全**:
   - 避免同时执行多个清理或更新操作
   - ViewModel中使用_isLoading状态防止重复操作
   - 建议在操作进行时禁用相关按钮

5. **存储空间**:
   - 清理前建议检查文件大小，避免误删大文件
   - 提供撤销功能（可选）
   - 记录清理日志供用户查看

## 扩展功能建议

可以考虑添加以下功能：

1. **自动更新检查**: 应用启动时自动检查更新
2. **后台定时检查**: 使用WorkManager定期检查更新
3. **差异对比界面**: 显示哪些Feature有更新，详细列出变化
4. **清理预览**: 显示即将清理的文件列表和大小，让用户确认
5. **版本号管理**: 在配置文件中添加版本号字段
6. **更新日志**: 记录每次更新的详细信息
7. **回滚功能**: 保留旧版本文件，支持回滚
8. **选择性更新**: 让用户选择更新哪些Feature

## 相关文件索引

| 文件 | 说明 |
|------|------|
| `FileCleanupManager.kt` | 文件清理管理器 |
| `FeatureDownloadManager.kt` | Feature下载管理器（扩展） |
| `DownloadViewModel.kt` | ViewModel（扩展） |
| `MainActivity.kt` | 主界面菜单处理 |
| `menu_main.xml` | 菜单布局 |
| `download.json` | 配置文件 |

## API说明

### FileCleanupManager

```kotlin
// 扫描并清理不再需要的文件
suspend fun scanAndCleanUnusedFiles(config: DownloadConfig): CleanupResult

// 获取可清理文件列表（不执行删除）
suspend fun getUnusedFiles(config: DownloadConfig): List<UnusedFileInfo>

// 清理临时下载文件
suspend fun cleanTempFiles(): Int
```

### FeatureDownloadManager

```kotlin
// 检查Feature是否有更新
suspend fun checkForUpdates(featureId: Int, files: List<FileInfo>): UpdateCheckResult

// 增量更新Feature
suspend fun updateFeature(featureId: Int, files: List<FileInfo>)
```

### DownloadViewModel

```kotlin
// 检查所有Feature的更新
fun checkForUpdates()

// 清理不再需要的文件
fun cleanupUnusedFiles()

// 更新单个Feature
fun updateFeature(featureId: Int)
```

## 测试方法

### 测试增量更新

1. 首次下载所有Feature
2. 修改download.json添加新文件或修改MD5
3. 点击"检查更新"
4. 观察日志，验证只下载变化的文件
5. 检查UI状态是否正确更新

### 测试文件清理

1. 手动复制一些不在配置中的文件到下载目录
2. 点击"清理存储"
3. 查看日志确认这些文件被删除
4. 验证配置中的文件没有被删除

### 测试MD5更新

1. 下载某个Feature
2. 修改配置文件中该Feature某个文件的MD5
3. 点击"检查更新"
4. 观察该文件是否被标记为需要更新
5. 下载后验证旧文件被删除，新文件被下载

## 故障排查

### 检查更新没有反应

1. 检查日志是否有异常
2. 确认download.json格式正确
3. 检查网络权限和存储权限
4. 验证ConfigParser是否正确解析

### 文件没有被清理

1. 检查文件是否在配置文件中
2. 确认文件名是否完全匹配
3. 查看清理日志确认发现的文件数
4. 检查文件权限是否允许删除

### 增量下载失败

1. 检查MD5Validator是否正常工作
2. 确认MockConfig配置正确
3. 查看FeatureDownloadManager日志
4. 验证文件路径是否正确
