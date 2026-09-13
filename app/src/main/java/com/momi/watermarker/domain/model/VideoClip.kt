package com.momi.watermarker.domain.model

/**
 * A platform-neutral reference to a video.
 *
 * Mirrors [WatermarkImage]: the domain layer only passes around the string form
 * of a content/file URI plus optional metadata. The data layer turns this into
 * concrete Android types ([android.net.Uri], MediaCodec, etc.).
 */
data class VideoClip(
    val uri: String,
    /** Duration in milliseconds, when known (null until probed). */
    val durationMs: Long? = null,
    /** Encoded frame width before display rotation, when known. */
    val encodedWidth: Int? = null,
    /** Encoded frame height before display rotation, when known. */
    val encodedHeight: Int? = null,
    /**
     * Rotation already stored in the file (what a player applies automatically).
     */
    val metadataRotationDegrees: Int = 0,
    /**
     * Rotation applied when exporting (merge). Starts as [metadataRotationDegrees]
     * so the clip is upright; the user can change it before Apply.
     */
    val rotationDegrees: Int = 0,
) {
    /** Extra rotation to apply on top of player-applied metadata, for live preview. */
    val previewRotationDelta: Int
        get() = normalizeRotationDegrees(rotationDegrees - metadataRotationDegrees)

    /**
     * Displayed width/height after [rotationDegrees]. Null if size was never probed.
     */
    fun displayAspectRatioOrNull(): Float? {
        val width = encodedWidth ?: return null
        val height = encodedHeight ?: return null
        if (width <= 0 || height <= 0) return null
        val quarterTurn = rotationDegrees % 180 != 0
        val displayWidth = if (quarterTurn) height else width
        val displayHeight = if (quarterTurn) width else height
        return displayWidth.toFloat() / displayHeight.toFloat()
    }
}

/** Snaps any integer angle onto `0 / 90 / 180 / 270`. */
fun normalizeRotationDegrees(degrees: Int): Int {
    var d = degrees % 360
    if (d < 0) d += 360
    return d
}
