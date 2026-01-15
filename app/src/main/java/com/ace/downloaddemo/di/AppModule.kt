package com.ace.downloaddemo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.ace.downloaddemo.core.AutoDownloadConfig
import com.ace.downloaddemo.core.download.FileDownloader
import com.ace.downloaddemo.core.download.FileDownloaderImpl
import com.ace.downloaddemo.data.local.DownloadDao
import com.ace.downloaddemo.data.local.DownloadDatabase
import com.ace.downloaddemo.data.repository.DownloadRepositoryImpl
import com.ace.downloaddemo.domain.repository.DownloadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideAutoDownloadConfig(@ApplicationContext context: Context): AutoDownloadConfig {
        return AutoDownloadConfig(context)
    }

    @Provides
    @Singleton
    fun provideDownloadDatabase(@ApplicationContext context: Context): DownloadDatabase {
        // 系统应用跨用户共享数据库方案：
        // 方案1: 使用设备加密存储 (Device Protected Storage)，所有用户共享
        // 方案2: 使用应用内部存储 + 系统权限

        // 这里使用设备加密存储，确保数据在多用户间共享且不会因用户切换而隔离
        val deviceContext = context.createDeviceProtectedStorageContext()

        // 创建共享数据库目录
        val dbDir = java.io.File(deviceContext.filesDir, "SharedDownloads")
        if (!dbDir.exists()) {
            val created = dbDir.mkdirs()
            android.util.Log.i("AppModule", "创建数据库目录: ${dbDir.absolutePath}, 结果: $created")
        }

        val dbFile = java.io.File(dbDir, "download_database.db")
        android.util.Log.i("AppModule", "数据库路径: ${dbFile.absolutePath}")

        return Room.databaseBuilder(
            context,
            DownloadDatabase::class.java,
            dbFile.absolutePath
        )
            .fallbackToDestructiveMigration()  // 简化起见，使用破坏性迁移（生产环境应使用Migration）
            .build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideFileDownloader(impl: FileDownloaderImpl): FileDownloader {
        return impl
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
    }
}
