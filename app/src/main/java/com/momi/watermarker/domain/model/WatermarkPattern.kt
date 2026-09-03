package com.momi.watermarker.domain.model

/**
 * Predefined layout patterns describing *where* and *how many times* the
 * watermark text is drawn over an image.
 *
 * Kept in the domain layer as a pure enum so the choice of patterns is
 * independent of any rendering technology (Canvas, OpenGL, server-side, ...).
 */
enum class WatermarkPattern(val displayName: String) {
    /** A single watermark centered on the image. */
    SINGLE_CENTER("Center"),

    /** A single watermark in the bottom-right corner. */
    BOTTOM_RIGHT("Bottom Right"),

    /** A single watermark in the bottom-left corner. */
    BOTTOM_LEFT("Bottom Left"),

    /** A single watermark in the top-right corner. */
    TOP_RIGHT("Top Right"),

    /** A single watermark in the top-left corner. */
    TOP_LEFT("Top Left"),

    /** The text repeated edge-to-edge in a horizontal grid. */
    TILED("Tiled"),

    /** The text repeated in a grid and rotated 45° across the whole image. */
    DIAGONAL("Diagonal");

    companion object {
        val DEFAULT = BOTTOM_RIGHT
    }
}
