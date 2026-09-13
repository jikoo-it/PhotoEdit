package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.repository.StudioRepository
import com.momi.watermarker.domain.util.Outcome
import javax.inject.Inject

/** Extracts detected people from [sourceUri] as a transparent PNG. */
class ExtractPeopleUseCase @Inject constructor(
    private val studioRepository: StudioRepository,
) {
    suspend operator fun invoke(sourceUri: String, maxLongEdge: Int): Outcome<String> =
        studioRepository.extractPeople(sourceUri, maxLongEdge)
}
