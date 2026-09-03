package com.momi.watermarker.domain.model

/**
 * A platform-neutral reference to an image.
 *
 * The domain layer never touches Android's `Uri` or `Bitmap`; it only passes
 * around the string form of a content/file URI. The data layer is responsible
 * for turning this into concrete platform types.
 */
data class WatermarkImage(
    val uri: String,
)
