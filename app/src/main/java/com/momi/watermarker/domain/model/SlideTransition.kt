package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

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
enum class SlideTransition(@StringRes val labelRes: Int) {
    /** Hard cut — no in-between frames. */
    NONE(R.string.slide_transition_cut),

    /** Cross-dissolve: outgoing fades out as incoming fades in (both visible). */
    DISSOLVE(R.string.slide_transition_dissolve),

    /** Dip through black, then back up into the incoming image. */
    FADE_BLACK(R.string.slide_transition_fade_black),

    /** Dip through white, then back up into the incoming image. */
    FADE_WHITE(R.string.slide_transition_fade_white),

    // Wipe: a hard edge sweeps across, revealing the incoming image.
    WIPE_LEFT(R.string.slide_transition_wipe_left),
    WIPE_RIGHT(R.string.slide_transition_wipe_right),
    WIPE_UP(R.string.slide_transition_wipe_up),
    WIPE_DOWN(R.string.slide_transition_wipe_down),

    // Push: both images move together; outgoing is pushed off as incoming enters.
    SLIDE_LEFT(R.string.slide_transition_push_left),
    SLIDE_RIGHT(R.string.slide_transition_push_right),
    SLIDE_UP(R.string.slide_transition_push_up),
    SLIDE_DOWN(R.string.slide_transition_push_down),

    // Cover: incoming slides in over a stationary outgoing image.
    COVER_LEFT(R.string.slide_transition_cover_left),
    COVER_RIGHT(R.string.slide_transition_cover_right),
    COVER_UP(R.string.slide_transition_cover_up),
    COVER_DOWN(R.string.slide_transition_cover_down),

    // Reveal: outgoing slides off to expose a stationary incoming image beneath.
    REVEAL_LEFT(R.string.slide_transition_reveal_left),
    REVEAL_RIGHT(R.string.slide_transition_reveal_right),
    REVEAL_UP(R.string.slide_transition_reveal_up),
    REVEAL_DOWN(R.string.slide_transition_reveal_down),

    /** Incoming grows from the centre over the outgoing image. */
    ZOOM_IN(R.string.slide_transition_zoom_in),

    /** Outgoing shrinks to the centre, revealing the incoming image. */
    ZOOM_OUT(R.string.slide_transition_zoom_out),

    /** Circular reveal of the incoming image, expanding from the centre. */
    IRIS_OPEN(R.string.slide_transition_iris_open),

    /** Circular collapse of the outgoing image toward the centre. */
    IRIS_CLOSE(R.string.slide_transition_iris_close),

    /** Horizontal blinds that fill in with the incoming image. */
    BLINDS_H(R.string.slide_transition_blinds_h),

    /** Vertical blinds that fill in with the incoming image. */
    BLINDS_V(R.string.slide_transition_blinds_v),

    /** Checkerboard of cells that flip to the incoming image. */
    CHECKER(R.string.slide_transition_checker),

    /** Diagonal wipe sweeping from the top-left corner. */
    WIPE_DIAG_TL(R.string.slide_transition_wipe_diag_tl),

    /** Diagonal wipe sweeping from the top-right corner. */
    WIPE_DIAG_TR(R.string.slide_transition_wipe_diag_tr),

    /** Incoming rotates and fades in over the outgoing image. */
    ROTATE(R.string.slide_transition_rotate);

    companion object {
        val DEFAULT = DISSOLVE
    }
}
