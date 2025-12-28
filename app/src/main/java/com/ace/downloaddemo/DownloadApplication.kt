package com.ace.downloaddemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DownloadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
