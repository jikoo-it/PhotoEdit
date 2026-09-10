package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/** What to place behind a cut-out subject. */
enum class BackgroundMode(@StringRes val labelRes: Int) {
    /** Keep only the subject; the background is fully transparent (exports as PNG). */
    TRANSPARENT(R.string.background_mode_transparent),

    /** Fill the background with a single solid color. */
    COLOR(R.string.background_mode_color),

    /** Keep the original photo behind the subject, but blurred (portrait look). */
    BLUR(R.string.background_mode_blur),

    /** Replace the background with another picked image (cover-fit). */
    IMAGE(R.string.background_mode_image),
}

/**
 * A platform-neutral description of how to composite a previously extracted
 * subject [cutoutUri] over a chosen background. The data layer turns this into
 * a rendered image.
 *
 * [sourceUri] is the original photo (needed for the [BackgroundMode.BLUR] look);
 * [cutoutUri] is the transparent subject PNG produced by segmentation.
 */
data class CutoutRenderSpec(
    val sourceUri: String,
    val cutoutUri: String,
    val mode: BackgroundMode,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundImageUri: String? = null,
    /** Blur amount in 0f..1f, only used by [BackgroundMode.BLUR]. */
    val blurStrength: Float = 0.6f,
) {
    val isTransparent: Boolean get() = mode == BackgroundMode.TRANSPARENT
}
