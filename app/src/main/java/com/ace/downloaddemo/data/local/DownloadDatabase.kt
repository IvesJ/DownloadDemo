package com.ace.downloaddemo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadRecord::class,
        DownloadStateEntity::class  // 新增：下载状态实体
    ],
    version = 2,  // 版本升级
    exportSchema = false
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
