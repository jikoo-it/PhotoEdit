package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.momi.watermarker.domain.model.Layer
import com.momi.watermarker.domain.model.LayerContent
import com.momi.watermarker.domain.model.LayerDocument
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints a [LayerDocument] bottom-to-top into a single bitmap. Does not decode
 * or recycle layer source bitmaps itself — the caller supplies rasters already
 * loaded at working size via [rasterFor].
 */
@Singleton
class LayerCompositor @Inject constructor(
    private val bitmapBlur: BitmapBlur,
) {

    /**
     * [rasterFor] is invoked for every visible raster layer and must return a
     * bitmap the compositor may draw but not recycle.
     */
    fun flatten(
        document: LayerDocument,
        outWidth: Int,
        outHeight: Int,
        rasterFor: (uri: String) -> Bitmap,
    ): Bitmap {
        var dest = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        for (layer in document.layers) {
            if (!layer.visible || layer.opacity <= 0f) continue
            dest = applyLayer(dest, layer, rasterFor)
        }
        return dest
    }

    private fun applyLayer(
        dest: Bitmap,
        layer: Layer,
        rasterFor: (String) -> Bitmap,
    ): Bitmap {
        val opacity = layer.opacity.coerceIn(0f, 1f)
        return when (val content = layer.content) {
            is LayerContent.Fill -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = content.colorArgb
                    alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
                }
                Canvas(dest).drawRect(0f, 0f, dest.width.toFloat(), dest.height.toFloat(), paint)
                dest
            }

            is LayerContent.Raster -> {
                val src = rasterFor(content.uri)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
                }
                Canvas(dest).drawBitmap(src, null, containRect(src, dest.width, dest.height), paint)
                dest
            }

            is LayerContent.Adjustment -> {
                val adjusted = adjust(dest, content)
                if (adjusted !== dest) dest.recycle()
                adjusted
            }
        }
    }

    private fun adjust(src: Bitmap, adjustment: LayerContent.Adjustment): Bitmap {
        var working: Bitmap = src
        var owned = false
        if (adjustment.grayscale) {
            val gray = grayscale(working)
            if (owned && gray !== working) working.recycle()
            working = gray
            owned = true
        }
        val strength = adjustment.blurStrength.coerceIn(0f, 1f)
        if (strength > 0.01f) {
            val radius = blurRadiusPx(strength, max(working.width, working.height))
            val blurred = bitmapBlur.blur(working, radius)
            if (owned && blurred !== working) working.recycle()
            working = blurred
            owned = true
        }
        return if (owned) working else src.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun grayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun containRect(src: Bitmap, targetW: Int, targetH: Int): RectF {
        val scale = min(targetW / src.width.toFloat(), targetH / src.height.toFloat())
        val drawW = src.width * scale
        val drawH = src.height * scale
        val left = (targetW - drawW) / 2f
        val top = (targetH - drawH) / 2f
        return RectF(left, top, left + drawW, top + drawH)
    }

    private fun blurRadiusPx(strength: Float, longEdge: Int): Int =
        (strength.coerceIn(0f, 1f) * MAX_BLUR_FRACTION * longEdge).roundToInt()
            .coerceIn(0, MAX_BLUR_RADIUS)

    private companion object {
        const val MAX_BLUR_FRACTION = 0.03f
        const val MAX_BLUR_RADIUS = 80
    }
}