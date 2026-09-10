package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * Which kind of watermark is applied to an image. The editor switches between
 * these; each source image uses exactly one type at a time.
 */
enum class WatermarkType(@StringRes val labelRes: Int) {
    /** A text watermark drawn with the configured font/color. */
    TEXT(R.string.watermark_type_text),

    /** A (cropped) image/logo stamped onto the photo. */
    IMAGE(R.string.watermark_type_image);

    companion object {
        val DEFAULT = TEXT
    }
}
