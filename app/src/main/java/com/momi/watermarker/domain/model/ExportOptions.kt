package com.momi.watermarker.domain.model

/** How the export quality is decided. */
enum class CompressionMode {
    /** Encode once at a fixed [ExportOptions.quality]. */
    QUALITY,

    /** Search for the highest quality whose encoded size fits [ExportOptions.targetSizeBytes]. */
    TARGET_SIZE,
}

/**
 * How a finished image is encoded on export (compression).
 *
 * This is deliberately *not* an [ImageOp]: it changes how the final pixels are
 * encoded, not the pixels themselves, so it is applied once at the write stage
 * rather than folded into the [Pipeline].
 *
 * @param format          the container/codec to encode with.
 * @param quality         lossy quality in `0..100` (used in [CompressionMode.QUALITY]);
 *                        ignored for formats where [ExportFormat.supportsQuality] is false.
 * @param mode            whether quality is fixed or chosen to hit a size budget.
 * @param targetSizeBytes size budget for [CompressionMode.TARGET_SIZE] (bytes).
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.DEFAULT,
    val quality: Int = DEFAULT_QUALITY,
    val mode: CompressionMode = CompressionMode.QUALITY,
    val targetSizeBytes: Long? = null,
) {
    init {
        require(quality in 0..100) { "quality must be within 0..100, was $quality" }
        require(targetSizeBytes == null || targetSizeBytes > 0) {
            "targetSizeBytes must be > 0, was $targetSizeBytes"
        }
    }

    /** The quality to actually apply, forced to lossless for formats without quality. */
    val effectiveQuality: Int get() = if (format.supportsQuality) quality else 100

    /**
     * Whether the target-size search actually applies: only in that mode, with a
     * budget set, and a format whose quality can be traded for size.
     */
    val usesTargetSize: Boolean
        get() = mode == CompressionMode.TARGET_SIZE &&
            targetSizeBytes != null &&
            format.supportsQuality

    companion object {
        const val DEFAULT_QUALITY = 90

        /** Target-size presets (in bytes) offered to the user. */
        val TARGET_SIZE_PRESETS = listOf(100_000L, 250_000L, 500_000L, 1_000_000L)
    }
}
