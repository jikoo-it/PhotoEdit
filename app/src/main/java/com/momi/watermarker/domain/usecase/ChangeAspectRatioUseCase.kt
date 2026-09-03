package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Reframes [source] to the target [aspectRatio] (width / height), cropping to
 * fill so the whole frame is used (no letterboxing).
 */
class ChangeAspectRatioUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(
        source: VideoClip,
        aspectRatio: Float,
    ): Outcome<VideoClip> {
        if (aspectRatio <= 0f) {
            return Outcome.Failure(
                IllegalArgumentException("Aspect ratio must be positive."),
            )
        }
        return videoRepository.export(
            VideoEditRequest(
                segments = listOf(VideoSegment(source.uri)),
                aspectRatio = aspectRatio,
            ),
        )
    }
}
