package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Stamps the image at [overlayImageUri] over every frame of [source] at the
 * given [alpha] opacity (centered). The basis for logo / watermark overlays on
 * video, reusing the same imagery pipeline as the photo side.
 */
class OverlayImageUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(
        source: VideoClip,
        overlayImageUri: String,
        alpha: Float = 1f,
    ): Outcome<VideoClip> =
        videoRepository.export(
            VideoEditRequest(
                segments = listOf(VideoSegment(source.uri)),
                overlayImageUri = overlayImageUri,
                overlayAlpha = alpha.coerceIn(0f, 1f),
            ),
        )
}
