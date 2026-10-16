package com.radillo.playerwave.data.permissions

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioPermissionTest {

    @Test
    fun `permiso segun version`() {
        assertThat(AudioPermission.forSdk(32)).isEqualTo(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertThat(AudioPermission.forSdk(33)).isEqualTo(Manifest.permission.READ_MEDIA_AUDIO)
        assertThat(AudioPermission.notificationPermissionOrNull(32)).isNull()
        assertThat(AudioPermission.notificationPermissionOrNull(33)).isEqualTo(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `isGranted refleja el estado del sistema`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertThat(AudioPermission.isGranted(app)).isFalse()
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertThat(AudioPermission.isGranted(app)).isTrue()
    }
}
