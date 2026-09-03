package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.WatermarkRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Applies a watermark to a source image, enforcing the domain rule that the
 * active watermark type must have something to render (non-blank text, or a
 * chosen image) before it can be drawn.
 */
class ApplyWatermarkUseCase @Inject constructor(
    private val watermarkRepository: WatermarkRepository,
) {
    suspend operator fun invoke(
        source: WatermarkImage,
        config: WatermarkConfig,
    ): Outcome<WatermarkImage> {
        if (!config.isRenderable) {
            return Outcome.Failure(
                IllegalArgumentException("Add watermark text or choose a watermark image first."),
            )
        }
        return watermarkRepository.applyWatermark(source, config)
    }
}
