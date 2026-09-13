package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.LayerContent
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.model.LayerIds
import com.momi.watermarker.domain.model.StudioBackdrop
import com.momi.watermarker.domain.model.backdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerDocumentTest {

    private fun photo() = LayerDocument.fromPhoto("content://photo", 1000, 800)

    @Test
    fun `fromPhoto starts with a visible background raster`() {
        val doc = photo()
        assertEquals(1, doc.layers.size)
        assertEquals(LayerIds.BACKGROUND, doc.layers.single().id)
        assertEquals(StudioBackdrop.ORIGINAL, doc.backdrop())
        assertFalse(doc.producesTransparency())
        assertFalse(doc.hasSubject())
    }

    @Test
    fun `withSubject stacks the subject above the background`() {
        val doc = photo().withSubject("content://cutout")
        assertEquals(listOf(LayerIds.BACKGROUND, LayerIds.SUBJECT), doc.layers.map { it.id })
        assertEquals(LayerIds.SUBJECT, doc.selectedLayerId)
        assertTrue(doc.hasSubject())
        assertEquals(StudioBackdrop.ORIGINAL, doc.backdrop())
    }

    @Test
    fun `transparent backdrop hides opaque layers but keeps the subject`() {
        val doc = photo().withSubject("content://cutout").showingTransparentBackdrop()
        assertEquals(StudioBackdrop.TRANSPARENT, doc.backdrop())
        assertTrue(doc.producesTransparency())
        assertTrue(doc.layer(LayerIds.SUBJECT)!!.visible)
        assertFalse(doc.layer(LayerIds.BACKGROUND)!!.visible)
    }

    @Test
    fun `color fill hides the original photo`() {
        val doc = photo().withSubject("content://cutout").showingColorFill(0xFF2196F3.toInt())
        assertEquals(StudioBackdrop.COLOR, doc.backdrop())
        assertFalse(doc.producesTransparency())
        assertTrue(doc.layer(LayerIds.FILL)!!.visible)
        assertFalse(doc.layer(LayerIds.BACKGROUND)!!.visible)
        assertEquals(
            listOf(LayerIds.BACKGROUND, LayerIds.FILL, LayerIds.SUBJECT),
            doc.layers.map { it.id },
        )
    }

    @Test
    fun `replacement image hides the original and fill`() {
        val doc = photo()
            .showingColorFill()
            .showingReplacement("content://bg")
        assertEquals(StudioBackdrop.IMAGE, doc.backdrop())
        assertFalse(doc.layer(LayerIds.BACKGROUND)!!.visible)
        assertFalse(doc.layer(LayerIds.FILL)!!.visible)
        assertTrue(doc.layer(LayerIds.REPLACEMENT)!!.visible)
    }

    @Test
    fun `portrait look adds a grayscale adjustment above the background`() {
        val doc = photo().withSubject("content://people").withPortraitLook(true, blurStrength = 0.4f)
        assertTrue(doc.portraitLookEnabled())
        assertEquals(0.4f, doc.blurStrength(), 0.0f)
        assertEquals(StudioBackdrop.ORIGINAL, doc.backdrop())
        val adj = doc.layer(LayerIds.ADJUSTMENT)!!.content as LayerContent.Adjustment
        assertTrue(adj.grayscale)
        assertEquals(
            listOf(LayerIds.BACKGROUND, LayerIds.ADJUSTMENT, LayerIds.SUBJECT),
            doc.layers.map { it.id },
        )
    }

    @Test
    fun `disabling portrait look with no blur removes the adjustment`() {
        val doc = photo().withPortraitLook(true).withPortraitLook(false)
        assertNull(doc.layer(LayerIds.ADJUSTMENT))
        assertFalse(doc.portraitLookEnabled())
    }

    @Test
    fun `disabling portrait look keeps background blur in color`() {
        val doc = photo()
            .withSubject("content://people")
            .withPortraitLook(true)
            .withBlur(0.5f)
            .withPortraitLook(false)
        assertFalse(doc.portraitLookEnabled())
        assertEquals(0.5f, doc.blurStrength(), 0.0f)
        val adj = doc.layer(LayerIds.ADJUSTMENT)!!.content as LayerContent.Adjustment
        assertFalse(adj.grayscale)
    }

    @Test
    fun `blur without portrait look leaves the background in color`() {
        val doc = photo().withSubject("content://people").withBlur(0.4f)
        assertFalse(doc.portraitLookEnabled())
        assertEquals(0.4f, doc.blurStrength(), 0.0f)
        val adj = doc.layer(LayerIds.ADJUSTMENT)!!.content as LayerContent.Adjustment
        assertFalse(adj.grayscale)
    }

    @Test
    fun `transparent backdrop is kept original while portrait look is on`() {
        val doc = photo()
            .withSubject("content://people")
            .withPortraitLook(true)
            .showingTransparentBackdrop()
        assertTrue(doc.portraitLookEnabled())
        assertEquals(StudioBackdrop.ORIGINAL, doc.backdrop())
        assertTrue(doc.layer(LayerIds.BACKGROUND)!!.visible)
        assertFalse(doc.producesTransparency())
    }

    @Test
    fun `background cannot be removed`() {
        val doc = photo().removing(LayerIds.BACKGROUND)
        assertNotNull(doc.layer(LayerIds.BACKGROUND))
    }

    @Test
    fun `removing the selected subject selects the remaining layer`() {
        val doc = photo().withSubject("content://cutout").removing(LayerIds.SUBJECT)
        assertFalse(doc.hasSubject())
        assertEquals(LayerIds.BACKGROUND, doc.selectedLayerId)
    }

    @Test
    fun `toggling visibility does not drop the layer`() {
        val doc = photo().togglingVisibility(LayerIds.BACKGROUND)
        assertFalse(doc.layer(LayerIds.BACKGROUND)!!.visible)
        assertEquals(StudioBackdrop.TRANSPARENT, doc.backdrop())
        assertTrue(doc.producesTransparency())
    }
}
