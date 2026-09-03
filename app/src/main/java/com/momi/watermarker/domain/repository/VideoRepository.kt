package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.util.Outcome

/**
 * Video acquisition, transformation, and persistence.
 *
 * As with [MediaRepository], picking a video is a UI concern (Activity Result
 * APIs), so it lives in the presentation layer; this repository owns metadata
 * probing, transformation (backed by Media3 Transformer), and gallery export.
 *
 * All editing operations funnel through [export]: the use-case layer builds a
 * [VideoEditRequest] describing the operation, and this repository runs it.
 */
interface VideoRepository {

    /** Reads the duration (ms) of the video at [clip]. */
    suspend fun getDurationMs(clip: VideoClip): Outcome<Long>

    /**
     * Runs [request] through the Media3 pipeline and writes the result to the
     * app cache, returning a reference (FileProvider URI) to the exported clip.
     */
    suspend fun export(request: VideoEditRequest): Outcome<VideoClip>

    /**
     * Persists [clip] into the shared gallery (MediaStore Movies) under
     * [displayName], returning a reference to the saved entry.
     */
    suspend fun saveToGallery(clip: VideoClip, displayName: String): Outcome<VideoClip>
}
