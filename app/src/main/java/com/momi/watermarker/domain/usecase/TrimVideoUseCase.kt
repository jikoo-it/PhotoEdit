package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Trims [source] to the window [startMs, endMs], enforcing the domain rule that
 * the window is non-empty and ordered before any work is dispatched.
 */
class TrimVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(
        source: VideoClip,
        startMs: Long,
        endMs: Long,
    ): Outcome<VideoClip> {
        if (startMs < 0 || endMs <= startMs) {
            return Outcome.Failure(
                IllegalArgumentException("Trim window must be positive (start < end)."),
            )
        }
        return videoRepository.trim(source, startMs, endMs)
    }
}
