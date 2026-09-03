package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Applies the same [WatermarkConfig] to a batch of source images and saves each
 * result to the gallery. Rendering happens here (rather than reusing a single
 * cached preview) so every image in the batch gets a pixel-accurate watermark.
 *
 * Failures are collected per-image rather than aborting the whole batch, so a
 * single unreadable image doesn't lose the others.
 */
class SaveWatermarkedImagesUseCase @Inject constructor(
    private val applyWatermark: ApplyWatermarkUseCase,
    private val saveWatermarkedImage: SaveWatermarkedImageUseCase,
) {
    suspend operator fun invoke(
        sources: List<WatermarkImage>,
        config: WatermarkConfig,
    ): BatchSaveResult {
        val errors = mutableListOf<Throwable>()
        var savedCount = 0

        for (source in sources) {
            when (val rendered = applyWatermark(source, config)) {
                is Outcome.Success -> when (val saved = saveWatermarkedImage(rendered.data)) {
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
