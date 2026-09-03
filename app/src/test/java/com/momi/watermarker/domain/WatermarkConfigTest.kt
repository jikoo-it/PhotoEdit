package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkConfigTest {

    @Test
    fun `hasText is false for blank text`() {
        assertFalse(WatermarkConfig(text = "   ").hasText)
    }

    @Test
    fun `hasText is true for non-blank text`() {
        assertTrue(WatermarkConfig(text = "©2026").hasText)
    }

    @Test
    fun `text watermark is renderable only with non-blank text`() {
        assertTrue(WatermarkConfig(type = WatermarkType.TEXT, text = "©").isRenderable)
        assertFalse(WatermarkConfig(type = WatermarkType.TEXT, text = " ").isRenderable)
    }

    @Test
    fun `image watermark is renderable only once an image is chosen`() {
        assertFalse(WatermarkConfig(type = WatermarkType.IMAGE, text = "©").isRenderable)
        assertTrue(
            WatermarkConfig(
                type = WatermarkType.IMAGE,
                text = "©",
                imageUri = "content://logo",
            ).isRenderable,
        )
    }

    @Test
    fun `non-positive image size ratio is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WatermarkConfig(text = "x", imageSizeRatio = 0f)
        }
    }

    @Test
    fun `opacity outside range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WatermarkConfig(text = "x", opacity = 1.5f)
        }
    }

    @Test
    fun `non-positive text size ratio is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WatermarkConfig(text = "x", textSizeRatio = 0f)
        }
    }

    @Test
    fun `negative tile and line spacing ratios are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WatermarkConfig(text = "x", tileSpacingRatio = -0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WatermarkConfig(text = "x", lineSpacingRatio = -0.1f)
        }
    }

    @Test
    fun `zero spacing ratios are allowed`() {
        val config = WatermarkConfig(text = "x", tileSpacingRatio = 0f, lineSpacingRatio = 0f)
        assertEquals(0f, config.tileSpacingRatio, 0f)
        assertEquals(0f, config.lineSpacingRatio, 0f)
    }
}
