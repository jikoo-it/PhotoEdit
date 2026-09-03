package com.momi.watermarker.domain.model

/**
 * The animation played at the boundary between two adjacent clips (typically
 * two images in a slideshow). Each boundary carries its own value, so every
 * transition in a sequence can differ.
 *
 * - [NONE]  — a hard cut.
 * - [FADE]  — dip through black: the outgoing clip darkens, the incoming clip
 *   brightens back in.
 * - [FLASH] — dip through white.
 * - [SLIDE] — the outgoing clip slides off to the left, the incoming clip
 *   slides in from the right.
 * - [ZOOM]  — the outgoing clip shrinks to the centre, the incoming clip grows
 *   back out from it.
 *
 * (Media3 1.5.1 has no native clip-to-clip transition API; see
 * docs/video-editing.md §4b. These are all rendered by composition-wide,
 * time-varying effects that need no clip overlap — [FADE]/[FLASH] via a colour
 * matrix, [SLIDE]/[ZOOM] via a geometric matrix. True cross-dissolve, where
 * both clips are visible at once, requires overlapping sequences and remains
 * future work.)
 */
enum class VideoTransition {
    NONE,
    FADE,
    FLASH,
    SLIDE,
    ZOOM,
}
