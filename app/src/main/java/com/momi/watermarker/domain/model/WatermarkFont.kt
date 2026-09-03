package com.momi.watermarker.domain.model

/**
 * Predefined font families the user can choose for the watermark text.
 *
 * The domain layer only names the fonts; mapping a [WatermarkFont] to a
 * platform `Typeface` is the data/rendering layer's responsibility, keeping
 * the domain free of Android dependencies.
 */
enum class WatermarkFont(val displayName: String) {
    SANS_SERIF("Sans Serif"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    SANS_SERIF_BOLD("Sans Bold"),
    SERIF_BOLD("Serif Bold"),
    SANS_SERIF_ITALIC("Sans Italic");

    companion object {
        val DEFAULT = SANS_SERIF_BOLD
    }
}
