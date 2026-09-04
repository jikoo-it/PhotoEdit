package com.momi.watermarker.domain.repository

import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
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
     * Builds a slideshow video from still [images] (each an `isImage`
     * [VideoSegment] carrying its uri and on-screen duration), pre-rendering the
     * per-boundary [transitions] frame-by-frame before exporting. Unlike the
     * composition-wide [VideoTransition] effects used by [export], these are true
     * cross-dissolves (both images visible during the blend).
     *
     * @param transitions one entry per boundary (size = images - 1).
     * @param transitionDurationMs length of each non-[SlideTransition.NONE] blend.
     * @param aspectRatio output width/height; null uses the first image's ratio.
     */
    suspend fun createSlideshow(
        images: List<VideoSegment>,
        transitions: List<SlideTransition>,
        transitionDurationMs: Long,
        aspectRatio: Float?,
    ): Outcome<VideoClip>

    /**
     * Persists [clip] into the shared gallery (MediaStore Movies) under
     * [displayName], returning a reference to the saved entry.
     */
    suspend fun saveToGallery(clip: VideoClip, displayName: String): Outcome<VideoClip>
}
