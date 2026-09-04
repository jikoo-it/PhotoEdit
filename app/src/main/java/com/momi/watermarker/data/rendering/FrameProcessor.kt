package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.momi.watermarker.domain.model.FrameStyle
import com.momi.watermarker.domain.model.ImageOp
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Draws a decorative [ImageOp.Frame] around a bitmap. Returns a new (usually
 * larger) bitmap, or the input unchanged for the identity frame; never recycles
 * the input. Any intermediate created here is recycled before returning.
 */
@Singleton
class FrameProcessor @Inject constructor() {

    fun frame(src: Bitmap, op: ImageOp.Frame): Bitmap {
        if (op.isIdentity) return src
        return when (op.style) {
            FrameStyle.NONE -> src
            FrameStyle.SOLID -> bordered(src, op, keyline = false)
            FrameStyle.INSET -> bordered(src, op, keyline = true)
            FrameStyle.ROUNDED -> rounded(src, op)
            FrameStyle.SHADOW -> shadow(src, op)
        }
    }

    private fun borderPx(src: Bitmap, op: ImageOp.Frame): Int =
        (op.widthRatio * minOf(src.width, src.height)).roundToInt().coerceAtLeast(1)

    /** A solid-color border (optionally with a thin inner keyline, for a mat look). */
    private fun bordered(src: Bitmap, op: ImageOp.Frame, keyline: Boolean): Bitmap {
        val border = borderPx(src, op)
        val output = Bitmap.createBitmap(
            src.width + 2 * border,
            src.height + 2 * border,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        if (!op.transparentBackground) canvas.drawColor(op.colorArgb)
        canvas.drawBitmap(src, border.toFloat(), border.toFloat(), null)
        // A keyline only reads against a solid mat, so skip it when transparent.
        if (keyline && !op.transparentBackground) {
            val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (border * KEYLINE_FRACTION).coerceAtLeast(1f)
                // A translucent dark line reads on light mats and vice versa.
                color = if (isLight(op.colorArgb)) 0x40000000 else 0x59FFFFFF
            }
            val half = keyPaint.strokeWidth / 2f
            canvas.drawRect(
                border - half,
                border - half,
                border + src.width + half,
                border + src.height + half,
                keyPaint,
            )
        }
        return output
    }

    /** Rounds the photo's corners; the output's true corners are transparent. */
    private fun rounded(src: Bitmap, op: ImageOp.Frame): Bitmap {
        val border = borderPx(src, op)
        val radius = (op.cornerRadiusRatio * minOf(src.width, src.height)).coerceAtLeast(0f)
        val output = Bitmap.createBitmap(
            src.width + 2 * border,
            src.height + 2 * border,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)

        // Rounded colored border behind the photo (outer radius grows with border).
        if (border > 0 && !op.transparentBackground) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = op.colorArgb }
            canvas.drawRoundRect(
                RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()),
                radius + border,
                radius + border,
                borderPaint,
            )
        }

        val photoRounded = roundCorners(src, radius)
        canvas.drawBitmap(photoRounded, border.toFloat(), border.toFloat(), null)
        if (photoRounded !== src) photoRounded.recycle()
        return output
    }

    /** Masks [src]'s corners to [radius], leaving them transparent. */
    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return src
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(
            RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()),
            radius,
            radius,
            paint,
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /** A soft drop shadow around the photo over a [colorArgb] background. */
    private fun shadow(src: Bitmap, op: ImageOp.Frame): Bitmap {
        val pad = borderPx(src, op)
        val output = Bitmap.createBitmap(
            src.width + 2 * pad,
            src.height + 2 * pad,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        if (!op.transparentBackground) canvas.drawColor(op.colorArgb)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            setShadowLayer(pad * SHADOW_BLUR_FRACTION, 0f, pad * SHADOW_DY_FRACTION, 0x99000000.toInt())
        }
        canvas.drawRect(
            pad.toFloat(),
            pad.toFloat(),
            (pad + src.width).toFloat(),
            (pad + src.height).toFloat(),
            shadowPaint,
        )
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), null)
        return output
    }

    private fun isLight(argb: Int): Boolean {
        val luminance = 0.299 * Color.red(argb) + 0.587 * Color.green(argb) + 0.114 * Color.blue(argb)
        return luminance > 140
    }

    private companion object {
        const val KEYLINE_FRACTION = 0.12f
        const val SHADOW_BLUR_FRACTION = 0.6f
        const val SHADOW_DY_FRACTION = 0.25f
    }
}
