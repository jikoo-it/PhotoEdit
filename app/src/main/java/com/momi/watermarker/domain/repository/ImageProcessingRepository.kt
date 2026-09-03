package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.Pipeline
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.util.Outcome

/**
 * Applies a [Pipeline] of edits to an image.
 *
 * Declared in the domain layer (Dependency Inversion): use cases depend on this
 * abstraction, and the data layer provides the concrete bitmap-based renderer.
 */
interface ImageProcessingRepository {

    /**
     * Runs every op in [pipeline] over [source], writes the result to a
     * temporary location, and returns a reference to the new image.
     */
    suspend fun applyPipeline(
        source: WatermarkImage,
        pipeline: Pipeline,
    ): Outcome<WatermarkImage>
}
