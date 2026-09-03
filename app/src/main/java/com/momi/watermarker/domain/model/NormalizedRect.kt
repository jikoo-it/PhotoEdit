package com.momi.watermarker.domain.model

/**
 * A crop rectangle expressed as fractions (0f..1f) of an image's width and
 * height, so it stays resolution-independent: the presentation layer measures
 * the crop against the on-screen image, and the data layer applies the same
 * fractions to the full-resolution bitmap.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) {
            "NormalizedRect bounds must be within 0f..1f"
        }
        require(right > left && bottom > top) { "NormalizedRect must have positive area" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** The whole image. */
        val FULL = NormalizedRect(0f, 0f, 1f, 1f)
    }
}
