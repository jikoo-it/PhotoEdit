package com.momi.watermarker.domain.model

/**
 * A half-open time window [startMs, endMs) over a video, in milliseconds, with
 * an optional playback [speed].
 *
 * Used both for single-range trimming and for the list of segments to keep in a
 * cut-and-join edit. [speed] applies in cut & join (e.g. 2.0 = double speed,
 * 0.5 = half speed / slow motion); trimming leaves it at 1.0.
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val isValid: Boolean get() = startMs >= 0L && endMs > startMs
}
