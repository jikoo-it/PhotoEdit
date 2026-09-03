package com.momi.watermarker.data.repository

import android.net.Uri
import com.momi.watermarker.data.storage.ImageStorage
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.MediaRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** [MediaRepository] backed by [ImageStorage] (FileProvider + MediaStore). */
class MediaRepositoryImpl @Inject constructor(
    private val imageStorage: ImageStorage,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : MediaRepository {

    override suspend fun createCaptureDestination(): Outcome<WatermarkImage> =
        withContext(dispatcher) {
            Outcome.catching { WatermarkImage(imageStorage.createCaptureFile().toString()) }
        }

    override suspend fun saveToGallery(
        image: WatermarkImage,
        displayName: String,
    ): Outcome<WatermarkImage> = withContext(dispatcher) {
        Outcome.catching {
            val saved = imageStorage.saveToGallery(Uri.parse(image.uri), displayName)
            WatermarkImage(saved.toString())
        }
    }

    override suspend fun cropImage(
        source: WatermarkImage,
        rect: NormalizedRect,
        shape: CropShape,
    ): Outcome<WatermarkImage> = withContext(dispatcher) {
        Outcome.catching {
            val cropped = imageStorage.cropToCache(Uri.parse(source.uri), rect, shape)
            WatermarkImage(cropped.toString())
        }
    }
}
