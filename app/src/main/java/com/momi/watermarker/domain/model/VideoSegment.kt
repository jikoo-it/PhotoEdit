package com.momi.watermarker.domain.model

/**
 * One clip in a [VideoEditRequest]'s concatenation sequence.
 *
 * For a video source, [startMs]/[endMs] optionally clip it to a window (null =
 * from start / to end). For an image source ([isImage] true), the clip is shown
 * for [imageDurationMs] as a still — the basis for image-to-video slideshows.
 */
data class VideoSegment(
    val uri: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val isImage: Boolean = false,
    val imageDurationMs: Long = 3_000L,
)
