package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Builds a video from a list of still images ("slideshow"), where each image is
 * shown for its own duration and each boundary between two images plays its own
 * [SlideTransition].
 *
 * Transitions are **pre-rendered** frame-by-frame (see
 * [VideoRepository.createSlideshow]) so cross-dissolves show both images at
 * once, rather than approximated by a composition-wide video effect.
 */
class CreateSlideshowUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    /** One image and how long it stays on screen. */
    data class Frame(val uri: String, val durationMs: Long)

    suspend operator fun invoke(
        frames: List<Frame>,
        transitions: List<SlideTransition>,
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
        val images = frames.map { frame ->
            VideoSegment(
                uri = frame.uri,
                isImage = true,
                imageDurationMs = frame.durationMs,
            )
        }
        // Exactly one transition per internal boundary; pad/trim a mismatched
        // list so it can't misalign against the boundaries.
        val boundaries = frames.size - 1
        val normalized = List(boundaries) { i ->
            transitions.getOrElse(i) { SlideTransition.NONE }
        }
        return videoRepository.createSlideshow(
            images = images,
            transitions = normalized,
            transitionDurationMs = transitionDurationMs,
            aspectRatio = aspectRatio,
        )
    }
}
