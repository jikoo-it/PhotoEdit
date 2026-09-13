package com.momi.watermarker.domain.model

import kotlin.math.roundToInt

/**
 * A recommended resize (aspect kept) plus the estimated encoded size after
 * combining that resize with target-size compression.
 *
 * [scalePercent] is relative to the currently shown pixels (`1f` = no shrink).
 */
data class SizeFitSuggestion(
    val widthPx: Int,
    val heightPx: Int,
    val scalePercent: Float,
    val estimatedBytes: Long,
    val needsResize: Boolean,
    val canMeetTarget: Boolean,
) {
    val scalePercentInt: Int get() = (scalePercent * 100f).roundToInt().coerceIn(1, 100)
}

/** Byte-size estimate of the current pixels, plus an optional resize hint. */
data class ExportSizeAnalysis(
    val estimatedBytes: Long,
    val suggestion: SizeFitSuggestion? = null,
)

/** Pixel math for fitting an image into a file-size budget. */
object SizeFitPlanner {
    /** JPEG/WebP quality used while searching for a scale that still looks decent. */
    const val PREFERRED_QUALITY = 75

    /** If current pixels already fit at this quality or higher, skip resizing. */
    const val GOOD_ENOUGH_QUALITY = 70

    const val MIN_SCALE = 0.05f
    const val MIN_DIMENSION_PX = 16

    fun scaledDimensions(width: Int, height: Int, scale: Float): Pair<Int, Int> {
        val s = scale.coerceIn(MIN_SCALE, 1f)
        return (width * s).roundToInt().coerceAtLeast(MIN_DIMENSION_PX) to
            (height * s).roundToInt().coerceAtLeast(MIN_DIMENSION_PX)
    }

    /**
     * Whether shrinking pixels is worth suggesting: the current size misses the
     * budget, or it only fits at a quality below [GOOD_ENOUGH_QUALITY].
     */
    fun needsResize(fitsAtCurrent: Boolean, qualityUsed: Int, formatSupportsQuality: Boolean): Boolean {
        if (!fitsAtCurrent) return true
        if (!formatSupportsQuality) return false
        return qualityUsed < GOOD_ENOUGH_QUALITY
    }
}
