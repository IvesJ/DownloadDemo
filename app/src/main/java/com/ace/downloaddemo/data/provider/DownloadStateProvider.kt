package com.ace.downloaddemo.data.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.ace.downloaddemo.data.local.DownloadDao
import com.ace.downloaddemo.data.local.DownloadDatabase
import com.ace.downloaddemo.data.local.DownloadStateEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

/**
 * ContentProvider for cross-user download state sharing
 *
 * 跨用户下载状态共享的 ContentProvider
 *
 * 工作原理：
 * - Service (singleUser=true) 运行在 User 0，提供此 Provider
 * - 其他用户的 Activity 通过 ContentResolver 访问数据
 * - 使用 ContentObserver 监听数据变化，实现实时同步
 */
class DownloadStateProvider : ContentProvider() {

    companion object {
        private const val TAG = "DownloadStateProvider"

        // Authority（必须与 AndroidManifest.xml 中配置一致）
        const val AUTHORITY = "com.ace.downloaddemo.provider.download"

        // Content URI
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/states")

        // URI 匹配码
        private const val STATES = 1
        private const val STATE_ID = 2

        // URI 匹配器
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "states", STATES)
            addURI(AUTHORITY, "states/#", STATE_ID)
        }

        // Cursor 列名
        object Columns {
            const val FEATURE_ID = "featureId"
            const val STATE_TYPE = "stateType"
            const val PROGRESS = "progress"
            const val CURRENT_FILE = "currentFile"
            const val COMPLETED_FILES = "completedFiles"
            const val TOTAL_FILES = "totalFiles"
            const val ERROR = "error"
            const val FAILED_FILE = "failedFile"
            const val LAST_UPDATED_TIME = "lastUpdatedTime"
        }
    }

    // Hilt EntryPoint for accessing DownloadDao
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadStateProviderEntryPoint {
        fun downloadDao(): DownloadDao
    }

    private lateinit var downloadDao: DownloadDao

    override fun onCreate(): Boolean {
        Log.i(TAG, "📡 DownloadStateProvider 初始化")

        // 通过 Hilt EntryPoint 获取 DownloadDao
        val appContext = context?.applicationContext ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            DownloadStateProviderEntryPoint::class.java
        )
        downloadDao = entryPoint.downloadDao()

        Log.i(TAG, "✅ DownloadStateProvider 初始化完成")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "📥 query: $uri")

        return when (uriMatcher.match(uri)) {
            STATES -> {
                // 查询所有状态
                queryAllStates()
            }
            STATE_ID -> {
                // 查询单个状态
                val featureId = ContentUris.parseId(uri).toInt()
                queryStateById(featureId)
            }
            else -> {
                Log.e(TAG, "❌ Unknown URI: $uri")
                null
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.d(TAG, "📝 insert: $uri")

        if (values == null) {
            Log.e(TAG, "❌ ContentValues is null")
            return null
        }

        return when (uriMatcher.match(uri)) {
            STATES -> {
                val entity = contentValuesToEntity(values)
                runBlocking {
                    downloadDao.insertOrUpdateState(entity)
                }
                // 通知数据变化
                context?.contentResolver?.notifyChange(CONTENT_URI, null)
                ContentUris.withAppendedId(CONTENT_URI, entity.featureId.toLong())
            }
            else -> {
                Log.e(TAG, "❌ Unknown URI: $uri")
                null
            }
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        Log.d(TAG, "🔄 update: $uri")

        if (values == null) {
            Log.e(TAG, "❌ ContentValues is null")
            return 0
        }

        return when (uriMatcher.match(uri)) {
            STATE_ID -> {
                val featureId = ContentUris.parseId(uri).toInt()
                val entity = contentValuesToEntity(values).copy(featureId = featureId)
                runBlocking {
                    downloadDao.insertOrUpdateState(entity)
                }
                // 通知数据变化
                context?.contentResolver?.notifyChange(CONTENT_URI, null)
                1
            }
            else -> {
                Log.e(TAG, "❌ Unknown URI: $uri")
                0
            }
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.d(TAG, "🗑️ delete: $uri")

        return when (uriMatcher.match(uri)) {
            STATE_ID -> {
                val featureId = ContentUris.parseId(uri).toInt()
                runBlocking {
                    downloadDao.deleteState(featureId)
                }
                // 通知数据变化
                context?.contentResolver?.notifyChange(CONTENT_URI, null)
                1
            }
            STATES -> {
                runBlocking {
                    downloadDao.deleteAllStates()
                }
                // 通知数据变化
                context?.contentResolver?.notifyChange(CONTENT_URI, null)
                1
            }
            else -> {
                Log.e(TAG, "❌ Unknown URI: $uri")
                0
            }
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            STATES -> "vnd.android.cursor.dir/vnd.$AUTHORITY.states"
            STATE_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.state"
            else -> null
        }
    }

    /**
     * 查询所有下载状态
     */
    private fun queryAllStates(): Cursor {
        val states = runBlocking { downloadDao.getAllStates() }
        return entitiesToCursor(states)
    }

    /**
     * 查询指定 Feature 的下载状态
     */
    private fun queryStateById(featureId: Int): Cursor {
        val state = runBlocking { downloadDao.getState(featureId) }
        return if (state != null) {
            entitiesToCursor(listOf(state))
        } else {
            MatrixCursor(getAllColumnNames())
        }
    }

    /**
     * 将实体列表转换为 Cursor
     */
    private fun entitiesToCursor(entities: List<DownloadStateEntity>): Cursor {
        val cursor = MatrixCursor(getAllColumnNames())

        entities.forEach { entity ->
            cursor.addRow(arrayOf(
                entity.featureId,
                entity.stateType,
                entity.progress,
                entity.currentFile,
                entity.completedFiles,
                entity.totalFiles,
                entity.error,
                entity.failedFile,
                entity.lastUpdatedTime
            ))
        }

        return cursor
    }

    /**
     * 将 ContentValues 转换为实体
     */
    private fun contentValuesToEntity(values: ContentValues): DownloadStateEntity {
        return DownloadStateEntity(
            featureId = values.getAsInteger(Columns.FEATURE_ID) ?: 0,
            stateType = values.getAsString(Columns.STATE_TYPE) ?: DownloadStateEntity.STATE_IDLE,
            progress = values.getAsFloat(Columns.PROGRESS) ?: 0f,
            currentFile = values.getAsString(Columns.CURRENT_FILE) ?: "",
            completedFiles = values.getAsInteger(Columns.COMPLETED_FILES) ?: 0,
            totalFiles = values.getAsInteger(Columns.TOTAL_FILES) ?: 0,
            error = values.getAsString(Columns.ERROR) ?: "",
            failedFile = values.getAsString(Columns.FAILED_FILE) ?: "",
            lastUpdatedTime = values.getAsLong(Columns.LAST_UPDATED_TIME) ?: System.currentTimeMillis()
        )
    }

    /**
     * 获取所有列名
     */
    private fun getAllColumnNames(): Array<String> {
        return arrayOf(
            Columns.FEATURE_ID,
            Columns.STATE_TYPE,
            Columns.PROGRESS,
            Columns.CURRENT_FILE,
            Columns.COMPLETED_FILES,
            Columns.TOTAL_FILES,
            Columns.ERROR,
            Columns.FAILED_FILE,
            Columns.LAST_UPDATED_TIME
        )
    }
}
