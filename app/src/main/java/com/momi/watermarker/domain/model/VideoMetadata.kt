package com.momi.watermarker.domain.model

/**
 * Probed file metadata: encoded pixels plus the rotation a player would apply
 * so the picture is upright.
 */
data class VideoMetadata(
    val durationMs: Long,
    val encodedWidth: Int,
    val encodedHeight: Int,
    val rotationDegrees: Int,
)
