package com.momi.watermarker.presentation.editor

import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.WatermarkConfig

/**
 * An immutable capture of every user-editable setting in the editor — the crop,
 * transform, resize, filter, adjustments, watermark config and export options.
 * Used for undo/redo: the ViewModel snapshots the state before each edit and
 * restores a snapshot on undo/redo.
 *
 * The source image list, selection and transient flags are deliberately excluded
 * — undo affects edits, not which images are loaded.
 */
data class EditSnapshot(
    val crop: ImageOp.Crop,
    val transform: ImageOp.Transform,
    val resize: ImageOp.Resize,
    val aspectPad: ImageOp.AspectPad,
    val filter: ImageOp.Filter,
    val adjust: ImageOp.Adjust,
    val pixelate: ImageOp.Pixelate,
    val frame: ImageOp.Frame,
    val config: WatermarkConfig,
    val exportOptions: ExportOptions,
) {
    companion object {
        /** The "no edits" snapshot used by a global reset. */
        val PRISTINE = EditSnapshot(
            crop = ImageOp.Crop(),
            transform = ImageOp.Transform(),
            resize = ImageOp.Resize(),
            aspectPad = ImageOp.AspectPad(),
            filter = ImageOp.Filter(),
            adjust = ImageOp.Adjust(),
            pixelate = ImageOp.Pixelate(),
            frame = ImageOp.Frame(),
            config = WatermarkConfig(text = ""),
            exportOptions = ExportOptions(),
        )
    }
}

/** Captures the current editable settings for the undo/redo history. */
fun EditorUiState.snapshot(): EditSnapshot = EditSnapshot(
    crop = crop,
    transform = transform,
    resize = resize,
    aspectPad = aspectPad,
    filter = filter,
    adjust = adjust,
    pixelate = pixelate,
    frame = frame,
    config = config,
    exportOptions = exportOptions,
)

/** Returns a copy of this state with every editable setting from [snapshot]. */
fun EditorUiState.restore(snapshot: EditSnapshot): EditorUiState = copy(
    crop = snapshot.crop,
    transform = snapshot.transform,
    resize = snapshot.resize,
    aspectPad = snapshot.aspectPad,
    filter = snapshot.filter,
    adjust = snapshot.adjust,
    pixelate = snapshot.pixelate,
    frame = snapshot.frame,
    config = snapshot.config,
    exportOptions = snapshot.exportOptions,
)
