package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.model.VideoTransition
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Builds a video from a list of still images ("slideshow"), where each image is
 * shown for its own duration and each boundary between two images plays its own
 * [VideoTransition].
 *
 * Images are normalised to [aspectRatio] (they usually differ in size) and
 * concatenated through the shared export pipeline.
 */
class CreateSlideshowUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    /** One image and how long it stays on screen. */
    data class Frame(val uri: String, val durationMs: Long)

    suspend operator fun invoke(
        frames: List<Frame>,
        transitions: List<VideoTransition>,
        transitionDurationMs: Long,
        aspectRatio: Float?,
    ): Outcome<VideoClip> {
        if (frames.size < 2) {
            return Outcome.Failure(
                IllegalArgumentException("Pick at least two images to build a video."),
            )
        }
        if (frames.any { it.durationMs <= 0L }) {
            return Outcome.Failure(
                IllegalArgumentException("Every image needs a positive duration."),
            )
        }
        val segments = frames.map { frame ->
            VideoSegment(
                uri = frame.uri,
                isImage = true,
                imageDurationMs = frame.durationMs,
            )
        }
        // The pipeline expects one transition per internal boundary; pad/trim
        // to exactly frames - 1 so a mismatched list can't misalign fades.
        val boundaries = frames.size - 1
        val normalized = List(boundaries) { i ->
            transitions.getOrElse(i) { VideoTransition.NONE }
        }
        return videoRepository.export(
            VideoEditRequest(
                segments = segments,
                aspectRatio = aspectRatio,
                transitions = normalized,
                transitionDurationMs = transitionDurationMs,
            ),
        )
    }
}
