package com.momi.watermarker.domain.model

/**
 * A half-open time window [startMs, endMs) over a video, in milliseconds.
 *
 * Used both for single-range trimming and for the list of segments to keep in a
 * cut-and-join edit.
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val isValid: Boolean get() = startMs >= 0L && endMs > startMs
}
