package com.radillo.playerwave.player.di

import com.radillo.playerwave.player.audiotap.AudioTapHub
import com.radillo.playerwave.player.engine.MediaControllerPlaybackController
import com.radillo.playerwave.player.engine.PlaybackController
import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerBindsModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: MediaControllerPlaybackController): PlaybackController

    @Binds
    @Singleton
    abstract fun bindPlaybackClock(impl: MediaControllerPlaybackController): PlaybackClock
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerProvidesModule {
    /** Fuente de PCM del visualizador: el tap instalado en el ExoPlayer del servicio. */
    @Provides
    @Singleton
    fun provideAudioSource(): AudioSource = AudioTapHub.source
}
