package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.PhotoFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies color [ImageOp]s — preset [ImageOp.Filter]s and fine-grained
 * [ImageOp.Adjust]ments — to a bitmap via a single [ColorMatrix] draw.
 *
 * Like the other processors, each method either returns a new bitmap or the
 * input unchanged (for a no-op); it never recycles the input. The caller
 * ([PipelineRenderer]) owns the lifecycle of the bitmaps it feeds in.
 */
@Singleton
class ColorProcessor @Inject constructor() {

    /** Applies [op]'s preset. Returns [src] when the filter is the identity. */
    fun filter(src: Bitmap, op: ImageOp.Filter): Bitmap {
        if (op.isIdentity) return src
        return applyMatrix(src, matrixFor(op.filter))
    }

    /** Applies [op]'s adjustments. Returns [src] when nothing is adjusted. */
    fun adjust(src: Bitmap, op: ImageOp.Adjust): Bitmap {
        if (op.isIdentity) return src
        return applyMatrix(src, matrixFor(op))
    }

    /** Draws [src] through [matrix] into a fresh bitmap. */
    private fun applyMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /** The color matrix for a fine-grained adjustment op. */
    private fun matrixFor(op: ImageOp.Adjust): ColorMatrix = ColorMatrix().apply {
        // Saturation: -1 → grayscale (0), 0 → unchanged (1), +1 → doubled (2).
        setSaturation(1f + op.saturation)

        // Contrast: multiplier around the 0.5 midpoint. -1 → 0x, 0 → 1x, +1 → 2x.
        val contrast = 1f + op.contrast
        val translate = (1f - contrast) * HALF_255
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

        // Brightness: uniform add across RGB, ±BRIGHTNESS_RANGE at the extremes.
        val brightness = op.brightness * BRIGHTNESS_RANGE
        // Warmth: push red up and blue down (positive = warmer).
        val warmth = op.warmth * WARMTH_RANGE
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, brightness + warmth,
                    0f, 1f, 0f, 0f, brightness,
                    0f, 0f, 1f, 0f, brightness - warmth,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    /** The color matrix for a named preset. */
    private fun matrixFor(filter: PhotoFilter): ColorMatrix = when (filter) {
        PhotoFilter.NONE -> ColorMatrix()
        PhotoFilter.MONO -> ColorMatrix().apply { setSaturation(0f) }
        PhotoFilter.NOIR -> ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -20f,
                        0f, 1.25f, 0f, 0f, -20f,
                        0f, 0f, 1.25f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        PhotoFilter.SEPIA -> ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 40f,
                        0f, 1f, 0f, 0f, 20f,
                        0f, 0f, 1f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        PhotoFilter.VIVID -> ColorMatrix().apply { setSaturation(1.6f) }
        PhotoFilter.COOL -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.15f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        PhotoFilter.WARM -> ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 15f,
                0f, 1.02f, 0f, 0f, 0f,
                0f, 0f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        PhotoFilter.VINTAGE -> ColorMatrix().apply {
            setSaturation(0.6f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 20f,
                        0f, 1f, 0f, 0f, 10f,
                        0f, 0f, 0.85f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val HALF_255 = 127.5f
        const val BRIGHTNESS_RANGE = 100f
        const val WARMTH_RANGE = 30f
    }
}
