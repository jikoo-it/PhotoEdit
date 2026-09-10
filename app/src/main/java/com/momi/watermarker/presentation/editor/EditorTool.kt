package com.momi.watermarker.presentation.editor

import androidx.annotation.StringRes
import com.momi.watermarker.R

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
enum class EditorTool(@StringRes val labelRes: Int, val supportsBatch: Boolean = true) {
    CROP(R.string.tool_crop, supportsBatch = false),
    TRANSFORM(R.string.tool_transform),
    RESIZE(R.string.tool_resize),
    ASPECT(R.string.tool_aspect),
    FILTER(R.string.tool_filters),
    ADJUST(R.string.tool_adjust),
    PIXELATE(R.string.tool_pixelate),
    FRAME(R.string.tool_frame),
    WATERMARK(R.string.tool_watermark),
    EXPORT(R.string.tool_export);

    companion object {
        val DEFAULT = WATERMARK
    }
}
