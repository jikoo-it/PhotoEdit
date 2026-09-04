package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.momi.watermarker.domain.model.BackgroundMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-pixel compositing for the cut-out feature: places an extracted subject
 * (a transparent-background bitmap) over a chosen background. Holds no Android
 * storage or ML types.
 */
@Singleton
class CutoutComposer @Inject constructor() {

    /** The subject alone, on a transparent canvas (a defensive copy). */
    fun transparent(cutout: Bitmap): Bitmap =
        cutout.copy(Bitmap.Config.ARGB_8888, false)

    /** The subject over a solid [colorArgb] fill. */
    fun overColor(cutout: Bitmap, colorArgb: Int): Bitmap =
        composite(cutout) { canvas -> canvas.drawColor(colorArgb) }

    /**
     * The subject sharp over a blurred copy of [source] (a portrait / depth
     * look). [source] is drawn to fill the subject's frame; [strength] (0f..1f)
     * controls how heavy the blur is.
     */
    fun overBlurredSource(cutout: Bitmap, source: Bitmap, strength: Float): Bitmap {
        val blurred = blur(coverFit(source, cutout.width, cutout.height), strength)
        return try {
            composite(cutout) { canvas -> canvas.drawBitmap(blurred, 0f, 0f, null) }
        } finally {
            if (blurred != cutout) blurred.recycle()
        }
    }

    /** The subject over [background], cover-fit to the subject's frame. */
    fun overImage(cutout: Bitmap, background: Bitmap): Bitmap {
        val fitted = coverFit(background, cutout.width, cutout.height)
        return try {
            composite(cutout) { canvas -> canvas.drawBitmap(fitted, 0f, 0f, null) }
        } finally {
            if (fitted != cutout) fitted.recycle()
        }
    }

    /** Whether [mode] needs the original source bitmap decoded. */
    fun needsSource(mode: BackgroundMode): Boolean = mode == BackgroundMode.BLUR

    // --- internals ------------------------------------------------------------

    /** Draws a background via [drawBackground], then stamps the subject on top. */
    private inline fun composite(cutout: Bitmap, drawBackground: (Canvas) -> Unit): Bitmap {
        val out = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawBackground(canvas)
        canvas.drawBitmap(cutout, 0f, 0f, null)
        return out
    }

    /**
     * Scales [src] to completely cover a [targetW] × [targetH] frame, centre-
     * cropping the overflow. Returns a new bitmap sized exactly to the frame.
     */
    private fun coverFit(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = max(targetW / src.width.toFloat(), targetH / src.height.toFloat())
        val drawW = (src.width * scale).roundToInt()
        val drawH = (src.height * scale).roundToInt()
        val left = (targetW - drawW) / 2
        val top = (targetH - drawH) / 2
        canvas.drawBitmap(
            src,
            null,
            Rect(left, top, left + drawW, top + drawH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /**
     * Cheap, encoder-friendly blur: downscale [src] far down (bilinear) then
     * scale it back up, so high-frequency detail is averaged away. [strength]
     * (0f..1f) maps to how aggressively we downscale.
     */
    private fun blur(src: Bitmap, strength: Float): Bitmap {
        val s = strength.coerceIn(0f, 1f)
        // 1f -> /40 (very soft), 0f -> /4 (light).
        val divisor = 4f + s * 36f
        val smallW = max(1, (src.width / divisor).roundToInt())
        val smallH = max(1, (src.height / divisor).roundToInt())
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val blurred = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (small != src && small != blurred) small.recycle()
        if (src != blurred) src.recycle()
        return blurred
    }
}
