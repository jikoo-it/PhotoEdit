package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoColorFilter
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Applies a preset color [filter] to the whole [source] video (e.g. black &
 * white, invert, warm/cool grade).
 */
class ApplyVideoFilterUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(
        source: VideoClip,
        filter: VideoColorFilter,
    ): Outcome<VideoClip> =
        videoRepository.export(
            VideoEditRequest(
                segments = listOf(VideoSegment(source.uri)),
                colorFilter = filter,
            ),
        )
}
