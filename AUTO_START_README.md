# 开机自启动下载功能说明

## 功能概述

应用支持在系统开机后自动启动，并在后台自动下载所有Feature的资源文件。

## 核心组件

### 1. BootCompletedReceiver - 开机广播接收器

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\receiver\BootCompletedReceiver.kt`

**功能**:
- 监听系统 `BOOT_COMPLETED` 广播
- 检查自启动配置开关
- 启动自动下载服务

**触发时机**: 系统启动完成时

### 2. AutoDownloadService - 自动下载服务

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\service\AutoDownloadService.kt`

**功能**:
- 前台服务，显示下载通知
- 逐个下载所有Feature
- 实时更新通知显示进度
- 检查已下载的Feature并跳过
- 下载完成后自动停止服务

**服务特性**:
- ✅ 前台服务，不易被系统杀死
- ✅ 显示持久通知，用户可见进度
- ✅ 支持点击通知打开应用
- ✅ 下载完成后自动停止
- ✅ 支持手动停止

### 3. AutoDownloadConfig - 配置管理

**位置**: `D:\Code\Demo\DownLoadDemo\app\src\main\java\com\ace\downloaddemo\core\AutoDownloadConfig.kt`

**功能**:
- 保存和读取自启动配置
- 使用SharedPreferences持久化
- 提供开关控制

**配置项**:
| 配置 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `autoStartOnBoot` | Boolean | `true` | 是否开机自启动 |
| `autoDownloadAll` | Boolean | `true` | 是否自动下载所有Feature |

## 使用方式

### 方式1: 开机自动启动（默认）

1. 系统开机完成
2. 触发 `BootCompletedReceiver`
3. 检查配置开关（默认启用）
4. 启动 `AutoDownloadService`
5. 显示通知，开始下载
6. 下载完成后自动停止

### 方式2: 手动启动

在应用中通过菜单启动：

1. 打开应用
2. 点击右上角菜单
3. 选择"自动下载全部"
4. 服务启动，显示通知
5. 后台下载所有Feature

**代码调用**:
```kotlin
AutoDownloadService.start(context)
```

### 方式3: 手动停止

1. 点击菜单中的"停止自动下载"
2. 或者通过代码：
```kotlin
AutoDownloadService.stop(context)
```

## 权限要求

### AndroidManifest.xml 配置

```xml
<!-- 开机自启动权限 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- 前台服务权限 (Android 9.0+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 后台服务权限 (Android 14+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

### 注册服务和接收器

```xml
<!-- 自动下载服务 -->
<service
    android:name=".service.AutoDownloadService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync" />

<!-- 开机广播接收器 -->
<receiver
    android:name=".receiver.BootCompletedReceiver"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

## 通知栏显示

服务运行时会在通知栏显示：

### 通知内容
- **标题**: "自动下载服务"
- **内容**: 当前下载的Feature和进度
- **进度条**: 显示总体下载进度

### 通知示例
```
自动下载服务
正在下载: 品牌 (1/5)
━━━━━━━━━━━━━━━━━━ 20%
```

### 点击通知
点击通知会打开应用主界面（MainActivity）

## 下载流程

```
开机 → BootCompletedReceiver
  ↓
检查配置 (AutoDownloadConfig)
  ↓
启动服务 (AutoDownloadService)
  ↓
显示通知 (前台服务)
  ↓
解析配置文件 (download.json)
  ↓
遍历所有Feature
  ↓
检查是否已下载 → 已下载? → 跳过
  ↓ 未下载
开始下载
  ↓
监听下载状态 → 更新通知
  ↓
下载完成?
  ↓ 是
标记完成，下载下一个
  ↓
所有完成?
  ↓ 是
显示完成通知，3秒后停止服务
```

## 日志输出

服务运行时会输出详细日志：

```
I/BootCompletedReceiver: 📱 系统开机完成
I/BootCompletedReceiver: ✅ 开机自启动已启用，启动自动下载服务
I/AutoDownloadService: 🚀 自动下载服务创建
I/AutoDownloadService: 🎬 自动下载服务启动
I/AutoDownloadService: 📄 开始解析配置文件
I/AutoDownloadService: 📦 共有 5 个Feature需要下载
I/AutoDownloadService: 📥 开始下载 Feature 1: 品牌 (5个文件)
I/AutoDownloadService: ✅ Feature 1 下载完成
...
I/AutoDownloadService: 🎉 所有Feature下载完成
I/AutoDownloadService: 🛑 自动下载服务销毁
```

