package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.repository.ImageCutoutRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Extracts the main subject from [sourceUri] on-device, returning the URI of a
 * transparent-background cut-out PNG.
 */
class CutoutSubjectUseCase @Inject constructor(
    private val repository: ImageCutoutRepository,
) {
    suspend operator fun invoke(sourceUri: String): Outcome<String> =
        repository.cutoutSubject(sourceUri)
}
