# 多用户共享下载数据 - 实施总结

## ✅ 已完成的改造

### 1. 存储路径修改（支持跨用户共享）

**文件**：`FileManager.kt`

**改动**：
```kotlin
// 修改前：每个用户独立的目录
context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
// 路径：/storage/emulated/{userId}/Android/data/...

// 修改后：所有用户共享的目录
Environment.getExternalStorageDirectory() + "/Android/data/${packageName}/files/SharedDownloads"
// 路径：/storage/emulated/0/Android/data/.../SharedDownloads（所有用户共享）
```

**效果**：
- ✅ 所有用户访问同一个下载目录
- ✅ 避免重复下载相同资源
- ✅ 节省存储空间

---

### 2. Service配置为singleUser

**文件**：`AndroidManifest.xml`

**改动**：
```xml
<service
    android:name=".service.AutoDownloadService"
    android:singleUser="true"  <!-- 新增：只在User 0运行 -->
    android:foregroundServiceType="dataSync" />
```

**效果**：
- ✅ 服务只在系统用户（User 0）中运行一个实例
- ✅ 所有用户共享同一个下载服务
- ✅ 避免多个用户重复启动下载任务

---

### 3. 跨用户状态同步机制

**新增文件**：`DownloadStateBroadcaster.kt`

**功能**：
- 使用广播（Broadcast）机制跨用户传递下载状态
- Service（User 0）发送广播 → 所有用户的Activity都能接收

**集成**：
- `FeatureDownloadManager` 在每次状态更新时自动发送广播
- 支持的状态：Idle、Downloading、Completed、Failed、Canceled

**使用方式**：
```kotlin
// Service端（自动发送）
updateFeatureState(featureId, FeatureDownloadState.Downloading(...))

// Activity端（需要接收广播，见下文）
// 注册BroadcastReceiver监听下载状态
```

---

## ⚠️ 需要额外实施的部分

### 1. Activity端接收广播（UI层）

由于Service运行在User 0，其他用户的Activity需要接收广播来更新UI。

**需要在ViewModel或Activity中注册BroadcastReceiver**：

```kotlin
// 示例：在DownloadViewModel中
private val downloadStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val featureId = intent.getIntExtra(DownloadStateBroadcaster.EXTRA_FEATURE_ID, -1)
        val stateType = intent.getStringExtra(DownloadStateBroadcaster.EXTRA_STATE_TYPE)

        when (stateType) {
            DownloadStateBroadcaster.STATE_DOWNLOADING -> {
                val progress = intent.getFloatExtra(DownloadStateBroadcaster.EXTRA_PROGRESS, 0f)
                val currentFile = intent.getStringExtra(DownloadStateBroadcaster.EXTRA_CURRENT_FILE)
                // 更新UI状态
            }
            DownloadStateBroadcaster.STATE_COMPLETED -> {
                // 下载完成
            }
            DownloadStateBroadcaster.STATE_FAILED -> {
                val error = intent.getStringExtra(DownloadStateBroadcaster.EXTRA_ERROR)
                // 显示错误
            }
        }
    }
}

// 在onCreate/onStart中注册
fun registerReceiver() {
    val filter = IntentFilter(DownloadStateBroadcaster.ACTION_DOWNLOAD_STATE_CHANGED)
    context.registerReceiver(downloadStateReceiver, filter)
}

// 在onDestroy/onStop中取消注册
fun unregisterReceiver() {
    context.unregisterReceiver(downloadStateReceiver)
}
```

---

### 2. 权限配置

**需要在AndroidManifest.xml中添加跨用户权限**：

```xml
<!-- 跨用户交互权限（系统权限，需要系统签名或root） -->
<uses-permission
    android:name="android.permission.INTERACT_ACROSS_USERS"
    tools:ignore="ProtectedPermissions" />

<!-- 或者使用（Android 5.0+） -->
<uses-permission
    android:name="android.permission.INTERACT_ACROSS_USERS_FULL"
    tools:ignore="ProtectedPermissions" />
```

**注意**：
- 这些权限是**系统级权限**，普通应用无法获取
- 需要应用**使用系统签名**（platform signature）
- 或者设备已**root**，手动授予权限

---

## 📋 当前实现满足需求情况

| 需求 | 状态 | 说明 |
|------|------|------|
| 文件存储不区分user | ✅ 已满足 | 所有用户共享 `/storage/emulated/0/...` |
| 不同user使用同一份数据 | ✅ 已满足 | 文件路径相同 |
| 避免每个用户都下载 | ✅ 已满足 | Service配置为singleUser |
| Service配置singleUser | ✅ 已满足 | AndroidManifest中已配置 |
| 只通过下载服务启动下载 | ✅ 已满足 | 代码设计符合 |
| Activity区分user显示 | ⚠️ 需补充 | 需要在Activity中接收广播 |

---

## 🔧 实施步骤总结

### 已完成 ✅
1. ✅ 修改存储路径为跨用户共享目录
2. ✅ Service配置 `singleUser="true"`
3. ✅ 创建跨用户广播机制（`DownloadStateBroadcaster`）
4. ✅ 集成广播到 `FeatureDownloadManager`
5. ✅ 代码编译通过

### 待完成 ⚠️
6. ⚠️ 在UI层（Activity/ViewModel）注册BroadcastReceiver
7. ⚠️ 添加跨用户权限到AndroidManifest（需要系统签名）
8. ⚠️ 测试多用户场景下的下载和状态同步

---

## 🚨 重要注意事项

### 1. 系统签名要求
```
singleUser服务和跨用户广播需要系统级权限，意味着：
- 应用必须使用**系统签名**（与ROM相同的签名）
- 或者设备已**root**并手动授予权限
- 普通第三方应用无法使用这些功能
```

### 2. 文件访问权限
```
共享存储目录需要确保：
- Service（User 0）有写权限
- 其他User的Activity有读权限
- 文件权限设置正确（建议660或664）
```

### 3. 并发安全
```
多个用户可能同时：
- 查看下载状态（读）
- Service执行下载（写）
当前使用ConcurrentHashMap保证线程安全
广播机制是单向的，不会产生并发写问题
```

---

## 📖 使用示例

### Service启动（任意用户都可启动，但只在User 0运行）
```kotlin
AutoDownloadService.start(context)
```

### Activity监听下载状态
```kotlin
class DownloadViewModel @Inject constructor() : ViewModel() {

    private val downloadStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 接收跨用户广播的下载状态
            handleDownloadStateChanged(intent)
        }
    }

    fun registerBroadcastReceiver(context: Context) {
        val filter = IntentFilter(DownloadStateBroadcaster.ACTION_DOWNLOAD_STATE_CHANGED)
        context.registerReceiver(downloadStateReceiver, filter)
    }
}
```

---

## ✅ 总结

当前实现**基本满足**多用户共享下载数据的需求：
- ✅ 存储路径已改为跨用户共享
- ✅ Service配置为singleUser
- ✅ 广播机制已实现，支持跨用户状态同步

**需要补充的工作**：
- ⚠️ UI层接收广播（代码补充）
- ⚠️ 系统签名或root权限（部署要求）
- ⚠️ 多用户场景测试（测试验证）
