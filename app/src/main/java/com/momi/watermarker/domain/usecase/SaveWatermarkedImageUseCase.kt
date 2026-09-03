package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.MediaRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Persists a finished (watermarked) image into the device gallery. */
class SaveWatermarkedImageUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(image: WatermarkImage): Outcome<WatermarkImage> =
        mediaRepository.saveToGallery(image, displayName = "Watermark_${System.currentTimeMillis()}")
}
