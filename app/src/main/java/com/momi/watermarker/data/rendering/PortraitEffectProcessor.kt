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
}
