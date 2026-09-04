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
    /**
     * @param clips videos to concatenate, in order.
     * @param aspectRatios optional per-clip reframe (width/height); an entry may
     *   be null to keep that clip's own ratio. When shorter than [clips], missing
     *   entries default to null.
     */
    suspend operator fun invoke(
        clips: List<VideoClip>,
        aspectRatios: List<Float?> = emptyList(),
    ): Outcome<VideoClip> {
        if (clips.size < 2) {
            return Outcome.Failure(
                IllegalArgumentException("Pick at least two videos to merge."),
            )
        }
        val segments = clips.mapIndexed { index, clip ->
            VideoSegment(clip.uri, aspectRatio = aspectRatios.getOrNull(index))
        }
        return videoRepository.export(
            VideoEditRequest(segments = segments, forceAudioTrack = true),
        )
    }
}
