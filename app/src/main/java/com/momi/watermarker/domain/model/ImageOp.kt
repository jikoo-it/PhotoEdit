package com.momi.watermarker.domain.model

/**
 * A single, self-contained image transformation.
 *
 * An [ImageOp] is a pure, Android-free description of *what* to do to an image;
 * the data layer supplies a processor that knows *how* to do it to a bitmap.
 * Ordered [ImageOp]s form a [Pipeline] that is applied identically to every
 * image in a batch.
 *
 * New operations are added here as the editor grows (crop, resize, transform,
 * adjust, filter, ...). Keeping them in one sealed hierarchy means the renderer
 * and the UI both fail to compile until a new op is handled everywhere.
 */
sealed interface ImageOp {

    /** Draws a watermark ([WatermarkConfig]) over the image. */
    data class Watermark(val config: WatermarkConfig) : ImageOp
}
