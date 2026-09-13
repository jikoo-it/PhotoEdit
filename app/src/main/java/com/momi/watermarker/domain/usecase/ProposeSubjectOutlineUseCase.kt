package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.isUsableSelection
import com.momi.watermarker.domain.repository.ImageCutoutRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Runs on-device subject detection and returns a closed outline for preview.
 * Does not write a cut-out until the user confirms.
 */
class ProposeSubjectOutlineUseCase @Inject constructor(
    private val repository: ImageCutoutRepository,
) {
    suspend operator fun invoke(sourceUri: String): Outcome<List<NormalizedPoint>> {
        return when (val result = repository.proposeSubjectOutline(sourceUri)) {
            is Outcome.Success -> {
                if (!result.data.isUsableSelection()) {
                    Outcome.Failure(IllegalStateException("Couldn't find a subject outline."))
                } else {
                    result
                }
            }
            is Outcome.Failure -> result
        }
    }
}
