package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Persists [clip] to the shared gallery under [displayName]. */
class SaveVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(clip: VideoClip, displayName: String): Outcome<VideoClip> =
        videoRepository.saveToGallery(clip, displayName)
}
