package com.momi.watermarker.domain.model

/** How a [ImageOp.Resize] computes its target dimensions. */
enum class ResizeMode(val label: String) {
    /** Scale both dimensions by a percentage of the original. */
    PERCENT("Scale"),

    /** Downscale so the longest side is at most a given pixel count (aspect kept). */
    LONGEST_SIDE("Max size");

    companion object {
        val DEFAULT = PERCENT
    }
}
