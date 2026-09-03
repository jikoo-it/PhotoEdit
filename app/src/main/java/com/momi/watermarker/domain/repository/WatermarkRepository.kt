package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.WatermarkConfig
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.util.Outcome

/**
 * Applies a [WatermarkConfig] onto an image.
 *
 * Declared in the domain layer (Dependency Inversion): use cases depend on this
 * abstraction, and the data layer provides the concrete Canvas-based renderer.
 */
interface WatermarkRepository {

    /**
     * Renders [config] over [source] and writes the result to a temporary
     * location, returning a reference to the new image.
     */
    suspend fun applyWatermark(
        source: WatermarkImage,
        config: WatermarkConfig,
    ): Outcome<WatermarkImage>
}
