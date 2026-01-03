package com.ace.downloaddemo

import android.app.Application
import com.ace.downloaddemo.data.service.DownloadServiceManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DownloadApplication : Application() {

    @Inject
    lateinit var downloadServiceManager: DownloadServiceManager

    override fun onCreate() {
        super.onCreate()

        // 在Application启动时绑定下载服务（Singleton，全局共享）
        downloadServiceManager.bindService()
    }

    override fun onTerminate() {
        // 注意：onTerminate()只在模拟器中调用，生产环境不会调用
        // 对于系统应用，Service通常应该持续运行，不需要解绑
        downloadServiceManager.unbindService()
        super.onTerminate()
    }
}
