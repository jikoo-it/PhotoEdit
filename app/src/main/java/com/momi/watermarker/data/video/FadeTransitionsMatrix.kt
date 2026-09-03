package com.momi.watermarker.data.video

import androidx.media3.effect.RgbMatrix

/**
 * A time-varying [RgbMatrix] that dips the whole frame through black (or white)
 * around a set of composition timestamps — the basis for fade/flash transitions
 * between clips without needing the clips to overlap (Media3 1.5.1 has no native
 * transition API; see docs/video-editing.md §4b).
 *
 * Each boundary is a centre time and a half-width: a "dip" factor ramps from 0
 * up to 1 as the frame's presentation time approaches the centre, then back to
 * 0 — so the outgoing clip fades out and the incoming clip fades in across a
 * window of `2 * halfWidth`. Outside every window the matrix is identity,
 * leaving those frames untouched. Per-boundary [toWhite] chooses the dip colour.
 *
 * This effect is applied at the composition level, so the presentation times it
 * receives are composition-relative (matching the absolute [centersUs] computed
 * from the clip layout).
 */
class FadeTransitionsMatrix(
    private val centersUs: LongArray,
    private val halfWidthsUs: LongArray,
    private val toWhite: BooleanArray,
) : RgbMatrix {

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        var dip = 0f
        var white = false
        for (i in centersUs.indices) {
            val halfWidth = halfWidthsUs[i]
            if (halfWidth <= 0L) continue
            val distance = kotlin.math.abs(presentationTimeUs - centersUs[i])
            if (distance < halfWidth) {
                val local = 1f - distance.toFloat() / halfWidth
                if (local > dip) {
                    dip = local
                    white = toWhite[i]
                }
            }
        }
        if (dip <= 0f) return IDENTITY.copyOf()

        val scale = 1f - dip
        // Column-major 4x4 applied to (r, g, b, a). Scaling RGB by `scale`
        // darkens toward black. For a white dip, the alpha column adds `dip`
        // (frames are opaque, a≈1), pushing each channel toward 1.
        val add = if (white) dip else 0f
        return floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            add, add, add, 1f,
        )
    }

    private companion object {
        val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
