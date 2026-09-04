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
    /** Image to stamp over every frame; null for no image overlay. */
    val overlayImageUri: String? = null,
    /**
     * Text to stamp over every frame; null for no text overlay. When set, it
     * takes precedence over [overlayImageUri] and is rendered to a bitmap.
     */
    val overlayText: String? = null,
    /** Color (ARGB) of the [overlayText]. */
    val overlayTextColorArgb: Int = 0xFFFFFFFF.toInt(),
    /** Overlay opacity in [0, 1]. */
    val overlayAlpha: Float = 1f,
    /** Where the overlay sits within the frame. */
    val overlayPosition: OverlayPosition = OverlayPosition.DEFAULT,
    /**
     * Overlay size as a fraction of the frame: an image overlay's width, or a
     * text overlay's cap height. In `0f..1f`.
     */
    val overlaySizeFraction: Float = 0.3f,
    /** Optional crop applied to an image overlay before it is stamped. */
    val overlayCropRect: NormalizedRect? = null,
    /** Shape the [overlayCropRect] is masked to (transparent outside). */
    val overlayCropShape: CropShape = CropShape.RECTANGLE,
    /**
     * Force a (silent, if needed) audio track in the output. Needed when
     * concatenating sources whose audio presence differs (e.g. merging clips
     * where some have sound and some don't).
     */
    val forceAudioTrack: Boolean = false,
    /**
     * Animation at each internal boundary between segments. When non-empty this
     * has [segments].size - 1 entries: `transitions[i]` is played between
     * segment i and segment i + 1. Empty means every boundary is a hard cut.
     */
    val transitions: List<VideoTransition> = emptyList(),
    /** Duration of each non-[VideoTransition.NONE] transition, in milliseconds. */
    val transitionDurationMs: Long = 0L,
    /** A color look applied to the whole output; [VideoColorFilter.NONE] = untouched. */
    val colorFilter: VideoColorFilter = VideoColorFilter.NONE,
)
