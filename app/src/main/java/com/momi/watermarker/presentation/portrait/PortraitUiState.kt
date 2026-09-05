package com.momi.watermarker.presentation.portrait

import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.PortraitEffect

/**
 * Immutable UI state for the portrait "selective color + background blur" tool.
 *
 * The person is kept in color and the background is desaturated (and optionally
 * blurred). [resultUri] is a downscaled live preview; the full-resolution image
 * is only rendered when saving.
 */
data class PortraitUiState(
    val sourceUri: String? = null,
    /** The downscaled preview of the current effect. */
    val resultUri: String? = null,
    /** Keep the detected person(s) in color over a grayscale background. */
    val selectiveColor: Boolean = true,
    /** Also Gaussian-blur the (grayscale) background. */
    val backgroundBlur: Boolean = false,
    /** Normalized blur intensity 0f..1f. */
    val blurStrength: Float = 0.5f,
    /** Before/after compare: while true the untouched original is shown. */
    val showOriginal: Boolean = false,
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val hasSource: Boolean get() = sourceUri != null

    /**
     * The effect to render, or `null` when selective color is off (nothing to
     * process — the preview is just the original).
     */
    val effect: PortraitEffect?
        get() = when {
            !selectiveColor -> null
            backgroundBlur -> PortraitEffect.SelectiveColorWithBlur(blurStrength)
            else -> PortraitEffect.SelectiveColor
        }

    /** The image to show in the preview. */
    val previewUri: String?
        get() = if (showOriginal) sourceUri else (resultUri ?: sourceUri)

    /** True when a processed result exists and can be saved. */
    val canSave: Boolean get() = resultUri != null && effect != null && !isRendering && !isSaving

    /** Portrait output is opaque, so it exports as JPEG. */
    val exportFormat: ExportFormat get() = ExportFormat.JPEG
}
