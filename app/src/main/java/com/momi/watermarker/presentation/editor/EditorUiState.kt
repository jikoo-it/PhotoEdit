package com.momi.watermarker.presentation.editor

import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkFont
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.WatermarkPattern

/**
 * Immutable UI state for the editor screen. The ViewModel is the single source
 * of truth; the composable renders this and emits events back.
 *
 * A batch of [sourceImages] shares one [config]; [selectedIndex] picks which one
 * is shown in the live preview. Saving applies the config to every image.
 */
data class EditorUiState(
    val sourceImages: List<WatermarkImage> = emptyList(),
    /** Index into [sourceImages] currently shown in the preview. */
    val selectedIndex: Int = 0,
    /**
     * True when the current batch was picked from the gallery, so the originals
     * exist there and can be offered for deletion. Camera captures are not in
     * the gallery, so there is nothing to delete for them.
     */
    val sourceFromGallery: Boolean = false,
    /** The rendered, watermarked preview of the selected source image. */
    val previewImage: WatermarkImage? = null,
    val config: WatermarkConfig = WatermarkConfig(text = "© MomiWaterMarker"),
    val availablePatterns: List<WatermarkPattern> = emptyList(),
    val availableFonts: List<WatermarkFont> = emptyList(),
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
) {
    val selectedSource: WatermarkImage? get() = sourceImages.getOrNull(selectedIndex)
    val hasImage: Boolean get() = sourceImages.isNotEmpty()
    val imageCount: Int get() = sourceImages.size
    val hasMultipleImages: Boolean get() = sourceImages.size > 1

    /** Whether a "delete originals" choice should be offered at save time. */
    val canDeleteOriginals: Boolean get() = sourceFromGallery && sourceImages.isNotEmpty()

    val canSave: Boolean get() = hasImage && config.isRenderable && !isSaving && !isRendering
}

/** One-shot side effects the screen must react to (not part of durable state). */
sealed interface EditorEffect {
    data class LaunchCamera(val destinationUri: String) : EditorEffect

    /** Ask the screen to launch the system consent dialog to delete these originals. */
    data class RequestDeleteOriginals(val uris: List<String>) : EditorEffect

    data class ShowMessage(val message: String) : EditorEffect
}
