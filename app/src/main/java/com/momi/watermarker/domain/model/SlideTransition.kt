package com.momi.watermarker.domain.model

/**
 * A transition played at the boundary between two images in a slideshow.
 *
 * Unlike [VideoTransition] (composition-wide, single-texture effects used for
 * video merges, which can only dim/move one clip at a time), slideshow
 * transitions are **pre-rendered**: because both neighbours are still images,
 * the data layer draws every in-between frame on a `Canvas` — blending both
 * images per pixel — and splices those frames into the timeline as short image
 * clips. That yields **true cross-dissolves** (both images visible at once) and
 * a wide family of effects with no GL shader and no clip overlap.
 *
 * Directional variants encode the direction the effect travels (← → ↑ ↓).
 */
enum class SlideTransition(val label: String) {
    /** Hard cut — no in-between frames. */
    NONE("Cut"),

    /** Cross-dissolve: outgoing fades out as incoming fades in (both visible). */
    DISSOLVE("Dissolve"),

    /** Dip through black, then back up into the incoming image. */
    FADE_BLACK("Fade black"),

    /** Dip through white, then back up into the incoming image. */
    FADE_WHITE("Fade white"),

    // Wipe: a hard edge sweeps across, revealing the incoming image.
    WIPE_LEFT("Wipe ←"),
    WIPE_RIGHT("Wipe →"),
    WIPE_UP("Wipe ↑"),
    WIPE_DOWN("Wipe ↓"),

    // Push: both images move together; outgoing is pushed off as incoming enters.
    SLIDE_LEFT("Push ←"),
    SLIDE_RIGHT("Push →"),
    SLIDE_UP("Push ↑"),
    SLIDE_DOWN("Push ↓"),

    // Cover: incoming slides in over a stationary outgoing image.
    COVER_LEFT("Cover ←"),
    COVER_RIGHT("Cover →"),
    COVER_UP("Cover ↑"),
    COVER_DOWN("Cover ↓"),

    // Reveal: outgoing slides off to expose a stationary incoming image beneath.
    REVEAL_LEFT("Reveal ←"),
    REVEAL_RIGHT("Reveal →"),
    REVEAL_UP("Reveal ↑"),
    REVEAL_DOWN("Reveal ↓"),

    /** Incoming grows from the centre over the outgoing image. */
    ZOOM_IN("Zoom in"),

    /** Outgoing shrinks to the centre, revealing the incoming image. */
    ZOOM_OUT("Zoom out"),

    /** Circular reveal of the incoming image, expanding from the centre. */
    IRIS_OPEN("Iris open"),

    /** Circular collapse of the outgoing image toward the centre. */
    IRIS_CLOSE("Iris close"),

    /** Horizontal blinds that fill in with the incoming image. */
    BLINDS_H("Blinds ═"),

    /** Vertical blinds that fill in with the incoming image. */
    BLINDS_V("Blinds ║"),

    /** Checkerboard of cells that flip to the incoming image. */
    CHECKER("Checker"),

    /** Diagonal wipe sweeping from the top-left corner. */
    WIPE_DIAG_TL("Wipe ◤"),

    /** Diagonal wipe sweeping from the top-right corner. */
    WIPE_DIAG_TR("Wipe ◥"),

    /** Incoming rotates and fades in over the outgoing image. */
    ROTATE("Rotate");

    companion object {
        val DEFAULT = DISSOLVE
    }
}