## 配置开关管理

### 启用/禁用开机自启动

```kotlin
val config = AutoDownloadConfig(context)

// 启用开机自启动
config.autoStartOnBoot = true

// 禁用开机自启动
config.autoStartOnBoot = false
```

### 查询当前配置

```kotlin
val config = AutoDownloadConfig(context)
val isEnabled = config.autoStartOnBoot
```

## 电池优化问题

### 部分厂商限制

某些厂商（如小米、华为、OPPO等）的系统会限制应用自启动，需要用户手动授权：

1. **小米**: 设置 → 应用设置 → 应用管理 → 应用 → 自启动
2. **华为**: 设置 → 应用 → 应用启动管理 → 应用 → 手动管理
3. **OPPO**: 设置 → 应用管理 → 应用列表 → 应用 → 自启动

### 检测和引导

可以在应用中检测并引导用户设置：

```kotlin
// TODO: 可以添加引导用户设置自启动权限的逻辑
```

## 性能考虑

### 电池消耗
- 前台服务会消耗电池
- 下载过程消耗网络和CPU
- 建议仅在WiFi环境下使用（可扩展）

### 内存占用
- 服务保持在内存中运行
- 使用协程优化并发
- 下载完成后自动释放资源

### 网络流量
- 模拟模式不消耗真实流量
- 生产模式根据文件大小消耗流量
- 建议添加WiFi检测（可扩展）

## 扩展功能建议

可以考虑添加以下功能：

1. **WiFi检测**: 仅在WiFi下自动下载
2. **充电检测**: 仅在充电时自动下载
3. **时间控制**: 设置自动下载的时间段
4. **失败重试**: 下载失败自动重试
5. **增量更新**: 仅下载更新的文件
6. **优先级设置**: 设置Feature下载优先级

## 测试方法

### 测试开机自启动

1. **模拟器测试**:
   ```bash
   # 重启模拟器
   adb reboot

   # 查看日志
   adb logcat | grep -E "BootCompletedReceiver|AutoDownloadService"
   ```

2. **真机测试**:
   - 关闭设备
   - 重新开机
   - 查看通知栏是否显示下载通知
   - 查看日志输出

### 测试手动启动

1. 打开应用
2. 点击菜单"自动下载全部"
3. 观察通知栏
4. 查看下载进度

### 测试停止功能

1. 启动自动下载
2. 点击"停止自动下载"
3. 观察服务是否停止
4. 通知是否消失

## 注意事项

⚠️ **重要提醒**:

1. **权限授权**: 首次使用需要授予存储权限
2. **厂商限制**: 部分厂商需要手动开启自启动
3. **电池优化**: 可能需要关闭电池优化限制
4. **前台服务**: Android 8.0+ 必须使用前台服务
5. **通知权限**: Android 13+ 需要通知权限

## 故障排查

### 开机后服务没有启动

1. 检查日志是否有 `BootCompletedReceiver` 输出
2. 检查自启动权限是否授予
3. 检查 `autoStartOnBoot` 配置是否为 `true`
4. 检查厂商系统是否限制自启动

### 服务启动后立即停止

1. 检查是否有异常日志
2. 检查download.json是否存在
3. 检查存储权限是否授予
4. 检查磁盘空间是否充足

### 通知不显示

1. 检查通知权限是否授予
2. 检查通知渠道是否创建
3. 检查系统通知设置

## 相关文件索引

| 文件 | 说明 |
|------|------|
| `BootCompletedReceiver.kt` | 开机广播接收器 |
| `AutoDownloadService.kt` | 自动下载服务 |
| `AutoDownloadConfig.kt` | 配置管理 |
| `MainActivity.kt` | 手动启动入口 |
| `AndroidManifest.xml` | 权限和组件注册 |

## API说明

### AutoDownloadService

```kotlin
// 启动服务
AutoDownloadService.start(context)

// 停止服务
AutoDownloadService.stop(context)
```

### AutoDownloadConfig

```kotlin
val config = AutoDownloadConfig(context)

// 读取配置
val autoStart = config.autoStartOnBoot
val autoDownload = config.autoDownloadAll

// 修改配置
config.autoStartOnBoot = true
config.autoDownloadAll = true
```
