package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.MediaRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Persists a finished (processed) image into the device gallery as [format]. */
class SaveImageUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(
        image: WatermarkImage,
        format: ExportFormat = ExportFormat.DEFAULT,
    ): Outcome<WatermarkImage> =
        mediaRepository.saveToGallery(image, displayName = "Momi_${System.currentTimeMillis()}", format = format)
}
