package com.momi.watermarker.domain.model

import androidx.annotation.StringRes
import com.momi.watermarker.R

/**
 * Where an overlay (image or text) is anchored within the video frame.
 *
 * [anchorX] / [anchorY] are normalized device coordinates in `-1f..1f`, matching
 * Media3's overlay anchoring: x runs left (-1) → right (+1), y runs bottom (-1)
 * → top (+1). The same value is used for both the overlay's own anchor point and
 * the background anchor, so a corner position sits flush inside that corner.
 */
enum class OverlayPosition(
    @StringRes val labelRes: Int,
    val anchorX: Float,
    val anchorY: Float,
) {
    TOP_LEFT(R.string.overlay_pos_top_left, -1f, 1f),
    TOP_CENTER(R.string.overlay_pos_top, 0f, 1f),
    TOP_RIGHT(R.string.overlay_pos_top_right, 1f, 1f),
    CENTER_LEFT(R.string.overlay_pos_left, -1f, 0f),
    CENTER(R.string.overlay_pos_center, 0f, 0f),
    CENTER_RIGHT(R.string.overlay_pos_right, 1f, 0f),
    BOTTOM_LEFT(R.string.overlay_pos_bottom_left, -1f, -1f),
    BOTTOM_CENTER(R.string.overlay_pos_bottom, 0f, -1f),
    BOTTOM_RIGHT(R.string.overlay_pos_bottom_right, 1f, -1f);

    companion object {
        val DEFAULT = CENTER
    }
}
