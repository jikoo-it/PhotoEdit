package com.momi.watermarker.domain.model

/**
 * An ordered list of [ImageOp]s applied to an image, in order.
 *
 * The same pipeline is applied identically to every image in a batch, so the
 * user configures the edits once and they fan out across the whole selection.
 * Order matters: e.g. cropping before watermarking anchors the mark to the
 * cropped result, not the original.
 */
data class Pipeline(val ops: List<ImageOp> = emptyList()) {

    val isEmpty: Boolean get() = ops.isEmpty()
    val isNotEmpty: Boolean get() = ops.isNotEmpty()

    companion object {
        /** A no-op pipeline (the image is returned unchanged). */
        val EMPTY = Pipeline()
    }
}
