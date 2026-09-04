package com.momi.watermarker.presentation.editor

import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageInfo
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.Pipeline
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
    /** Which editing tool's control panel is currently shown. */
    val selectedTool: EditorTool = EditorTool.DEFAULT,
    /**
     * True when the current batch was picked from the gallery, so the originals
     * exist there and can be offered for deletion. Camera captures are not in
     * the gallery, so there is nothing to delete for them.
     */
    val sourceFromGallery: Boolean = false,
    /** The rendered, watermarked preview of the selected source image. */
    val previewImage: WatermarkImage? = null,
    /**
     * Dimensions/size of the image currently shown in the preview (the rendered
     * [previewImage] when present, otherwise the selected source). Null until read.
     */
    val selectedImageInfo: ImageInfo? = null,
    val config: WatermarkConfig = WatermarkConfig(text = "© MomiWaterMarker"),
    /** Rectangular crop contributed by the Crop tool. */
    val crop: ImageOp.Crop = ImageOp.Crop(),
    /** Rotate/flip settings contributed by the Transform tool. */
    val transform: ImageOp.Transform = ImageOp.Transform(),
    /** Scale settings contributed by the Resize tool. */
    val resize: ImageOp.Resize = ImageOp.Resize(),
    /** Aspect-ratio padding contributed by the Aspect ratio tool. */
    val aspectPad: ImageOp.AspectPad = ImageOp.AspectPad(),
    /** Preset color filter contributed by the Filters tool. */
    val filter: ImageOp.Filter = ImageOp.Filter(),
    /** Fine-grained color adjustments contributed by the Adjust tool. */
    val adjust: ImageOp.Adjust = ImageOp.Adjust(),
    /** Mosaic/pixelate effect contributed by the Pixelate tool. */
    val pixelate: ImageOp.Pixelate = ImageOp.Pixelate(),
    /** Decorative frame contributed by the Frame tool. */
    val frame: ImageOp.Frame = ImageOp.Frame(),
    /** Encoding (format + quality) applied at export by the Compress tool. */
    val exportOptions: ExportOptions = ExportOptions(),
    /** Estimated size (bytes) of the shown image re-encoded per [exportOptions]. */
    val estimatedExportSize: Long? = null,
    val availablePatterns: List<WatermarkPattern> = emptyList(),
    val availableFonts: List<WatermarkFont> = emptyList(),
    /** Whether there is a prior edit state to undo / redo. */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isRendering: Boolean = false,
    val isSaving: Boolean = false,
) {
    val selectedSource: WatermarkImage? get() = sourceImages.getOrNull(selectedIndex)
    val hasImage: Boolean get() = sourceImages.isNotEmpty()
    val imageCount: Int get() = sourceImages.size
    val hasMultipleImages: Boolean get() = sourceImages.size > 1

    /** Whether a "delete originals" choice should be offered at save time. */
    val canDeleteOriginals: Boolean get() = sourceFromGallery && sourceImages.isNotEmpty()

    /**
     * The ordered edit pipeline assembled from the current tool settings. Ops are
     * added in a fixed, sensible order (geometry first, watermark last) and this
     * same pipeline is applied to every image in the batch. Compression is not an
     * op — it is applied at export via [exportOptions]. Further tools (crop,
     * filters, ...) contribute their own ops here as they land.
     */
    val pipeline: Pipeline
        get() = Pipeline(
            buildList {
                if (!crop.isIdentity) add(crop)
                if (!transform.isIdentity) add(transform)
                if (!resize.isIdentity) add(resize)
                if (!filter.isIdentity) add(filter)
                if (!adjust.isIdentity) add(adjust)
                if (!pixelate.isIdentity) add(pixelate)
                if (config.isRenderable) add(ImageOp.Watermark(config))
                // Aspect padding reshapes the finished canvas; the frame then
                // wraps that. Both come after the watermark so it stays anchored
                // to the photo rather than the added bars/border.
                if (!aspectPad.isIdentity) add(aspectPad)
                if (!frame.isIdentity) add(frame)
            },
        )

    /**
     * Whether the assembled pipeline yields pixels with transparency (a shaped
     * crop or a rounded frame), which requires an alpha-capable export format.
     */
    val producesTransparency: Boolean
        get() = crop.hasTransparency || frame.hasTransparency || aspectPad.hasTransparency

    /**
     * The file size to show in the preview badge. It reflects the *real* file
     * size: the loaded image's on-disk size when nothing has been changed, and
     * otherwise the estimated size of the file that will be written on save
     * (rendered pixels encoded with the current [exportOptions]).
     */
    val displayedSizeBytes: Long?
        get() = if (!hasPreviewableEdits && exportOptions == ExportOptions()) {
            selectedImageInfo?.sizeBytes
        } else {
            estimatedExportSize ?: selectedImageInfo?.sizeBytes
        }

    /**
     * The tools available for the current batch: single-image-only tools (see
     * [EditorTool.supportsBatch]) are hidden while multiple images are selected.
     */
    val visibleTools: List<EditorTool>
        get() = EditorTool.entries.filter { !hasMultipleImages || it.supportsBatch }

    /**
     * Whether there is any edit to preview: a non-empty pipeline. (Export-only
     * changes like compression have no visible preview, so they don't count here.)
     */
    val hasPreviewableEdits: Boolean get() = pipeline.isNotEmpty

    /** Whether any editing has been done (used to enable a global "reset all"). */
    val hasAnyEdits: Boolean
        get() = !crop.isIdentity || !transform.isIdentity || !resize.isIdentity ||
            !aspectPad.isIdentity || !filter.isIdentity || !adjust.isIdentity ||
            !pixelate.isIdentity || !frame.isIdentity || config.isRenderable ||
            exportOptions != ExportOptions()

    /**
     * Save is available whenever an image is loaded — even with no edits, since
     * re-encoding per [exportOptions] (compression / format conversion) is itself
     * a valid batch action.
     */
    val canSave: Boolean get() = hasImage && !isSaving && !isRendering
}

/** One-shot side effects the screen must react to (not part of durable state). */
sealed interface EditorEffect {
    data class LaunchCamera(val destinationUri: String) : EditorEffect

    /** Ask the screen to launch the system consent dialog to delete these originals. */
    data class RequestDeleteOriginals(val uris: List<String>) : EditorEffect

    data class ShowMessage(val message: String) : EditorEffect
}
