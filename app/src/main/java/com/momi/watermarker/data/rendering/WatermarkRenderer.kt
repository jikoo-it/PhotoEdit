package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkPattern
import com.momi.watermarker.domain.model.WatermarkType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

/**
 * Draws a [WatermarkConfig] onto a bitmap using the Android [Canvas] API.
 *
 * This class has a single responsibility — turning a config + source bitmap
 * into a watermarked bitmap — and knows nothing about files, URIs, or
 * coroutines. All layout math is resolution-independent (driven by the image's
 * shortest side) so a watermark looks the same on a thumbnail and a 12MP photo.
 *
 * Layout (corner/center anchoring or tiling) is shared between text and image
 * watermarks; only the drawing of an individual "stamp" differs.
 */
@Singleton
class WatermarkRenderer @Inject constructor(
    private val typefaceProvider: TypefaceProvider,
) {

    /**
     * Returns a new mutable bitmap containing [source] with the watermark drawn
     * on top. [source] is never mutated.
     *
     * For [WatermarkType.IMAGE] watermarks, [watermarkBitmap] supplies the
     * (already-cropped) image to stamp; if it is null there is nothing to draw.
     */
    fun render(source: Bitmap, config: WatermarkConfig, watermarkBitmap: Bitmap? = null): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ true)
        val canvas = Canvas(result)

        when (config.type) {
            WatermarkType.TEXT -> if (config.hasText) drawText(canvas, config)
            WatermarkType.IMAGE -> if (watermarkBitmap != null) {
                drawImage(canvas, config, watermarkBitmap)
            }
        }
        return result
    }

    // --- Text watermark ---

    private fun drawText(canvas: Canvas, config: WatermarkConfig) {
        val paint = buildTextPaint(config, shortestSide = minOf(canvas.width, canvas.height))
        val lines = config.text.split("\n")
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val topToBaseline = -metrics.ascent
        val lineWidths = lines.map { paint.measureText(it) }
        val blockWidth = (lineWidths.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val blockHeight = (lineHeight * lines.size).coerceAtLeast(1f)

        // Draws the whole (possibly multi-line) text block with its top-left at
        // (left, top); lines are centered within the block.
        val drawBlock: (Float, Float) -> Unit = { left, top ->
            lines.forEachIndexed { index, line ->
                val lineX = left + (blockWidth - lineWidths[index]) / 2f
                val baseline = top + topToBaseline + index * lineHeight
                canvas.drawText(line, lineX, baseline, paint)
            }
        }
        layout(canvas, config, blockWidth, blockHeight, drawBlock)
    }

    private fun buildTextPaint(config: WatermarkConfig, shortestSide: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typefaceProvider.typefaceFor(config.font)
            textSize = shortestSide * config.textSizeRatio
            color = config.colorArgb
            // Combine the color's own alpha with the user-selected opacity.
            alpha = (Color.alpha(config.colorArgb) * config.opacity).toInt().coerceIn(0, 255)
            // A soft shadow keeps light text legible over light backgrounds.
            setShadowLayer(textSize * 0.06f, 0f, 0f, Color.argb(alpha / 2, 0, 0, 0))
        }

    // --- Image watermark ---

    private fun drawImage(canvas: Canvas, config: WatermarkConfig, watermark: Bitmap) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            alpha = (config.opacity * 255).toInt().coerceIn(0, 255)
        }
        val shortestSide = minOf(canvas.width, canvas.height)
        val target = shortestSide * config.imageSizeRatio
        val scale = target / maxOf(watermark.width, watermark.height).toFloat()
        val stampWidth = (watermark.width * scale).coerceAtLeast(1f)
        val stampHeight = (watermark.height * scale).coerceAtLeast(1f)

        val drawStamp: (Float, Float) -> Unit = { left, top ->
            canvas.drawBitmap(watermark, null, RectF(left, top, left + stampWidth, top + stampHeight), paint)
        }
        layout(canvas, config, stampWidth, stampHeight, drawStamp)
    }

    // --- Shared layout ---

    /**
     * Places a stamp of size [stampWidth] x [stampHeight] according to the
     * config's pattern, invoking [drawStamp] with the stamp's top-left corner.
     */
    private fun layout(
        canvas: Canvas,
        config: WatermarkConfig,
        stampWidth: Float,
        stampHeight: Float,
        drawStamp: (left: Float, top: Float) -> Unit,
    ) {
        when (config.pattern) {
            WatermarkPattern.SINGLE_CENTER -> drawAnchored(canvas, config, Anchor.CENTER, stampWidth, stampHeight, drawStamp)
            WatermarkPattern.BOTTOM_RIGHT -> drawAnchored(canvas, config, Anchor.BOTTOM_RIGHT, stampWidth, stampHeight, drawStamp)
            WatermarkPattern.BOTTOM_LEFT -> drawAnchored(canvas, config, Anchor.BOTTOM_LEFT, stampWidth, stampHeight, drawStamp)
            WatermarkPattern.TOP_RIGHT -> drawAnchored(canvas, config, Anchor.TOP_RIGHT, stampWidth, stampHeight, drawStamp)
            WatermarkPattern.TOP_LEFT -> drawAnchored(canvas, config, Anchor.TOP_LEFT, stampWidth, stampHeight, drawStamp)
            WatermarkPattern.TILED -> drawTiled(canvas, config, stampWidth, stampHeight, extraRotation = 0f, drawStamp)
            WatermarkPattern.DIAGONAL -> drawTiled(canvas, config, stampWidth, stampHeight, extraRotation = -45f, drawStamp)
        }
    }

    /** Places a single stamp anchored to a corner/center with a uniform margin. */
    private fun drawAnchored(
        canvas: Canvas,
        config: WatermarkConfig,
        anchor: Anchor,
        stampWidth: Float,
        stampHeight: Float,
        drawStamp: (Float, Float) -> Unit,
    ) {
        val w = canvas.width
        val h = canvas.height
        val margin = minOf(w, h) * MARGIN_RATIO

        val left = when (anchor.horizontal) {
            Horizontal.START -> margin
            Horizontal.CENTER -> (w - stampWidth) / 2f
            Horizontal.END -> w - stampWidth - margin
        }
        val top = when (anchor.vertical) {
            Vertical.TOP -> margin
            Vertical.CENTER -> (h - stampHeight) / 2f
            Vertical.BOTTOM -> h - stampHeight - margin
        }

        canvas.withRotation(config.rotationDegrees, left + stampWidth / 2f, top + stampHeight / 2f) {
            drawStamp(left, top)
        }
    }

    /** Repeats the stamp across a grid, optionally rotating the whole grid. */
    private fun drawTiled(
        canvas: Canvas,
        config: WatermarkConfig,
        stampWidth: Float,
        stampHeight: Float,
        extraRotation: Float,
        drawStamp: (Float, Float) -> Unit,
    ) {
        val w = canvas.width
        val h = canvas.height
        val stepX = stampWidth * (1f + config.tileSpacingRatio)
        val stepY = stampHeight * (1f + config.lineSpacingRatio)

        // When rotating the grid, cover a square large enough that no corner of
        // the (rotated) image is left blank.
        val extent = if (extraRotation != 0f) hypot(w.toFloat(), h.toFloat()) else 0f

        canvas.save()
        canvas.rotate(extraRotation + config.rotationDegrees, w / 2f, h / 2f)

        var y = -extent
        var row = 0
        while (y < h + extent) {
            // Offset alternate rows so the pattern reads as a brick layout.
            val rowOffset = if (row % 2 == 0) 0f else stepX / 2f
            var x = -extent - rowOffset
            while (x < w + extent) {
                drawStamp(x, y)
                x += stepX
            }
            y += stepY
            row++
        }
        canvas.restore()
    }

    /** Rotate around a pivot for the duration of [block], then restore. */
    private inline fun Canvas.withRotation(
        degrees: Float,
        pivotX: Float,
        pivotY: Float,
        block: Canvas.() -> Unit,
    ) {
        if (degrees == 0f) {
            block()
            return
        }
        save()
        rotate(degrees, pivotX, pivotY)
        block()
        restore()
    }

    private enum class Horizontal { START, CENTER, END }
    private enum class Vertical { TOP, CENTER, BOTTOM }

    private enum class Anchor(val horizontal: Horizontal, val vertical: Vertical) {
        CENTER(Horizontal.CENTER, Vertical.CENTER),
        TOP_LEFT(Horizontal.START, Vertical.TOP),
        TOP_RIGHT(Horizontal.END, Vertical.TOP),
        BOTTOM_LEFT(Horizontal.START, Vertical.BOTTOM),
        BOTTOM_RIGHT(Horizontal.END, Vertical.BOTTOM),
    }

    private companion object {
        const val MARGIN_RATIO = 0.04f
    }
}
