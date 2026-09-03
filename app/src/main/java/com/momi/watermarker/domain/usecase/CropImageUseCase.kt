package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.MediaRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Crops a chosen image to [NormalizedRect] and masks it to [shape] to produce an
 * image watermark.
 */
class CropImageUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(
        source: WatermarkImage,
        rect: NormalizedRect,
        shape: CropShape = CropShape.DEFAULT,
    ): Outcome<WatermarkImage> = mediaRepository.cropImage(source, rect, shape)
}
