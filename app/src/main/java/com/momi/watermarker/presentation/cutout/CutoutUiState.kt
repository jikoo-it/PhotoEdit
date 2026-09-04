package com.momi.watermarker.presentation.cutout

import com.momi.watermarker.domain.model.BackgroundMode
import com.momi.watermarker.domain.model.ExportFormat

/**
 * Immutable UI state for the single-image cut-out studio.
 *
 * The subject is extracted once ([cutoutUri]); changing the background just
 * re-renders [resultUri] from that cut-out.
 */
data class CutoutUiState(
    val sourceUri: String? = null,
    /** Transparent-background subject produced by segmentation. */
    val cutoutUri: String? = null,
    /** The composited preview/result for the current background. */
    val resultUri: String? = null,
    val mode: BackgroundMode = BackgroundMode.TRANSPARENT,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundImageUri: String? = null,
    val blurStrength: Float = 0.6f,
    /** Running the ML subject extraction. */
    val isSegmenting: Boolean = false,
    /** Compositing the subject over the chosen background. */
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val hasSource: Boolean get() = sourceUri != null
    val hasCutout: Boolean get() = cutoutUri != null
    val isBusy: Boolean get() = isSegmenting || isRendering

    /** Whether the result is ready to save. */
    val canSave: Boolean get() = resultUri != null && !isBusy && !isSaving

    /** Transparent backgrounds must keep an alpha channel; others flatten to JPEG. */
    val exportFormat: ExportFormat
        get() = if (mode == BackgroundMode.TRANSPARENT) ExportFormat.PNG else ExportFormat.JPEG

    /** The image to show in the main preview: the composited result if present. */
    val previewUri: String? get() = resultUri ?: sourceUri
}
