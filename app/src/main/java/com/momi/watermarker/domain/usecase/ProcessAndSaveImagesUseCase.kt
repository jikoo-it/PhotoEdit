package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Applies the same edit [Pipeline] to a batch of source images and saves each
 * result to the gallery. Rendering happens here (rather than reusing a single
 * cached preview) so every image in the batch is processed at full resolution.
 *
 * Failures are collected per-image rather than aborting the whole batch, so a
 * single unreadable image doesn't lose the others.
 */
class ProcessAndSaveImagesUseCase @Inject constructor(
    private val applyPipeline: ApplyPipelineUseCase,
    private val saveImage: SaveImageUseCase,
) {
    suspend operator fun invoke(
        sources: List<WatermarkImage>,
        pipeline: Pipeline,
    ): BatchSaveResult {
        val errors = mutableListOf<Throwable>()
        var savedCount = 0

        for (source in sources) {
            when (val rendered = applyPipeline(source, pipeline)) {
                is Outcome.Success -> when (val saved = saveImage(rendered.data)) {
                    is Outcome.Success -> savedCount++
                    is Outcome.Failure -> errors.add(saved.error)
                }
                is Outcome.Failure -> errors.add(rendered.error)
            }
        }

        return BatchSaveResult(savedCount = savedCount, requested = sources.size, errors = errors)
    }
}

/** Summary of a batch save: how many were requested, saved, and any failures. */
data class BatchSaveResult(
    val savedCount: Int,
    val requested: Int,
    val errors: List<Throwable>,
) {
    val anySucceeded: Boolean get() = savedCount > 0
    val allSucceeded: Boolean get() = savedCount == requested
}
