package com.ace.downloaddemo.data.repository

import com.ace.downloaddemo.data.local.DownloadDao
import com.ace.downloaddemo.data.local.DownloadRecord
import com.ace.downloaddemo.domain.repository.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override suspend fun saveDownloadRecord(featureId: Int, status: String, progress: Float) {
        val record = DownloadRecord(
            featureId = featureId,
            status = status,
            progress = progress,
            updatedAt = System.currentTimeMillis()
        )
        downloadDao.insert(record)
    }

    override suspend fun getDownloadStatus(featureId: Int): DownloadRecord? {
        return downloadDao.getRecord(featureId)
    }

    override suspend fun getAllDownloadRecords(): List<DownloadRecord> {
        return downloadDao.getAllRecords()
    }

    override suspend fun deleteRecord(featureId: Int) {
        downloadDao.deleteRecord(featureId)
    }

    override suspend fun clearAll() {
        downloadDao.deleteAll()
    }
}
