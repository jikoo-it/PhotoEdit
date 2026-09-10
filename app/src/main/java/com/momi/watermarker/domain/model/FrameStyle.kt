package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * A decorative frame drawn around the image. [NONE] is the identity (no frame).
 *
 * Pure label; the data layer maps each style to concrete Canvas drawing. Styles
 * that reveal the background ([ROUNDED], [SHADOW]) introduce transparency and so
 * require an alpha-capable export format.
 */
enum class FrameStyle(@StringRes val labelRes: Int) {
    NONE(R.string.frame_style_none),

    /** A solid-color border added around the photo. */
    SOLID(R.string.frame_style_solid),

    /** A colored mat with the photo inset within it (matted-print look). */
    INSET(R.string.frame_style_inset),

    /** The photo's corners rounded off (optionally with a border). */
    ROUNDED(R.string.frame_style_rounded),

    /** A soft drop shadow around the photo over a background color. */
    SHADOW(R.string.frame_style_shadow);

    companion object {
        val DEFAULT = NONE
    }
}
