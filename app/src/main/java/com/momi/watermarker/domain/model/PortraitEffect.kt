package com.momi.watermarker.domain.model

/**
 * A portrait effect that keeps the detected person(s) in color and processes
 * the background. The person is isolated with an actual on-device segmentation
 * mask (not a crop or color key), blended with feathered edges.
 */
sealed class PortraitEffect {

    /** Person stays in color; the background is converted to grayscale. */
    data object SelectiveColor : PortraitEffect()

    /**
     * Person stays in color and sharp; the background is grayscale **and**
     * Gaussian-blurred.
     *
     * [blurRadius] is a normalized intensity in `0f..1f`; the processor scales
     * it to a pixel radius relative to the image size so previews (downscaled)
     * and exports (full-size) look the same.
     */
    data class SelectiveColorWithBlur(
        val blurRadius: Float,
    ) : PortraitEffect()
}
