package com.momi.watermarker.data.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.momi.watermarker.data.storage.VideoStorage
import com.momi.watermarker.domain.model.SlideTransition
import com.momi.watermarker.domain.model.VideoSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a list of still images into a self-contained slideshow timeline whose
 * transitions are **pre-rendered** rather than approximated by a video effect.
 *
 * Because every neighbour is a static image, each transition frame is fully
 * determined: the composer cover-fits both images to one output canvas, asks
 * [TransitionRenderer] to draw each in-between frame, writes it as a short image
 * clip, and emits the whole thing as an ordinary list of image [VideoSegment]s.
 * The transformer then just concatenates equally-sized image clips — no
 * `Presentation`, no composition-wide transition effect, and a genuine
 * cross-dissolve where both images are visible at once.
 *
 * Each internal boundary contributes `D/2` from the end of the outgoing image
 * and `D/2` from the start of the incoming one; `D` is clamped so neither
 * neighbour is consumed entirely.
 */
@Singleton
class SlideshowComposer @Inject constructor(
    private val videoStorage: VideoStorage,
    private val transitionRenderer: TransitionRenderer,
) {

    /** One source image and how long it should remain fully on screen. */
    data class Frame(val uri: String, val durationMs: Long)

    /**
     * Builds the baked image-segment timeline. [transitions] has one entry per
     * boundary (size = frames - 1). [aspectRatio] (width/height) sets the output
     * canvas; null uses the first image's own ratio.
     */
    fun compose(
        frames: List<Frame>,
        transitions: List<SlideTransition>,
        transitionDurationMs: Long,
        aspectRatio: Float?,
    ): List<VideoSegment> {
        require(frames.size >= 2) { "A slideshow needs at least two images." }
        videoStorage.clearSlideshowFrames()

        // Decode all sources, then cover-fit each to a shared output canvas.
        val raw = frames.map { videoStorage.decodeBitmap(Uri.parse(it.uri)) }
        val ratio = aspectRatio ?: (raw.first().width.toFloat() / raw.first().height.toFloat())
        val (canvasW, canvasH) = canvasSize(ratio)
        val canvasBitmaps = raw.map { coverFit(it, canvasW, canvasH) }
        raw.forEach { it.recycle() }

        // Effective transition length per boundary: never more than half of
        // either neighbour, so a short image can't be over-consumed.
        val boundaries = frames.size - 1
        val effective = LongArray(boundaries) { b ->
            val kind = transitions.getOrElse(b) { SlideTransition.NONE }
            if (kind == SlideTransition.NONE) 0L
            else minOf(transitionDurationMs, frames[b].durationMs / 2, frames[b + 1].durationMs / 2)
                .coerceAtLeast(0L)
        }

        val segments = ArrayList<VideoSegment>()
        var frameIndex = 0
        val scratch = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratch)

        for (i in frames.indices) {
            // Steady portion: full image minus the half-transitions on each side.
            val dLeft = if (i > 0) effective[i - 1] else 0L
            val dRight = if (i < boundaries) effective[i] else 0L
            val steady = (frames[i].durationMs - dLeft / 2 - dRight / 2).coerceAtLeast(MIN_STEADY_MS)
            segments += VideoSegment(
                uri = videoStorage.writeSlideshowFrame(canvasBitmaps[i], frameIndex++).toString(),
                isImage = true,
                imageDurationMs = steady,
            )

            // Transition into the next image.
            if (i < boundaries && effective[i] > 0L) {
                val kind = transitions.getOrElse(i) { SlideTransition.NONE }
                val d = effective[i]
                val count = (d / 1000f * FPS).roundToInt().coerceIn(MIN_FRAMES, MAX_FRAMES)
                val base = d / count
                for (f in 0 until count) {
                    val t = (f + 0.5f) / count
                    scratch.eraseColor(Color.BLACK)
                    transitionRenderer.render(
                        scratchCanvas, canvasBitmaps[i], canvasBitmaps[i + 1], kind, t, canvasW, canvasH,
                    )
                    // Last frame absorbs the rounding remainder so the total holds.
                    val dur = if (f == count - 1) d - base * (count - 1) else base
                    segments += VideoSegment(
                        uri = videoStorage.writeSlideshowFrame(scratch, frameIndex++).toString(),
                        isImage = true,
                        imageDurationMs = dur.coerceAtLeast(1L),
                    )
                }
            }
        }

        scratch.recycle()
        canvasBitmaps.forEach { it.recycle() }
        return segments
    }

    /** Output canvas dimensions (even, for the encoder) for a width/height [ratio]. */
    private fun canvasSize(ratio: Float): Pair<Int, Int> {
        val safe = if (ratio.isFinite() && ratio > 0f) ratio else 16f / 9f
        val (w, h) = if (safe >= 1f) LONG_EDGE.toFloat() to LONG_EDGE / safe
        else LONG_EDGE * safe to LONG_EDGE.toFloat()
        return even(w) to even(h)
    }

    private fun even(value: Float): Int {
        val v = value.roundToInt().coerceAtLeast(2)
        return if (v % 2 == 0) v else v + 1
    }

    /** Scales [src] to cover the whole [w]×[h] canvas (center-crop), on black. */
    private fun coverFit(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate((w - dw) / 2f, (h - dh) / 2f)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private companion object {
        /** Longest output edge, in pixels; keeps baked-frame count/size sane. */
        const val LONG_EDGE = 1280

        /** Frame rate the transition windows are sampled at. */
        const val FPS = 24

        const val MIN_FRAMES = 2
        const val MAX_FRAMES = 60

        /** A steady image never shrinks below this (guards against tiny durations). */
        const val MIN_STEADY_MS = 1L
    }
}
