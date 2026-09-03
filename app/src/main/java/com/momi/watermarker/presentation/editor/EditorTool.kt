package com.momi.watermarker.presentation.editor

/**
 * The editing tools shown in the editor's tool switcher. Each tool contributes
 * one [com.momi.watermarker.domain.model.ImageOp] (or, for [EXPORT], the encode
 * settings) to the pipeline that is applied to every image in the batch.
 *
 * Tools are added here as each is implemented; the tool switcher renders the
 * full set and swaps the control panel to match the selection.
 */
enum class EditorTool(val label: String) {
    TRANSFORM("Transform"),
    RESIZE("Resize"),
    WATERMARK("Watermark"),
    EXPORT("Export");

    companion object {
        val DEFAULT = WATERMARK
    }
}
