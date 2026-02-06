package com.mydeck.app.io.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.mydeck.app.io.db.dao.BookmarkDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MyDeckDatabase {
        return Room.databaseBuilder(context, MyDeckDatabase::class.java, "readeck.db")
            .addMigrations(
                MyDeckDatabase.MIGRATION_1_2,
                MyDeckDatabase.MIGRATION_2_3,
                MyDeckDatabase.MIGRATION_3_4,
                MyDeckDatabase.MIGRATION_4_5
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(readeckDatabase: MyDeckDatabase): BookmarkDao =
        readeckDatabase.getBookmarkDao()
}
