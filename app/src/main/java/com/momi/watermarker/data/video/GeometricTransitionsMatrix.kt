package com.momi.watermarker.data.video

import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation

/**
 * A time-varying [MatrixTransformation] that slides or zooms the frame around a
 * set of composition timestamps — geometric clip-to-clip transitions that need
 * no clip overlap (Media3 1.5.1 has no native transition API; see
 * docs/video-editing.md §4b).
 *
 * The returned [Matrix] operates in normalised device coordinates: the visible
 * frame spans [-1, 1] on each axis and is centred at the origin, so a translate
 * of 2 moves it fully off-screen and a scale is about the frame centre.
 *
 * For each boundary (a centre time and half-width) the first half animates the
 * outgoing clip away and the second half animates the incoming clip back in:
 * - [SLIDE] — outgoing slides left off-screen, incoming slides in from the right.
 * - [ZOOM]  — outgoing shrinks to the centre, incoming grows back out from it.
 *
 * Outside every window the matrix is identity. Because slides/zooms reveal the
 * (black) background rather than the other clip, this reads as a motion through
 * black rather than a true cross-dissolve.
 */
class GeometricTransitionsMatrix(
    private val centersUs: LongArray,
    private val halfWidthsUs: LongArray,
    /** Per-boundary mode: false = slide, true = zoom. */
    private val zoom: BooleanArray,
) : MatrixTransformation {

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val matrix = Matrix() // identity
        for (i in centersUs.indices) {
            val halfWidth = halfWidthsUs[i]
            if (halfWidth <= 0L) continue
            val offset = presentationTimeUs - centersUs[i]
            if (offset <= -halfWidth || offset >= halfWidth) continue

            // progress: 0 in place → 1 fully transitioned (at the centre).
            val outgoing = offset < 0L
            val progress = if (outgoing) {
                1f - (-offset).toFloat() / halfWidth // -h→0, centre→1
            } else {
                1f - offset.toFloat() / halfWidth // centre→1, +h→0
            }.coerceIn(0f, 1f)

            if (zoom[i]) {
                val scale = (1f - progress).coerceAtLeast(0.001f)
                matrix.postScale(scale, scale)
            } else {
                val translateX = if (outgoing) -2f * progress else 2f * progress
                matrix.postTranslate(translateX, 0f)
            }
            break // windows don't overlap; at most one boundary is active.
        }
        return matrix
    }
}
