package com.momi.watermarker.domain.model

/**
 * Which kind of watermark is applied to an image. The editor switches between
 * these; each source image uses exactly one type at a time.
 */
enum class WatermarkType(val displayName: String) {
    /** A text watermark drawn with the configured font/color. */
    TEXT("Text"),

    /** A (cropped) image/logo stamped onto the photo. */
    IMAGE("Image");

    companion object {
        val DEFAULT = TEXT
    }
}
