package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.util.Outcome

/**
 * Video acquisition, transformation, and persistence.
 *
 * As with [MediaRepository], picking a video is a UI concern (Activity Result
 * APIs), so it lives in the presentation layer; this repository owns metadata
 * probing, transformation (backed by Media3 Transformer), and gallery export.
 *
 * Spike scope (Phase 0): duration probe, trim, and save. Crop/merge/overlay are
 * added in later phases behind this same interface.
 */
interface VideoRepository {

    /** Reads the duration (ms) of the video at [clip]. */
    suspend fun getDurationMs(clip: VideoClip): Outcome<Long>

    /**
     * Trims [source] to the window [startMs, endMs] and writes the result to the
     * app cache, returning a reference (FileProvider URI) to the trimmed clip.
     */
    suspend fun trim(source: VideoClip, startMs: Long, endMs: Long): Outcome<VideoClip>

    /**
     * Persists [clip] into the shared gallery (MediaStore Movies) under
     * [displayName], returning a reference to the saved entry.
     */
    suspend fun saveToGallery(clip: VideoClip, displayName: String): Outcome<VideoClip>
}
