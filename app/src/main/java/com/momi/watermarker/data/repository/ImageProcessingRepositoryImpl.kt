package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.momi.watermarker.data.rendering.PipelineRenderer
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.DefaultDispatcher
import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.WatermarkType
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Decodes the source image, feeds it through the [PipelineRenderer], and writes
 * the result to the shared cache — all off the main thread. Any image watermarks
 * referenced by the pipeline are decoded up front and recycled afterwards.
 */
class ImageProcessingRepositoryImpl @Inject constructor(
    private val imageStorage: ImageStorage,
    private val renderer: PipelineRenderer,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ImageProcessingRepository {

    override suspend fun applyPipeline(
        source: WatermarkImage,
        pipeline: Pipeline,
        export: ExportOptions,
    ): Outcome<WatermarkImage> = withContext(dispatcher) {
        Outcome.catching {
            val bitmap = imageStorage.decodeBitmap(Uri.parse(source.uri))
            val watermarkBitmaps = decodeWatermarkBitmaps(pipeline)
            try {
                val result = renderer.render(bitmap, pipeline, watermarkBitmaps)
                val outputUri = imageStorage.writeToCache(
                    bitmap = result,
                    prefix = "processed",
                    format = export.format,
                    quality = export.effectiveQuality,
                )
                if (result !== bitmap) result.recycle()
                WatermarkImage(outputUri.toString())
            } finally {
                bitmap.recycle()
                watermarkBitmaps.values.forEach(Bitmap::recycle)
            }
        }
    }

    /** Decodes the (already-cropped) image watermarks the pipeline needs, keyed by URI. */
    private fun decodeWatermarkBitmaps(pipeline: Pipeline): Map<String, Bitmap> =
        pipeline.ops
            .filterIsInstance<ImageOp.Watermark>()
            .mapNotNull { op -> op.config.imageUri?.takeIf { op.config.type == WatermarkType.IMAGE } }
            .distinct()
            .associateWith { uri -> imageStorage.decodeBitmap(Uri.parse(uri)) }
}
