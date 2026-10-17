package com.radillo.playerwave.data.tags

import kotlin.random.Random

/** «Audio» de prueba con tramas MPEG-1 capa III reales (128 kbps, 44,1 kHz: 417 bytes cada una). */
object MpegFixtures {
    const val FRAME = 417

    fun frames(count: Int, seed: Int = 42): ByteArray {
        val random = Random(seed)
        val out = ByteArray(count * FRAME)
        for (i in 0 until count) {
            val o = i * FRAME
            out[o] = 0xFF.toByte()
            out[o + 1] = 0xFB.toByte()
            out[o + 2] = 0x90.toByte()
            out[o + 3] = 0x64
            random.nextBytes(out, o + 4, o + FRAME)
        }
        return out
    }
}
