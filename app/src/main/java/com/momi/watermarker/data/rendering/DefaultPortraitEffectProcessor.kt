package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.momi.watermarker.data.mlkit.PersonSegmenter
import com.momi.watermarker.domain.model.PortraitEffect
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Default [PortraitEffectProcessor]:
 *
 * ```
 * person mask (ML Kit) ─┐
 * source ── grayscale ──┼─ [optional blur] ── background
 *          (color copy) └─ DST_IN(mask) ───── foreground
 * background + foreground ─────────────────── composite
 * ```
 *
 * The mask is feathered so edges blend smoothly (no halos/jaggies). Per-pixel
 * blending is done via alpha compositing (`DST_IN`), which is exactly
 * `foreground·mask + background·(1 − mask)`.
 */
@Singleton
class DefaultPortraitEffectProcessor @Inject constructor(
    private val segmenter: PersonSegmenter,
    private val bitmapBlur: BitmapBlur,
) : PortraitEffectProcessor {

    override suspend fun apply(bitmap: Bitmap, effect: PortraitEffect): Bitmap {
        val foreground = extractForeground(bitmap)
        try {
            return composite(bitmap, foreground, effect)
        } finally {
            foreground.recycle()
        }
    }

    override fun composite(source: Bitmap, foreground: Bitmap, effect: PortraitEffect): Bitmap {
        val w = source.width
        val h = source.height
        val longEdge = max(w, h)
        val gray = grayscale(source)
        val background = when (effect) {
            is PortraitEffect.SelectiveColor -> gray
            is PortraitEffect.SelectiveColorWithBlur -> {
                val radius = blurRadiusPx(effect.blurRadius, longEdge)
                val blurred = bitmapBlur.blur(gray, radius)
                if (blurred !== gray) gray.recycle()
                blurred
            }
        }
        try {
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val stamp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            foreground.setHasAlpha(true)
            Canvas(result).apply {
                drawBitmap(background, 0f, 0f, null)
                drawBitmap(foreground, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), stamp)
            }
            result.setHasAlpha(false)
            return result
        } finally {
            background.recycle()
        }
    }

    override suspend fun extractForeground(bitmap: Bitmap): Bitmap {
        var mask = segmenter.personMask(bitmap)
        var foreground: Bitmap? = null
        try {
            if (mask.width != bitmap.width || mask.height != bitmap.height) {
                val scaled = Bitmap.createScaledBitmap(mask, bitmap.width, bitmap.height, true)
                if (scaled !== mask) {
                    mask.recycle()
                    mask = scaled
                }
            }
            feather(mask, featherRadius(max(bitmap.width, bitmap.height)))
            foreground = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(foreground).drawBitmap(
                mask,
                null,
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                },
            )
            // copy() from a JPEG keeps hasAlpha=false; without this, PNG encode
            // writes punched holes as opaque black and the photo disappears.
            foreground.setHasAlpha(true)
            return foreground
        } catch (t: Throwable) {
            foreground?.recycle()
            throw t
        } finally {
            mask.recycle()
        }
    }

    /** A fully desaturated (grayscale) opaque copy of [src]. */
    private fun grayscale(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { dest ->
                Canvas(dest).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c ushr 16) and 0xff
            val g = (c ushr 8) and 0xff
            val b = c and 0xff
            val y = (r * 54 + g * 183 + b * 19) shr 8
            pixels[i] = (0xff shl 24) or (y shl 16) or (y shl 8) or y
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        out.setHasAlpha(false)
        return out
    }

    /**
     * Softens the [mask]'s alpha edges in place with a small separable box blur,
     * so the color/grayscale transition around the person is gradual.
     */
    private fun feather(mask: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = mask.width
        val h = mask.height
        val src = IntArray(w * h)
        mask.getPixels(src, 0, w, 0, 0, w, h)
        val alpha = IntArray(w * h) { (src[it] ushr 24) and 0xff }
        val tmp = IntArray(w * h)
        boxBlurAlpha(alpha, tmp, w, h, radius) // horizontal → tmp
        boxBlurAlphaVertical(tmp, alpha, w, h, radius) // vertical → alpha
        for (i in src.indices) src[i] = (alpha[i] shl 24) or 0x00FFFFFF
        mask.setPixels(src, 0, w, 0, 0, w, h)
    }

    /** Horizontal running-average box blur of an alpha plane: [input] → [output]. */
    private fun boxBlurAlpha(input: IntArray, output: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius + radius + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -radius..radius) sum += input[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                output[row + x] = sum / window
                val add = input[row + (x + radius + 1).coerceIn(0, w - 1)]
                val sub = input[row + (x - radius).coerceIn(0, w - 1)]
                sum += add - sub
            }
        }
    }

    /** Vertical running-average box blur of an alpha plane: [input] → [output]. */
    private fun boxBlurAlphaVertical(input: IntArray, output: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius + radius + 1
        for (x in 0 until w) {
            var sum = 0
            for (i in -radius..radius) sum += input[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                output[y * w + x] = sum / window
                val add = input[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                val sub = input[(y - radius).coerceIn(0, h - 1) * w + x]
                sum += add - sub
            }
        }
    }

    private fun featherRadius(longEdge: Int): Int = (longEdge / 400f).roundToInt().coerceIn(1, 12)

    /** Maps a normalized 0f..1f intensity to a pixel radius relative to image size. */
    private fun blurRadiusPx(strength: Float, longEdge: Int): Int {
        val s = strength.coerceIn(0f, 1f)
        return (s * MAX_BLUR_FRACTION * longEdge).roundToInt().coerceIn(0, MAX_BLUR_RADIUS)
    }

    private companion object {
        // Heaviest blur ≈ 3% of the long edge. The cap is a safety bound; at the
        // export ceiling (2560px long edge) the fraction gives ~77px, so the cap
        // isn't normally reached. Kept modest because the stack-blur division
        // table grows with the radius (~O(radius²) ints).
        const val MAX_BLUR_FRACTION = 0.03f
        const val MAX_BLUR_RADIUS = 80
    }
}
