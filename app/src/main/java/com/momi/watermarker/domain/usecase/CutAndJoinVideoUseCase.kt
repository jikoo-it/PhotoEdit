package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.TrimRange
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Keeps several [ranges] of a single [source] video and stitches them together
 * (in the given order) into one clip — i.e. cut out the unwanted parts and join
 * what remains. Each kept range becomes one segment of the concatenation.
 */
class CutAndJoinVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(
        source: VideoClip,
        ranges: List<TrimRange>,
    ): Outcome<VideoClip> {
        if (ranges.isEmpty()) {
            return Outcome.Failure(
                IllegalArgumentException("Add at least one segment to keep."),
            )
        }
        if (ranges.any { !it.isValid }) {
            return Outcome.Failure(
                IllegalArgumentException("Every segment must be positive (start < end)."),
            )
        }
        val segments = ranges.map { VideoSegment(source.uri, it.startMs, it.endMs) }
        return videoRepository.export(VideoEditRequest(segments = segments))
    }
}
