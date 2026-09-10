package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/** How a [ImageOp.Resize] computes its target dimensions. */
enum class ResizeMode(@StringRes val labelRes: Int) {
    /** Scale both dimensions by a percentage of the original. */
    PERCENT(R.string.resize_mode_percent),

    /** Downscale so the longest side is at most a given pixel count (aspect kept). */
    LONGEST_SIDE(R.string.resize_mode_longest_side);

    companion object {
        val DEFAULT = PERCENT
    }
}
