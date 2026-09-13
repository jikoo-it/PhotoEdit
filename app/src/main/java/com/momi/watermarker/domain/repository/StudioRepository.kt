package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.FlattenedImage
import com.momi.watermarker.domain.model.LayerDocument
import com.momi.watermarker.domain.util.Outcome

/**
 * Layer-document I/O for Studio: extract people as an alpha PNG, and flatten a
 * [LayerDocument] to a cached image.
 *
 * Subject (salient-object) extraction stays on [ImageCutoutRepository].
 */
interface StudioRepository {

    /**
     * Isolates detected people from [sourceUri] into a transparent PNG, working
     * at a resolution capped to [maxLongEdge].
     */
    suspend fun extractPeople(sourceUri: String, maxLongEdge: Int): Outcome<String>

    /**
     * Composites [document] bottom-to-top at a working size whose long edge is
     * at most [maxLongEdge], and returns the cached result.
     */
    suspend fun flatten(document: LayerDocument, maxLongEdge: Int): Outcome<FlattenedImage>
}
