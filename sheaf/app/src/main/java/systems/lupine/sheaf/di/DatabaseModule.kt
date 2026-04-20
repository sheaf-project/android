package systems.lupine.sheaf.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import systems.lupine.sheaf.data.db.CacheDao
import systems.lupine.sheaf.data.db.PendingOperationsDao
import systems.lupine.sheaf.data.db.SheafDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SheafDatabase =
        Room.databaseBuilder(context, SheafDatabase::class.java, "sheaf.db").build()

    @Provides fun provideCacheDao(db: SheafDatabase): CacheDao = db.cacheDao()

    @Provides fun providePendingOperationsDao(db: SheafDatabase): PendingOperationsDao =
        db.pendingOperationsDao()
}
