package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.isUsableSelection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cuts a region out of a bitmap using a freehand outline. Pixels inside the
 * closed path stay; everything else becomes transparent.
 */
@Singleton
class PathCutout @Inject constructor() {

    fun extract(source: Bitmap, outline: List<NormalizedPoint>): Bitmap {
        require(outline.isUsableSelection()) { "Selection is too small." }
        val w = source.width
        val h = source.height
        val path = Path().apply {
            moveTo(outline[0].x * w, outline[0].y * h)
            for (i in 1 until outline.size) {
                lineTo(outline[i].x * w, outline[i].y * h)
            }
            close()
        }
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            },
        )
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawBitmap(
            mask,
            0f,
            0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) },
        )
        mask.recycle()
        return out
    }
}
