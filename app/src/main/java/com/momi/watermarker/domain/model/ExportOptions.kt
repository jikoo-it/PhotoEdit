package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/** How the export quality is decided. */
enum class CompressionMode(@StringRes val labelRes: Int) {
    /** Encode once at a fixed [ExportOptions.quality]. */
    QUALITY(R.string.compression_quality),

    /** Search for the highest quality whose encoded size fits [ExportOptions.targetSizeBytes]. */
    TARGET_SIZE(R.string.compression_target_size),

    /**
     * Shrink pixels (aspect kept) only if quality-only compression cannot hit
     * [ExportOptions.targetSizeBytes], then encode to that budget.
     */
    FIT_TO_SIZE(R.string.compression_fit_to_size),
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

    /** Whether pixels may be shrunk so the encoded file hits the size budget. */
    val usesFitToSize: Boolean
        get() = mode == CompressionMode.FIT_TO_SIZE &&
            targetSizeBytes != null &&
            format.supportsQuality

    companion object {
        const val DEFAULT_QUALITY = 90

        /** Target-size presets (in bytes) offered to the user. */
        val TARGET_SIZE_PRESETS = listOf(100_000L, 250_000L, 500_000L, 1_000_000L)

        const val MIN_TARGET_SIZE_BYTES = 1_000L
        const val MAX_TARGET_SIZE_BYTES = 50_000_000L

        /** Converts a whole-KB value typed by the user into a byte budget. */
        fun bytesFromKb(kb: Long): Long =
            (kb * 1_000L).coerceIn(MIN_TARGET_SIZE_BYTES, MAX_TARGET_SIZE_BYTES)

        /** Whole KB shown in the custom-size field for a byte budget. */
        fun kbFromBytes(bytes: Long): Long = (bytes / 1_000L).coerceAtLeast(1L)
    }
}
