package com.momi.watermarker.domain.model

/**
 * A decorative frame drawn around the image. [NONE] is the identity (no frame).
 *
 * Pure label; the data layer maps each style to concrete Canvas drawing. Styles
 * that reveal the background ([ROUNDED], [SHADOW]) introduce transparency and so
 * require an alpha-capable export format.
 */
enum class FrameStyle(val label: String) {
    NONE("None"),

    /** A solid-color border added around the photo. */
    SOLID("Solid"),

    /** A colored mat with the photo inset within it (matted-print look). */
    INSET("Inset"),

    /** The photo's corners rounded off (optionally with a border). */
    ROUNDED("Rounded"),

    /** A soft drop shadow around the photo over a background color. */
    SHADOW("Shadow");

    companion object {
        val DEFAULT = NONE
    }
}
