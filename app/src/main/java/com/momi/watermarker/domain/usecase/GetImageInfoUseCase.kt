package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.ImageInfo
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.repository.ImageProcessingRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Reads an image's dimensions and encoded size without decoding the full bitmap. */
class GetImageInfoUseCase @Inject constructor(
    private val repository: ImageProcessingRepository,
) {
    suspend operator fun invoke(source: WatermarkImage): Outcome<ImageInfo> =
        repository.imageInfo(source)
}
