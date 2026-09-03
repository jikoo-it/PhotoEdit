package com.momi.watermarker.domain.usecase

import com.momi.watermarker.domain.model.WatermarkFont
import com.momi.watermarker.domain.model.WatermarkPattern
import javax.inject.Inject

/**
 * Exposes the predefined watermark patterns and fonts the user can choose
 * from. Centralizing this here means the presentation layer never hard-codes
 * the catalog, and it can later be swapped for a remote/config-driven source.
 */
class GetWatermarkOptionsUseCase @Inject constructor() {

    fun patterns(): List<WatermarkPattern> = WatermarkPattern.entries

    fun fonts(): List<WatermarkFont> = WatermarkFont.entries
}
