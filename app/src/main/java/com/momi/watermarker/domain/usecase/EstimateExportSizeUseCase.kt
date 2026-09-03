package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Estimates the on-disk size of an image re-encoded with the given export options. */
class EstimateExportSizeUseCase @Inject constructor(
    private val repository: ImageProcessingRepository,
) {
    suspend operator fun invoke(source: WatermarkImage, export: ExportOptions): Outcome<Long> =
        repository.estimateExportSize(source, export)
}
