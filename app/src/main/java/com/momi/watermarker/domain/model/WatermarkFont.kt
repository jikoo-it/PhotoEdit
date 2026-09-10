package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * Predefined font families the user can choose for the watermark text.
 *
 * The domain layer only names the fonts; mapping a [WatermarkFont] to a
 * platform `Typeface` is the data/rendering layer's responsibility, keeping
 * the domain free of Android dependencies.
 */
enum class WatermarkFont(@StringRes val labelRes: Int) {
    SANS_SERIF(R.string.font_sans_serif),
    SERIF(R.string.font_serif),
    MONOSPACE(R.string.font_monospace),
    SANS_SERIF_BOLD(R.string.font_sans_bold),
    SERIF_BOLD(R.string.font_serif_bold),
    SANS_SERIF_ITALIC(R.string.font_sans_italic);

    companion object {
        val DEFAULT = SANS_SERIF_BOLD
    }
}
