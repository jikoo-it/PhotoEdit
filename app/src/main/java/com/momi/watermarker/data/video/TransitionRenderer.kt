package com.momi.watermarker.data.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.momi.watermarker.domain.model.SlideTransition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Draws a single in-between frame of a slideshow [SlideTransition] onto a
 * [Canvas].
 *
 * Both [from] and [to] are expected to be the **same size as the canvas** and
 * fully opaque (the composer cover-fits every source image to the output
 * canvas first), so blending is a straightforward 2-D compositing job — no GL,
 * no per-pixel shader code. [progress] runs 0→1 across the transition.
 *
 * Every effect is expressible as "draw the outgoing image, then reveal/blend
 * the incoming one by `progress`", which is why a rich family of transitions
 * costs only a `when` here rather than a shader per effect.
 */
@Singleton
class TransitionRenderer @Inject constructor() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Renders the frame at [progress] of [transition] between [from] and [to]
     * into [canvas] (which covers `0,0 .. width,height`).
     */
    fun render(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        transition: SlideTransition,
        progress: Float,
        width: Int,
        height: Int,
    ) {
        val t = progress.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        when (transition) {
            SlideTransition.NONE -> canvas.drawBitmap(if (t < 0.5f) from else to, 0f, 0f, null)

            SlideTransition.DISSOLVE -> {
                canvas.drawBitmap(from, 0f, 0f, null)
                drawWithAlpha(canvas, to, (t * 255).toInt())
            }

            SlideTransition.FADE_BLACK -> fadeThroughColor(canvas, from, to, t, Color.BLACK, w, h)
            SlideTransition.FADE_WHITE -> fadeThroughColor(canvas, from, to, t, Color.WHITE, w, h)

            SlideTransition.WIPE_LEFT -> wipe(canvas, from, to, w * (1f - t), 0f, w, h)
            SlideTransition.WIPE_RIGHT -> wipe(canvas, from, to, 0f, 0f, w * t, h)
            SlideTransition.WIPE_UP -> wipe(canvas, from, to, 0f, h * (1f - t), w, h)
            SlideTransition.WIPE_DOWN -> wipe(canvas, from, to, 0f, 0f, w, h * t)

            SlideTransition.SLIDE_LEFT -> push(canvas, from, to, -w * t, 0f, w, 0f)
            SlideTransition.SLIDE_RIGHT -> push(canvas, from, to, w * t, 0f, -w, 0f)
            SlideTransition.SLIDE_UP -> push(canvas, from, to, 0f, -h * t, 0f, h)
            SlideTransition.SLIDE_DOWN -> push(canvas, from, to, 0f, h * t, 0f, -h)

            SlideTransition.COVER_LEFT -> cover(canvas, from, to, w * (1f - t), 0f)
            SlideTransition.COVER_RIGHT -> cover(canvas, from, to, -w * (1f - t), 0f)
            SlideTransition.COVER_UP -> cover(canvas, from, to, 0f, h * (1f - t))
            SlideTransition.COVER_DOWN -> cover(canvas, from, to, 0f, -h * (1f - t))

            SlideTransition.REVEAL_LEFT -> reveal(canvas, from, to, -w * t, 0f)
            SlideTransition.REVEAL_RIGHT -> reveal(canvas, from, to, w * t, 0f)
            SlideTransition.REVEAL_UP -> reveal(canvas, from, to, 0f, -h * t)
            SlideTransition.REVEAL_DOWN -> reveal(canvas, from, to, 0f, h * t)

            SlideTransition.ZOOM_IN -> {
                canvas.drawBitmap(from, 0f, 0f, null)
                drawScaledCentered(canvas, to, t.coerceAtLeast(0.001f), (t * 255).toInt(), w, h)
            }
            SlideTransition.ZOOM_OUT -> {
                canvas.drawBitmap(to, 0f, 0f, null)
                drawScaledCentered(canvas, from, (1f - t).coerceAtLeast(0.001f), 255, w, h)
            }

            SlideTransition.IRIS_OPEN -> iris(canvas, from, to, radiusFor(t, w, h), w, h)
            SlideTransition.IRIS_CLOSE -> iris(canvas, to, from, radiusFor(1f - t, w, h), w, h)

            SlideTransition.BLINDS_H -> blinds(canvas, from, to, t, w, h, horizontal = true)
            SlideTransition.BLINDS_V -> blinds(canvas, from, to, t, w, h, horizontal = false)

            SlideTransition.CHECKER -> checker(canvas, from, to, t, w, h)

            SlideTransition.WIPE_DIAG_TL -> diagonalWipe(canvas, from, to, t, w, h, fromTopLeft = true)
            SlideTransition.WIPE_DIAG_TR -> diagonalWipe(canvas, from, to, t, w, h, fromTopLeft = false)

            SlideTransition.ROTATE -> {
                canvas.drawBitmap(from, 0f, 0f, null)
                canvas.save()
                canvas.rotate((1f - t) * 90f, w / 2f, h / 2f)
                val scale = 0.6f + 0.4f * t
                canvas.scale(scale, scale, w / 2f, h / 2f)
                drawWithAlpha(canvas, to, (t * 255).toInt())
                canvas.restore()
            }
        }
    }

    // --- Effect helpers -------------------------------------------------------

    private fun drawWithAlpha(canvas: Canvas, bitmap: Bitmap, alpha: Int) {
        paint.reset()
        paint.isFilterBitmap = true
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    private fun fadeThroughColor(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        t: Float,
        color: Int,
        w: Float,
        h: Float,
    ) {
        // First half: outgoing → colour. Second half: colour → incoming.
        if (t < 0.5f) {
            canvas.drawBitmap(from, 0f, 0f, null)
            canvas.drawColor(withAlpha(color, (t * 2f * 255).toInt()))
        } else {
            canvas.drawBitmap(to, 0f, 0f, null)
            canvas.drawColor(withAlpha(color, ((1f - t) * 2f * 255).toInt()))
        }
    }

    /** Draws [from] then the sub-rect of [to] inside `[l,t,r,b]`. */
    private fun wipe(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        l: Float,
        top: Float,
        r: Float,
        b: Float,
    ) {
        canvas.drawBitmap(from, 0f, 0f, null)
        canvas.save()
        canvas.clipRect(l, top, r, b)
        canvas.drawBitmap(to, 0f, 0f, null)
        canvas.restore()
    }

    /** Both images translate together by ([fromDx],[fromDy]) / ([toDx],[toDy]+offset). */
    private fun push(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        fromDx: Float,
        fromDy: Float,
        toBaseX: Float,
        toBaseY: Float,
    ) {
        canvas.drawBitmap(from, fromDx, fromDy, null)
        canvas.drawBitmap(to, toBaseX + fromDx, toBaseY + fromDy, null)
    }

    /** [to] slides in over a stationary [from]. */
    private fun cover(canvas: Canvas, from: Bitmap, to: Bitmap, toDx: Float, toDy: Float) {
        canvas.drawBitmap(from, 0f, 0f, null)
        canvas.drawBitmap(to, toDx, toDy, null)
    }

    /** [from] slides off a stationary [to] beneath it. */
    private fun reveal(canvas: Canvas, from: Bitmap, to: Bitmap, fromDx: Float, fromDy: Float) {
        canvas.drawBitmap(to, 0f, 0f, null)
        canvas.drawBitmap(from, fromDx, fromDy, null)
    }

    private fun drawScaledCentered(
        canvas: Canvas,
        bitmap: Bitmap,
        scale: Float,
        alpha: Int,
        w: Float,
        h: Float,
    ) {
        canvas.save()
        canvas.scale(scale, scale, w / 2f, h / 2f)
        drawWithAlpha(canvas, bitmap, alpha)
        canvas.restore()
    }

    private fun radiusFor(t: Float, w: Float, h: Float): Float =
        hypot(w, h) / 2f * t

    /** Draws [base] full, then [reveal] clipped to a centred circle of [radius]. */
    private fun iris(
        canvas: Canvas,
        base: Bitmap,
        reveal: Bitmap,
        radius: Float,
        w: Float,
        h: Float,
    ) {
        canvas.drawBitmap(base, 0f, 0f, null)
        if (radius <= 0f) return
        canvas.save()
        val path = Path().apply { addCircle(w / 2f, h / 2f, radius, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(reveal, 0f, 0f, null)
        canvas.restore()
    }

    private fun blinds(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        t: Float,
        w: Float,
        h: Float,
        horizontal: Boolean,
    ) {
        canvas.drawBitmap(from, 0f, 0f, null)
        val bars = BLIND_COUNT
        if (horizontal) {
            val band = h / bars
            for (i in 0 until bars) {
                val top = i * band
                canvas.save()
                canvas.clipRect(0f, top, w, top + band * t)
                canvas.drawBitmap(to, 0f, 0f, null)
                canvas.restore()
            }
        } else {
            val band = w / bars
            for (i in 0 until bars) {
                val left = i * band
                canvas.save()
                canvas.clipRect(left, 0f, left + band * t, h)
                canvas.drawBitmap(to, 0f, 0f, null)
                canvas.restore()
            }
        }
    }

    private fun checker(canvas: Canvas, from: Bitmap, to: Bitmap, t: Float, w: Float, h: Float) {
        canvas.drawBitmap(from, 0f, 0f, null)
        val cols = CHECKER_COLS
        val rows = ceil(cols * h / w).toInt().coerceAtLeast(1)
        val cw = w / cols
        val ch = h / rows
        // Two interleaved groups reveal on staggered schedules for a woven look.
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val group = (r + c) % 2
                val local = ((t * 2f) - group * 0.5f).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val cx = c * cw
                val cy = r * ch
                canvas.save()
                canvas.clipRect(cx, cy, cx + cw * local, cy + ch * local)
                canvas.drawBitmap(to, 0f, 0f, null)
                canvas.restore()
            }
        }
    }

    /**
     * Diagonal wipe using a moving linear-gradient alpha mask, so the reveal
     * edge is a clean diagonal line rather than a stair-stepped clip.
     */
    private fun diagonalWipe(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        t: Float,
        w: Float,
        h: Float,
        fromTopLeft: Boolean,
    ) {
        canvas.drawBitmap(from, 0f, 0f, null)
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawBitmap(to, 0f, 0f, null)
        // Gradient runs along the diagonal; a near-instant step at `t` is the edge.
        val (x0, y0, x1, y1) = if (fromTopLeft) {
            listOf(0f, 0f, w, h)
        } else {
            listOf(w, 0f, 0f, h)
        }
        val edge = t.coerceIn(0.001f, 0.999f)
        maskPaint.reset()
        maskPaint.isAntiAlias = true
        maskPaint.shader = LinearGradient(
            x0, y0, x1, y1,
            intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, edge, (edge + 0.02f).coerceAtMost(1f)),
            Shader.TileMode.CLAMP,
        )
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        maskPaint.xfermode = null
        maskPaint.shader = null
        canvas.restoreToCount(layer)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    // Destructuring for the (x0,y0,x1,y1) list above.
    private operator fun List<Float>.component1() = this[0]
    private operator fun List<Float>.component2() = this[1]
    private operator fun List<Float>.component3() = this[2]
    private operator fun List<Float>.component4() = this[3]

    private companion object {
        const val BLIND_COUNT = 10
        const val CHECKER_COLS = 8
    }
}
