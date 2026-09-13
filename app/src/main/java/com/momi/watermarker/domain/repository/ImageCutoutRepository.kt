package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.CutoutRenderSpec
import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.util.Outcome

/**
 * On-device subject cut-out and background compositing for a single image.
 *
 * Segmentation (the expensive ML step) is separated from compositing so the
 * subject is extracted once and the background can be re-rendered cheaply as
 * the user tweaks it.
 */
interface ImageCutoutRepository {

    /**
     * Runs on-device subject segmentation on the image at [sourceUri] and
     * returns the URI of a transparent PNG containing just the subject (the
     * background made fully transparent).
     */
    suspend fun cutoutSubject(sourceUri: String): Outcome<String>

    /**
     * Detects the salient subject and returns a closed outline in normalized
     * image coordinates. Nothing is written; the caller previews and may edit
     * the outline before [cutoutPath].
     */
    suspend fun proposeSubjectOutline(sourceUri: String): Outcome<List<NormalizedPoint>>

    /**
     * Keeps the pixels inside the closed [outline] (normalized 0f..1f image
     * coordinates) and makes everything else transparent. No ML is used.
     */
    suspend fun cutoutPath(
        sourceUri: String,
        outline: List<NormalizedPoint>,
    ): Outcome<String>

    /**
     * Composites the already-extracted subject over the background described by
     * [spec] and returns the URI of the rendered image (PNG when the background
     * is transparent, otherwise JPEG).
     */
    suspend fun renderResult(spec: CutoutRenderSpec): Outcome<String>
}
