package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.OverlayPosition
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/**
 * Stamps an overlay — either an image (optionally cropped) or a line of text —
 * over every frame of [source], sized, positioned and faded as requested. The
 * basis for logo / watermark overlays on video.
 */
class OverlayImageUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(
        source: VideoClip,
        imageUri: String? = null,
        text: String? = null,
        textColorArgb: Int = WHITE,
        alpha: Float = 1f,
        position: OverlayPosition = OverlayPosition.DEFAULT,
        sizeFraction: Float = 0.3f,
        cropRect: NormalizedRect? = null,
        cropShape: CropShape = CropShape.RECTANGLE,
    ): Outcome<VideoClip> {
        val cleanText = text?.takeIf { it.isNotBlank() }
        require(cleanText != null || imageUri != null) {
            "An overlay needs either text or an image."
        }
        return videoRepository.export(
            VideoEditRequest(
                segments = listOf(VideoSegment(source.uri)),
                // Text takes precedence; only one overlay is stamped.
                overlayImageUri = if (cleanText != null) null else imageUri,
                overlayText = cleanText,
                overlayTextColorArgb = textColorArgb,
                overlayAlpha = alpha.coerceIn(0f, 1f),
                overlayPosition = position,
                overlaySizeFraction = sizeFraction.coerceIn(0.02f, 1f),
                overlayCropRect = if (cleanText != null) null else cropRect,
                overlayCropShape = cropShape,
            ),
        )
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
