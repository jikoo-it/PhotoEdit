package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.squircleUnitPoints

/**
 * Masks [src] to [shape]: the shape is drawn opaque, then the source is
 * composited only where the shape covers it (`SRC_IN`). The area outside the
 * shape is left transparent when [fillArgb] is null, or painted with that
 * opaque color otherwise. [CropShape.RECTANGLE] needs no mask and returns [src]
 * unchanged. Never recycles [src]; the caller owns its lifecycle.
 */
fun maskToShape(src: Bitmap, shape: CropShape, fillArgb: Int? = null): Bitmap {
    if (shape == CropShape.RECTANGLE) return src
    val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(shapePath(shape, src.width.toFloat(), src.height.toFloat()), paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(src, 0f, 0f, paint)
    if (fillArgb == null) return output

    // Composite the shaped (transparent-outside) result over a solid backdrop so
    // the masked area is filled instead of see-through.
    val filled = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    Canvas(filled).apply {
        drawColor(fillArgb)
        drawBitmap(output, 0f, 0f, null)
    }
    output.recycle()
    return filled
}

/** The [shape] outline filling the [w]×[h] box. */
fun shapePath(shape: CropShape, w: Float, h: Float): Path = Path().apply {
    when (shape) {
        CropShape.RECTANGLE -> addRect(0f, 0f, w, h, Path.Direction.CW)
        CropShape.CIRCLE -> addOval(0f, 0f, w, h, Path.Direction.CW)
        CropShape.ROUNDED -> {
            val r = minOf(w, h) * CropShape.ROUNDED_CORNER_FRACTION
            addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
        }
        CropShape.SQUIRCLE -> {
            squircleUnitPoints().forEachIndexed { i, (ux, uy) ->
                val px = ux * w
                val py = uy * h
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
    }
}
