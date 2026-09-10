package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * The encoding used when writing a processed image out.
 *
 * Pure metadata (label, MIME type, file extension, whether a quality setting
 * applies); the data layer maps each value to its Android `CompressFormat`.
 */
enum class ExportFormat(
    @StringRes val labelRes: Int,
    val mimeType: String,
    val extension: String,
    val supportsQuality: Boolean,
) {
    JPEG(R.string.export_format_jpeg, "image/jpeg", "jpg", supportsQuality = true),
    PNG(R.string.export_format_png, "image/png", "png", supportsQuality = false),
    WEBP(R.string.export_format_webp, "image/webp", "webp", supportsQuality = true);

    companion object {
        val DEFAULT = JPEG
    }
}
