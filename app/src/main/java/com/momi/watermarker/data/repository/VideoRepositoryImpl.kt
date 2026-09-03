package com.momi.watermarker.data.repository

import android.net.Uri
import com.momi.watermarker.data.storage.VideoStorage
import com.momi.watermarker.data.video.VideoTransformer
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoEditRequest
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

    override suspend fun export(request: VideoEditRequest): Outcome<VideoClip> =
        Outcome.catching {
            // VideoTransformer manages its own (main-thread) execution, so this
            // is not wrapped in withContext(dispatcher). The one blocking bit —
            // decoding the overlay bitmap — is cheap and one-shot.
            val overlay = request.overlayImageUri?.let {
                videoStorage.decodeBitmap(Uri.parse(it))
            }
            val spec = VideoTransformer.ExportSpec(
                clips = request.segments.map { segment ->
                    VideoTransformer.Clip(
                        uri = Uri.parse(segment.uri),
                        startMs = segment.startMs,
                        endMs = segment.endMs,
                        isImage = segment.isImage,
                        imageDurationMs = segment.imageDurationMs,
                    )
                },
                removeAudio = request.removeAudio,
                aspectRatio = request.aspectRatio,
                overlay = overlay,
                overlayAlpha = request.overlayAlpha,
                forceAudioTrack = request.forceAudioTrack,
            )
            val outputFile = videoStorage.createOutputFile(prefix = "edit")
            videoTransformer.export(spec, outputFile.absolutePath)
            VideoClip(uri = videoStorage.fileProviderUri(outputFile).toString())
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
