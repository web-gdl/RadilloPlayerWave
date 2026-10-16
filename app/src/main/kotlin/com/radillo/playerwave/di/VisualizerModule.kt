package com.radillo.playerwave.di

import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import com.radillo.playerwave.visualizer.engine.DefaultScenes
import com.radillo.playerwave.visualizer.engine.VisualizerEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisualizerModule {

    /** Un único motor por proceso: conserva el estado de las escenas entre pantallas. */
    @Provides
    @Singleton
    fun provideVisualizerEngine(source: AudioSource, clock: PlaybackClock): VisualizerEngine =
        VisualizerEngine(source = source, clock = clock, registry = DefaultScenes.registry())
}
