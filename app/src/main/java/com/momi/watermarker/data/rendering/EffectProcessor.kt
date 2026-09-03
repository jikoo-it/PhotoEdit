package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import com.momi.watermarker.domain.model.ImageOp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies pixel-sampling effects to a bitmap. Like the other processors, each
 * method returns a new bitmap or the input unchanged (for a no-op) and never
 * recycles the input; [PipelineRenderer] owns the lifecycle.
 */
@Singleton
class EffectProcessor @Inject constructor() {

    /**
     * Mosaic/pixelate: downsamples [src] to blocks of [op].blockSizePx and scales
     * it back up with nearest-neighbor sampling so each block is a flat color.
     */
    fun pixelate(src: Bitmap, op: ImageOp.Pixelate): Bitmap {
        if (op.isIdentity) return src
        val block = op.blockSizePx
        val smallW = (src.width / block).coerceAtLeast(1)
        val smallH = (src.height / block).coerceAtLeast(1)
        // Averaged downscale (filter = true), then blocky upscale (filter = false).
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, /* filter = */ true)
        return try {
            Bitmap.createScaledBitmap(small, src.width, src.height, /* filter = */ false)
        } finally {
            if (small !== src) small.recycle()
        }
    }
}
