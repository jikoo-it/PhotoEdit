package com.momi.watermarker.presentation

import com.momi.watermarker.presentation.video.formatTimecode
import org.junit.Assert.assertEquals
import org.junit.Test

class TimecodeTest {

    @Test
    fun zero() {
        assertEquals("0:00.000", formatTimecode(0L))
    }

    @Test
    fun subSecond() {
        assertEquals("0:00.007", formatTimecode(7L))
        assertEquals("0:00.250", formatTimecode(250L))
    }

    @Test
    fun minutesAndMillis() {
        assertEquals("1:02.003", formatTimecode(62_003L))
        assertEquals("0:01.250", formatTimecode(1_250L))
    }

    @Test
    fun hours() {
        assertEquals("1:01:02.003", formatTimecode(3_662_003L))
    }

    @Test
    fun negativeClampsToZero() {
        assertEquals("0:00.000", formatTimecode(-40L))
    }
}
