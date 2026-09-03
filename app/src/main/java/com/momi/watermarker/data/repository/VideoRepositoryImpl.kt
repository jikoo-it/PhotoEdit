package com.momi.watermarker.data.repository

import android.net.Uri
import com.momi.watermarker.data.storage.VideoStorage
import com.momi.watermarker.data.video.VideoTransformer
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [VideoRepository] backed by [VideoStorage] (metadata/MediaStore/FileProvider)
 * and [VideoTransformer] (Media3 export engine).
 */
class VideoRepositoryImpl @Inject constructor(
    private val videoStorage: VideoStorage,
    private val videoTransformer: VideoTransformer,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : VideoRepository {

    override suspend fun getDurationMs(clip: VideoClip): Outcome<Long> =
        withContext(dispatcher) {
            Outcome.catching { videoStorage.probeDurationMs(Uri.parse(clip.uri)) }
        }

    override suspend fun trim(
        source: VideoClip,
        startMs: Long,
        endMs: Long,
    ): Outcome<VideoClip> = Outcome.catching {
        // VideoTransformer manages its own (main-thread) execution, so this is
        // not wrapped in withContext(dispatcher).
        val outputFile = videoStorage.createOutputFile(prefix = "trim")
        videoTransformer.trim(Uri.parse(source.uri), outputFile.absolutePath, startMs, endMs)
        VideoClip(
            uri = videoStorage.fileProviderUri(outputFile).toString(),
            durationMs = endMs - startMs,
        )
    }

    override suspend fun saveToGallery(
        clip: VideoClip,
        displayName: String,
    ): Outcome<VideoClip> = withContext(dispatcher) {
        Outcome.catching {
            val saved = videoStorage.saveToGallery(Uri.parse(clip.uri), displayName)
            VideoClip(saved.toString())
        }
    }
}
