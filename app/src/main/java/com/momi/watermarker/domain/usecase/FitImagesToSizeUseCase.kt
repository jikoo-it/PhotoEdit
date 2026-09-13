package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * For each image, shrinks pixels (aspect kept) only if quality-only compression
 * cannot hit [targetBytes], then encodes to that budget and saves to the gallery.
 * Each photo is fitted independently so a mixed batch can share one size target.
 */
class FitImagesToSizeUseCase @Inject constructor(
    private val processing: ImageProcessingRepository,
    private val saveImage: SaveImageUseCase,
) {
    suspend operator fun invoke(
        sources: List<WatermarkImage>,
        targetBytes: Long,
        format: ExportFormat,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BatchSaveResult {
        val errors = mutableListOf<Throwable>()
        var savedCount = 0
        for ((index, source) in sources.withIndex()) {
            when (val fitted = processing.fitToTargetSize(source, targetBytes, format)) {
                is Outcome.Success -> when (val saved = saveImage(fitted.data, format)) {
                    is Outcome.Success -> savedCount++
                    is Outcome.Failure -> errors.add(saved.error)
                }
                is Outcome.Failure -> errors.add(fitted.error)
            }
            onProgress(index + 1, sources.size)
        }
        return BatchSaveResult(savedCount = savedCount, requested = sources.size, errors = errors)
    }
}
