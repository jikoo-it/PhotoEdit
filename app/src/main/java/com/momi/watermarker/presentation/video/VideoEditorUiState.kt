package com.momi.watermarker.presentation.video

import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.OverlayPosition
import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoColorFilter
import com.momi.watermarker.domain.model.VideoTransition

/**
 * The editing operations offered on the video home screen. Each is a distinct,
 * self-contained flow that funnels into the same export pipeline.
 */
enum class VideoOp(val title: String, val subtitle: String) {
    CUT_JOIN("Trim / Cut & Join", "Keep one section, or several stitched together"),
    MERGE("Merge", "Join multiple videos into one"),
    REMOVE_AUDIO("Remove Sound", "Strip the audio track"),
    ASPECT_RATIO("Aspect Ratio", "Reframe to 16:9, 1:1, 9:16…"),
    FILTER("Color Filter", "Apply a look: B&W, invert, warm/cool…"),
    OVERLAY("Image Overlay", "Stamp a logo or image onto the video"),
    SLIDESHOW("Images to Video", "Turn photos into a video with per-image timing and transitions"),
}

/** Whether the video overlay is an image/logo or a line of text. */
enum class OverlayMode { IMAGE, TEXT }

/** One image in a slideshow, with how long it stays on screen. */
data class SlideItem(
    val uri: String,
    val durationMs: Long = 3_000L,
)

/** Selectable output aspect ratios (width / height); [ratio] null keeps the source. */
enum class AspectRatioOption(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    WIDE("16:9", 16f / 9f),
    SQUARE("1:1", 1f),
    VERTICAL("9:16", 9f / 16f),
    CLASSIC("4:3", 4f / 3f),
}

/**
 * Immutable UI state for the whole video editor.
 *
 * [op] null means the home/op-picker is showing; otherwise the state carries
 * whatever the active operation needs (a trim window, a list of kept ranges,
 * multiple sources to merge, an aspect ratio, an overlay image, …).
 */
data class VideoEditorUiState(
    val op: VideoOp? = null,
    val sources: List<VideoClip> = emptyList(),
    val durationMs: Long = 0L,
    // Trim / cut & join (one or more kept ranges)
    val keepRanges: List<TrimRange> = emptyList(),
    // Aspect ratio
    val aspectRatio: AspectRatioOption = AspectRatioOption.ORIGINAL,
    /** Per-source reframe for Merge, parallel to [sources]; kept in sync on add/reorder. */
    val mergeAspects: List<AspectRatioOption> = emptyList(),
    // Color filter (whole-video look)
    val colorFilter: VideoColorFilter = VideoColorFilter.NONE,
    // Overlay
    val overlayMode: OverlayMode = OverlayMode.IMAGE,
    val overlayUri: String? = null,
    val overlayText: String = "",
    val overlayTextColorArgb: Int = 0xFFFFFFFF.toInt(),
    val overlayAlpha: Float = 1f,
    val overlayPosition: OverlayPosition = OverlayPosition.DEFAULT,
    /** Overlay size as a fraction of the frame (image width / text height). */
    val overlaySizeFraction: Float = 0.3f,
    /** Crop applied to an image overlay before stamping; null = whole image. */
    val overlayCropRect: NormalizedRect? = null,
    val overlayCropShape: CropShape = CropShape.RECTANGLE,
    // Slideshow (images to video)
    val slides: List<SlideItem> = emptyList(),
    /** One entry per boundary between adjacent slides (size = slides - 1). */
    val transitions: List<VideoTransition> = emptyList(),
    val transitionDurationMs: Long = 600L,
    val slideshowAspect: AspectRatioOption = AspectRatioOption.WIDE,
    // Result / progress
    val resultClip: VideoClip? = null,
    val isExporting: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val primarySource: VideoClip? get() = sources.firstOrNull()
    val hasVideo: Boolean get() = sources.isNotEmpty()
    val isReady: Boolean get() = hasVideo && durationMs > 0L

    /** The clip to show in the preview player: the result if present, else the first source. */
    val previewUri: String? get() = resultClip?.uri ?: primarySource?.uri

    /** Whether the active operation has everything it needs to export. */
    val canExport: Boolean
        get() = !isExporting && when (op) {
            VideoOp.CUT_JOIN -> isReady && keepRanges.isNotEmpty() && keepRanges.all { it.isValid }
            VideoOp.MERGE -> sources.size >= 2
            VideoOp.REMOVE_AUDIO -> hasVideo
            VideoOp.ASPECT_RATIO -> hasVideo && aspectRatio.ratio != null
            VideoOp.FILTER -> hasVideo && colorFilter != VideoColorFilter.NONE
            VideoOp.OVERLAY -> hasVideo && when (overlayMode) {
                OverlayMode.IMAGE -> overlayUri != null
                OverlayMode.TEXT -> overlayText.isNotBlank()
            }
            VideoOp.SLIDESHOW -> slides.size >= 2 && slides.all { it.durationMs > 0L }
            null -> false
        }
}

/** One-shot side effects surfaced to the screen (transient messages). */
sealed interface VideoEditorEffect {
    data class ShowMessage(val message: String) : VideoEditorEffect
}
