package com.momi.watermarker.domain.model

/**
 * A platform-neutral reference to a video.
 *
 * Mirrors [WatermarkImage]: the domain layer only passes around the string form
 * of a content/file URI plus optional metadata. The data layer turns this into
 * concrete Android types ([android.net.Uri], MediaCodec, etc.).
 */
data class VideoClip(
    val uri: String,
    /** Duration in milliseconds, when known (null until probed). */
    val durationMs: Long? = null,
)
