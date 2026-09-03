package com.momi.watermarker.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The outline a watermark crop is masked to. Everything outside the shape is
 * made transparent, so a logo can be round, rounded, etc. The crop rectangle
 * remains the bounding box; the shape is inscribed within it.
 */
enum class CropShape(val displayName: String) {
    RECTANGLE("Rectangle"),
    CIRCLE("Circle"),
    ROUNDED("Rounded"),
    SQUIRCLE("Squircle");

    companion object {
        val DEFAULT = RECTANGLE

        /** Corner radius as a fraction of the shorter side, for [ROUNDED]. */
        const val ROUNDED_CORNER_FRACTION = 0.18f
    }
}

/**
 * Samples a superellipse (squircle) outline as points in the unit square
 * (0f..1f), so the on-screen preview and the bitmap mask draw an identical
 * shape. Higher [exponent] approaches a rectangle; ~4 gives the classic
 * squircle.
 */
fun squircleUnitPoints(steps: Int = 96, exponent: Float = 4f): List<Pair<Float, Float>> {
    val power = (2f / exponent).toDouble()
    return (0 until steps).map { i ->
        val t = 2.0 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = sign(c) * abs(c).pow(power)
        val y = sign(s) * abs(s).pow(power)
        // Map from [-1, 1] to [0, 1].
        (((x + 1.0) / 2.0).toFloat()) to (((y + 1.0) / 2.0).toFloat())
    }
}
