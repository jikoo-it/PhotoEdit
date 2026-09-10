package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * A target aspect ratio the image can be *padded* to (never cropped) by
 * [ImageOp.AspectPad]. [ratio] is width ÷ height; a null ratio ([ORIGINAL])
 * means "leave the image's own ratio", i.e. the identity.
 */
enum class AspectRatioPreset(@StringRes val labelRes: Int, val ratio: Float?) {
    ORIGINAL(R.string.aspect_original, null),
    SQUARE(R.string.aspect_1_1, 1f),
    R4_3(R.string.aspect_4_3, 4f / 3f),
    R3_4(R.string.aspect_3_4, 3f / 4f),
    R3_2(R.string.aspect_3_2, 3f / 2f),
    R2_3(R.string.aspect_2_3, 2f / 3f),
    R16_9(R.string.aspect_16_9, 16f / 9f),
    R9_16(R.string.aspect_9_16, 9f / 16f);

    companion object {
        val DEFAULT = ORIGINAL
    }
}
