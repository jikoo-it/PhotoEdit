package com.momi.watermarker.data.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
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
        /** Playback speed multiplier (1f = original). Ignored for image clips. */
        val speed: Float = 1f,
        /**
         * Per-clip reframe (width / height); null falls back to the output-wide
         * [ExportSpec.aspectRatio]. Lets merged sources each be framed differently.
         */
        val aspectRatio: Float? = null,
    )

    /** The animation played at a boundary between two clips. */
    enum class TransitionKind { NONE, FADE, FLASH, SLIDE, ZOOM }

    /** A preset color look applied across the whole output. */
    enum class ColorFilterKind { NONE, GRAYSCALE, INVERT, WARM, COOL, BRIGHT, DARK, HIGH_CONTRAST }

    /** A full export description: clips to concatenate plus output-wide options. */
    data class ExportSpec(
        val clips: List<Clip>,
        val removeAudio: Boolean = false,
        val aspectRatio: Float? = null,
        val overlay: Bitmap? = null,
        val overlayAlpha: Float = 1f,
        /**
         * Overlay anchor in normalized device coordinates (`-1f..1f`, y up). The
         * overlay bitmap is pre-sized by the caller, so the same anchor is used
         * for both the overlay and the background frame — a corner anchor sits
         * flush in that corner.
         */
        val overlayAnchorX: Float = 0f,
        val overlayAnchorY: Float = 0f,
        val forceAudioTrack: Boolean = false,
        /**
         * Transition at each internal boundary. When non-empty this has
         * [clips].size - 1 entries; `transitions[i]` is the boundary between
         * clip i and clip i + 1.
         */
        val transitions: List<TransitionKind> = emptyList(),
        /** Duration of each non-[TransitionKind.NONE] transition, in milliseconds. */
        val transitionDurationMs: Long = 0L,
        /** Preset color look applied to every clip; [ColorFilterKind.NONE] = untouched. */
        val colorFilter: ColorFilterKind = ColorFilterKind.NONE,
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
                // sources share one look. Order matters: colour grade first,
                // then the (ungraded-by-transitions) overlay, then transitions —
                // whose colour dips darken everything drawn before them.
                val sharedEffects = buildList {
                    colorFilterEffect(spec.colorFilter)?.let(::add)
                    spec.overlay?.let { bitmap ->
                        val settings = OverlaySettings.Builder()
                            .setAlphaScale(spec.overlayAlpha)
                            .setOverlayFrameAnchor(spec.overlayAnchorX, spec.overlayAnchorY)
                            .setBackgroundFrameAnchor(spec.overlayAnchorX, spec.overlayAnchorY)
                            .build()
                        val overlay: TextureOverlay =
                            BitmapOverlay.createStaticBitmapOverlay(bitmap, settings)
                        add(OverlayEffect(listOf(overlay)))
                    }
                    // Transitions are composition-wide, time-varying effects; their
                    // boundary times are absolute on the composition timeline.
                    addAll(buildTransitionEffects(spec))
                }

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

                    // Per-clip effects: speed (audio + video kept in sync) and a
                    // reframe (per-clip aspect, else the output-wide one), applied
                    // before the shared look.
                    val audioProcessors = mutableListOf<AudioProcessor>()
                    val videoEffects = mutableListOf<Effect>()
                    if (!clip.isImage && clip.speed > 0f && clip.speed != 1f) {
                        val speedPair = Effects.createExperimentalSpeedChangingEffect(
                            ConstantSpeedProvider(clip.speed),
                        )
                        audioProcessors.add(speedPair.first)
                        videoEffects.add(speedPair.second)
                    }
                    (clip.aspectRatio ?: spec.aspectRatio)?.let { ratio ->
                        videoEffects.add(
                            Presentation.createForAspectRatio(
                                ratio,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                            ),
                        )
                    }
                    videoEffects.addAll(sharedEffects)

                    if (audioProcessors.isNotEmpty() || videoEffects.isNotEmpty()) {
                        itemBuilder.setEffects(Effects(audioProcessors, videoEffects))
                    }
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

    /**
     * Builds the composition-wide transition effects for [spec] — at most one
     * colour effect (fade/flash) and one geometric effect (slide/zoom). Walks
     * the clips accumulating each one's duration to find the absolute timestamp
     * of every boundary; each half-width is clamped to the shorter neighbouring
     * clip so a short image can't be transitioned end-to-end.
     */
    private fun buildTransitionEffects(spec: ExportSpec): List<Effect> {
        val transitionUs = spec.transitionDurationMs * 1_000L
        if (transitionUs <= 0L || spec.transitions.all { it == TransitionKind.NONE }) {
            return emptyList()
        }

        val durationsUs = spec.clips.map(::clipDurationUs)
        // Colour dips (fade/flash).
        val colorCenters = ArrayList<Long>()
        val colorHalves = ArrayList<Long>()
        val colorWhite = ArrayList<Boolean>()
        // Geometric moves (slide/zoom).
        val geoCenters = ArrayList<Long>()
        val geoHalves = ArrayList<Long>()
        val geoZoom = ArrayList<Boolean>()

        var boundaryUs = 0L
        for (i in 0 until spec.clips.size - 1) {
            boundaryUs += durationsUs[i]
            val kind = spec.transitions.getOrElse(i) { TransitionKind.NONE }
            if (kind == TransitionKind.NONE) continue
            if (durationsUs[i] <= 0L || durationsUs[i + 1] <= 0L) continue
            val half = minOf(transitionUs, durationsUs[i], durationsUs[i + 1])
            when (kind) {
                TransitionKind.FADE, TransitionKind.FLASH -> {
                    colorCenters.add(boundaryUs)
                    colorHalves.add(half)
                    colorWhite.add(kind == TransitionKind.FLASH)
                }
                TransitionKind.SLIDE, TransitionKind.ZOOM -> {
                    geoCenters.add(boundaryUs)
                    geoHalves.add(half)
                    geoZoom.add(kind == TransitionKind.ZOOM)
                }
                TransitionKind.NONE -> Unit
            }
        }

        val effects = mutableListOf<Effect>()
        if (geoCenters.isNotEmpty()) {
            effects.add(
                GeometricTransitionsMatrix(
                    geoCenters.toLongArray(),
                    geoHalves.toLongArray(),
                    geoZoom.toBooleanArray(),
                ),
            )
        }
        if (colorCenters.isNotEmpty()) {
            effects.add(
                FadeTransitionsMatrix(
                    colorCenters.toLongArray(),
                    colorHalves.toLongArray(),
                    colorWhite.toBooleanArray(),
                ),
            )
        }
        return effects
    }

    /** Maps a [ColorFilterKind] to a Media3 color [Effect], or null for NONE. */
    private fun colorFilterEffect(kind: ColorFilterKind): Effect? = when (kind) {
        ColorFilterKind.NONE -> null
        ColorFilterKind.GRAYSCALE -> RgbFilter.createGrayscaleFilter()
        ColorFilterKind.INVERT -> RgbFilter.createInvertedFilter()
        ColorFilterKind.WARM ->
            RgbAdjustment.Builder().setRedScale(1.15f).setBlueScale(0.85f).build()
        ColorFilterKind.COOL ->
            RgbAdjustment.Builder().setRedScale(0.85f).setBlueScale(1.15f).build()
        ColorFilterKind.BRIGHT -> Brightness(0.15f)
        ColorFilterKind.DARK -> Brightness(-0.2f)
        ColorFilterKind.HIGH_CONTRAST -> Contrast(0.3f)
    }

    /** A [SpeedProvider] that reports one constant [speed] for the whole clip. */
    private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    /** This clip's on-screen duration in microseconds, or 0 if unknown. */
    private fun clipDurationUs(clip: Clip): Long = when {
        clip.isImage -> clip.imageDurationMs * 1_000L
        clip.startMs != null || clip.endMs != null -> {
            val sourceUs = (((clip.endMs ?: 0L) - (clip.startMs ?: 0L)).coerceAtLeast(0L)) * 1_000L
            if (clip.speed > 0f) (sourceUs / clip.speed).toLong() else sourceUs
        }
        else -> 0L
    }

    private companion object {
        const val IMAGE_FRAME_RATE = 30
    }
}
