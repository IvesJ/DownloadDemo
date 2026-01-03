package com.ace.downloaddemo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    // ========== 下载记录相关 ==========
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

    // ========== 下载状态相关（新增，用于跨用户共享） ==========

    /**
     * 插入或更新下载状态
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateState(state: DownloadStateEntity)

    /**
     * 获取Feature的下载状态
     */
    @Query("SELECT * FROM download_states WHERE featureId = :featureId")
    suspend fun getState(featureId: Int): DownloadStateEntity?

    /**
     * 观察Feature的下载状态（响应式）
     */
    @Query("SELECT * FROM download_states WHERE featureId = :featureId")
    fun observeState(featureId: Int): Flow<DownloadStateEntity?>

    /**
     * 获取所有下载状态
     */
    @Query("SELECT * FROM download_states ORDER BY lastUpdatedTime DESC")
    suspend fun getAllStates(): List<DownloadStateEntity>

    /**
     * 观察所有下载状态（响应式）
     */
    @Query("SELECT * FROM download_states ORDER BY lastUpdatedTime DESC")
    fun observeAllStates(): Flow<List<DownloadStateEntity>>

    /**
     * 删除Feature的下载状态
     */
    @Query("DELETE FROM download_states WHERE featureId = :featureId")
    suspend fun deleteState(featureId: Int)

    /**
     * 清空所有下载状态
     */
    @Query("DELETE FROM download_states")
    suspend fun deleteAllStates()
}
