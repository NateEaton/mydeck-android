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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: UrlInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(baseUrlInterceptor)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
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
    fun provideNotificationHelper(
        @ApplicationContext context: Context,
        notificationManager: NotificationManagerCompat
    ): NotificationHelper {
        return NotificationHelperImpl(context, notificationManager)
    }
}
