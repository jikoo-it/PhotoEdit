package com.momi.watermarker.data.rendering

import android.graphics.Typeface
import com.momi.watermarker.domain.model.WatermarkFont
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates a domain [WatermarkFont] into a platform [Typeface].
 *
 * Isolating this mapping keeps Android's `Typeface` out of the domain layer and
 * gives the renderer a single, testable seam for font resolution.
 */
@Singleton
class TypefaceProvider @Inject constructor() {

    fun typefaceFor(font: WatermarkFont): Typeface = when (font) {
        WatermarkFont.SANS_SERIF -> Typeface.SANS_SERIF
        WatermarkFont.SERIF -> Typeface.SERIF
        WatermarkFont.MONOSPACE -> Typeface.MONOSPACE
        WatermarkFont.SANS_SERIF_BOLD ->
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        WatermarkFont.SERIF_BOLD ->
            Typeface.create(Typeface.SERIF, Typeface.BOLD)
        WatermarkFont.SANS_SERIF_ITALIC ->
            Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }
}
