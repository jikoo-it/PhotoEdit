package com.momi.watermarker.presentation

import com.momi.watermarker.presentation.video.resolveVideoDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFileNameTest {

    @Test
    fun blankFallsBack() {
        assertEquals("auto", resolveVideoDisplayName("  ", "auto"))
        assertEquals("auto", resolveVideoDisplayName("...", "auto"))
    }

    @Test
    fun stripsMp4AndIllegalChars() {
        assertEquals("My clip", resolveVideoDisplayName("My clip.mp4", "auto"))
        assertEquals("a_b_c", resolveVideoDisplayName("a/b:c", "auto"))
    }

    @Test
    fun keepsSimpleName() {
        assertEquals("Holiday trim", resolveVideoDisplayName("Holiday trim", "auto"))
    }
}
