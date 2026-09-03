package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Reads the duration (ms) of [clip], used to bound the trim range UI. */
class GetVideoDurationUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(clip: VideoClip): Outcome<Long> =
        videoRepository.getDurationMs(clip)
}
