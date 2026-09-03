package com.momi.watermarker.data.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine wrapper around Media3 [Transformer].
 *
 * Media3 requires the transformer to be built, started, and cancelled on a
 * thread that owns a [Looper]; its listener callbacks arrive on that same
 * thread. We therefore drive everything on the main thread and bridge the
 * async listener into a suspending call, so callers (repositories) stay on
 * plain coroutines and never see the threading constraint.
 *
 * Every editing operation is expressed as one [ExportSpec] — an ordered list of
 * [Clip]s concatenated into a single [Composition], with optional output-wide
 * transforms (aspect ratio, overlay, audio) applied on top.
 */
@Singleton
class VideoTransformer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** One clip in an export sequence. See [com.momi.watermarker.domain.model.VideoSegment]. */
    data class Clip(
        val uri: Uri,
        val startMs: Long? = null,
        val endMs: Long? = null,
        val isImage: Boolean = false,
        val imageDurationMs: Long = 3_000L,
    )

    /** A full export description: clips to concatenate plus output-wide options. */
    data class ExportSpec(
        val clips: List<Clip>,
        val removeAudio: Boolean = false,
        val aspectRatio: Float? = null,
        val overlay: Bitmap? = null,
        val overlayAlpha: Float = 1f,
        val forceAudioTrack: Boolean = false,
    )

    /**
     * Runs [spec] and writes the result to [outputPath]. Suspends until export
     * completes; throws [ExportException] on failure. Cancelling the coroutine
     * cancels the in-flight export.
     */
    suspend fun export(spec: ExportSpec, outputPath: String) {
        require(spec.clips.isNotEmpty()) { "Export requires at least one clip." }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                    })
                    .build()

                // Output-wide video effects, applied to every clip so merged
                // sources are normalized to a consistent frame.
                val videoEffects = mutableListOf<Effect>()
                spec.aspectRatio?.let { ratio ->
                    videoEffects.add(
                        Presentation.createForAspectRatio(
                            ratio,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                        ),
                    )
                }
                spec.overlay?.let { bitmap ->
                    val settings = OverlaySettings.Builder()
                        .setAlphaScale(spec.overlayAlpha)
                        .build()
                    val overlay: TextureOverlay =
                        BitmapOverlay.createStaticBitmapOverlay(bitmap, settings)
                    videoEffects.add(OverlayEffect(listOf(overlay)))
                }
                val effects =
                    if (videoEffects.isEmpty()) Effects.EMPTY
                    else Effects(emptyList(), videoEffects)

                val items = spec.clips.map { clip ->
                    val mediaItemBuilder = MediaItem.Builder().setUri(clip.uri)
                    if (!clip.isImage && (clip.startMs != null || clip.endMs != null)) {
                        val clipping = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs ?: 0L)
                        if (clip.endMs != null) clipping.setEndPositionMs(clip.endMs)
                        mediaItemBuilder.setClippingConfiguration(clipping.build())
                    }

                    val itemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
                    if (spec.removeAudio) itemBuilder.setRemoveAudio(true)
                    if (clip.isImage) {
                        itemBuilder
                            .setDurationUs(clip.imageDurationMs * 1_000L)
                            .setFrameRate(IMAGE_FRAME_RATE)
                    }
                    if (videoEffects.isNotEmpty()) itemBuilder.setEffects(effects)
                    itemBuilder.build()
                }

                val sequence = EditedMediaItemSequence(items)
                val compositionBuilder = Composition.Builder(sequence)
                if (spec.forceAudioTrack) {
                    compositionBuilder.experimentalSetForceAudioTrack(true)
                }
                val composition = compositionBuilder.build()

                // cancel() must run on the transformer's (main) thread, but
                // invokeOnCancellation may fire from any thread — so hop to main.
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }

                transformer.start(composition, outputPath)
            }
        }
    }

    private companion object {
        const val IMAGE_FRAME_RATE = 30
    }
}
