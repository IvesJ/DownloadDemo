package com.ace.downloaddemo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Query("SELECT * FROM download_records WHERE featureId = :featureId")
    suspend fun getRecord(featureId: Int): DownloadRecord?

    @Query("SELECT * FROM download_records")
    suspend fun getAllRecords(): List<DownloadRecord>

    @Query("DELETE FROM download_records WHERE featureId = :featureId")
    suspend fun deleteRecord(featureId: Int)

    @Query("DELETE FROM download_records")
    suspend fun deleteAll()

    @Query("UPDATE download_records SET status = :status, progress = :progress, updatedAt = :updatedAt WHERE featureId = :featureId")
    suspend fun updateStatus(featureId: Int, status: String, progress: Float, updatedAt: Long)
}
