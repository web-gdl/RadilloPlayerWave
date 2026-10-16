package com.radillo.playerwave.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `formatea minutos y horas`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(215_000)).isEqualTo("3:35")
        assertThat(formatDuration(3_725_000)).isEqualTo("1:02:05")
        assertThat(formatDuration(-5_000)).isEqualTo("0:00")
    }
}
