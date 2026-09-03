package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.ExportFormat
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.WatermarkImage
import com.momi.watermarker.domain.util.Outcome

/**
 * Handles image acquisition (camera capture destinations) and persistence
 * (saving finished images to the device gallery).
 *
 * Picking from the gallery and launching the camera are UI concerns driven by
 * the Android Activity Result APIs, so they live in the presentation layer;
 * this repository owns everything that touches storage.
 */
interface MediaRepository {

    /**
     * Creates a fresh destination the camera can write a full-resolution photo
     * into, returning a reference to that (initially empty) location.
     */
    suspend fun createCaptureDestination(): Outcome<WatermarkImage>

    /**
     * Persists [image] into the shared gallery (MediaStore Pictures) under
     * [displayName], encoded as [format], returning a reference to the saved
     * entry. [image] is expected to already be encoded in [format].
     */
    suspend fun saveToGallery(
        image: WatermarkImage,
        displayName: String,
        format: ExportFormat,
    ): Outcome<WatermarkImage>

    /**
     * Crops [source] to [rect] (fractions of the full image), masks it to
     * [shape], and returns a reference to the cropped result. Used to prepare an
     * image watermark.
     */
    suspend fun cropImage(
        source: WatermarkImage,
        rect: NormalizedRect,
        shape: CropShape,
    ): Outcome<WatermarkImage>
}
