package com.momi.watermarker.presentation.video

import com.momi.watermarker.domain.model.VideoClip

/**
 * Immutable UI state for the video editor (Phase 0 spike: trim + export).
 *
 * [trimStartMs]/[trimEndMs] describe the user's selected window over
 * [durationMs]; [resultClip] is the exported clip once a trim completes.
 */
data class VideoEditorUiState(
    val sourceClip: VideoClip? = null,
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val resultClip: VideoClip? = null,
    val isExporting: Boolean = false,
) {
    val hasVideo: Boolean get() = sourceClip != null
    val isReady: Boolean get() = hasVideo && durationMs > 0L
    val canExport: Boolean get() = isReady && !isExporting && trimEndMs > trimStartMs

    /** The clip to show in the preview player: the result if present, else the source. */
    val previewUri: String? get() = resultClip?.uri ?: sourceClip?.uri
}

/** One-shot side effects surfaced to the screen (transient messages). */
sealed interface VideoEditorEffect {
    data class ShowMessage(val message: String) : VideoEditorEffect
}
