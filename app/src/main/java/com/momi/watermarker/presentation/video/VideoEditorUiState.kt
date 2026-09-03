package com.momi.watermarker.presentation.video

import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.VideoClip

/**
 * The editing operations offered on the video home screen. Each is a distinct,
 * self-contained flow that funnels into the same export pipeline.
 */
enum class VideoOp(val title: String, val subtitle: String) {
    TRIM("Trim", "Keep one section of a video"),
    CUT_JOIN("Cut & Join", "Keep several sections and stitch them together"),
    MERGE("Merge", "Join multiple videos into one"),
    REMOVE_AUDIO("Remove Sound", "Strip the audio track"),
    ASPECT_RATIO("Aspect Ratio", "Reframe to 16:9, 1:1, 9:16…"),
    OVERLAY("Image Overlay", "Stamp a logo or image onto the video"),
}

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
    // Trim
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    // Cut & join
    val keepRanges: List<TrimRange> = emptyList(),
    // Aspect ratio
    val aspectRatio: AspectRatioOption = AspectRatioOption.ORIGINAL,
    // Overlay
    val overlayUri: String? = null,
    val overlayAlpha: Float = 1f,
    // Result / progress
    val resultClip: VideoClip? = null,
    val isExporting: Boolean = false,
) {
    val primarySource: VideoClip? get() = sources.firstOrNull()
    val hasVideo: Boolean get() = sources.isNotEmpty()
    val isReady: Boolean get() = hasVideo && durationMs > 0L

    /** The clip to show in the preview player: the result if present, else the first source. */
    val previewUri: String? get() = resultClip?.uri ?: primarySource?.uri

    /** Whether the active operation has everything it needs to export. */
    val canExport: Boolean
        get() = !isExporting && when (op) {
            VideoOp.TRIM -> isReady && trimEndMs > trimStartMs
            VideoOp.CUT_JOIN -> isReady && keepRanges.isNotEmpty() && keepRanges.all { it.isValid }
            VideoOp.MERGE -> sources.size >= 2
            VideoOp.REMOVE_AUDIO -> hasVideo
            VideoOp.ASPECT_RATIO -> hasVideo && aspectRatio.ratio != null
            VideoOp.OVERLAY -> hasVideo && overlayUri != null
            null -> false
        }
}

/** One-shot side effects surfaced to the screen (transient messages). */
sealed interface VideoEditorEffect {
    data class ShowMessage(val message: String) : VideoEditorEffect
}
