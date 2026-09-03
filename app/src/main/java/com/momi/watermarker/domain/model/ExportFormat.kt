package com.momi.watermarker.domain.model

/**
 * The encoding used when writing a processed image out.
 *
 * Pure metadata (label, MIME type, file extension, whether a quality setting
 * applies); the data layer maps each value to its Android `CompressFormat`.
 */
enum class ExportFormat(
    val label: String,
    val mimeType: String,
    val extension: String,
    val supportsQuality: Boolean,
) {
    JPEG("JPEG", "image/jpeg", "jpg", supportsQuality = true),
    PNG("PNG", "image/png", "png", supportsQuality = false),
    WEBP("WebP", "image/webp", "webp", supportsQuality = true);

    companion object {
        val DEFAULT = JPEG
    }
}
