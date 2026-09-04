package com.momi.watermarker.presentation.editor

/**
 * The editing tools shown in the editor's tool switcher. Each tool contributes
 * one [com.momi.watermarker.domain.model.ImageOp] (or, for [EXPORT], the encode
 * settings) to the pipeline that is applied to every image in the batch.
 *
 * Tools are added here as each is implemented; the tool switcher renders the
 * full set and swaps the control panel to match the selection.
 *
 * [supportsBatch] is false for tools that only make sense on a single image
 * (e.g. cropping to a specific composition); those are hidden while more than
 * one image is selected.
 */
enum class EditorTool(val label: String, val supportsBatch: Boolean = true) {
    CROP("Crop", supportsBatch = false),
    TRANSFORM("Transform"),
    RESIZE("Resize"),
    ASPECT("Aspect ratio"),
    FILTER("Filters"),
    ADJUST("Adjust"),
    PIXELATE("Pixelate"),
    FRAME("Frame"),
    WATERMARK("Watermark"),
    EXPORT("Export");

    companion object {
        val DEFAULT = WATERMARK
    }
}
