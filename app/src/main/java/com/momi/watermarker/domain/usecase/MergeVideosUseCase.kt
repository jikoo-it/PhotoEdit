package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Concatenates multiple whole videos, in order, into one clip.
 *
 * Forces an output audio track so clips with differing audio presence (some
 * with sound, some without) still merge cleanly.
 */
class MergeVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(clips: List<VideoClip>): Outcome<VideoClip> {
        if (clips.size < 2) {
            return Outcome.Failure(
                IllegalArgumentException("Pick at least two videos to merge."),
            )
        }
        val segments = clips.map { VideoSegment(it.uri) }
        return videoRepository.export(
            VideoEditRequest(segments = segments, forceAudioTrack = true),
        )
    }
}
