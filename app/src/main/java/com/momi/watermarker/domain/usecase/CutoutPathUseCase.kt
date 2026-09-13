package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.isUsableSelection
import com.momi.watermarker.domain.repository.ImageCutoutRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Cuts out the region inside a hand-traced outline on [sourceUri]. */
class CutoutPathUseCase @Inject constructor(
    private val repository: ImageCutoutRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        outline: List<NormalizedPoint>,
    ): Outcome<String> {
        if (!outline.isUsableSelection()) {
            return Outcome.Failure(IllegalArgumentException("Selection is too small."))
        }
        return repository.cutoutPath(sourceUri, outline)
    }
}
