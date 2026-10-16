package com.radillo.playerwave.player.di

import com.radillo.playerwave.player.engine.MediaControllerPlaybackController
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: MediaControllerPlaybackController): PlaybackController
}
