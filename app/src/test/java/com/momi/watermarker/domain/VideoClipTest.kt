package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.normalizeRotationDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoClipTest {

    @Test
    fun normalizeWrapsOntoQuarterTurns() {
        assertEquals(0, normalizeRotationDegrees(0))
        assertEquals(90, normalizeRotationDegrees(90))
        assertEquals(0, normalizeRotationDegrees(360))
        assertEquals(90, normalizeRotationDegrees(450))
        assertEquals(270, normalizeRotationDegrees(-90))
        assertEquals(180, normalizeRotationDegrees(-180))
    }

    @Test
    fun previewDeltaIsExportMinusMetadata() {
        val clip = VideoClip(
            uri = "content://clip",
            metadataRotationDegrees = 90,
            rotationDegrees = 90,
        )
        assertEquals(0, clip.previewRotationDelta)

        assertEquals(
            90,
            clip.copy(rotationDegrees = 180).previewRotationDelta,
        )
        assertEquals(
            270,
            clip.copy(rotationDegrees = 0).previewRotationDelta,
        )
    }

    @Test
    fun displayAspectSwapsOnQuarterTurn() {
        val landscape = VideoClip(
            uri = "content://clip",
            encodedWidth = 1920,
            encodedHeight = 1080,
            rotationDegrees = 0,
        )
        assertEquals(16f / 9f, landscape.displayAspectRatioOrNull()!!, 0.001f)

        val portraitPhone = landscape.copy(rotationDegrees = 90)
        assertEquals(9f / 16f, portraitPhone.displayAspectRatioOrNull()!!, 0.001f)

        assertNull(VideoClip(uri = "content://clip").displayAspectRatioOrNull())
    }
}
