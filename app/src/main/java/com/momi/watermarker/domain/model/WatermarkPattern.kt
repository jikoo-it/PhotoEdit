package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * Predefined layout patterns describing *where* and *how many times* the
 * watermark text is drawn over an image.
 *
 * Kept in the domain layer as a pure enum so the choice of patterns is
 * independent of any rendering technology (Canvas, OpenGL, server-side, ...).
 */
enum class WatermarkPattern(@StringRes val labelRes: Int) {
    /** A single watermark centered on the image. */
    SINGLE_CENTER(R.string.pattern_center),

    /** A single watermark in the bottom-right corner. */
    BOTTOM_RIGHT(R.string.pattern_bottom_right),

    /** A single watermark in the bottom-left corner. */
    BOTTOM_LEFT(R.string.pattern_bottom_left),

    /** A single watermark in the top-right corner. */
    TOP_RIGHT(R.string.pattern_top_right),

    /** A single watermark in the top-left corner. */
    TOP_LEFT(R.string.pattern_top_left),

    /** The text repeated edge-to-edge in a horizontal grid. */
    TILED(R.string.pattern_tiled),

    /** The text repeated in a grid and rotated 45° across the whole image. */
    DIAGONAL(R.string.pattern_diagonal);

    companion object {
        val DEFAULT = BOTTOM_RIGHT
    }
}
