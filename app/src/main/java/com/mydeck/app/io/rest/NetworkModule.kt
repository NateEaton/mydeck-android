package com.mydeck.app.io.rest

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.mydeck.app.BuildConfig
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.prefs.SettingsDataStoreImpl
import com.mydeck.app.io.rest.auth.AuthInterceptor
import com.mydeck.app.io.rest.auth.NotificationHelper
import com.mydeck.app.io.rest.auth.NotificationHelperImpl
import com.mydeck.app.io.rest.sync.MultipartSyncClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import timber.log.Timber
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("readeckApi")
    fun provideReadeckOkHttpClient(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: UrlInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(ReadeckHttpPolicyInterceptor(BuildConfig.ALLOW_INSECURE_HTTP))

        if (BuildConfig.DEBUG) {
            // Route OkHttp wire logs through Timber so they land in the on-device
            // log file alongside everything else. Default logger writes to logcat
            // only, which is invisible to the in-app log viewer / share flow.
            // Use DEBUG (priority 3), not VERBOSE — the FileLoggerTree is
            // configured at level=3 and would silently drop VERBOSE messages.
            val timberLogger = HttpLoggingInterceptor.Logger { message ->
                Timber.tag("OkHttp").d(message)
            }
            val loggingInterceptor = HttpLoggingInterceptor(timberLogger).apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        @Named("readeckApi")
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val mediaType = "application/json; charset=UTF8".toMediaType()
        return Retrofit.Builder()
            .baseUrl("http://readeck.invalid/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
    }

    @Provides
    @Singleton
    fun provideReadeckApiService(retrofit: Retrofit): ReadeckApi {
        return retrofit.create()
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(impl: SettingsDataStoreImpl): SettingsDataStore {
        return impl
    }

    @Provides
    @Singleton
    fun provideNotificationManagerCompat(@ApplicationContext context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @Singleton
    fun provideMultipartSyncClient(
        readeckApi: ReadeckApi,
        json: Json,
        @ApplicationContext context: Context
    ): MultipartSyncClient {
        val tempDir = File(context.cacheDir, "multipart_sync_temp")
        tempDir.mkdirs()
        return MultipartSyncClient(readeckApi, json, tempDir)
    }

    @Provides
    fun provideNotificationHelper(
        @ApplicationContext context: Context,
        notificationManager: NotificationManagerCompat
    ): NotificationHelper {
        return NotificationHelperImpl(context, notificationManager)
    }
}
