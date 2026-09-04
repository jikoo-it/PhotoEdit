package com.momi.watermarker.domain.model

/**
 * A target aspect ratio the image can be *padded* to (never cropped) by
 * [ImageOp.AspectPad]. [ratio] is width ÷ height; a null ratio ([ORIGINAL])
 * means "leave the image's own ratio", i.e. the identity.
 */
enum class AspectRatioPreset(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    R4_3("4:3", 4f / 3f),
    R3_4("3:4", 3f / 4f),
    R3_2("3:2", 3f / 2f),
    R2_3("2:3", 2f / 3f),
    R16_9("16:9", 16f / 9f),
    R9_16("9:16", 9f / 16f);

    companion object {
        val DEFAULT = ORIGINAL
    }
}
