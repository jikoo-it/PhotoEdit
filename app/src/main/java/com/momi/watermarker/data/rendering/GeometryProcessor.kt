package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Matrix
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.ResizeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Applies geometric [ImageOp]s — right-angle rotation, mirroring and downscaling
 * — to a bitmap.
 *
 * Every method either returns a new bitmap or the input unchanged (when the op
 * is a no-op for that image); it never recycles the input. The caller
 * ([PipelineRenderer]) owns the lifecycle of the bitmaps it feeds in.
 */
@Singleton
class GeometryProcessor @Inject constructor() {

    /** Crops [src] to [op]'s rectangle. Returns [src] when the crop is identity. */
    fun crop(src: Bitmap, op: ImageOp.Crop): Bitmap {
        if (op.isIdentity) return src
        val rect = op.rect
        // Convert fractional bounds to pixels, clamped so we never exceed the
        // bitmap or produce a zero-size crop.
        val left = (rect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val top = (rect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val width = (rect.width * src.width).roundToInt().coerceIn(1, src.width - left)
        val height = (rect.height * src.height).roundToInt().coerceIn(1, src.height - top)
        if (left == 0 && top == 0 && width == src.width && height == src.height) return src
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    /** Rotates and/or flips [src] per [op]. Returns [src] when [op] is identity. */
    fun transform(src: Bitmap, op: ImageOp.Transform): Bitmap {
        if (op.isIdentity) return src
        val matrix = Matrix().apply {
            if (op.rotationDegrees != 0) postRotate(op.rotationDegrees.toFloat())
            val scaleX = if (op.flipHorizontal) -1f else 1f
            val scaleY = if (op.flipVertical) -1f else 1f
            if (scaleX != 1f || scaleY != 1f) postScale(scaleX, scaleY)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, /* filter = */ true)
    }

    /** Downscales [src] per [op]. Returns [src] when no scaling is needed. */
    fun resize(src: Bitmap, op: ImageOp.Resize): Bitmap {
        val (targetWidth, targetHeight) = targetSize(src.width, src.height, op) ?: return src
        if (targetWidth == src.width && targetHeight == src.height) return src
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, /* filter = */ true)
    }

    /** The target dimensions for [op], or null if the image should be left as-is. */
    private fun targetSize(width: Int, height: Int, op: ImageOp.Resize): Pair<Int, Int>? =
        when (op.mode) {
            ResizeMode.PERCENT -> {
                if (op.percent >= 1f) null
                else scaled(width, height, op.percent)
            }
            ResizeMode.LONGEST_SIDE -> {
                val longest = maxOf(width, height)
                if (longest <= op.maxDimensionPx) null
                else scaled(width, height, op.maxDimensionPx.toFloat() / longest)
            }
        }

    private fun scaled(width: Int, height: Int, scale: Float): Pair<Int, Int> =
        (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
}
