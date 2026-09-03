package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.momi.watermarker.data.rendering.WatermarkRenderer
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.model.WatermarkType
import com.momi.watermarker.domain.repository.WatermarkRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Loads the source image, delegates drawing to [WatermarkRenderer], and writes
 * the result to the shared cache — all off the main thread.
 */
class WatermarkRepositoryImpl @Inject constructor(
    private val imageStorage: ImageStorage,
    private val renderer: WatermarkRenderer,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : WatermarkRepository {

    override suspend fun applyWatermark(
        source: WatermarkImage,
        config: WatermarkConfig,
    ): Outcome<WatermarkImage> = withContext(dispatcher) {
        Outcome.catching {
            val bitmap = imageStorage.decodeBitmap(Uri.parse(source.uri))
            // Decode the (cropped) watermark image only when in image mode.
            val watermarkBitmap: Bitmap? = config.imageUri
                ?.takeIf { config.type == WatermarkType.IMAGE }
                ?.let { imageStorage.decodeBitmap(Uri.parse(it)) }
            try {
                val watermarked = renderer.render(bitmap, config, watermarkBitmap)
                val outputUri = imageStorage.writeToCache(watermarked, prefix = "watermarked")
                watermarked.recycle()
                WatermarkImage(outputUri.toString())
            } finally {
                bitmap.recycle()
                watermarkBitmap?.recycle()
            }
        }
    }
}
