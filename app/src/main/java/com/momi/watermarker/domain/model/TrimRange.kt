package com.momi.watermarker.domain.model

/**
 * A half-open time window [startMs, endMs) over a video, in milliseconds, with
 * an optional playback [speed].
 *
 * Used both for single-range trimming and for the list of segments to keep or
 * exclude in a cut-and-join edit. [speed] applies in cut & join (e.g. 2.0 =
 * double speed, 0.5 = half speed / slow motion); trimming leaves it at 1.0.
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long,
    val speed: Float = 1f,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val isValid: Boolean get() = startMs >= 0L && endMs > startMs

    companion object {
        /** The whole clip, used as the default "keep this" range. */
        fun covering(durationMs: Long): TrimRange = TrimRange(0L, durationMs.coerceAtLeast(0L))

        /**
         * A centered slice of [durationMs], used as a starting exclude window so
         * the user isn't asked to cut out the entire video by default.
         */
        fun centeredSlice(durationMs: Long, fraction: Float = 0.25f): TrimRange {
            if (durationMs <= 0L) return TrimRange(0L, 0L)
            val length = (durationMs * fraction.toDouble()).toLong()
                .coerceIn(1L, durationMs)
            val start = ((durationMs - length) / 2).coerceAtLeast(0L)
            return TrimRange(start, (start + length).coerceAtMost(durationMs))
        }
    }
}

/**
 * Inverse of these windows over `[0, durationMs)`: the gaps that remain after
 * the ranges are cut out. Overlapping and out-of-bounds windows are merged and
 * clipped first. An empty list means keep the whole duration.
 */
fun List<TrimRange>.complementWithin(durationMs: Long): List<TrimRange> {
    if (durationMs <= 0L) return emptyList()
    val merged = map {
        TrimRange(
            startMs = it.startMs.coerceAtLeast(0L),
            endMs = it.endMs.coerceAtMost(durationMs),
        )
    }.filter { it.isValid }
        .sortedBy { it.startMs }
        .fold(mutableListOf<TrimRange>()) { acc, range ->
            val last = acc.lastOrNull()
            if (last != null && range.startMs <= last.endMs) {
                acc[acc.lastIndex] = last.copy(endMs = maxOf(last.endMs, range.endMs))
            } else {
                acc += range
            }
            acc
        }

    val keep = ArrayList<TrimRange>(merged.size + 1)
    var cursor = 0L
    for (excluded in merged) {
        if (excluded.startMs > cursor) keep += TrimRange(cursor, excluded.startMs)
        if (excluded.endMs > cursor) cursor = excluded.endMs
    }
    if (cursor < durationMs) keep += TrimRange(cursor, durationMs)
    return keep
}
