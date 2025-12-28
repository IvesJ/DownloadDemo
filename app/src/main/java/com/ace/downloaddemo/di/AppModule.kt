package com.ace.downloaddemo.di

import android.content.Context
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
        return Room.databaseBuilder(
            context,
            DownloadDatabase::class.java,
            "download_database"
        ).build()
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
}
