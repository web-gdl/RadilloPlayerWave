package com.radillo.playerwave.data.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.radillo.playerwave.data.collections.PlaylistRepository
import com.radillo.playerwave.data.collections.PlaylistRepositoryImpl
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.data.library.LibraryRepositoryImpl
import com.radillo.playerwave.data.library.db.RadilloDatabase
import com.radillo.playerwave.data.settings.AppSettingsRepository
import com.radillo.playerwave.data.settings.AudioEffectsSettingsAdapter
import com.radillo.playerwave.data.settings.AudioEffectsSettingsRepository
import com.radillo.playerwave.data.settings.PrefsAppSettingsRepository
import com.radillo.playerwave.data.settings.PrefsVisualizerSettingsRepository
import com.radillo.playerwave.data.settings.VisualizerSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RadilloDatabase =
        Room.databaseBuilder(context, RadilloDatabase::class.java, RadilloDatabase.NAME)
            // La tabla es una caché de MediaStore: ante un cambio de esquema se regenera escaneando.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindVisualizerSettings(impl: PrefsVisualizerSettingsRepository): VisualizerSettingsRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindAppSettings(impl: PrefsAppSettingsRepository): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAudioEffectsSettings(impl: AudioEffectsSettingsAdapter): AudioEffectsSettingsRepository
}
