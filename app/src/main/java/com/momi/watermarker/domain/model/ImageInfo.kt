package com.momi.watermarker.domain.model

/**
 * Lightweight metadata about an image, read without decoding the full bitmap:
 * its pixel [width] and [height] (already EXIF-oriented) and its encoded byte
 * size ([sizeBytes], or `null` when it can't be determined).
 */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val sizeBytes: Long?,
)
