package com.momi.watermarker.domain.model

/**
 * A single, platform-neutral description of a video export.
 *
 * Every editing operation (trim, cut-and-join, merge, remove-audio, aspect
 * ratio, image overlay, …) is expressed as one of these and fed through the
 * same pipeline: an ordered list of [segments] concatenated end-to-end, with
 * optional whole-output transforms applied on top. New capabilities (e.g.
 * transitions) extend this request rather than adding new pipelines.
 */
data class VideoEditRequest(
    /** Ordered clips to concatenate into the output (must be non-empty). */
    val segments: List<VideoSegment>,
    /** Drop the audio track from the output. */
    val removeAudio: Boolean = false,
    /** Target aspect ratio (width / height); null keeps the source ratio. */
    val aspectRatio: Float? = null,
    /** Image to stamp over every frame; null for no overlay. */
    val overlayImageUri: String? = null,
    /** Overlay opacity in [0, 1]. */
    val overlayAlpha: Float = 1f,
    /**
     * Force a (silent, if needed) audio track in the output. Needed when
     * concatenating sources whose audio presence differs (e.g. merging clips
     * where some have sound and some don't).
     */
    val forceAudioTrack: Boolean = false,
)
