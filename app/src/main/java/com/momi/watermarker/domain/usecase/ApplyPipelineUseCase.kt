package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Applies an edit [Pipeline] to a single image, enforcing the domain rule that
 * there must be at least one op to run.
 */
class ApplyPipelineUseCase @Inject constructor(
    private val repository: ImageProcessingRepository,
) {
    suspend operator fun invoke(
        source: WatermarkImage,
        pipeline: Pipeline,
    ): Outcome<WatermarkImage> {
        if (pipeline.isEmpty) {
            return Outcome.Failure(IllegalArgumentException("Add at least one edit first."))
        }
        return repository.applyPipeline(source, pipeline)
    }
}
