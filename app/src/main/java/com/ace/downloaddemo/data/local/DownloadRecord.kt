package com.ace.downloaddemo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_records")
data class DownloadRecord(
    @PrimaryKey
    val featureId: Int,
    val status: String, // "idle", "downloading", "completed", "failed", "canceled"
    val progress: Float,
    val updatedAt: Long = System.currentTimeMillis()
)
