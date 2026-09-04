package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.CutoutRenderSpec
import com.momi.watermarker.domain.repository.ImageCutoutRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Composites an extracted subject over the background described by [spec],
 * returning the URI of the rendered image.
 */
class RenderCutoutUseCase @Inject constructor(
    private val repository: ImageCutoutRepository,
) {
    suspend operator fun invoke(spec: CutoutRenderSpec): Outcome<String> =
        repository.renderResult(spec)
}
