package com.momi.watermarker.domain.model

/**
 * Where an overlay (image or text) is anchored within the video frame.
 *
 * [anchorX] / [anchorY] are normalized device coordinates in `-1f..1f`, matching
 * Media3's overlay anchoring: x runs left (-1) → right (+1), y runs bottom (-1)
 * → top (+1). The same value is used for both the overlay's own anchor point and
 * the background anchor, so a corner position sits flush inside that corner.
 */
enum class OverlayPosition(
    val label: String,
    val anchorX: Float,
    val anchorY: Float,
) {
    TOP_LEFT("Top left", -1f, 1f),
    TOP_CENTER("Top", 0f, 1f),
    TOP_RIGHT("Top right", 1f, 1f),
    CENTER_LEFT("Left", -1f, 0f),
    CENTER("Center", 0f, 0f),
    CENTER_RIGHT("Right", 1f, 0f),
    BOTTOM_LEFT("Bottom left", -1f, -1f),
    BOTTOM_CENTER("Bottom", 0f, -1f),
    BOTTOM_RIGHT("Bottom right", 1f, -1f);

    companion object {
        val DEFAULT = CENTER
    }
}
