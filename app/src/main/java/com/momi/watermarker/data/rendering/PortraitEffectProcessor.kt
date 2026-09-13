package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import com.momi.watermarker.domain.model.PortraitEffect

/**
 * Reusable, UI-independent portrait-effect component: keeps the detected
 * person(s) in color and processes the background (grayscale, optionally
 * blurred), blending with a feathered segmentation mask.
 *
 * Implementations do all work off the main thread and never recycle [bitmap]
 * (the caller owns it); they return a new bitmap.
 */
interface PortraitEffectProcessor {
    suspend fun apply(bitmap: Bitmap, effect: PortraitEffect): Bitmap

    /**
     * Returns a copy of [bitmap] where non-person pixels are fully transparent.
     * The caller owns [bitmap] and the returned bitmap.
     */
    suspend fun extractForeground(bitmap: Bitmap): Bitmap

    /**
     * Grayscale (± blur) [source], then stamp [foreground] on top. [source] is
     * the full photo and stays opaque as the backdrop; [foreground] should be
     * the color subject with a transparent background.
     */
    fun composite(source: Bitmap, foreground: Bitmap, effect: PortraitEffect): Bitmap
}
