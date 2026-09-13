package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.FlattenedImage
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.repository.StudioRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Flattens a [LayerDocument] to a cached bitmap URI. */
class FlattenLayerDocumentUseCase @Inject constructor(
    private val studioRepository: StudioRepository,
) {
    suspend operator fun invoke(document: LayerDocument, maxLongEdge: Int): Outcome<FlattenedImage> =
        studioRepository.flatten(document, maxLongEdge)
}
