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
            // Solo la versión 1 (caché de MediaStore, sin datos del usuario) se puede tirar. Desde la 2
            // hay favoritos y listas: un cambio de esquema futuro debe llevar su migración; sin ella
            // la app falla en pruebas en vez de borrar en silencio lo del usuario al actualizar.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
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
}
