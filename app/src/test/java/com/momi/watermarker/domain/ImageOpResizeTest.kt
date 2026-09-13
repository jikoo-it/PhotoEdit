package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.ResizeMode
import com.momi.watermarker.domain.model.SizeFitPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageOpResizeTest {

    @Test
    fun `percent identity is 100 percent`() {
        assertTrue(ImageOp.Resize().isIdentity)
        assertFalse(ImageOp.Resize(percent = 0.5f).isIdentity)
    }

    @Test
    fun `percent scales both sides`() {
        val op = ImageOp.Resize(mode = ResizeMode.PERCENT, percent = 0.5f)
        assertEquals(500 to 400, op.targetDimensions(1_000, 800))
    }

    @Test
    fun `longest side downscales and keeps aspect`() {
        val op = ImageOp.Resize(mode = ResizeMode.LONGEST_SIDE, maxDimensionPx = 1_024)
        assertEquals(1_024 to 512, op.targetDimensions(2_048, 1_024))
    }

    @Test
    fun `longest side leaves smaller images unchanged`() {
        val op = ImageOp.Resize(mode = ResizeMode.LONGEST_SIDE, maxDimensionPx = 2_048)
        assertEquals(800 to 600, op.targetDimensions(800, 600))
    }

    @Test
    fun `exact pixels ignore the source size`() {
        val op = ImageOp.Resize(mode = ResizeMode.EXACT, widthPx = 800, heightPx = 600)
        assertEquals(800 to 600, op.targetDimensions(1_920, 1_080))
        assertFalse(op.isIdentity)
    }

    @Test
    fun `exact with no size typed is identity`() {
        assertTrue(ImageOp.Resize(mode = ResizeMode.EXACT).isIdentity)
        assertEquals(1_920 to 1_080, ImageOp.Resize(mode = ResizeMode.EXACT).targetDimensions(1_920, 1_080))
    }

    @Test
    fun `locked width change updates height`() {
        val op = ImageOp.Resize(
            mode = ResizeMode.EXACT,
            widthPx = 1_920,
            heightPx = 1_080,
            lockAspectRatio = true,
        ).withExactWidth(800)
        assertEquals(800, op.widthPx)
        assertEquals(450, op.heightPx)
    }

    @Test
    fun `unlocked width change leaves height`() {
        val op = ImageOp.Resize(
            mode = ResizeMode.EXACT,
            widthPx = 1_920,
            heightPx = 1_080,
            lockAspectRatio = false,
        ).withExactWidth(800)
        assertEquals(800, op.widthPx)
        assertEquals(1_080, op.heightPx)
    }

    @Test
    fun `scaledBy shrinks percent and exact sizes`() {
        val percent = ImageOp.Resize(percent = 1f).scaledBy(0.5f)
        assertEquals(0.5f, percent.percent, 0.0001f)
        val exact = ImageOp.Resize(
            mode = ResizeMode.EXACT,
            widthPx = 1_000,
            heightPx = 800,
        ).scaledBy(0.5f)
        assertEquals(500, exact.widthPx)
        assertEquals(400, exact.heightPx)
    }

    @Test
    fun `custom target size converts whole KB to bytes`() {
        assertEquals(200_000L, ExportOptions.bytesFromKb(200L))
        assertEquals(200L, ExportOptions.kbFromBytes(200_000L))
        assertEquals(ExportOptions.MIN_TARGET_SIZE_BYTES, ExportOptions.bytesFromKb(0L))
    }

    @Test
    fun `size fit planner scales dimensions and flags a quality-only miss`() {
        assertEquals(640 to 360, SizeFitPlanner.scaledDimensions(1_280, 720, 0.5f))
        assertFalse(SizeFitPlanner.needsResize(fitsAtCurrent = true, qualityUsed = 80, formatSupportsQuality = true))
        assertTrue(SizeFitPlanner.needsResize(fitsAtCurrent = false, qualityUsed = 5, formatSupportsQuality = true))
        assertTrue(SizeFitPlanner.needsResize(fitsAtCurrent = true, qualityUsed = 40, formatSupportsQuality = true))
    }
}
