package com.momi.watermarker.data.rendering

import android.graphics.Bitmap
import com.momi.watermarker.domain.model.NormalizedPoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Turns a binary mask into a simplified closed outline that the UI can show
 * and the user can drag before confirming a cut-out.
 */
object MaskContour {

    /**
     * 8-connected clockwise offsets, starting east:
     * E, SE, S, SW, W, NW, N, NE.
     */
    private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    fun fromBitmap(bitmap: Bitmap, alphaThreshold: Int = 128): List<NormalizedPoint> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return emptyList()
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val mask = BooleanArray(w * h) { ((pixels[it] ushr 24) and 0xFF) >= alphaThreshold }
        return fromMask(mask, w, h)
    }

    fun fromMask(mask: BooleanArray, width: Int, height: Int): List<NormalizedPoint> {
        require(mask.size == width * height)
        val contour = trace(mask, width, height)
        if (contour.size < 8) return emptyList()
        val normalized = contour.map { (x, y) ->
            NormalizedPoint(
                ((x + 0.5f) / width).coerceIn(0f, 1f),
                ((y + 0.5f) / height).coerceIn(0f, 1f),
            )
        }
        return simplify(normalized)
    }

    /**
     * Left-hand 8-connected boundary walk. Returns pixel coordinates of the
     * outer contour, without repeating the start point at the end.
     */
    fun trace(mask: BooleanArray, width: Int, height: Int): List<Pair<Int, Int>> {
        fun inside(x: Int, y: Int): Boolean =
            x in 0 until width && y in 0 until height && mask[y * width + x]

        var sx = -1
        var sy = -1
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (inside(x, y)) {
                    sx = x
                    sy = y
                    break@outer
                }
            }
        }
        if (sx < 0) return emptyList()

        val contour = ArrayList<Pair<Int, Int>>(256)
        var x = sx
        var y = sy
        // Came from the west of the first foreground pixel; look "left" first.
        var searchDir = 4
        val limit = width * height * 4
        var steps = 0
        do {
            contour.add(x to y)
            var found = false
            for (i in 0 until 8) {
                val dir = (searchDir + i) % 8
                val nx = x + DX[dir]
                val ny = y + DY[dir]
                if (inside(nx, ny)) {
                    searchDir = (dir + 6) % 8
                    x = nx
                    y = ny
                    found = true
                    break
                }
            }
            if (!found) break
            steps++
        } while (!(x == sx && y == sy) && steps < limit)

        return contour
    }

    fun simplify(
        points: List<NormalizedPoint>,
        epsilon: Float = 0.006f,
        maxPoints: Int = 48,
    ): List<NormalizedPoint> {
        if (points.size <= 8) return points
        var eps = epsilon
        var result = rdp(points, eps)
        while (result.size > maxPoints && eps < 0.05f) {
            eps *= 1.4f
            result = rdp(points, eps)
        }
        return if (result.size >= 3) result else points.take(8)
    }

    private fun rdp(points: List<NormalizedPoint>, epsilon: Float): List<NormalizedPoint> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        val end = points.lastIndex
        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points.first(), points[end])
            if (d > maxDist) {
                index = i
                maxDist = d
            }
        }
        return if (maxDist > epsilon) {
            val left = rdp(points.subList(0, index + 1), epsilon)
            val right = rdp(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points.first(), points.last())
        }
    }

    private fun perpendicularDistance(
        p: NormalizedPoint,
        a: NormalizedPoint,
        b: NormalizedPoint,
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)
        if (length < 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / length
    }
}
