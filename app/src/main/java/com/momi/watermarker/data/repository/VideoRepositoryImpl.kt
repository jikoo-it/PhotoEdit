package com.momi.watermarker.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.momi.watermarker.data.rendering.GeometryProcessor
import com.momi.watermarker.data.storage.VideoStorage
import com.momi.watermarker.data.video.SlideshowComposer
import com.momi.watermarker.data.video.VideoTransformer
import com.momi.watermarker.di.IoDispatcher
import com.momi.watermarker.domain.model.ImageOp
import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.VideoClip
import com.momi.watermarker.domain.model.VideoColorFilter
import com.momi.watermarker.domain.model.VideoEditRequest
import com.momi.watermarker.domain.model.VideoSegment
import com.momi.watermarker.domain.model.VideoTransition
import com.momi.watermarker.domain.repository.VideoRepository
import com.momi.watermarker.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * [VideoRepository] backed by [VideoStorage] (metadata/MediaStore/FileProvider)
 * and [VideoTransformer] (Media3 export engine).
 */
class VideoRepositoryImpl @Inject constructor(
    private val videoStorage: VideoStorage,
    private val videoTransformer: VideoTransformer,
    private val geometryProcessor: GeometryProcessor,
    private val slideshowComposer: SlideshowComposer,
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
            // building the overlay bitmap — is cheap and one-shot.
            val overlay = buildOverlayBitmap(request)
            val spec = VideoTransformer.ExportSpec(
                clips = request.segments.map { segment ->
                    VideoTransformer.Clip(
                        uri = Uri.parse(segment.uri),
                        startMs = segment.startMs,
                        endMs = segment.endMs,
                        isImage = segment.isImage,
                        imageDurationMs = segment.imageDurationMs,
                        speed = segment.speed,
                        aspectRatio = segment.aspectRatio,
                    )
                },
                removeAudio = request.removeAudio,
                aspectRatio = request.aspectRatio,
                overlay = overlay,
                overlayAlpha = request.overlayAlpha,
                overlayAnchorX = request.overlayPosition.anchorX,
                overlayAnchorY = request.overlayPosition.anchorY,
                forceAudioTrack = request.forceAudioTrack,
                transitions = request.transitions.map { it.toKind() },
                transitionDurationMs = request.transitionDurationMs,
                colorFilter = request.colorFilter.toKind(),
            )
            val outputFile = videoStorage.createOutputFile(prefix = "edit")
            videoTransformer.export(spec, outputFile.absolutePath)
            VideoClip(uri = videoStorage.fileProviderUri(outputFile).toString())
        }

    /**
     * Produces the overlay bitmap for [request], pre-sized to the video frame so
     * the transformer can stamp it 1:1. Text takes precedence over an image; a
     * cropped image overlay reuses the photo pipeline's cropper. Returns null
     * when no overlay is requested.
     */
    private fun buildOverlayBitmap(request: VideoEditRequest): Bitmap? {
        val text = request.overlayText
        val imageUri = request.overlayImageUri
        if (text == null && imageUri == null) return null

        // Frame size drives overlay sizing (text by height, image by width).
        val frame = videoStorage.probeDisplaySize(Uri.parse(request.segments.first().uri))

        if (text != null) {
            val heightPx = (request.overlaySizeFraction * frame.height).roundToInt().coerceAtLeast(1)
            return videoStorage.renderTextBitmap(text, request.overlayTextColorArgb, heightPx)
        }

        var bitmap = videoStorage.decodeBitmap(Uri.parse(imageUri!!))

        // Optional crop (may introduce transparency via a shaped mask).
        request.overlayCropRect?.let { rect ->
            val cropped = geometryProcessor.crop(
                bitmap,
                ImageOp.Crop(rect = rect, shape = request.overlayCropShape),
            )
            if (cropped !== bitmap) {
                bitmap.recycle()
                bitmap = cropped
            }
        }

        // Scale so the overlay width is the requested fraction of the frame width.
        val targetW = (request.overlaySizeFraction * frame.width).roundToInt().coerceAtLeast(1)
        if (bitmap.width != targetW) {
            val scale = targetW.toFloat() / bitmap.width
            val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, /* filter = */ true)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        return bitmap
    }

    private fun VideoTransition.toKind(): VideoTransformer.TransitionKind = when (this) {
        VideoTransition.NONE -> VideoTransformer.TransitionKind.NONE
        VideoTransition.FADE -> VideoTransformer.TransitionKind.FADE
        VideoTransition.FLASH -> VideoTransformer.TransitionKind.FLASH
        VideoTransition.SLIDE -> VideoTransformer.TransitionKind.SLIDE
        VideoTransition.ZOOM -> VideoTransformer.TransitionKind.ZOOM
    }

    private fun VideoColorFilter.toKind(): VideoTransformer.ColorFilterKind = when (this) {
        VideoColorFilter.NONE -> VideoTransformer.ColorFilterKind.NONE
        VideoColorFilter.GRAYSCALE -> VideoTransformer.ColorFilterKind.GRAYSCALE
        VideoColorFilter.INVERT -> VideoTransformer.ColorFilterKind.INVERT
        VideoColorFilter.WARM -> VideoTransformer.ColorFilterKind.WARM
        VideoColorFilter.COOL -> VideoTransformer.ColorFilterKind.COOL
        VideoColorFilter.BRIGHT -> VideoTransformer.ColorFilterKind.BRIGHT
        VideoColorFilter.DARK -> VideoTransformer.ColorFilterKind.DARK
        VideoColorFilter.HIGH_CONTRAST -> VideoTransformer.ColorFilterKind.HIGH_CONTRAST
    }

    override suspend fun createSlideshow(
        images: List<VideoSegment>,
        transitions: List<SlideTransition>,
        transitionDurationMs: Long,
        aspectRatio: Float?,
    ): Outcome<VideoClip> {
        // Pre-render every transition frame (CPU/IO-heavy) off the main thread…
        val baked = try {
            withContext(dispatcher) {
                val frames = images.map { SlideshowComposer.Frame(it.uri, it.imageDurationMs) }
                slideshowComposer.compose(frames, transitions, transitionDurationMs, aspectRatio)
            }
        } catch (t: Throwable) {
            return Outcome.Failure(t)
        }
        // …then feed the baked stills through the ordinary export pipeline; they
        // are already exact-canvas images, so no aspect/transition transforms.
        return export(VideoEditRequest(segments = baked))
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
