package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * A preset color look applied to a whole video, mapped in the data layer to
 * Media3 color effects. Platform-neutral: the presentation layer offers these
 * by [labelRes] and the transformer turns each into the matching GL effect.
 */
enum class VideoColorFilter(@StringRes val labelRes: Int) {
    NONE(R.string.video_filter_none),
    GRAYSCALE(R.string.video_filter_bw),
    INVERT(R.string.video_filter_invert),
    WARM(R.string.video_filter_warm),
    COOL(R.string.video_filter_cool),
    BRIGHT(R.string.video_filter_bright),
    DARK(R.string.video_filter_dark),
    HIGH_CONTRAST(R.string.video_filter_punch);

    companion object {
        val DEFAULT = NONE
    }
}
