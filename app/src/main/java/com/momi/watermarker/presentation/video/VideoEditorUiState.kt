package com.momi.watermarker.presentation.video

import androidx.annotation.StringRes
import com.momi.watermarker.R

import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.OverlayPosition
import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoColorFilter
import com.momi.watermarker.domain.model.complementWithin

/**
 * The editing operations offered on the video home screen. Each is a distinct,
 * self-contained flow that funnels into the same export pipeline.
 */
enum class VideoOp(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    CUT_JOIN(R.string.video_op_cut_join, R.string.video_op_cut_join_subtitle),
    MERGE(R.string.video_op_merge, R.string.video_op_merge_subtitle),
    REMOVE_AUDIO(R.string.video_op_remove_audio, R.string.video_op_remove_audio_subtitle),
    ASPECT_RATIO(R.string.video_op_aspect, R.string.video_op_aspect_subtitle),
    FILTER(R.string.video_op_filter, R.string.video_op_filter_subtitle),
    OVERLAY(R.string.video_op_overlay, R.string.video_op_overlay_subtitle),
    SLIDESHOW(R.string.video_op_slideshow, R.string.video_op_slideshow_subtitle),
}

/** Whether the video overlay is an image/logo or a line of text. */
enum class OverlayMode { IMAGE, TEXT }

/** One image in a slideshow, with how long it stays on screen. */
data class SlideItem(
    val uri: String,
    val durationMs: Long = 3_000L,
)

/** Selectable output aspect ratios (width / height); [ratio] null keeps the source. */
enum class AspectRatioOption(@StringRes val labelRes: Int, val ratio: Float?) {
    ORIGINAL(R.string.aspect_original, null),
    WIDE(R.string.aspect_16_9, 16f / 9f),
    SQUARE(R.string.aspect_1_1, 1f),
    VERTICAL(R.string.aspect_9_16, 9f / 16f),
    CLASSIC(R.string.aspect_4_3, 4f / 3f),
}

/**
 * Immutable UI state for the whole video editor.
 *
 * [op] null means the home/op-picker is showing; otherwise the state carries
 * whatever the active operation needs (a trim window, a list of keep-or-exclude
 * ranges, multiple sources to merge, an aspect ratio, an overlay image, …).
 */
data class VideoEditorUiState(
    val op: VideoOp? = null,
    val sources: List<VideoClip> = emptyList(),
    val durationMs: Long = 0L,
    // Trim / cut & join (ranges to keep, or to exclude when [excludeSections] is on)
    val keepRanges: List<TrimRange> = emptyList(),
    /** When true, [keepRanges] are cut out and the leftover parts are joined. */
    val excludeSections: Boolean = false,
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
    val transitions: List<SlideTransition> = emptyList(),
    val transitionDurationMs: Long = 600L,
    val slideshowAspect: AspectRatioOption = AspectRatioOption.WIDE,
    // Result / progress
    val resultClip: VideoClip? = null,
    val isExporting: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /**
     * When a [resultClip] exists, the top player shows that result (demo)
     * rather than the original source.
     */
    val showDemoPreview: Boolean = false,
    /** Optional gallery file name (no extension); blank uses an auto name. */
    val outputFileName: String = "",
) {
    val primarySource: VideoClip? get() = sources.firstOrNull()
    val hasVideo: Boolean get() = sources.isNotEmpty()
    val isReady: Boolean get() = hasVideo && durationMs > 0L

    /** Demo is the processed result playing in the top player. */
    val isDemoPreview: Boolean get() = showDemoPreview && resultClip != null

    /** URI for the top player: demo result, else the first source. */
    val playerUri: String?
        get() = if (isDemoPreview) resultClip?.uri else primarySource?.uri

    /**
     * Ranges actually sent to cut-and-join: the marked windows as-is, or the
     * leftover after those windows are excluded.
     */
    val resolvedKeepRanges: List<TrimRange>
        get() = if (excludeSections) keepRanges.complementWithin(durationMs) else keepRanges

    /** Whether the active operation has everything it needs to export. */
    val canExport: Boolean
        get() = !isExporting && when (op) {
            VideoOp.CUT_JOIN ->
                isReady &&
                    keepRanges.isNotEmpty() &&
                    keepRanges.all { it.isValid } &&
                    resolvedKeepRanges.isNotEmpty()
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
