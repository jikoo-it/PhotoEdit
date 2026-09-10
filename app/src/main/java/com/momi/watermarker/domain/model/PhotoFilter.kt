package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * A named color preset applied to the whole image. Each value is a pure label;
 * the data layer maps it to a concrete color transformation (a `ColorMatrix`).
 *
 * [NONE] is the identity filter — selecting it contributes no op to the pipeline.
 */
enum class PhotoFilter(@StringRes val labelRes: Int) {
    NONE(R.string.photo_filter_original),
    // Tonal presets.
    MONO(R.string.photo_filter_mono),
    SEPIA(R.string.photo_filter_sepia),
    NOIR(R.string.photo_filter_noir),
    VIVID(R.string.photo_filter_vivid),
    COOL(R.string.photo_filter_cool),
    WARM(R.string.photo_filter_warm),
    VINTAGE(R.string.photo_filter_vintage),
    // Color-tint presets (a colored wash over the image).
    RED(R.string.photo_filter_red),
    ORANGE(R.string.photo_filter_orange),
    GREEN(R.string.photo_filter_green),
    TEAL(R.string.photo_filter_teal),
    BLUE(R.string.photo_filter_blue),
    VIOLET(R.string.photo_filter_violet);

    companion object {
        val DEFAULT = NONE
    }
}
