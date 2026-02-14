package com.mydeck.app

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.mydeck.app.coroutine.ApplicationScope
import com.mydeck.app.coroutine.IoDispatcher
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.BookmarkRepositoryImpl
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.UserRepositoryImpl
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.domain.sync.ConnectivityMonitorImpl
import com.mydeck.app.io.rest.NetworkModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module(includes = [NetworkModule::class])
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindBookmarkRepository(bookmarkRepositoryImpl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    abstract fun bindUserRepository(userRepositoryImpl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: ConnectivityMonitorImpl): ConnectivityMonitor

    companion object {
        @Singleton
        @Provides
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope {
            return CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        @Singleton
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true // Handle unknown keys gracefully
                isLenient = true // Allow lenient parsing
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}