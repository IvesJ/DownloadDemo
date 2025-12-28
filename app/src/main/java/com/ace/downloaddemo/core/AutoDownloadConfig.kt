package com.ace.downloaddemo.core

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动下载配置管理
 * 用于保存和读取自动下载相关的配置
 */
@Singleton
class AutoDownloadConfig @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "auto_download_config"
        private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        private const val KEY_AUTO_DOWNLOAD_ALL = "auto_download_all"

        /**
         * 是否启用开机自启动
         * TODO: 生产环境可根据需求设置默认值
         */
        const val DEFAULT_AUTO_START_ON_BOOT = true
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 是否开机自启动
     */
    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, DEFAULT_AUTO_START_ON_BOOT)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_ON_BOOT, value).apply()

    /**
     * 是否自动下载所有Feature
     */
    var autoDownloadAll: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DOWNLOAD_ALL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_ALL, value).apply()
}
