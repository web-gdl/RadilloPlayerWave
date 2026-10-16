package com.radillo.playerwave.di

import android.content.Context
import com.radillo.playerwave.core.device.DeviceProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {

    /** Perfil del hardware (RAM, nivel de API real, pantalla), calculado una vez por proceso. */
    @Provides
    @Singleton
    fun provideDeviceProfile(@ApplicationContext context: Context): DeviceProfile = DeviceProfile.detect(context)
}
