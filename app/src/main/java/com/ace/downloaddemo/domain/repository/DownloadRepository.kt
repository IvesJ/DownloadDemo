package com.ace.downloaddemo.domain.repository

import com.ace.downloaddemo.data.local.DownloadRecord

interface DownloadRepository {
    suspend fun saveDownloadRecord(featureId: Int, status: String, progress: Float)
    suspend fun getDownloadStatus(featureId: Int): DownloadRecord?
    suspend fun getAllDownloadRecords(): List<DownloadRecord>
    suspend fun deleteRecord(featureId: Int)
    suspend fun clearAll()
}
