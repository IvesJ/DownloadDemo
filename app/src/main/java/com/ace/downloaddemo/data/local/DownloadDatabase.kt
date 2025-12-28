package com.ace.downloaddemo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadRecord::class],
    version = 1,
    exportSchema = false
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
