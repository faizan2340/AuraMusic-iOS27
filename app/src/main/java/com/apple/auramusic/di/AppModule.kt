package com.apple.auramusic.di

import android.content.Context
import androidx.room.Room
import com.apple.auramusic.data.local.MusicDatabase
import com.apple.auramusic.data.local.TrackDao
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
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "aura_music_ios27.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideTrackDao(db: MusicDatabase): TrackDao = db.trackDao()
}
