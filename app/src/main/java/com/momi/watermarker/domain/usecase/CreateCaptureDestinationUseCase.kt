package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.MediaRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Produces a destination for the camera to write a captured photo into.
 * The returned URI is handed to the `TakePicture` Activity Result contract.
 */
class CreateCaptureDestinationUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(): Outcome<WatermarkImage> =
        mediaRepository.createCaptureDestination()
}
