package com.momi.watermarker.presentation.studio

import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.Layer
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.model.LayerIds
import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.StudioBackdrop
import com.momi.watermarker.domain.model.backdrop

/** Immutable UI state for the single-image layered Studio. */
data class StudioUiState(
    val document: LayerDocument? = null,
    val previewUri: String? = null,
    val isSegmenting: Boolean = false,
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val fillColorArgb: Int = LayerDocument.DEFAULT_FILL,
    val pendingBlur: Float? = null,
    val isTracing: Boolean = false,
    val cutoutOutline: List<NormalizedPoint> = emptyList(),
    /** True from Auto cut-out / Trace until confirm or cancel, including the find-subject wait. */
    val cutoutActive: Boolean = false,
) {
    val hasSource: Boolean get() = document != null
    val sourceUri: String? get() = document?.sourceUri
    val imageAspect: Float
        get() {
            val doc = document ?: return 1f
            return doc.canvasWidth / doc.canvasHeight.toFloat().coerceAtLeast(1f)
        }
    val isReviewingCutout: Boolean get() = cutoutOutline.isNotEmpty()
    val inCutoutSession: Boolean get() = cutoutActive || isTracing || isReviewingCutout
    val layers: List<Layer> get() = document?.layers.orEmpty().asReversed()
    val selectedLayerId: String? get() = document?.selectedLayerId
    val hasSubject: Boolean get() = document?.hasSubject() == true
    val portraitLook: Boolean get() = document?.portraitLookEnabled() == true
    val blurStrength: Float get() = pendingBlur ?: document?.blurStrength() ?: 0f
    val backdrop: StudioBackdrop get() = document?.backdrop() ?: StudioBackdrop.ORIGINAL
    val isBusy: Boolean get() = isSegmenting || isRendering
    val canSave: Boolean get() = previewUri != null && !isBusy && !isSaving && !inCutoutSession
    val producesTransparency: Boolean get() = document?.producesTransparency() == true
    val exportFormat: ExportFormat
        get() = if (producesTransparency) ExportFormat.PNG else ExportFormat.JPEG
    val displayUri: String? get() = if (inCutoutSession) sourceUri else previewUri ?: sourceUri
    val canDeleteSelected: Boolean
        get() = selectedLayerId != null && selectedLayerId != LayerIds.BACKGROUND
    val hasReplacement: Boolean get() = document?.layer(LayerIds.REPLACEMENT) != null
}
