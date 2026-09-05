package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.PortraitEffect
import com.momi.watermarker.domain.util.Outcome

/**
 * Applies a [PortraitEffect] to a single image, returning the URI of the
 * rendered result. The heavy bitmap work happens off the main thread.
 */
interface PortraitEffectRepository {

    /**
     * Renders [effect] onto the image at [sourceUri], working at a resolution
     * capped to [maxLongEdge] px on the long side (small for live previews,
     * large for a final export), and returns the result's URI.
     */
    suspend fun render(
        sourceUri: String,
        effect: PortraitEffect,
        maxLongEdge: Int,
    ): Outcome<String>
}
