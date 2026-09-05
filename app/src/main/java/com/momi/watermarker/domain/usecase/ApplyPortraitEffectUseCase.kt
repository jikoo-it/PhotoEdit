package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.PortraitEffect
import com.momi.watermarker.domain.repository.PortraitEffectRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Applies a portrait [effect] (selective color, optionally with background
 * blur) to the image at [sourceUri], returning the rendered result's URI.
 *
 * Use a small [maxLongEdge] for a responsive live preview and a large one for
 * the final high-resolution export.
 */
class ApplyPortraitEffectUseCase @Inject constructor(
    private val repository: PortraitEffectRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        effect: PortraitEffect,
        maxLongEdge: Int,
    ): Outcome<String> = repository.render(sourceUri, effect, maxLongEdge)
}
