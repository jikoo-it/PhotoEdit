package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoMetadata
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Reads duration, encoded size, and display rotation of [clip]. */
class GetVideoMetadataUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(clip: VideoClip): Outcome<VideoMetadata> =
        videoRepository.getMetadata(clip)
}
