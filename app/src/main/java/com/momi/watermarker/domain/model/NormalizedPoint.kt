package com.momi.watermarker.domain.model

/**
 * A point on an image expressed as fractions (0f..1f) of width and height, so a
 * hand-drawn outline stays resolution-independent.
 */
data class NormalizedPoint(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "NormalizedPoint must be within 0f..1f" }
    }
}

/**
 * True when [this] is a closed-enough freehand outline to cut out: enough
 * vertices and a bounding box that covers a real region of the photo.
 */
fun List<NormalizedPoint>.isUsableSelection(): Boolean {
    if (size < MIN_SELECTION_POINTS) return false
    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    return (maxX - minX) * (maxY - minY) >= MIN_SELECTION_AREA
}

/** Even-odd fill test so the preview can tell taps inside the outline. */
fun List<NormalizedPoint>.containsPoint(px: Float, py: Float): Boolean {
    if (size < 3) return false
    var inside = false
    var j = lastIndex
    for (i in indices) {
        val yi = this[i].y
        val yj = this[j].y
        val xi = this[i].x
        val xj = this[j].x
        val intersect = ((yi > py) != (yj > py)) &&
            (px < (xj - xi) * (py - yi) / ((yj - yi).let { if (it == 0f) 1e-6f else it }) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

/** Shifts every point by [dx], [dy], clamped so the whole outline stays on the image. */
fun List<NormalizedPoint>.translated(dx: Float, dy: Float): List<NormalizedPoint> {
    if (isEmpty()) return this
    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    val cx = dx.coerceIn(-minX, 1f - maxX)
    val cy = dy.coerceIn(-minY, 1f - maxY)
    if (cx == 0f && cy == 0f) return this
    return map { NormalizedPoint(it.x + cx, it.y + cy) }
}

private const val MIN_SELECTION_POINTS = 8
private const val MIN_SELECTION_AREA = 0.004f
