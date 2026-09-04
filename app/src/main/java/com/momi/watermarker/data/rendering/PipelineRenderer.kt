package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.WatermarkType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a [Pipeline] of [ImageOp]s to a bitmap, in order.
 *
 * Each op is turned into a bitmap → bitmap transformation by delegating to a
 * dedicated processor (e.g. [WatermarkRenderer]); this class only owns the
 * folding and the lifecycle of the intermediate bitmaps it creates. It never
 * mutates or recycles the caller-owned [source]; every processor returns a new
 * bitmap, so intermediates are recycled here as the fold advances.
 */
@Singleton
class PipelineRenderer @Inject constructor(
    private val watermarkRenderer: WatermarkRenderer,
    private val geometryProcessor: GeometryProcessor,
    private val colorProcessor: ColorProcessor,
    private val effectProcessor: EffectProcessor,
    private val frameProcessor: FrameProcessor,
) {

    /**
     * Returns a new bitmap that is [source] with every op in [pipeline] applied.
     *
     * [watermarkBitmaps] resolves an image-watermark URI to its already-decoded
     * bitmap; the caller owns and recycles those. [source] is left untouched and
     * the returned bitmap is always distinct from it, so the caller can recycle
     * [source] independently.
     */
    fun render(
        source: Bitmap,
        pipeline: Pipeline,
        watermarkBitmaps: Map<String, Bitmap> = emptyMap(),
    ): Bitmap {
        var current = source
        for (op in pipeline.ops) {
            val next = apply(current, op, watermarkBitmaps)
            // Recycle the intermediate we produced, but never the caller's source.
            if (current !== source && current !== next) current.recycle()
            current = next
        }
        // Guarantee a distinct result so the caller can always recycle [source].
        return if (current === source) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, /* isMutable = */ true)
        } else {
            current
        }
    }

    private fun apply(
        source: Bitmap,
        op: ImageOp,
        watermarkBitmaps: Map<String, Bitmap>,
    ): Bitmap = when (op) {
        is ImageOp.Crop -> geometryProcessor.crop(source, op)
        is ImageOp.Transform -> geometryProcessor.transform(source, op)
        is ImageOp.Resize -> geometryProcessor.resize(source, op)
        is ImageOp.AspectPad -> geometryProcessor.pad(source, op)
        is ImageOp.Filter -> colorProcessor.filter(source, op)
        is ImageOp.Adjust -> colorProcessor.adjust(source, op)
        is ImageOp.Pixelate -> effectProcessor.pixelate(source, op)
        is ImageOp.Frame -> frameProcessor.frame(source, op)
        is ImageOp.Watermark -> {
            val watermarkBitmap = op.config.imageUri
                ?.takeIf { op.config.type == WatermarkType.IMAGE }
                ?.let { watermarkBitmaps[it] }
            watermarkRenderer.render(source, op.config, watermarkBitmap)
        }
    }
}
