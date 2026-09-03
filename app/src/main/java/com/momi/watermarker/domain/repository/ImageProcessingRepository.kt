package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.ExportOptions
import com.momi.watermarker.domain.model.ImageInfo
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
     * Runs every op in [pipeline] over [source], encodes the result per [export],
     * writes it to a temporary location, and returns a reference to the new image.
     * An empty [pipeline] is valid: the image is simply re-encoded per [export]
     * (e.g. to compress or convert format with no other edits).
     */
    suspend fun applyPipeline(
        source: WatermarkImage,
        pipeline: Pipeline,
        export: ExportOptions = ExportOptions(),
    ): Outcome<WatermarkImage>

    /**
     * Reads [source]'s dimensions and encoded byte size without decoding the
     * full bitmap into memory. Dimensions account for EXIF orientation.
     */
    suspend fun imageInfo(source: WatermarkImage): Outcome<ImageInfo>

    /**
     * Estimates the byte size [source]'s pixels would occupy when re-encoded per
     * [export] (used to preview the effect of quality / target-size settings).
     */
    suspend fun estimateExportSize(source: WatermarkImage, export: ExportOptions): Outcome<Long>
}
