package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
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

    /**
     * Crops [src] to [op]'s rectangle and masks it to [op]'s shape. Returns [src]
     * when the crop is identity. Never recycles [src]; any intermediate rectangle
     * bitmap created here is recycled before returning the shaped result.
     */
    fun crop(src: Bitmap, op: ImageOp.Crop): Bitmap {
        if (op.isIdentity) return src
        val rect = op.rect
        // Convert fractional bounds to pixels, clamped so we never exceed the
        // bitmap or produce a zero-size crop.
        val left = (rect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val top = (rect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val width = (rect.width * src.width).roundToInt().coerceIn(1, src.width - left)
        val height = (rect.height * src.height).roundToInt().coerceIn(1, src.height - top)
        val isFullFrame = left == 0 && top == 0 && width == src.width && height == src.height
        val rectangular = if (isFullFrame) src else Bitmap.createBitmap(src, left, top, width, height)
        val shaped = maskToShape(rectangular, op.shape, op.backgroundArgb)
        // Recycle the rectangular intermediate we made, unless it's the caller's
        // source or the shaped result returned it unchanged.
        if (rectangular !== src && rectangular !== shaped) rectangular.recycle()
        return shaped
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

    /** Scales [src] per [op] (up or down). Returns [src] when no scaling is needed. */
    fun resize(src: Bitmap, op: ImageOp.Resize): Bitmap {
        val (targetWidth, targetHeight) = targetSize(src.width, src.height, op) ?: return src
        if (targetWidth == src.width && targetHeight == src.height) return src
        return Bitmap.createScaledBitmap(src, targetWidth, targetHeight, /* filter = */ true)
    }

    /**
     * Pads [src] out to [op]'s target aspect ratio without cropping: bars are
     * added on the two shorter sides (transparent, or [op]'s fill color) and the
     * source is centered. Returns [src] when [op] is identity or already matches.
     */
    fun pad(src: Bitmap, op: ImageOp.AspectPad): Bitmap {
        val target = op.preset.ratio ?: return src
        val w = src.width
        val h = src.height
        val current = w.toFloat() / h
        // Grow the axis that's "too short" for the target so no pixels are lost.
        val (canvasW, canvasH) = if (current > target) {
            w to (w / target).roundToInt().coerceAtLeast(h)
        } else {
            (h * target).roundToInt().coerceAtLeast(w) to h
        }
        if (canvasW == w && canvasH == h) return src

        val output = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        op.fillArgb?.let { canvas.drawColor(it) }
        canvas.drawBitmap(src, (canvasW - w) / 2f, (canvasH - h) / 2f, null)
        return output
    }

    /** The target dimensions for [op], or null if the image should be left as-is. */
    private fun targetSize(width: Int, height: Int, op: ImageOp.Resize): Pair<Int, Int>? =
        when (op.mode) {
            ResizeMode.PERCENT -> {
                if (op.percent == 1f) null
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
