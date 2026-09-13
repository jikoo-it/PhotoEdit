package com.momi.watermarker.presentation.video

/**
 * Formats a millisecond offset as `m:ss.SSS`, or `h:mm:ss.SSS` when the value
 * is an hour or longer. Used by trim range labels and the video preview clock.
 */
fun formatTimecode(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val hours = total / 3_600_000L
    val minutes = (total % 3_600_000L) / 60_000L
    val seconds = (total % 60_000L) / 1_000L
    val millis = total % 1_000L
    return if (hours > 0L) {
        "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    } else {
        "%d:%02d.%03d".format(minutes, seconds, millis)
    }
}
