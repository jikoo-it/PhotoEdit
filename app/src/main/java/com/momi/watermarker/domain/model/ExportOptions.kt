package com.momi.watermarker.domain.model

/**
 * How a finished image is encoded on export (compression).
 *
 * This is deliberately *not* an [ImageOp]: it changes how the final pixels are
 * encoded, not the pixels themselves, so it is applied once at the write stage
 * rather than folded into the [Pipeline].
 *
 * @param format  the container/codec to encode with.
 * @param quality lossy quality in `0..100`; ignored for formats where
 *                [ExportFormat.supportsQuality] is false (e.g. PNG).
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.DEFAULT,
    val quality: Int = DEFAULT_QUALITY,
) {
    init {
        require(quality in 0..100) { "quality must be within 0..100, was $quality" }
    }

    /** The quality to actually apply, forced to lossless for formats without quality. */
    val effectiveQuality: Int get() = if (format.supportsQuality) quality else 100

    companion object {
        const val DEFAULT_QUALITY = 90
    }
}
