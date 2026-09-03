package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Strips the audio track from [source], producing a silent video. */
class RemoveAudioUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(source: VideoClip): Outcome<VideoClip> =
        videoRepository.export(
            VideoEditRequest(
                segments = listOf(VideoSegment(source.uri)),
                removeAudio = true,
            ),
        )
}
